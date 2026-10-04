#include "unity.h"
#include "class/hid/hid.h"
#include "keymap.h"
#include "tests.h"

static kbd_key_t s_keys[KEYMAP_MAX_ENTRIES];
static size_t s_count;

#define MOD_KEY(m) ((kbd_key_t){ .code = 0, .modifier = (m) })
#define REG_KEY(c) ((kbd_key_t){ .code = (c), .modifier = 0 })

static void assert_key(kbd_key_t expected, kbd_key_t actual)
{
    TEST_ASSERT_EQUAL_HEX8_MESSAGE(expected.code, actual.code, "code");
    TEST_ASSERT_EQUAL_HEX8_MESSAGE(expected.modifier, actual.modifier, "modifier");
}

static void assert_rejected(const char *csv)
{
    s_count = 99;
    TEST_ASSERT_FALSE_MESSAGE(keymap_parse(csv, s_keys, &s_count), csv);
    TEST_ASSERT_EQUAL_MESSAGE(0, s_count, csv);
}

static void test_parse_modifier_and_key(void)
{
    TEST_ASSERT_TRUE(keymap_parse("shift,k", s_keys, &s_count));
    TEST_ASSERT_EQUAL(2, s_count);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_LEFTSHIFT), s_keys[0]);
    assert_key(REG_KEY(HID_KEY_K), s_keys[1]);
}

static void test_parse_keeps_request_order(void)
{
    TEST_ASSERT_TRUE(keymap_parse("k,shift,1", s_keys, &s_count));
    TEST_ASSERT_EQUAL(3, s_count);
    assert_key(REG_KEY(HID_KEY_K), s_keys[0]);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_LEFTSHIFT), s_keys[1]);
    assert_key(REG_KEY(HID_KEY_1), s_keys[2]);
}

static void test_parse_is_case_insensitive_and_trims(void)
{
    TEST_ASSERT_TRUE(keymap_parse("  Ctrl , ALT,\tDelete ", s_keys, &s_count));
    TEST_ASSERT_EQUAL(3, s_count);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_LEFTCTRL), s_keys[0]);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_LEFTALT), s_keys[1]);
    assert_key(REG_KEY(HID_KEY_DELETE), s_keys[2]);
}

static void test_parse_empty_is_valid(void)
{
    s_count = 99;
    TEST_ASSERT_TRUE(keymap_parse("", s_keys, &s_count));
    TEST_ASSERT_EQUAL(0, s_count);

    s_count = 99;
    TEST_ASSERT_TRUE(keymap_parse("   ", s_keys, &s_count));
    TEST_ASSERT_EQUAL(0, s_count);
}

static void test_parse_aliases(void)
{
    TEST_ASSERT_TRUE(keymap_parse("cmd,esc,period", s_keys, &s_count));
    TEST_ASSERT_EQUAL(3, s_count);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_LEFTGUI), s_keys[0]);
    assert_key(REG_KEY(HID_KEY_ESCAPE), s_keys[1]);
    assert_key(REG_KEY(HID_KEY_PERIOD), s_keys[2]);
}

static void test_parse_special_names(void)
{
    TEST_ASSERT_TRUE(keymap_parse("comma,f12,rshift,up", s_keys, &s_count));
    TEST_ASSERT_EQUAL(4, s_count);
    assert_key(REG_KEY(HID_KEY_COMMA), s_keys[0]);
    assert_key(REG_KEY(HID_KEY_F12), s_keys[1]);
    assert_key(MOD_KEY(KEYBOARD_MODIFIER_RIGHTSHIFT), s_keys[2]);
    assert_key(REG_KEY(HID_KEY_ARROW_UP), s_keys[3]);
}

static void test_parse_rejects_unknown_names(void)
{
    assert_rejected("foo");
    assert_rejected("shift,foo");
    assert_rejected("f13");
    assert_rejected("shiftk");
}

static void test_parse_rejects_empty_entries(void)
{
    assert_rejected("shift,,k");
    assert_rejected("a,");
    assert_rejected(",a");
    assert_rejected(",");
    assert_rejected("a, ,b");
}

static void test_parse_rejects_duplicates(void)
{
    assert_rejected("a,a");
    assert_rejected("a,A");
    assert_rejected("shift,lshift");
    assert_rejected("gui,cmd");
}

static void test_parse_six_keys_max(void)
{
    TEST_ASSERT_TRUE(keymap_parse("a,b,c,d,e,f", s_keys, &s_count));
    TEST_ASSERT_EQUAL(6, s_count);
    assert_rejected("a,b,c,d,e,f,g");
}

static void test_parse_modifiers_do_not_count_toward_six(void)
{
    TEST_ASSERT_TRUE(keymap_parse("ctrl,shift,alt,gui,rctrl,rshift,ralt,rgui,a,b,c,d,e,f", s_keys, &s_count));
    TEST_ASSERT_EQUAL(KEYMAP_MAX_ENTRIES, s_count);
}

static void test_name_is_canonical(void)
{
    TEST_ASSERT_EQUAL_STRING("shift", keymap_name(MOD_KEY(KEYBOARD_MODIFIER_LEFTSHIFT)));
    TEST_ASSERT_EQUAL_STRING("gui", keymap_name(MOD_KEY(KEYBOARD_MODIFIER_LEFTGUI)));
    TEST_ASSERT_EQUAL_STRING("esc", keymap_name(REG_KEY(HID_KEY_ESCAPE)));
    TEST_ASSERT_EQUAL_STRING("k", keymap_name(REG_KEY(HID_KEY_K)));
    TEST_ASSERT_EQUAL_STRING("comma", keymap_name(REG_KEY(HID_KEY_COMMA)));
    TEST_ASSERT_EQUAL_STRING("?", keymap_name(REG_KEY(0xEE)));
}

static void test_name_round_trips_through_parse(void)
{
    static const char *names[] = { "ctrl", "rgui", "a", "z", "0", "9", "f1", "f10", "enter",
                                   "backspace", "quote", "grave", "pagedown", "left" };
    for (size_t i = 0; i < sizeof(names) / sizeof(names[0]); i++) {
        TEST_ASSERT_TRUE_MESSAGE(keymap_parse(names[i], s_keys, &s_count), names[i]);
        TEST_ASSERT_EQUAL(1, s_count);
        TEST_ASSERT_EQUAL_STRING(names[i], keymap_name(s_keys[0]));
    }
}

void run_keymap_tests(void)
{
    RUN_TEST(test_parse_modifier_and_key);
    RUN_TEST(test_parse_keeps_request_order);
    RUN_TEST(test_parse_is_case_insensitive_and_trims);
    RUN_TEST(test_parse_empty_is_valid);
    RUN_TEST(test_parse_aliases);
    RUN_TEST(test_parse_special_names);
    RUN_TEST(test_parse_rejects_unknown_names);
    RUN_TEST(test_parse_rejects_empty_entries);
    RUN_TEST(test_parse_rejects_duplicates);
    RUN_TEST(test_parse_six_keys_max);
    RUN_TEST(test_parse_modifiers_do_not_count_toward_six);
    RUN_TEST(test_name_is_canonical);
    RUN_TEST(test_name_round_trips_through_parse);
}
