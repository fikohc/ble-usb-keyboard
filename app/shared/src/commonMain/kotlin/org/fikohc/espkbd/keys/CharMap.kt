package org.fikohc.espkbd.keys

// Typed character → key combination on a US layout, as a firmware CSV ("shift,a")
private val charKeys: Map<Char, String> = buildMap {
    for (c in 'a'..'z') {
        put(c, c.toString())
        put(c.uppercaseChar(), "shift,$c")
    }
    for (c in '0'..'9') put(c, c.toString())
    ")!@#$%^&*(".forEachIndexed { digit, c -> put(c, "shift,$digit") }

    put(' ', "space")
    put('\n', "enter")
    put('\t', "tab")

    val punctuation = listOf(
        Triple('-', '_', "minus"),
        Triple('=', '+', "equal"),
        Triple('[', '{', "leftbrace"),
        Triple(']', '}', "rightbrace"),
        Triple('\\', '|', "backslash"),
        Triple(';', ':', "semicolon"),
        Triple('\'', '"', "quote"),
        Triple('`', '~', "grave"),
        Triple(',', '<', "comma"),
        Triple('.', '>', "dot"),
        Triple('/', '?', "slash"),
    )
    for ((plain, shifted, name) in punctuation) {
        put(plain, name)
        put(shifted, "shift,$name")
    }
}

// Null for characters a US keyboard can't type
fun charToKeys(c: Char): String? = charKeys[c]
