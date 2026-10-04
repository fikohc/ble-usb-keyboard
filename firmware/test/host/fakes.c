#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "freertos/queue.h"
#include "esp_timer.h"
#include "usb_kbd.h"
#include "ble_svc.h"
#include "fakes.h"

fake_report_t fake_reports[FAKE_MAX_REPORTS];
size_t fake_report_count;
int fake_keypress_notify_count;

void fakes_clear(void)
{
    memset(fake_reports, 0, sizeof(fake_reports));
    fake_report_count = 0;
    fake_keypress_notify_count = 0;
}

// usb_kbd

bool usb_kbd_send(uint8_t modifier, const uint8_t keycodes[6])
{
    assert(fake_report_count < FAKE_MAX_REPORTS);
    fake_reports[fake_report_count].modifier = modifier;
    memcpy(fake_reports[fake_report_count].keycodes, keycodes, 6);
    fake_report_count++;
    return true;
}

bool usb_kbd_connected(void)
{
    return true;
}

// ble_svc

void ble_svc_notify_keypress(void)
{
    fake_keypress_notify_count++;
}

void ble_svc_notify_state(void)
{
}

// esp_timer

static int64_t s_now_us;
static esp_timer_create_args_t s_timer;

esp_err_t esp_timer_create(const esp_timer_create_args_t *args, esp_timer_handle_t *out)
{
    s_timer = *args;
    *out = (esp_timer_handle_t)&s_timer;
    return ESP_OK;
}

esp_err_t esp_timer_start_periodic(esp_timer_handle_t timer, uint64_t period_us)
{
    return ESP_OK;
}

int64_t esp_timer_get_time(void)
{
    return s_now_us;
}

void fake_time_set_ms(int64_t ms)
{
    s_now_us = ms * 1000;
}

void fake_timer_fire(void)
{
    assert(s_timer.callback);
    s_timer.callback(s_timer.arg);
}

// FreeRTOS queue

struct fake_queue {
    size_t item_size;
    size_t length;
    size_t head;
    size_t count;
    uint8_t *items;
};

QueueHandle_t xQueueCreate(UBaseType_t length, UBaseType_t item_size)
{
    struct fake_queue *q = calloc(1, sizeof(*q));
    q->item_size = item_size;
    q->length = length;
    q->items = calloc(length, item_size);
    return q;
}

BaseType_t xQueueSend(QueueHandle_t q, const void *item, TickType_t wait)
{
    if (q->count == q->length) {
        return pdFALSE;
    }
    size_t tail = (q->head + q->count) % q->length;
    memcpy(q->items + tail * q->item_size, item, q->item_size);
    q->count++;
    return pdTRUE;
}

BaseType_t xQueueReceive(QueueHandle_t q, void *item, TickType_t wait)
{
    if (q->count == 0) {
        return pdFALSE;
    }
    memcpy(item, q->items + q->head * q->item_size, q->item_size);
    q->head = (q->head + 1) % q->length;
    q->count--;
    return pdTRUE;
}
