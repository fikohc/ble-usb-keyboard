#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "tinyusb.h"
#include "tinyusb_default_config.h"
#include "class/hid/hid_device.h"
#include "ble_svc.h"
#include "kbd.h"
#include "usb_kbd.h"

static const char *TAG = "USB_KBD";

#define HID_READY_TIMEOUT_MS 50

static volatile bool s_connected;

static const uint8_t s_report_desc[] = {
    TUD_HID_REPORT_DESC_KEYBOARD(),
};

#define CONFIG_TOTAL_LEN (TUD_CONFIG_DESC_LEN + TUD_HID_DESC_LEN)

static const uint8_t s_config_desc[] = {
    // Config number, interface count, string index, total length, attribute, power in mA
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, CONFIG_TOTAL_LEN, TUSB_DESC_CONFIG_ATT_REMOTE_WAKEUP, 100),
    // Interface number, string index, boot protocol, report descriptor len, EP In address, size & polling interval
    TUD_HID_DESCRIPTOR(0, 4, HID_ITF_PROTOCOL_KEYBOARD, sizeof(s_report_desc), 0x81, 8, 10),
};

static const char *s_strings[] = {
    (const char[]){ 0x09, 0x04 }, // 0: English (0x0409)
    "Espressif",                  // 1: Manufacturer
    "BLE Keyboard Adapter",       // 2: Product
    "000001",                     // 3: Serial
    "HID Keyboard",               // 4: HID interface
};

// TinyUSB HID callbacks

uint8_t const *tud_hid_descriptor_report_cb(uint8_t instance)
{
    return s_report_desc;
}

uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                               uint8_t *buffer, uint16_t reqlen)
{
    return 0;
}

void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type,
                           uint8_t const *buffer, uint16_t bufsize)
{
    // LED state (caps lock etc.) from the host, not used
}

static void usb_event_cb(tinyusb_event_t *event, void *arg)
{
    switch (event->id) {
    case TINYUSB_EVENT_ATTACHED:
        ESP_LOGI(TAG, "USB host connected");
        s_connected = true;
        ble_svc_notify_state();
        break;
    case TINYUSB_EVENT_DETACHED:
        ESP_LOGI(TAG, "USB host disconnected");
        s_connected = false;
        kbd_release_all();
        ble_svc_notify_state();
        break;
    default:
        break;
    }
}

esp_err_t usb_kbd_init(void)
{
    tinyusb_config_t cfg = TINYUSB_DEFAULT_CONFIG(usb_event_cb);
    cfg.descriptor.string = s_strings;
    cfg.descriptor.string_count = sizeof(s_strings) / sizeof(s_strings[0]);
    cfg.descriptor.full_speed_config = s_config_desc;
    return tinyusb_driver_install(&cfg);
}

bool usb_kbd_connected(void)
{
    return s_connected;
}

bool usb_kbd_send(uint8_t modifier, const uint8_t keycodes[6])
{
    if (!tud_mounted()) {
        return false;
    }
    if (tud_suspended()) {
        tud_remote_wakeup();
    }
    // Tick may be 10 ms (CONFIG_FREERTOS_HZ=100), so wait in whole ticks against a deadline
    TickType_t start = xTaskGetTickCount();
    TickType_t timeout = pdMS_TO_TICKS(HID_READY_TIMEOUT_MS) + 1;
    while (!tud_hid_ready()) {
        if (xTaskGetTickCount() - start > timeout) {
            ESP_LOGW(TAG, "HID endpoint busy, report dropped");
            return false;
        }
        vTaskDelay(1);
    }
    return tud_hid_keyboard_report(0, modifier, keycodes);
}
