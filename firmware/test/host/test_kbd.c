#include <stdbool.h>
#include "unity.h"
#include "class/hid/hid.h"
#include "kbd.h"
#include "fakes.h"
#include "tests.h"

#define SHIFT KEYBOARD_MODIFIER_LEFTSHIFT
#define CTRL  KEYBOARD_MODIFIER_LEFTCTRL

// Expect report i to have this modifier byte and these keycodes (rest zero)
#define EXPECT_REPORT(i, mod, ...)                                                   \
    do {                                                                             \
        const uint8_t expected_[6] = { __VA_ARGS__ };                                \
        TEST_ASSERT_TRUE_MESSAGE((i) < fake_report_count, "missing report");         \
        TEST_ASSERT_EQUAL_HEX8_MESSAGE((mod), fake_reports[i].modifier, "modifier"); \
        TEST_ASSERT_EQUAL_HEX8_ARRAY(expected_, fake_reports[i].keycodes, 6);        \
    } while (0)

static int drain(void)
{
    int n = 0;
    while (kbd_process_next(0)) {
        n++;
    }
    return n;
}

static void parse(const char *csv, kbd_key_t *keys, size_t *count)
{
    TEST_ASSERT_TRUE_MESSAGE(keymap_parse(csv, keys, count), csv);
}

static void keypress(const char *csv)
{
    kbd_key_t keys[KEYMAP_MAX_ENTRIES];
    size_t count;
    parse(csv, keys, &count);
    TEST_ASSERT_TRUE(kbd_set(keys, count));
    drain();
}

static void tap(const char *csv)
{
    kbd_key_t keys[KEYMAP_MAX_ENTRIES];
    size_t count;
    parse(csv, keys, &count);
    TEST_ASSERT_TRUE(kbd_tap(keys, count));
    drain();
}

static void assert_held(const char *expected)
{
    char buf[256];
    kbd_held_csv(buf, sizeof(buf));
    TEST_ASSERT_EQUAL_STRING(expected, buf);
}

void kbd_test_reset(void)
{
    static bool initialized;
    if (!initialized) {
        kbd_init();
        initialized = true;
    }
    kbd_release_all();
    drain();
    fake_time_set_ms(0);
    kbd_heartbeat();
    fakes_clear();
}

// keypress

static void test_keypress_presses_in_request_order(void)
{
    keypress("a,b,c");
    TEST_ASSERT_EQUAL(3, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_A);
    EXPECT_REPORT(1, 0, HID_KEY_A, HID_KEY_B);
    EXPECT_REPORT(2, 0, HID_KEY_A, HID_KEY_B, HID_KEY_C);
    assert_held("a,b,c");
}

static void test_keypress_adds_to_held_keys(void)
{
    keypress("shift");
    keypress("shift,a");
    TEST_ASSERT_EQUAL(2, fake_report_count);
    EXPECT_REPORT(0, SHIFT, 0);
    EXPECT_REPORT(1, SHIFT, HID_KEY_A);
    assert_held("shift,a");
}

static void test_keypress_releases_missing_before_pressing_new(void)
{
    keypress("a,b");
    fakes_clear();

    keypress("b,c");
    TEST_ASSERT_EQUAL(2, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_B);            // a released first
    EXPECT_REPORT(1, 0, HID_KEY_B, HID_KEY_C); // then c pressed
    assert_held("b,c");
}

static void test_keypress_empty_releases_all_in_held_order(void)
{
    keypress("ctrl,a,b");
    fakes_clear();

    keypress("");
    TEST_ASSERT_EQUAL(3, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_A, HID_KEY_B);
    EXPECT_REPORT(1, 0, HID_KEY_B);
    EXPECT_REPORT(2, 0, 0);
    assert_held("");
}

static void test_keypress_same_keys_sends_nothing(void)
{
    keypress("shift,a");
    fakes_clear();

    keypress("shift,a");
    TEST_ASSERT_EQUAL(0, fake_report_count);
    TEST_ASSERT_EQUAL(0, fake_keypress_notify_count);
    assert_held("shift,a");
}

static void test_keypress_notifies_on_change(void)
{
    keypress("a");
    TEST_ASSERT_EQUAL(1, fake_keypress_notify_count);
    keypress("a,b");
    TEST_ASSERT_EQUAL(2, fake_keypress_notify_count);
    keypress("");
    TEST_ASSERT_EQUAL(3, fake_keypress_notify_count);
}

// keys (tap)

static void test_tap_presses_all_then_releases_all_in_order(void)
{
    tap("ctrl,c");
    TEST_ASSERT_EQUAL(4, fake_report_count);
    EXPECT_REPORT(0, CTRL, 0);
    EXPECT_REPORT(1, CTRL, HID_KEY_C);
    EXPECT_REPORT(2, 0, HID_KEY_C);
    EXPECT_REPORT(3, 0, 0);
    assert_held("");
}

static void test_tap_does_not_change_held_keys(void)
{
    tap("a,b");
    TEST_ASSERT_EQUAL(0, fake_keypress_notify_count);
    assert_held("");
}

