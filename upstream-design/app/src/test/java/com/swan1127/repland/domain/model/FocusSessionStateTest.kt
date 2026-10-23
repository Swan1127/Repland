package com.swan1127.repland.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FocusSessionStateTest {
    @Test fun `background time is derived from persisted baseline without timer ticks`() {
        val original = FocusSessionState(1_500, runningSinceEpochMillis = 10_000, startRecorded = true)
        val restored = original.copy()
        assertEquals(600_000L, restored.elapsedMillis(610_000))
        assertEquals(10, restored.actualMinutes(610_000))
        assertTrue(restored.startRecorded)
    }

    @Test fun `paused interval is excluded when resuming`() {
        val paused = FocusSessionState(1_500, runningSinceEpochMillis = 1_000).pause(61_000)
        assertEquals(60_000L, paused.elapsedMillis(600_000))
        val resumed = paused.resume(600_000)
        assertEquals(120_000L, resumed.elapsedMillis(660_000))
        assertEquals(2, resumed.actualMinutes(660_000))
    }

    @Test fun `late lifecycle resume caps recorded effort at round length`() {
        val state = FocusSessionState(1_500, runningSinceEpochMillis = 10_000)
        assertTrue(state.finished(9_000_000))
        assertEquals(25, state.actualMinutes(9_000_000))
        assertNull(state.pause(9_000_000).resume(9_100_000).runningSinceEpochMillis)
    }

    @Test fun `an empty round does not invent a minute of effort`() {
        assertEquals(0, FocusSessionState(1_500, runningSinceEpochMillis = 10_000).actualMinutes(10_000))
        assertEquals(0L, FocusSessionState(1_500, runningSinceEpochMillis = 10_000).elapsedMillis(9_000))
    }

    @Test fun `starting near slot end uses its remaining time`() {
        val date = LocalDate.of(2026, 10, 22)
        val entry = TimelineEntry("slot", "学习", TimelineKind.TASK, date, 600, 660, "task")
        assertEquals(600, FocusRound.totalSeconds(entry, FocusSettings(25), date.atTime(10, 50)))
        assertTrue(FocusRound.shortenedBySlot(entry, FocusSettings(25), date.atTime(10, 50)))
    }
}
