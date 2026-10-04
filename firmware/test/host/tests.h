#pragma once

void run_keymap_tests(void);
void run_kbd_tests(void);

// Bring kbd back to a clean state (nothing held, time 0, fakes cleared)
void kbd_test_reset(void);
