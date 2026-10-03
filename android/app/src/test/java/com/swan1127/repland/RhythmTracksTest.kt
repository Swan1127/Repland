package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RhythmTracksTest {
    private val day = LocalDate.of(2026, 10, 3)
    @Test fun `empty tracks retain stable definitions and labels`() {
        val definitions = RhythmTracks.defaults + RhythmTrack("track-language", "语言学习")
        assertEquals(definitions, RhythmTracks.project(emptyList(), definitions).tracks)
    }
    @Test fun `entry lanes use stable ids instead of arrival order`() {
        val definitions = RhythmTracks.defaults + RhythmTrack("track-language", "语言学习")
        val event = TimelineEntry("event", "英语", TimelineKind.TASK, day, 540, 570, trackId = "track-language")
        val first = RhythmTracks.project(listOf(event), definitions)
        val course = TimelineEntry("course", "课程", TimelineKind.COURSE, day, 480, 510, trackId = "course")
        val later = RhythmTracks.project(listOf(event, course), definitions)
        assertEquals(3, first.entries.single().lane)
        assertEquals(3, later.entries.single { it.id == "event" }.lane)
        assertEquals("语言学习", later.tracks[3].name)
        assertEquals("track-language", later.entries.single { it.id == "event" }.trackId)
    }
    @Test fun `legacy overlapping placements remain visible without changing their ids`() {
        val events = listOf("a", "b").map { TimelineEntry(it, it, TimelineKind.TASK, day, 540, 570, trackId = "focus") }
        val projection = RhythmTracks.project(events, RhythmTracks.defaults)
        assertEquals(2, projection.entries.map { it.lane }.distinct().size)
        assertTrue(projection.entries.all { it.trackId == "focus" })
    }
}
