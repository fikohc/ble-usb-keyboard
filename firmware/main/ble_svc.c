#include <string.h>
#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "kbd.h"
#include "usb_kbd.h"
#include "ble_svc.h"

static const char *TAG = "BLE_SVC";

#define DEVICE_NAME        "BLE-KBD"
#define MAX_WRITE_LEN      128

// Readable 128-bit UUID: UUID128(0xAAAAAAAA, 0xBBBB, 0xCCCC, 0xDDDD, 0xEEEEEEEEEEEE) -> 16 little-endian bytes
#define UUID_B(x, n) ((uint8_t)(((x) >> (8 * (n))) & 0xFF))
#define UUID128(a, b, c, d, e)                                              \
    UUID_B(e, 0), UUID_B(e, 1), UUID_B(e, 2), UUID_B(e, 3), UUID_B(e, 4),   \
    UUID_B(e, 5), UUID_B(d, 0), UUID_B(d, 1), UUID_B(c, 0), UUID_B(c, 1),   \
    UUID_B(b, 0), UUID_B(b, 1), UUID_B(a, 0), UUID_B(a, 1), UUID_B(a, 2),   \
    UUID_B(a, 3)

// 4b9d0000-6a3e-4f8b-9c21-7d5e3a1b8c00 (service), ...0001..0004 (characteristics)
#define KBD_UUID(n) UUID128(0x4b9d0000 + (n), 0x6a3e, 0x4f8b, 0x9c21, 0x7d5e3a1b8c00ULL)

static const ble_uuid128_t s_svc_uuid = BLE_UUID128_INIT(KBD_UUID(0));

typedef enum {
    CHR_KEYS,
    CHR_KEYPRESS,
    CHR_HEARTBEAT,
    CHR_STATE,
} chr_id_t;

static uint8_t s_addr_type;
static int s_conn_count;
static uint16_t s_keypress_handle;
static uint16_t s_state_handle;

static void ble_advertise(void);

static int read_keys(struct ble_gatt_access_ctxt *ctxt, kbd_key_t *keys, size_t *count)
{
    char buf[MAX_WRITE_LEN + 1];
    uint16_t len = OS_MBUF_PKTLEN(ctxt->om);

    if (len > MAX_WRITE_LEN) {
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }
    if (ble_hs_mbuf_to_flat(ctxt->om, buf, len, NULL) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    buf[len] = '\0';
    if (!keymap_parse(buf, keys, count)) {
        ESP_LOGW(TAG, "Rejected key list: '%s'", buf);
        return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
    }
    return 0;
}

static int chr_access(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    chr_id_t id = (chr_id_t)(uintptr_t)arg;
    kbd_key_t keys[KEYMAP_MAX_ENTRIES];
    size_t count;
    int rc;

    switch (ctxt->op) {
    case BLE_GATT_ACCESS_OP_WRITE_CHR:
        if (id == CHR_HEARTBEAT) {
            kbd_heartbeat();
            return 0;
        }
        rc = read_keys(ctxt, keys, &count);
        if (rc != 0) {
            return rc;
        }
        if (id == CHR_KEYS) {
            return kbd_tap(keys, count) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        if (id == CHR_KEYPRESS) {
            return kbd_set(keys, count) ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        return BLE_ATT_ERR_UNLIKELY;

    case BLE_GATT_ACCESS_OP_READ_CHR:
        if (id == CHR_KEYPRESS) {
            char buf[KEYMAP_MAX_ENTRIES * 12];
            size_t len = kbd_held_csv(buf, sizeof(buf));
            return os_mbuf_append(ctxt->om, buf, len) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        if (id == CHR_STATE) {
            char state = usb_kbd_connected() ? '1' : '0';
            return os_mbuf_append(ctxt->om, &state, 1) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        return BLE_ATT_ERR_UNLIKELY;

    default:
        return BLE_ATT_ERR_UNLIKELY;
    }
}

static const struct ble_gatt_svc_def s_gatt_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &s_svc_uuid.u,
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = BLE_UUID128_DECLARE(KBD_UUID(1)),
                .access_cb = chr_access,
                .arg = (void *)CHR_KEYS,
                .flags = BLE_GATT_CHR_F_WRITE,
            },
            {
                .uuid = BLE_UUID128_DECLARE(KBD_UUID(2)),
                .access_cb = chr_access,
                .arg = (void *)CHR_KEYPRESS,
                .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
                .val_handle = &s_keypress_handle,
            },
            {
                .uuid = BLE_UUID128_DECLARE(KBD_UUID(3)),
                .access_cb = chr_access,
                .arg = (void *)CHR_HEARTBEAT,
                .flags = BLE_GATT_CHR_F_WRITE,
            },
            {
                .uuid = BLE_UUID128_DECLARE(KBD_UUID(4)),
                .access_cb = chr_access,
                .arg = (void *)CHR_STATE,
                .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
                .val_handle = &s_state_handle,
            },
            {0}
        },
    },
    {0}
};

void ble_svc_notify_keypress(void)
{
    if (s_keypress_handle) {
        ble_gatts_chr_updated(s_keypress_handle);
    }
}

void ble_svc_notify_state(void)
{
    if (s_state_handle) {
        ble_gatts_chr_updated(s_state_handle);
    }
}

static int ble_gap_event(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        ESP_LOGI(TAG, "Connect, status %d", event->connect.status);
        if (event->connect.status == 0) {
            s_conn_count++;
        }
        // Keep advertising while there are free connection slots
        if (s_conn_count < CONFIG_BT_NIMBLE_MAX_CONNECTIONS) {
            ble_advertise();
        }
        break;

    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "Disconnect, reason %d", event->disconnect.reason);
        if (s_conn_count > 0) {
            s_conn_count--;
        }
        if (s_conn_count == 0) {
            kbd_release_all();
        }
        ble_advertise();
        break;

    case BLE_GAP_EVENT_ADV_COMPLETE:
        ble_advertise();
        break;

    case BLE_GAP_EVENT_SUBSCRIBE:
        ESP_LOGI(TAG, "Subscribe: handle %d notify %d", event->subscribe.attr_handle, event->subscribe.cur_notify);
        break;

    case BLE_GAP_EVENT_MTU:
        ESP_LOGI(TAG, "MTU %d on conn %d", event->mtu.value, event->mtu.conn_handle);
        break;

    default:
        break;
    }
    return 0;
}

