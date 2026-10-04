#include "unity.h"
#include "tests.h"

void setUp(void)
{
    kbd_test_reset();
}

void tearDown(void)
{
}

int main(void)
{
    UNITY_BEGIN();
    run_keymap_tests();
    run_kbd_tests();
    return UNITY_END();
}
