package com.swan1127.repland.domain.model

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
/**
 * How long one focus round runs.
 *
 * Stored rather than fixed because 25 minutes is a *default*, not a rule. The number comes
 * from research on a particular kind of task, and a person reading a dense chapter needs a
 * different figure from one working through practice problems. Offering four choices and
 * remembering the last one costs nothing and removes a daily annoyance.
 */
data class FocusSettings(val focusMinutes: Int = DEFAULT_FOCUS_MINUTES) {

    init {
        require(focusMinutes in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES) {
            "focusMinutes must be in $MIN_FOCUS_MINUTES..$MAX_FOCUS_MINUTES, was $focusMinutes"
        }
    }

    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 120

        /** The choices the focus screen offers. */
        val PRESETS = listOf(15, 25, 45, 60)

        /**
         * Clamps rather than throws, so a preference written by an older build — or edited by
         * hand — cannot make the screen fail to open. Clamping keeps the user's intent (they
         * asked for a long round) where falling back to the default would discard it.
         */
        fun of(minutes: Int): FocusSettings =
            FocusSettings(minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES))

        /** A missing preference means the user has never chosen, which is the default. */
        fun fromStorage(raw: Int?): FocusSettings = of(raw ?: DEFAULT_FOCUS_MINUTES)
    }
}

/**
 * Turns a [FocusSettings] plus a timeline slot into the round that actually runs.
 *
 * A round is always the *shorter* of the user's setting and the remaining slot: starting a
 * 45-minute round inside a 20-minute gap would have the counter run past the block it
 * belongs to, and the app would then be telling the user to work through their next class.
 */
object FocusRound {

    fun totalSeconds(entry: TimelineEntry, settings: FocusSettings, now: LocalDateTime? = null): Int =
        minOf(settings.focusMinutes * 60, remainingSlotSeconds(entry, now))

    /**
     * True when the slot, not the user, decided the length. The screen says so, because a
     * round that silently ends early looks like a bug.
     */
    fun shortenedBySlot(entry: TimelineEntry, settings: FocusSettings, now: LocalDateTime? = null): Boolean =
        remainingSlotSeconds(entry, now) < settings.focusMinutes * 60

    private fun remainingSlotSeconds(entry: TimelineEntry, now: LocalDateTime?): Int =
        if (now != null && !now.isBefore(entry.start) && now.isBefore(entry.end)) {
            ChronoUnit.SECONDS.between(now, entry.end).toInt().coerceAtLeast(1)
        } else slotMinutes(entry) * 60

    /** [TimelineEntry] forbids an empty slot, but the guard keeps this total for any input. */
    private fun slotMinutes(entry: TimelineEntry): Int =
        (entry.endMinute - entry.startMinute).coerceAtLeast(1)
}
