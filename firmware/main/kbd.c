#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "ble_svc.h"
#include "usb_kbd.h"
#include "kbd.h"

static const char *TAG = "KBD";

#define HEARTBEAT_CHECK_MS 100
#define KBD_QUEUE_LEN      16

typedef enum {
    CMD_TAP,
    CMD_SET,
    CMD_RELEASE_ALL,
} kbd_cmd_type_t;

typedef struct {
    kbd_cmd_type_t type;
    uint8_t count;
    kbd_key_t keys[KEYMAP_MAX_ENTRIES];
} kbd_cmd_t;

static QueueHandle_t s_queue;

// Keys held via "keypress", in press order. Written by the kbd task, read by BLE.
static SemaphoreHandle_t s_lock;
static kbd_key_t s_held[KEYMAP_MAX_ENTRIES];
static size_t s_held_count;

// Keys currently down in the HID report (held + tap in progress). Only touched by the kbd task.
static kbd_key_t s_down[KEYMAP_MAX_ENTRIES * 2];
static size_t s_down_count;

static volatile int64_t s_last_heartbeat_us;
static volatile bool s_release_pending;

static bool list_contains(const kbd_key_t *list, size_t count, kbd_key_t key)
{
    for (size_t i = 0; i < count; i++) {
        if (kbd_key_eq(list[i], key)) {
            return true;
        }
    }
    return false;
}

static void send_report(void)
{
    uint8_t modifier = 0;
    uint8_t keycodes[KEYMAP_MAX_KEYS] = {0};
    size_t n = 0;

    for (size_t i = 0; i < s_down_count; i++) {
        if (s_down[i].modifier) {
            modifier |= s_down[i].modifier;
        } else if (n < KEYMAP_MAX_KEYS) {
            keycodes[n++] = s_down[i].code;
        } else {
            ESP_LOGW(TAG, "More than %d keys down, extra key not reported", KEYMAP_MAX_KEYS);
        }
    }
    usb_kbd_send(modifier, keycodes);
}

static void key_down(kbd_key_t key)
{
    if (list_contains(s_down, s_down_count, key) || s_down_count >= sizeof(s_down) / sizeof(s_down[0])) {
        return;
    }
    s_down[s_down_count++] = key;
    send_report();
}

static void key_up(kbd_key_t key)
{
    for (size_t i = 0; i < s_down_count; i++) {
        if (kbd_key_eq(s_down[i], key)) {
            memmove(&s_down[i], &s_down[i + 1], (s_down_count - i - 1) * sizeof(s_down[0]));
            s_down_count--;
            send_report();
            return;
        }
    }
}

static void set_held(const kbd_key_t *keys, size_t count)
{
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (count > 0) {
        memcpy(s_held, keys, count * sizeof(keys[0]));
    }
    s_held_count = count;
    xSemaphoreGive(s_lock);
    ble_svc_notify_keypress();
}

static void handle_tap(const kbd_cmd_t *cmd)
{
    for (size_t i = 0; i < cmd->count; i++) {
        key_down(cmd->keys[i]);
    }
    for (size_t i = 0; i < cmd->count; i++) {
        // Keys held via keypress stay down
        if (!list_contains(s_held, s_held_count, cmd->keys[i])) {
            key_up(cmd->keys[i]);
        }
    }
}

static void handle_set(const kbd_cmd_t *cmd)
{
    bool changed = cmd->count != s_held_count;

    for (size_t i = 0; i < s_held_count; i++) {
        if (!list_contains(cmd->keys, cmd->count, s_held[i])) {
            key_up(s_held[i]);
            changed = true;
        }
    }
    for (size_t i = 0; i < cmd->count; i++) {
        if (!list_contains(s_held, s_held_count, cmd->keys[i])) {
            key_down(cmd->keys[i]);
            changed = true;
        }
    }
    if (changed) {
        set_held(cmd->keys, cmd->count);
    }
}

static void handle_release_all(void)
{
    if (s_held_count > 0) {
        for (size_t i = 0; i < s_held_count; i++) {
            key_up(s_held[i]);
        }
        set_held(NULL, 0);
    }
    s_release_pending = false;
}

bool kbd_process_next(TickType_t wait)
{
    kbd_cmd_t cmd;
    if (xQueueReceive(s_queue, &cmd, wait) != pdTRUE) {
        return false;
    }
    switch (cmd.type) {
    case CMD_TAP:
        handle_tap(&cmd);
        break;
    case CMD_SET:
        handle_set(&cmd);
        break;
    case CMD_RELEASE_ALL:
        handle_release_all();
        break;
    }
    return true;
}

static void kbd_task(void *arg)
{
    for (;;) {
        kbd_process_next(portMAX_DELAY);
    }
}

static void heartbeat_check_cb(void *arg)
{
    int64_t age_us = esp_timer_get_time() - s_last_heartbeat_us;
    if (s_held_count > 0 && !s_release_pending && age_us > HEARTBEAT_TIMEOUT_MS * 1000LL) {
        ESP_LOGW(TAG, "Heartbeat timeout, releasing held keys");
        kbd_release_all();
    }
}

static bool enqueue(kbd_cmd_type_t type, const kbd_key_t *keys, size_t count)
{
    kbd_cmd_t cmd = { .type = type, .count = (uint8_t)count };
    if (count > KEYMAP_MAX_ENTRIES) {
        return false;
    }
    if (count > 0) {
        memcpy(cmd.keys, keys, count * sizeof(keys[0]));
    }
    if (xQueueSend(s_queue, &cmd, 0) != pdTRUE) {
        ESP_LOGW(TAG, "Command queue full");
        return false;
    }
    return true;
}

void kbd_init(void)
{
    s_lock = xSemaphoreCreateMutex();
    s_queue = xQueueCreate(KBD_QUEUE_LEN, sizeof(kbd_cmd_t));
    configASSERT(s_lock && s_queue);
    xTaskCreate(kbd_task, "kbd", 3072, NULL, 6, NULL);

    const esp_timer_create_args_t timer_args = {
        .callback = heartbeat_check_cb,
        .name = "heartbeat",
    };
    esp_timer_handle_t timer;
    ESP_ERROR_CHECK(esp_timer_create(&timer_args, &timer));
    ESP_ERROR_CHECK(esp_timer_start_periodic(timer, HEARTBEAT_CHECK_MS * 1000));
}

bool kbd_tap(const kbd_key_t *keys, size_t count)
{
    return enqueue(CMD_TAP, keys, count);
}

bool kbd_set(const kbd_key_t *keys, size_t count)
{
    kbd_heartbeat();
    return enqueue(CMD_SET, keys, count);
}

void kbd_release_all(void)
{
    s_release_pending = true;
    if (!enqueue(CMD_RELEASE_ALL, NULL, 0)) {
        s_release_pending = false;
    }
}

void kbd_heartbeat(void)
{
    s_last_heartbeat_us = esp_timer_get_time();
}

size_t kbd_held_csv(char *buf, size_t size)
{
    size_t len = 0;
    buf[0] = '\0';

    xSemaphoreTake(s_lock, portMAX_DELAY);
    for (size_t i = 0; i < s_held_count; i++) {
        int n = snprintf(buf + len, size - len, "%s%s", i ? "," : "", keymap_name(s_held[i]));
        if (n < 0 || (size_t)n >= size - len) {
            break;
        }
        len += n;
    }
    xSemaphoreGive(s_lock);
    return len;
}
