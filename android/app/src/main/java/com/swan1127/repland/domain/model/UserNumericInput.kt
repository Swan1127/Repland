package com.swan1127.repland.domain.model

/** Parse user text without changing it into a different fact. */
object UserNumericInput {
    fun integerIn(text: String, range: IntRange): Int? =
        text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
            ?.toIntOrNull()?.takeIf { it in range }

    fun clockMinute(hour: String, minute: String): Int? {
        val h = integerIn(hour, 0..23) ?: return null
        val m = integerIn(minute, 0..59) ?: return null
        return h * 60 + m
    }
}

/** Existing manual task placement chunk policy; never rewrites task total duration. */
object TaskPlacementPolicy {
    fun durationForTask(totalMinutes: Int?): Int? = when {
        totalMinutes == null -> 30
        totalMinutes !in 1..1_440 -> null
        else -> totalMinutes.coerceIn(15, 240)
    }
}
