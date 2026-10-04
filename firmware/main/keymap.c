#include <ctype.h>
#include <string.h>
#include <strings.h>
#include "class/hid/hid.h"
#include "keymap.h"

typedef struct {
    const char *name;
    uint8_t code;
    uint8_t modifier;
} keymap_entry_t;

#define KEY(n, c) { n, c, 0 }
#define MOD(n, m) { n, 0, m }

// The first name for a given key is its canonical name (used for read/notify)
static const keymap_entry_t s_keymap[] = {
    // Modifiers
    MOD("ctrl", KEYBOARD_MODIFIER_LEFTCTRL),   MOD("lctrl", KEYBOARD_MODIFIER_LEFTCTRL),
    MOD("shift", KEYBOARD_MODIFIER_LEFTSHIFT), MOD("lshift", KEYBOARD_MODIFIER_LEFTSHIFT),
    MOD("alt", KEYBOARD_MODIFIER_LEFTALT),     MOD("lalt", KEYBOARD_MODIFIER_LEFTALT),
    MOD("gui", KEYBOARD_MODIFIER_LEFTGUI),     MOD("win", KEYBOARD_MODIFIER_LEFTGUI),
    MOD("cmd", KEYBOARD_MODIFIER_LEFTGUI),     MOD("meta", KEYBOARD_MODIFIER_LEFTGUI),
    MOD("rctrl", KEYBOARD_MODIFIER_RIGHTCTRL), MOD("rshift", KEYBOARD_MODIFIER_RIGHTSHIFT),
    MOD("ralt", KEYBOARD_MODIFIER_RIGHTALT),   MOD("rgui", KEYBOARD_MODIFIER_RIGHTGUI),

    // Letters
    KEY("a", HID_KEY_A), KEY("b", HID_KEY_B), KEY("c", HID_KEY_C), KEY("d", HID_KEY_D),
    KEY("e", HID_KEY_E), KEY("f", HID_KEY_F), KEY("g", HID_KEY_G), KEY("h", HID_KEY_H),
    KEY("i", HID_KEY_I), KEY("j", HID_KEY_J), KEY("k", HID_KEY_K), KEY("l", HID_KEY_L),
    KEY("m", HID_KEY_M), KEY("n", HID_KEY_N), KEY("o", HID_KEY_O), KEY("p", HID_KEY_P),
    KEY("q", HID_KEY_Q), KEY("r", HID_KEY_R), KEY("s", HID_KEY_S), KEY("t", HID_KEY_T),
    KEY("u", HID_KEY_U), KEY("v", HID_KEY_V), KEY("w", HID_KEY_W), KEY("x", HID_KEY_X),
    KEY("y", HID_KEY_Y), KEY("z", HID_KEY_Z),

    // Digits
    KEY("1", HID_KEY_1), KEY("2", HID_KEY_2), KEY("3", HID_KEY_3), KEY("4", HID_KEY_4),
    KEY("5", HID_KEY_5), KEY("6", HID_KEY_6), KEY("7", HID_KEY_7), KEY("8", HID_KEY_8),
    KEY("9", HID_KEY_9), KEY("0", HID_KEY_0),

    // Function keys
    KEY("f1", HID_KEY_F1), KEY("f2", HID_KEY_F2),   KEY("f3", HID_KEY_F3),   KEY("f4", HID_KEY_F4),
    KEY("f5", HID_KEY_F5), KEY("f6", HID_KEY_F6),   KEY("f7", HID_KEY_F7),   KEY("f8", HID_KEY_F8),
    KEY("f9", HID_KEY_F9), KEY("f10", HID_KEY_F10), KEY("f11", HID_KEY_F11), KEY("f12", HID_KEY_F12),

    // Control and punctuation
    KEY("enter", HID_KEY_ENTER),
    KEY("esc", HID_KEY_ESCAPE), KEY("escape", HID_KEY_ESCAPE),
    KEY("backspace", HID_KEY_BACKSPACE),
    KEY("tab", HID_KEY_TAB),
    KEY("space", HID_KEY_SPACE),
    KEY("minus", HID_KEY_MINUS),
    KEY("equal", HID_KEY_EQUAL),
    KEY("leftbrace", HID_KEY_BRACKET_LEFT),
    KEY("rightbrace", HID_KEY_BRACKET_RIGHT),
    KEY("backslash", HID_KEY_BACKSLASH),
    KEY("semicolon", HID_KEY_SEMICOLON),
    KEY("quote", HID_KEY_APOSTROPHE),
    KEY("grave", HID_KEY_GRAVE),
    KEY("comma", HID_KEY_COMMA),
    KEY("dot", HID_KEY_PERIOD), KEY("period", HID_KEY_PERIOD),
    KEY("slash", HID_KEY_SLASH),
    KEY("capslock", HID_KEY_CAPS_LOCK),

    // Navigation
    KEY("printscreen", HID_KEY_PRINT_SCREEN),
    KEY("scrolllock", HID_KEY_SCROLL_LOCK),
    KEY("pause", HID_KEY_PAUSE),
    KEY("insert", HID_KEY_INSERT),
    KEY("home", HID_KEY_HOME),
    KEY("pageup", HID_KEY_PAGE_UP),
    KEY("delete", HID_KEY_DELETE),
    KEY("end", HID_KEY_END),
    KEY("pagedown", HID_KEY_PAGE_DOWN),
    KEY("right", HID_KEY_ARROW_RIGHT),
    KEY("left", HID_KEY_ARROW_LEFT),
    KEY("down", HID_KEY_ARROW_DOWN),
    KEY("up", HID_KEY_ARROW_UP),
};

#define KEYMAP_LEN (sizeof(s_keymap) / sizeof(s_keymap[0]))

static const keymap_entry_t *lookup(const char *name, size_t len)
{
    for (size_t i = 0; i < KEYMAP_LEN; i++) {
        if (strlen(s_keymap[i].name) == len && strncasecmp(s_keymap[i].name, name, len) == 0) {
            return &s_keymap[i];
        }
    }
    return NULL;
}

bool keymap_parse(const char *csv, kbd_key_t *out, size_t *count)
{
    size_t n = 0;
    size_t regular = 0;
    *count = 0;

    // Whole string blank -> empty list
    const char *p = csv;
    while (isspace((unsigned char)*p)) {
        p++;
    }
    if (*p == '\0') {
        return true;
    }

    p = csv;
    for (;;) {
        const char *end = strchr(p, ',');
        size_t len = end ? (size_t)(end - p) : strlen(p);

        // Trim whitespace around the entry
        const char *start = p;
        while (len > 0 && isspace((unsigned char)*start)) {
            start++;
            len--;
        }
        while (len > 0 && isspace((unsigned char)start[len - 1])) {
            len--;
        }
        if (len == 0) {
            return false;
        }

        const keymap_entry_t *e = lookup(start, len);
        if (e == NULL || n >= KEYMAP_MAX_ENTRIES) {
            return false;
        }
        kbd_key_t key = { .code = e->code, .modifier = e->modifier };
        for (size_t i = 0; i < n; i++) {
            if (kbd_key_eq(out[i], key)) {
                return false;
            }
        }
        if (key.code != 0 && ++regular > KEYMAP_MAX_KEYS) {
            return false;
        }
        out[n++] = key;

        if (end == NULL) {
            break;
        }
        p = end + 1;
    }

    *count = n;
    return true;
}

const char *keymap_name(kbd_key_t key)
{
    for (size_t i = 0; i < KEYMAP_LEN; i++) {
        if (s_keymap[i].code == key.code && s_keymap[i].modifier == key.modifier) {
            return s_keymap[i].name;
        }
    }
    return "?";
}
