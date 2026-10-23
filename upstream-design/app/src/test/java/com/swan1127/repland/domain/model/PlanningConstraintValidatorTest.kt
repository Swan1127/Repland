package com.swan1127.repland.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanningConstraintValidator] is the shared guard between the local planner and any
 * AI-proposed segment. Its day window (00:00-24:00) is deliberately wider than the local
 * generator's (08:00-22:00); [PlanGeneratorTest] pins the other side of that asymmetry.
 */
class PlanningConstraintValidatorTest {

    private fun input(
        weeklyBlocks: List<WeeklyTimeBlock> = listOf(
            weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60),
        ),
        dateOverrides: List<DateOverride> = emptyList(),
    ): PlanGenerationInput = planInput(
        tasks = emptyList(),
        weeklyBlocks = weeklyBlocks,
        dateOverrides = dateOverrides,
    )

    private fun isAvailable(
        input: PlanGenerationInput,
        startMinute: Int,
        endMinute: Int,
        date: java.time.LocalDate = TEST_MONDAY,
    ): Boolean = PlanningConstraintValidator.isAvailable(
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        weeklyBlocks = input.weeklyBlocks,
        dateOverrides = input.dateOverrides,
        semesterFirstWeekMonday = input.semesterFirstWeekMonday,
    )

    @Test
    fun `the validator accepts a midnight slot when availability covers it`() {
        val input = input(weeklyBlocks = listOf(weeklyBlock(startMinute = 0, endMinute = 2 * 60)))
        val proposal = AiProposedSegment("t1", TEST_MONDAY, 0, 30)

        assertTrue(PlanningConstraintValidator.supportsProposal(proposal, input))
    }

    @Test
    fun `a non-quantum-aligned range is rejected`() {
        val proposal = AiProposedSegment("t1", TEST_MONDAY, 9 * 60 + 15, 9 * 60 + 45)

        assertFalse(PlanningConstraintValidator.supportsProposal(proposal, input()))
    }

    @Test
    fun `no proposal is supported without declared availability`() {
        val proposal = AiProposedSegment("t1", TEST_MONDAY, 9 * 60, 10 * 60)

        assertFalse(
            PlanningConstraintValidator.supportsProposal(proposal, input(weeklyBlocks = emptyList())),
        )
    }

    @Test
    fun `a slot outside the declared weekday is rejected`() {
        val proposal = AiProposedSegment("t1", TEST_MONDAY.plusDays(1), 9 * 60, 10 * 60)

        assertFalse(PlanningConstraintValidator.supportsProposal(proposal, input()))
    }

    @Test
    fun `a proposal overlapping a course block is rejected`() {
        val input = input(
            weeklyBlocks = listOf(
                weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60),
                weeklyBlock(
                    id = "course",
                    kind = TimeBlockKind.COURSE,
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                ),
            ),
        )
        val proposal = AiProposedSegment("t1", TEST_MONDAY, 10 * 60, 10 * 60 + 30)

        assertFalse(PlanningConstraintValidator.supportsProposal(proposal, input))
    }

    @Test
    fun `a BLOCKED date override beats weekly availability`() {
        val input = input(
            dateOverrides = listOf(
                dateOverride(
                    type = DateOverrideType.BLOCKED,
                    date = TEST_MONDAY,
                    startMinute = 10 * 60,
                    endMinute = 11 * 60,
                ),
            ),
        )

        assertFalse(isAvailable(input, startMinute = 10 * 60, endMinute = 10 * 60 + 30))
    }

    @Test
    fun `an AVAILABLE date override makes a slot usable without a weekly window`() {
        val input = input(
            weeklyBlocks = listOf(
                weeklyBlock(
                    id = "course",
                    kind = TimeBlockKind.COURSE,
                    startMinute = 9 * 60,
                    endMinute = 12 * 60,
                ),
            ),
            dateOverrides = listOf(
                dateOverride(
                    type = DateOverrideType.AVAILABLE,
                    date = TEST_MONDAY,
                    startMinute = 9 * 60,
                    endMinute = 12 * 60,
                ),
            ),
        )

        assertTrue(PlanningConstraintValidator.hasExplicitAvailability(input))
        assertTrue(isAvailable(input, startMinute = 9 * 60, endMinute = 9 * 60 + 30))
    }

    @Test
    fun `an AVAILABLE override on one day does not leak into the next day`() {
        val input = input(
            weeklyBlocks = emptyList(),
            dateOverrides = listOf(
                dateOverride(
                    type = DateOverrideType.AVAILABLE,
                    date = TEST_MONDAY,
                    startMinute = 9 * 60,
                    endMinute = 12 * 60,
                ),
            ),
        )

        assertTrue(isAvailable(input, startMinute = 9 * 60, endMinute = 9 * 60 + 30, date = TEST_MONDAY))
        assertFalse(
            isAvailable(
                input,
                startMinute = 9 * 60,
                endMinute = 9 * 60 + 30,
                date = TEST_MONDAY.plusDays(1),
            ),
        )
    }

    @Test
    fun `a weekly block is ignored when its week pattern excludes the date`() {
        // A week pattern is only honoured once a semester start exists; without it
        // CourseWeekPattern deliberately reports "applies" so a real class never vanishes.
        val input = planInput(
            tasks = emptyList(),
            weeklyBlocks = listOf(
                weeklyBlock(
                    startMinute = 9 * 60,
                    endMinute = 12 * 60,
                    weekPattern = "1-2周",
                ),
            ),
            semesterFirstWeekMonday = TEST_MONDAY,
        )

        assertTrue(isAvailable(input, startMinute = 9 * 60, endMinute = 9 * 60 + 30, date = TEST_MONDAY))
        assertFalse(
            isAvailable(
                input,
                startMinute = 9 * 60,
                endMinute = 9 * 60 + 30,
                date = TEST_MONDAY.plusWeeks(3),
            ),
        )
    }
}
