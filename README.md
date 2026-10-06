# espkbd

Control a computer as if a real keyboard were plugged into it, without carrying a keyboard around.

A small board plugs into the target machine and shows up as an ordinary wired keyboard. You type on
your phone or laptop, and the keystrokes come out of the board. The target machine sees a plain USB
keyboard, so it needs no drivers, apps or setup. That makes it work wherever a real keyboard works:
BIOS and boot menus, servers, kiosks, TVs, single-board computers, locked-down machines.

## Why

- **No spare keyboards.** Leave a board plugged into each machine instead of a keyboard on every desk.
- **One device, many machines.** Control several targets from a single phone or laptop and switch
  between them in the app.
- **Use what's at hand.** Type on your laptop's own keyboard, or on your phone's on-screen keyboard.

## Planned

- Mouse support, so the board can act as a wired mouse as well.

## Docs

- [Key mapping](KEYMAP.md): every supported key and how it's named
- [Build scripts](scripts/README.md): building the firmware and the apps from the console
