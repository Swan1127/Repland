package com.swan1127.repland.ui.tasks

/** Empty is unknown; malformed, signed, overflowing and out-of-range input is never unknown. */
internal object FeedbackFormValidation {
    fun validNumber(text: String, range: IntRange, required: Boolean = false): Boolean {
        val raw = text.trim()
        if (raw.isEmpty()) return !required
        return raw.all { it in '0'..'9' } && raw.toIntOrNull()?.let { it in range } == true
    }
}
