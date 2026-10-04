#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Boot keyboard report: 8 modifier bits + 6 regular keys
#define KEYMAP_MAX_KEYS     6
#define KEYMAP_MAX_ENTRIES  (8 + KEYMAP_MAX_KEYS)

typedef struct {
    uint8_t code;      // HID usage code, 0 for modifiers
    uint8_t modifier;  // KEYBOARD_MODIFIER_* bit, 0 for regular keys
} kbd_key_t;

// Parse a comma-separated, case-insensitive list like "shift,k".
// An empty string is valid and yields 0 entries.
// Fails on unknown names, empty entries, duplicates or more than 6 regular keys.
bool keymap_parse(const char *csv, kbd_key_t *out, size_t *count);

// Canonical name of a key (e.g. "shift", "k"), or "?" if unknown
const char *keymap_name(kbd_key_t key);

static inline bool kbd_key_eq(kbd_key_t a, kbd_key_t b)
{
    return a.code == b.code && a.modifier == b.modifier;
}
