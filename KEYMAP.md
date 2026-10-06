# Key mapping

How a key travels from the app to the USB host: the app turns a key into a **BLE name**, the firmware
(`firmware/main/keymap.c`) parses that name into a **HID usage code** (or a modifier bit) and puts it
into a USB boot keyboard report.

- App side: `app/shared/src/commonMain/kotlin/org/fikohc/espkbd/keys/KeyNames.kt` (hardware keys),
  `CharMap.kt` (typed characters).
- Firmware side: `firmware/main/keymap.c`, constants from TinyUSB `class/hid/hid.h`.

## BLE format

Keys are sent as a comma-separated, case-insensitive list of names, e.g. `ctrl,shift,k`.

- Written to the *keys* characteristic (`…0001`) → tap (press + release).
- Written to the *keypress* characteristic (`…0002`) → set the held keys; reading/notify returns the held
  keys using canonical names.
- Up to 8 modifiers + 6 regular keys per list (boot keyboard report). Unknown names, empty entries and
  duplicates are rejected.
- The first name in the *BLE name* column is canonical (what the app sends and what the firmware reports);
  aliases are accepted on write only.

## Modifiers

Modifiers have HID code `0` and set a bit in the report's modifier byte.

| Key | BLE name | Aliases | Firmware modifier bit |
|---|---|---|---|
| Left Ctrl | `ctrl` | `lctrl` | `KEYBOARD_MODIFIER_LEFTCTRL` (`0x01`) |
| Left Shift | `shift` | `lshift` | `KEYBOARD_MODIFIER_LEFTSHIFT` (`0x02`) |
| Left Alt | `alt` | `lalt` | `KEYBOARD_MODIFIER_LEFTALT` (`0x04`) |
| Left GUI (Win / Cmd / Meta) | `gui` | `win`, `cmd`, `meta` | `KEYBOARD_MODIFIER_LEFTGUI` (`0x08`) |
| Right Ctrl | `rctrl` |  | `KEYBOARD_MODIFIER_RIGHTCTRL` (`0x10`) |
| Right Shift | `rshift` |  | `KEYBOARD_MODIFIER_RIGHTSHIFT` (`0x20`) |
| Right Alt | `ralt` |  | `KEYBOARD_MODIFIER_RIGHTALT` (`0x40`) |
| Right GUI | `rgui` |  | `KEYBOARD_MODIFIER_RIGHTGUI` (`0x80`) |

## Letters

| Key | BLE name | Aliases | Firmware keycode |
|---|---|---|---|
| A | `a` |  | `HID_KEY_A` (`0x04`) |
| B | `b` |  | `HID_KEY_B` (`0x05`) |
| C | `c` |  | `HID_KEY_C` (`0x06`) |
| D | `d` |  | `HID_KEY_D` (`0x07`) |
| E | `e` |  | `HID_KEY_E` (`0x08`) |
| F | `f` |  | `HID_KEY_F` (`0x09`) |
| G | `g` |  | `HID_KEY_G` (`0x0A`) |
| H | `h` |  | `HID_KEY_H` (`0x0B`) |
| I | `i` |  | `HID_KEY_I` (`0x0C`) |
| J | `j` |  | `HID_KEY_J` (`0x0D`) |
| K | `k` |  | `HID_KEY_K` (`0x0E`) |
| L | `l` |  | `HID_KEY_L` (`0x0F`) |
| M | `m` |  | `HID_KEY_M` (`0x10`) |
| N | `n` |  | `HID_KEY_N` (`0x11`) |
| O | `o` |  | `HID_KEY_O` (`0x12`) |
| P | `p` |  | `HID_KEY_P` (`0x13`) |
| Q | `q` |  | `HID_KEY_Q` (`0x14`) |
| R | `r` |  | `HID_KEY_R` (`0x15`) |
| S | `s` |  | `HID_KEY_S` (`0x16`) |
| T | `t` |  | `HID_KEY_T` (`0x17`) |
| U | `u` |  | `HID_KEY_U` (`0x18`) |
| V | `v` |  | `HID_KEY_V` (`0x19`) |
| W | `w` |  | `HID_KEY_W` (`0x1A`) |
| X | `x` |  | `HID_KEY_X` (`0x1B`) |
| Y | `y` |  | `HID_KEY_Y` (`0x1C`) |
| Z | `z` |  | `HID_KEY_Z` (`0x1D`) |

## Digits

| Key | BLE name | Aliases | Firmware keycode |
|---|---|---|---|
| 1 | `1` |  | `HID_KEY_1` (`0x1E`) |
| 2 | `2` |  | `HID_KEY_2` (`0x1F`) |
| 3 | `3` |  | `HID_KEY_3` (`0x20`) |
| 4 | `4` |  | `HID_KEY_4` (`0x21`) |
| 5 | `5` |  | `HID_KEY_5` (`0x22`) |
| 6 | `6` |  | `HID_KEY_6` (`0x23`) |
| 7 | `7` |  | `HID_KEY_7` (`0x24`) |
| 8 | `8` |  | `HID_KEY_8` (`0x25`) |
| 9 | `9` |  | `HID_KEY_9` (`0x26`) |
| 0 | `0` |  | `HID_KEY_0` (`0x27`) |

