#pragma once

#include <stddef.h>
#include <stdint.h>

#define FAKE_MAX_REPORTS 64

typedef struct {
    uint8_t modifier;
    uint8_t keycodes[6];
} fake_report_t;

// Reports passed to usb_kbd_send(), in order
extern fake_report_t fake_reports[FAKE_MAX_REPORTS];
extern size_t fake_report_count;

// Calls to ble_svc_notify_keypress()
extern int fake_keypress_notify_count;

// Clear recorded reports and notifies (does not touch kbd state)
void fakes_clear(void);

// Fake clock used by esp_timer_get_time()
void fake_time_set_ms(int64_t ms);

// Run the periodic esp_timer callback registered by kbd_init()
void fake_timer_fire(void);
