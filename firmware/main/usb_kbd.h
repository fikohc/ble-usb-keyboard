#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

// Install TinyUSB as a USB HID boot keyboard
esp_err_t usb_kbd_init(void);

// True while the USB host has the device mounted
bool usb_kbd_connected(void);

// Send one keyboard report. Waits briefly for the HID endpoint to be free.
// Returns false if not mounted or the endpoint stayed busy.
bool usb_kbd_send(uint8_t modifier, const uint8_t keycodes[6]);
