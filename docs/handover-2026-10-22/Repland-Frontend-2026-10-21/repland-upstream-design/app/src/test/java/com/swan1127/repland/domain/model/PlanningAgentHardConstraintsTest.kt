package com.swan1127.repland.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanningAgent.relevantHardConstraints] decides what the advisor is told it must not touch.
 * It is reachable only through [PlanningAgent.prepare], so these tests drive it the same way
 * the app does and read the resulting [AiReplanRequest.hardConstraints].
 *
 * Two rules carry the weight: a segment belonging to a task the user did NOT ask to replan is
 * protected, and the next thirty days of classes and blocked days are protected. Getting
 * either wrong lets the advisor quietly schedule work on top of a class.
 */
class PlanningAgentHardConstraintsTest {

    private val agent = PlanningAgent(NoOpAiAdvisor, PlanGenerator)
    private val today: LocalDate = LocalDate.now()

    // ---------------------------------------------------------------- weekly calendar

    @Test
    fun `a course block becomes a hard constraint on each matching weekday in the window`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(
                        id = "math",
                        kind = TimeBlockKind.COURSE,
                        dayOfWeek = today.dayOfWeek,
                        startMinute = 480,
                        endMinute = 540,
                    ),
                ),
            ),
        )
        assertEquals(
            // The window is offsets 0..29, so a block on today's weekday lands on these five
            // dates and on no others. This pins the window to exactly thirty days: a shorter
            // window would drop 28, a longer one would add 35.
            listOf(0L, 7L, 14L, 21L, 28L).map { today.plusDays(it) },
            constraints.map(AiHardConstraintContext::date).sorted(),
        )
        assertTrue(constraints.all { it.startMinute == 480 && it.endMinute == 540 })
    }

    @Test
    fun `no hard constraint falls outside the thirty day window`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = (0L..6L).map { offset ->
                    weeklyBlock(
                        id = "block-$offset",
                        kind = TimeBlockKind.COURSE,
                        dayOfWeek = today.plusDays(offset).dayOfWeek,
                        startMinute = 480,
                        endMinute = 540,
                    )
                },
            ),
        )
        val dates = constraints.map(AiHardConstraintContext::date).toSet()
        assertTrue(dates.all { !it.isBefore(today) })
        assertTrue(dates.all { !it.isAfter(today.plusDays(29)) })
        assertEquals(today.plusDays(29), dates.max())
    }

    @Test
    fun `an available block is not a hard constraint`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(
                        id = "free",
                        kind = TimeBlockKind.AVAILABLE,
                        dayOfWeek = today.dayOfWeek,
                        startMinute = 480,
                        endMinute = 540,
                    ),
                ),
            ),
        )
        assertTrue(constraints.isEmpty())
    }

    @Test
    fun `a rest or other block is a hard constraint`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(id = "rest", kind = TimeBlockKind.REST, dayOfWeek = today.dayOfWeek, startMinute = 720, endMinute = 780),
                    weeklyBlock(id = "other", kind = TimeBlockKind.OTHER, dayOfWeek = today.dayOfWeek, startMinute = 780, endMinute = 840),
                ),
            ),
        )
        assertEquals(setOf(720 to 780, 780 to 840), constraints.map { it.startMinute to it.endMinute }.toSet())
    }

    @Test
    fun `a week pattern outside the semester is not expanded`() {
        // Week 1 only, anchored at today's week. A block on today's weekday appears once.
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(
                        id = "w1",
                        kind = TimeBlockKind.COURSE,
                        dayOfWeek = today.dayOfWeek,
                        startMinute = 480,
                        endMinute = 540,
                        weekPattern = "1",
                    ),
                ),
                semesterFirstWeekMonday = today,
            ),
        )
        assertEquals(listOf(today), constraints.map(AiHardConstraintContext::date))
    }

    // ---------------------------------------------------------------- overrides

    @Test
    fun `a blocked override is a hard constraint and an available one is not`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                dateOverrides = listOf(
                    dateOverride(id = "busy", type = DateOverrideType.BLOCKED, date = today.plusDays(3), startMinute = 600, endMinute = 660),
                    dateOverride(id = "free", type = DateOverrideType.AVAILABLE, date = today.plusDays(4), startMinute = 600, endMinute = 660),
                ),
            ),
        )
        assertEquals(listOf(AiHardConstraintContext(today.plusDays(3), 600, 660)), constraints)
    }

    @Test
    fun `an override before today or beyond the window is not a hard constraint`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                dateOverrides = listOf(
                    dateOverride(id = "past", type = DateOverrideType.BLOCKED, date = today.minusDays(1), startMinute = 600, endMinute = 660),
                    dateOverride(id = "far", type = DateOverrideType.BLOCKED, date = today.plusDays(30), startMinute = 600, endMinute = 660),
                ),
            ),
        )
        assertTrue(constraints.isEmpty())
    }

    // ---------------------------------------------------------------- preserved plan segments

    @Test
    fun `a segment for a task outside the replan scope is preserved`() {
        val constraints = hardConstraints(
            affected = listOf(task("a")),
            input = planInput(tasks = listOf(task("a"), task("b"))),
            currentPlan = confirmedPlan(segments = listOf(segment("s-b", taskId = "b", startMinute = 480, endMinute = 540))),
        )
        assertEquals(listOf(AiHardConstraintContext(TEST_MONDAY, 480, 540)), constraints)
    }

    @Test
    fun `a segment for an affected task is not preserved`() {
        val constraints = hardConstraints(
            affected = listOf(task("a")),
            input = planInput(tasks = listOf(task("a"))),
            currentPlan = confirmedPlan(segments = listOf(segment("s-a", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        assertTrue(constraints.isEmpty())
    }

    @Test
    fun `a locked segment is preserved even when its task is affected`() {
        val constraints = hardConstraints(
            affected = listOf(task("a")),
            input = planInput(tasks = listOf(task("a"))),
            currentPlan = confirmedPlan(
                segments = listOf(segment("s-a", taskId = "a", startMinute = 480, endMinute = 540, isLocked = true)),
            ),
        )
        assertEquals(listOf(AiHardConstraintContext(TEST_MONDAY, 480, 540)), constraints)
    }

    @Test
    fun `a required task's segment is preserved even when its task is affected`() {
        val constraints = hardConstraints(
            affected = listOf(task("a", priority = TaskPriority.REQUIRED)),
            input = planInput(tasks = listOf(task("a", priority = TaskPriority.REQUIRED))),
            currentPlan = confirmedPlan(segments = listOf(segment("s-a", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        assertEquals(listOf(AiHardConstraintContext(TEST_MONDAY, 480, 540)), constraints)
    }

    @Test
    fun `a required task missing from the plan input does not protect its segment`() {
        // The priority is read off the plan input, not off the affected-task list, so a task
        // that only exists in the request does not get the protection.
        val constraints = hardConstraints(
            affected = listOf(task("a", priority = TaskPriority.REQUIRED)),
            input = planInput(tasks = emptyList()),
            currentPlan = confirmedPlan(segments = listOf(segment("s-a", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        assertTrue(constraints.isEmpty())
    }

    // ---------------------------------------------------------------- de-duplication and cap

    @Test
    fun `an identical block and override collapse into one constraint`() {
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(id = "math", kind = TimeBlockKind.COURSE, dayOfWeek = today.dayOfWeek, startMinute = 480, endMinute = 540),
                ),
                dateOverrides = listOf(
                    dateOverride(id = "dup", type = DateOverrideType.BLOCKED, date = today, startMinute = 480, endMinute = 540),
                ),
            ),
        )
        // Five weekday occurrences, with today counted once rather than twice.
        //
        // NOTE: this asserts the observable result, not the `.distinct()` inside
        // relevantHardConstraints — AiRequestFactory.replan applies `.distinct()` again, so
        // removing the first one leaves this test green. Verified by a surviving mutant.
        assertEquals(5, constraints.size)
        assertEquals(1, constraints.count { it.date == today })
    }

    @Test
    fun `the constraint list is capped at two hundred forty`() {
        // 12 time slots on each of the 7 weekdays: 84 blocks over a 30-day window produce 360
        // distinct constraints, so the cap is what decides the result.
        val blocks = buildList {
            (0L..6L).forEach { offset ->
                val dayOfWeek = today.plusDays(offset).dayOfWeek
                (0 until 12).forEach { slot ->
                    add(
                        weeklyBlock(
                            id = "b-$offset-$slot",
                            kind = TimeBlockKind.COURSE,
                            dayOfWeek = dayOfWeek,
                            startMinute = 480 + slot * 30,
                            endMinute = 510 + slot * 30,
                        ),
                    )
                }
            }
        }
        val constraints = hardConstraints(input = planInput(tasks = emptyList(), weeklyBlocks = blocks))
        // NOTE: the cap is applied twice — here and again in AiRequestFactory.replan — so
        // raising only the one inside relevantHardConstraints leaves this test green.
        // Verified by a surviving mutant.
        assertEquals(240, constraints.size)
    }

    @Test
    fun `the window is anchored to the wall clock day, not to the plan input`() {
        // relevantHardConstraints calls LocalDate.now() directly, while PlanGenerator takes an
        // injected Clock. In the app both mean "now" so they agree, but this path cannot be
        // driven from a fixed clock the way the local generator can. Pinned so that anyone who
        // makes the clock injectable knows this test must change with it.
        val constraints = hardConstraints(
            input = planInput(
                tasks = emptyList(),
                weeklyBlocks = listOf(
                    weeklyBlock(id = "math", kind = TimeBlockKind.COURSE, dayOfWeek = today.dayOfWeek, startMinute = 480, endMinute = 540),
                ),
            ),
        )
        assertFalse(constraints.isEmpty())
        assertEquals(today, constraints.map(AiHardConstraintContext::date).min())
    }

    // ---------------------------------------------------------------- helper

    private fun hardConstraints(
        input: PlanGenerationInput,
        affected: List<Task> = emptyList(),
        currentPlan: ConfirmedPlan? = null,
    ): List<AiHardConstraintContext> {
        val prepared = agent.prepare(
            PlanningAgentWork.Replan(
                affectedTasks = affected,
                executionLogs = emptyList(),
                currentPlan = currentPlan,
                localPlanInput = input,
                constraintSummary = emptyList(),
            ),
        )
        return (prepared.request as AiReplanRequest).hardConstraints
    }
}