static void test_tap_leaves_held_keys_down(void)
{
    keypress("shift");
    fakes_clear();

    tap("shift,a");
    TEST_ASSERT_EQUAL(2, fake_report_count);
    EXPECT_REPORT(0, SHIFT, HID_KEY_A); // shift already down, only a is pressed
    EXPECT_REPORT(1, SHIFT, 0);         // a released, shift stays
    assert_held("shift");
}

static void test_commands_run_in_queue_order(void)
{
    kbd_key_t keys[KEYMAP_MAX_ENTRIES];
    size_t count;

    parse("a", keys, &count);
    TEST_ASSERT_TRUE(kbd_tap(keys, count));
    parse("b", keys, &count);
    TEST_ASSERT_TRUE(kbd_set(keys, count));
    TEST_ASSERT_EQUAL(2, drain());

    TEST_ASSERT_EQUAL(3, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_A);
    EXPECT_REPORT(1, 0, 0);
    EXPECT_REPORT(2, 0, HID_KEY_B);
}

// heartbeat

static void test_heartbeat_timeout_releases_held_keys(void)
{
    keypress("shift,a");
    fakes_clear();

    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS - 100);
    fake_timer_fire();
    TEST_ASSERT_EQUAL(0, drain());
    assert_held("shift,a");

    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS + 100);
    fake_timer_fire();
    TEST_ASSERT_EQUAL(1, drain());
    TEST_ASSERT_EQUAL(2, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_A);
    EXPECT_REPORT(1, 0, 0);
    TEST_ASSERT_EQUAL(1, fake_keypress_notify_count);
    assert_held("");
}

static void test_heartbeat_keeps_keys_held(void)
{
    keypress("a");

    for (int t = 500; t <= 5000; t += 500) {
        fake_time_set_ms(t);
        kbd_heartbeat();
        fake_timer_fire();
        TEST_ASSERT_EQUAL(0, drain());
    }
    assert_held("a");

    fake_time_set_ms(5000 + HEARTBEAT_TIMEOUT_MS + 1);
    fake_timer_fire();
    drain();
    assert_held("");
}

static void test_keypress_write_counts_as_heartbeat(void)
{
    keypress("a");

    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS - 100);
    keypress("a,b");

    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS + 100);
    fake_timer_fire();
    TEST_ASSERT_EQUAL(0, drain());
    assert_held("a,b");
}

static void test_heartbeat_timeout_ignored_with_nothing_held(void)
{
    fake_time_set_ms(10 * HEARTBEAT_TIMEOUT_MS);
    fake_timer_fire();
    TEST_ASSERT_EQUAL(0, drain());
    TEST_ASSERT_EQUAL(0, fake_report_count);
}

static void test_heartbeat_timeout_queues_release_once(void)
{
    keypress("a");

    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS + 100);
    fake_timer_fire();
    fake_timer_fire(); // fires again before the kbd task ran
    TEST_ASSERT_EQUAL(1, drain());
}

static void test_keys_held_after_timeout_need_new_keypress(void)
{
    keypress("a");
    fake_time_set_ms(HEARTBEAT_TIMEOUT_MS + 100);
    fake_timer_fire();
    drain();
    fakes_clear();

    keypress("a");
    TEST_ASSERT_EQUAL(1, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_A);
    assert_held("a");
}

// release_all

static void test_release_all_releases_in_held_order(void)
{
    keypress("a,b,c");
    fakes_clear();

    kbd_release_all();
    drain();
    TEST_ASSERT_EQUAL(3, fake_report_count);
    EXPECT_REPORT(0, 0, HID_KEY_B, HID_KEY_C);
    EXPECT_REPORT(1, 0, HID_KEY_C);
    EXPECT_REPORT(2, 0, 0);
    assert_held("");
}

void run_kbd_tests(void)
{
    RUN_TEST(test_keypress_presses_in_request_order);
    RUN_TEST(test_keypress_adds_to_held_keys);
    RUN_TEST(test_keypress_releases_missing_before_pressing_new);
    RUN_TEST(test_keypress_empty_releases_all_in_held_order);
    RUN_TEST(test_keypress_same_keys_sends_nothing);
    RUN_TEST(test_keypress_notifies_on_change);
    RUN_TEST(test_tap_presses_all_then_releases_all_in_order);
    RUN_TEST(test_tap_does_not_change_held_keys);
    RUN_TEST(test_tap_leaves_held_keys_down);
    RUN_TEST(test_commands_run_in_queue_order);
    RUN_TEST(test_heartbeat_timeout_releases_held_keys);
    RUN_TEST(test_heartbeat_keeps_keys_held);
    RUN_TEST(test_keypress_write_counts_as_heartbeat);
    RUN_TEST(test_heartbeat_timeout_ignored_with_nothing_held);
    RUN_TEST(test_heartbeat_timeout_queues_release_once);
    RUN_TEST(test_keys_held_after_timeout_need_new_keypress);
    RUN_TEST(test_release_all_releases_in_held_order);
}
