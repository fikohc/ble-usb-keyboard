package org.fikohc.espkbd.keys

// The hidden IME text field always holds this text, so Backspace on an "empty" field still
// produces an edit we can see. Long enough that a burst of deletes between frames still fits.
const val SOFT_INPUT_SENTINEL = "xxxxxxxx"

data class SoftEdit(val backspaces: Int, val inserted: String)

// What the IME did to the sentinel text, as keystrokes
fun diffSoftInput(text: String, base: String = SOFT_INPUT_SENTINEL): SoftEdit {
    val common = base.commonPrefixWith(text).length
    return SoftEdit(backspaces = base.length - common, inserted = text.substring(common))
}
