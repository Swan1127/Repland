package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementWindowTest {

    
    private val availability = listOf(
        weeklyBlock("morning", dayOfWeek = DayOfWeek.MONDAY, startMinute = 480, endMinute = 720),
        weeklyBlock("afternoon", dayOfWeek = DayOfWeek.MONDAY, startMinute = 900, endMinute = 1080),
    )

    private fun plan(tasks: List<Task>, windows: Map<String, PlacementWindow>) =
        PlanGenerator.generate(
            tasks = tasks,
            weeklyBlocks = availability,
            dateOverrides = emptyList(),
            clock = TEST_CLOCK,
            placementWindows = windows,
        )

    @Test
    fun aPreferenceMovesTheBlockToTheWindowItAskedFor() {
        val tasks = listOf(task("late", 90))
        val plain = plan(tasks, emptyMap())
        val preferred = plan(tasks, mapOf("late" to PlacementWindow(900, 1080)))

        assertEquals(480, plain.segments.first().startMinute)
        assertTrue(
            "the preference changed nothing: ${preferred.segments.map { it.startMinute }}",
            preferred.segments.all { it.startMinute >= 900 },
        )
    }

    @Test
    fun aPreferenceBuysNoCapacityAndCostsNoWork() {
        val tasks = listOf(task("long", 240))
        val preferred = plan(tasks, mapOf("long" to PlacementWindow(900, 1080)))
        val plain = plan(tasks, emptyMap())

        assertEquals("work was dropped", totalMinutes(plain), totalMinutes(preferred))
        assertEquals(plain.unscheduledTasks, preferred.unscheduledTasks)
        assertEquals(240, totalMinutes(preferred))
        assertTrue(
            "the window should have been filled first: ${preferred.segments.map { it.startMinute to it.endMinute }}",
            preferred.segments.any { it.startMinute == 900 && it.endMinute == 1080 } &&
                preferred.segments.any { it.startMinute < 900 },
        )
    }

    @Test
    fun durationsAndOrderStayExactlyTheUsers() {
        val tasks = listOf(task("a", 60), task("b", 60))
        val plain = plan(tasks, emptyMap())
        val preferred = plan(tasks, mapOf("b" to PlacementWindow(900, 1080)))

        assertEquals(
            mapOf("a" to 60, "b" to 60),
            preferred.segments.groupBy(PlannedSegment::taskId).mapValues { (_, parts) ->
                parts.sumOf { it.endMinute - it.startMinute }
            },
        )
        assertEquals(plain.orderedTaskIds, preferred.orderedTaskIds)
        assertEquals(plain.priorityAssessments, preferred.priorityAssessments)
    }

    @Test
    fun aFullyOccupiedWindowFallsBackInsteadOfDroppingTheWork() {
        val locked = segment("lock-1", taskId = "busy", date = TEST_MONDAY, startMinute = 900, endMinute = 1080)
            .copy(isLocked = true)
        val tasks = listOf(task("busy", 180), task("late", 60))
        val draft = PlanGenerator.generate(
            tasks = tasks,
            weeklyBlocks = availability,
            dateOverrides = emptyList(),
            lockedSegments = listOf(locked),
            clock = TEST_CLOCK,
            placementWindows = mapOf("late" to PlacementWindow(900, 1080)),
        )

        val late = draft.segments.filter { it.taskId == "late" }
        assertEquals(
            "a preference that could not be honoured must still cost no work: ${draft.segments.map { it.startMinute to it.endMinute }}",
            60,
            late.sumOf { it.endMinute - it.startMinute },
        )
        assertTrue(
            "the window was fully occupied, so the work had to fall back: ${late.map { it.startMinute }}",
            late.all { it.endMinute <= 900 },
        )
        assertEquals(listOf(900 to 1080), draft.segments.filter { it.taskId == "busy" }.map { it.startMinute to it.endMinute })
    }

    @Test
    fun aPreferenceStaysOnTheEarliestDayThatCanHoldTheWork() {
        val week = listOf(
            weeklyBlock("mono", dayOfWeek = DayOfWeek.MONDAY, startMinute = 480, endMinute = 540),
            weeklyBlock("afternoons", dayOfWeek = DayOfWeek.MONDAY, startMinute = 900, endMinute = 1080),
        )
        val tasks = listOf(task("long", 240))
        val draft = PlanGenerator.generate(
            tasks = tasks,
            weeklyBlocks = week,
            dateOverrides = emptyList(),
            clock = TEST_CLOCK,
            placementWindows = mapOf("long" to PlacementWindow(900, 1080)),
        )

        assertEquals(
            "the remainder jumped to a later week: ${draft.segments.map { it.date to (it.startMinute to it.endMinute) }}",
            listOf(TEST_MONDAY, TEST_MONDAY),
            draft.segments.map(PlannedSegment::date),
        )
        assertEquals(240, totalMinutes(draft))
    }

    private fun totalMinutes(draft: PlanDraft): Int = draft.segments.sumOf { it.endMinute - it.startMinute }
}
