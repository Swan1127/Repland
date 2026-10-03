package com.swan1127.repland.domain.model

import org.junit.Assert.*
import org.junit.Test

class ExecutionSessionTest {
    private val session = ExecutionSession("s", "t", "title", "p", "segment", 1500, 10_000)
    @Test fun background_time_is_counted_but_pause_time_is_not() {
        val paused = session.pause(70_000)
        assertEquals(60_000L, paused.elapsedMillis(700_000))
        val resumed = paused.resume(700_000)
        assertEquals(90_000L, resumed.elapsedMillis(730_000))
    }
    @Test fun elapsed_does_not_become_negative_on_backward_clock_and_is_bounded() {
        assertEquals(0L, session.elapsedMillis(0))
        assertEquals(86_400_000L, session.elapsedMillis(Long.MAX_VALUE))
    }
    @Test fun timer_expiry_does_not_end_the_session_or_mark_task_complete() {
        assertEquals(3_000_000L, session.elapsedMillis(3_010_000))
        assertNull(session.outcome)
        assertNull(session.endedAtEpochMillis)
    }
}
