package com.swan1127.repland.ui.schedule

import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulePresentationTest {
    private val date = LocalDate.of(2026, 10, 21)

    @Test
    fun `an empty schedule has no covered clock time`() {
        assertEquals(0, scheduledClockMinutes(emptyList()))
    }

    @Test
    fun `overlapping parallel and nested entries cover their union once`() {
        val entries = listOf(
            entry("course", 540, 600, kind = TimelineKind.COURSE),
            entry("task", 570, 630),
            entry("rest", 555, 585, kind = TimelineKind.REST),
            entry("duplicate-time", 540, 600),
        )

        assertEquals(90, scheduledClockMinutes(entries))
    }

    @Test
    fun `a bridge between unordered ranges does not count overlap or an artificial gap`() {
        val entries = listOf(
            entry("last", 600, 660),
            entry("bridge", 510, 630),
            entry("first", 480, 540),
        )

        assertEquals(180, scheduledClockMinutes(entries))
    }

    @Test
    fun `adjacent ranges include both ranges but separated gaps remain uncovered`() {
        val entries = listOf(
            entry("first", 480, 510),
            entry("adjacent", 510, 540),
            entry("after-gap", 600, 630),
        )

        assertEquals(90, scheduledClockMinutes(entries))
    }

    @Test
    fun `matching clock ranges on distinct dates each contribute time`() {
        val entries = listOf(
            entry("today", 540, 600),
            entry("tomorrow", 540, 600, date = date.plusDays(1)),
        )

        assertEquals(120, scheduledClockMinutes(entries))
    }

    @Test
    fun `a cross midnight arrangement already split at day boundaries retains both slices`() {
        val entries = listOf(
            entry("before-midnight", 1_410, 1_440),
            entry("after-midnight", 0, 60, date = date.plusDays(1)),
        )

        assertEquals(90, scheduledClockMinutes(entries))
    }

    @Test
    fun `full day coverage includes midnight boundaries without exceeding one day`() {
        val entries = listOf(
            entry("whole-day", 0, 1_440),
            entry("nested", 480, 1_320),
        )

        assertEquals(1_440, scheduledClockMinutes(entries))
    }

    @Test
    fun `summarizing does not change the entries or their display order`() {
        val entries = mutableListOf(
            entry("later", 720, 750),
            entry("earlier", 480, 510),
        )
        val original = entries.toList()

        assertEquals(60, scheduledClockMinutes(entries))
        assertEquals(original, entries)
    }

    private fun entry(
        id: String,
        startMinute: Int,
        endMinute: Int,
        date: LocalDate = this.date,
        kind: TimelineKind = TimelineKind.TASK,
    ) = TimelineEntry(
        id = id,
        title = id,
        kind = kind,
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        trackId = id,
    )
}