static void ble_advertise(void)
{
    struct ble_hs_adv_fields fields = {0};
    struct ble_hs_adv_fields rsp_fields = {0};
    struct ble_gap_adv_params adv_params = {0};
    int rc;

    if (ble_gap_adv_active()) {
        return;
    }

    // Advertising packet: flags + service UUID (3 + 18 bytes)
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = &s_svc_uuid;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "Error setting adv fields: %d", rc);
        return;
    }

    // Scan response: device name
    const char *name = ble_svc_gap_device_name();
    rsp_fields.name = (const uint8_t *)name;
    rsp_fields.name_len = strlen(name);
    rsp_fields.name_is_complete = 1;
    rc = ble_gap_adv_rsp_set_fields(&rsp_fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "Error setting scan response: %d", rc);
        return;
    }

    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(s_addr_type, NULL, BLE_HS_FOREVER, &adv_params, ble_gap_event, NULL);
    if (rc != 0) {
        ESP_LOGE(TAG, "Error starting advertising: %d", rc);
    } else {
        ESP_LOGI(TAG, "Advertising as %s", name);
    }
}

static void ble_on_sync(void)
{
    ble_hs_id_infer_auto(0, &s_addr_type);
    ble_advertise();
}

static void ble_on_reset(int reason)
{
    ESP_LOGW(TAG, "NimBLE host reset, reason %d", reason);
}

static void ble_host_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

void ble_svc_init(void)
{
    ESP_ERROR_CHECK(nimble_port_init());

    ble_hs_cfg.sync_cb = ble_on_sync;
    ble_hs_cfg.reset_cb = ble_on_reset;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    ESP_ERROR_CHECK(ble_svc_gap_device_name_set(DEVICE_NAME));
    ESP_ERROR_CHECK(ble_gatts_count_cfg(s_gatt_svcs));
    ESP_ERROR_CHECK(ble_gatts_add_svcs(s_gatt_svcs));

    nimble_port_freertos_init(ble_host_task);
}