## Function keys

| Key | BLE name | Aliases | Firmware keycode |
|---|---|---|---|
| F1 | `f1` |  | `HID_KEY_F1` (`0x3A`) |
| F2 | `f2` |  | `HID_KEY_F2` (`0x3B`) |
| F3 | `f3` |  | `HID_KEY_F3` (`0x3C`) |
| F4 | `f4` |  | `HID_KEY_F4` (`0x3D`) |
| F5 | `f5` |  | `HID_KEY_F5` (`0x3E`) |
| F6 | `f6` |  | `HID_KEY_F6` (`0x3F`) |
| F7 | `f7` |  | `HID_KEY_F7` (`0x40`) |
| F8 | `f8` |  | `HID_KEY_F8` (`0x41`) |
| F9 | `f9` |  | `HID_KEY_F9` (`0x42`) |
| F10 | `f10` |  | `HID_KEY_F10` (`0x43`) |
| F11 | `f11` |  | `HID_KEY_F11` (`0x44`) |
| F12 | `f12` |  | `HID_KEY_F12` (`0x45`) |

## Control and punctuation

| Key | BLE name | Aliases | Firmware keycode |
|---|---|---|---|
| Enter | `enter` |  | `HID_KEY_ENTER` (`0x28`) |
| Escape | `esc` | `escape` | `HID_KEY_ESCAPE` (`0x29`) |
| Backspace | `backspace` |  | `HID_KEY_BACKSPACE` (`0x2A`) |
| Tab | `tab` |  | `HID_KEY_TAB` (`0x2B`) |
| Space | `space` |  | `HID_KEY_SPACE` (`0x2C`) |
| `-` `_` | `minus` |  | `HID_KEY_MINUS` (`0x2D`) |
| `=` `+` | `equal` |  | `HID_KEY_EQUAL` (`0x2E`) |
| `[` `{` | `leftbrace` |  | `HID_KEY_BRACKET_LEFT` (`0x2F`) |
| `]` `}` | `rightbrace` |  | `HID_KEY_BRACKET_RIGHT` (`0x30`) |
| `\` `\|` | `backslash` |  | `HID_KEY_BACKSLASH` (`0x31`) |
| `;` `:` | `semicolon` |  | `HID_KEY_SEMICOLON` (`0x33`) |
| `'` `"` | `quote` |  | `HID_KEY_APOSTROPHE` (`0x34`) |
| `` ` `` `~` | `grave` |  | `HID_KEY_GRAVE` (`0x35`) |
| `,` `<` | `comma` |  | `HID_KEY_COMMA` (`0x36`) |
| `.` `>` | `dot` | `period` | `HID_KEY_PERIOD` (`0x37`) |
| `/` `?` | `slash` |  | `HID_KEY_SLASH` (`0x38`) |
| Caps Lock | `capslock` |  | `HID_KEY_CAPS_LOCK` (`0x39`) |

## Navigation

| Key | BLE name | Aliases | Firmware keycode |
|---|---|---|---|
| Print Screen | `printscreen` |  | `HID_KEY_PRINT_SCREEN` (`0x46`) |
| Scroll Lock | `scrolllock` |  | `HID_KEY_SCROLL_LOCK` (`0x47`) |
| Pause / Break | `pause` |  | `HID_KEY_PAUSE` (`0x48`) |
| Insert | `insert` |  | `HID_KEY_INSERT` (`0x49`) |
| Home | `home` |  | `HID_KEY_HOME` (`0x4A`) |
| Page Up | `pageup` |  | `HID_KEY_PAGE_UP` (`0x4B`) |
| Delete | `delete` |  | `HID_KEY_DELETE` (`0x4C`) |
| End | `end` |  | `HID_KEY_END` (`0x4D`) |
| Page Down | `pagedown` |  | `HID_KEY_PAGE_DOWN` (`0x4E`) |
| → | `right` |  | `HID_KEY_ARROW_RIGHT` (`0x4F`) |
| ← | `left` |  | `HID_KEY_ARROW_LEFT` (`0x50`) |
| ↓ | `down` |  | `HID_KEY_ARROW_DOWN` (`0x51`) |
| ↑ | `up` |  | `HID_KEY_ARROW_UP` (`0x52`) |

## Notes

- Shifted characters (`!`, `A`, `{`, …) have no name of their own; the app sends them as `shift,<key>`
  using a US layout (`CharMap.kt`), e.g. `A` → `shift,a`, `!` → `shift,1`, `?` → `shift,slash`.
- Numpad Enter is sent as `enter`. Other numpad keys, F13+, media and non-US keys are not mapped and
  are ignored by the app / rejected by the firmware.
- To add a key, add an entry to `s_keymap` in `firmware/main/keymap.c` and to `keyNames` in `KeyNames.kt`.
