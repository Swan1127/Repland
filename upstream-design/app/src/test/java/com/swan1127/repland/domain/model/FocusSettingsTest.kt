package com.swan1127.repland.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focus round is the one place where the app decides how long the user should work, so
 * the rules about what it is allowed to decide are worth pinning:
 *
 * * a stored preference can never make the screen fail to open,
 * * a round never runs past the slot it belongs to,
 * * and the choices offered must stay inside the range the value class accepts — otherwise
 *   the chips would offer something the constructor rejects.
 */
class FocusSettingsTest {

    private fun entry(startMinute: Int, endMinute: Int) = TimelineEntry(
        id = "e1",
        title = "复习",
        kind = TimelineKind.TASK,
        date = LocalDate.of(2026, 3, 2),
        startMinute = startMinute,
        endMinute = endMinute,
    )

    // ---------------------------------------------------------------- the value

    @Test
    fun `the default round is twenty-five minutes`() {
        assertEquals(25, FocusSettings().focusMinutes)
    }

    @Test
    fun `a value below the minimum is clamped rather than rejected`() {
        assertEquals(FocusSettings.MIN_FOCUS_MINUTES, FocusSettings.of(1).focusMinutes)
    }

    @Test
    fun `a value above the maximum is clamped rather than rejected`() {
        // Clamping keeps the user's intent — they asked for a long round — where falling back
        // to the default would silently discard it.
        assertEquals(FocusSettings.MAX_FOCUS_MINUTES, FocusSettings.of(999).focusMinutes)
    }

    @Test
    fun `a missing stored value is the default`() {
        assertEquals(FocusSettings.DEFAULT_FOCUS_MINUTES, FocusSettings.fromStorage(null).focusMinutes)
    }

    @Test
    fun `a stored value is honoured`() {
        assertEquals(45, FocusSettings.fromStorage(45).focusMinutes)
    }

    @Test
    fun `a corrupt stored value is clamped on the way in`() {
        // A preference written by another build, or edited by hand, must not make the focus
        // screen unopenable. Clamping goes to the *nearest* bound, so a negative value lands
        // on the minimum rather than on the maximum.
        assertEquals(FocusSettings.MIN_FOCUS_MINUTES, FocusSettings.fromStorage(0).focusMinutes)
        assertEquals(FocusSettings.MIN_FOCUS_MINUTES, FocusSettings.fromStorage(-30).focusMinutes)
        assertEquals(FocusSettings.MAX_FOCUS_MINUTES, FocusSettings.fromStorage(999).focusMinutes)
    }

    @Test
    fun `the constructor itself refuses an out-of-range value`() {
        assertThrows(IllegalArgumentException::class.java) { FocusSettings(0) }
        assertThrows(IllegalArgumentException::class.java) { FocusSettings(1_000) }
    }

    @Test
    fun `every offered preset is inside the accepted range`() {
        FocusSettings.PRESETS.forEach { minutes ->
            assertEquals("preset $minutes", minutes, FocusSettings.of(minutes).focusMinutes)
        }
    }

    @Test
    fun `the offered presets include the default`() {
        // Otherwise the chips would render with nothing selected on a fresh install.
        assertTrue(
            "the default must be selectable",
            FocusSettings.DEFAULT_FOCUS_MINUTES in FocusSettings.PRESETS,
        )
    }

    // ---------------------------------------------------------------- the round

    @Test
    fun `a long slot runs the user's full setting`() {
        val round = FocusRound.totalSeconds(entry(480, 600), FocusSettings(25))
        assertEquals(25 * 60, round)
        assertFalse(FocusRound.shortenedBySlot(entry(480, 600), FocusSettings(25)))
    }

    @Test
    fun `a short slot shortens the round`() {
        // Starting a 45-minute round inside a 20-minute gap would have the counter run past
        // the block it belongs to.
        val shortSlot = entry(480, 500)
        assertEquals(20 * 60, FocusRound.totalSeconds(shortSlot, FocusSettings(45)))
        assertTrue(FocusRound.shortenedBySlot(shortSlot, FocusSettings(45)))
    }

    @Test
    fun `a slot exactly the setting length is not reported as shortened`() {
        // The boundary belongs to the user's own choice: nothing was taken away from them.
        val exact = entry(480, 505)
        assertEquals(25 * 60, FocusRound.totalSeconds(exact, FocusSettings(25)))
        assertFalse(FocusRound.shortenedBySlot(exact, FocusSettings(25)))
    }

    @Test
    fun `one minute short is already reported as shortened`() {
        assertTrue(FocusRound.shortenedBySlot(entry(480, 504), FocusSettings(25)))
    }

    @Test
    fun `a one minute slot still produces a positive round`() {
        val tiny = entry(600, 601)
        assertEquals(60, FocusRound.totalSeconds(tiny, FocusSettings(25)))
        assertTrue(FocusRound.shortenedBySlot(tiny, FocusSettings(25)))
    }
}
