package org.fikohc.espkbd.keys

// Tracks which keys are physically held, in press order.
// Each method returns true when the held set changed (auto-repeat key downs don't change it).
class HeldKeys {
    private val keys = LinkedHashSet<String>()

    val current: List<String> get() = keys.toList()

    fun press(name: String): Boolean {
        if (name in keys) return false
        if (name !in MODIFIERS && keys.count { it !in MODIFIERS } >= MAX_REGULAR_KEYS) return false
        return keys.add(name)
    }

    fun release(name: String): Boolean = keys.remove(name)

    fun clear(): Boolean {
        if (keys.isEmpty()) return false
        keys.clear()
        return true
    }
}
