package org.fikohc.espkbd.keys

// The hidden IME text field starts with this text, so Backspace on an "empty" field still
// produces an edit we can see. Edits are diffed against the previous text rather than reset
// each time: changing the text from the app restarts the IME, which drops its Caps Lock state.
const val SOFT_INPUT_SENTINEL = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

// Reset to the sentinel only when the buffer gets this short (Backspaces ate it) or this long
private const val SOFT_INPUT_MIN = 8
private const val SOFT_INPUT_MAX = 256

data class SoftEdit(val backspaces: Int, val inserted: String)

// What the IME did to [base] (the previous text), as keystrokes
fun diffSoftInput(text: String, base: String = SOFT_INPUT_SENTINEL): SoftEdit {
    val common = base.commonPrefixWith(text).length
    return SoftEdit(backspaces = base.length - common, inserted = text.substring(common))
}

// Whether the field should go back to the sentinel (a rare IME restart)
fun softInputNeedsReset(text: String): Boolean = text.length !in SOFT_INPUT_MIN..SOFT_INPUT_MAX
