#pragma once

#include <stdbool.h>
#include <stddef.h>
#include "freertos/FreeRTOS.h"
#include "keymap.h"

// Held keys are released if no heartbeat arrives within this time
#define HEARTBEAT_TIMEOUT_MS 1000

void kbd_init(void);

// "keys": press all in order, then release all in order.
// Returns false if the command queue is full.
bool kbd_tap(const kbd_key_t *keys, size_t count);

// "keypress": make exactly these keys held (release missing ones, press new ones in order).
// Also counts as a heartbeat. Returns false if the command queue is full.
bool kbd_set(const kbd_key_t *keys, size_t count);

// Release every held key (heartbeat timeout, BLE/USB disconnect)
void kbd_release_all(void);

void kbd_heartbeat(void);

// Write the held keys as "shift,k" (empty string if none). Returns the length.
size_t kbd_held_csv(char *buf, size_t size);

// Run one queued command, waiting up to `wait` ticks for it. Returns false if none arrived.
// Called in a loop by the kbd task; unit tests call it directly to step the queue.
bool kbd_process_next(TickType_t wait);
