package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryModelTest {

    private val day0: LocalDate = LocalDate.of(2026, 3, 2)

    private fun seeded(importance: Int = 3) = MemoryModel.seed(importance, day0)

    
    private fun walkedGaps(times: Int): List<Int> {
        var state = seeded()
        var today = day0
        return buildList {
            repeat(times) {
                val due = MemoryModel.dueOn(state, today)
                add(ChronoUnit.DAYS.between(today, due).toInt())
                state = MemoryModel.grade(state, RecallGrade.RECALLED, due)
                today = due
            }
        }
    }

    @Test
    fun stabilityIsExactlyTheNinetyPercentDay() {
        for (stability in listOf(1.0, 2.5, 9.0, 40.0)) {
            assertEquals(0.9, MemoryModel.retrievability(stability, stability), 1e-9)
            assertEquals(stability, MemoryModel.intervalDays(stability), 1e-9)
        }
        assertEquals(1.0, MemoryModel.retrievability(5.0, 0.0), 1e-12)
    }

    @Test
    fun theCurveOnlyFallsAndNeverOverflows() {
        var previous = 1.0
        for (day in 1..400) {
            val retention = MemoryModel.retrievability(4.0, day.toDouble())
            assertTrue("retention rose on day $day", retention < previous)
            assertTrue("retention went non-positive on day $day", retention > 0.0)
            previous = retention
        }
    }

    @Test
    fun theDefaultPathWalksTheShippedLadder() {
        assertEquals(listOf(1, 2, 4, 7, 13), walkedGaps(5))
    }

    @Test
    fun gapsWidenInsteadOfRepeating() {
        val gaps = walkedGaps(8)
        assertTrue("gaps did not widen: $gaps", gaps.zipWithNext().all { (previousGap, nextGap) -> nextGap > previousGap })
    }

    @Test
    fun fuzzierAnswersBuyLessStrength() {
        val state = MemoryModel.fromLadderStage(day0, stage = 3, importance = 3)
        val today = day0.plusDays(7)
        assertEquals(0.9, MemoryModel.retrievabilityAt(state, today), 1e-6)

        val recalled = MemoryModel.grade(state, RecallGrade.RECALLED, today)
        val fuzzy = MemoryModel.grade(state, RecallGrade.FUZZY, today)
        assertEquals(11.2972, recalled.stabilityDays, 1e-3)
        assertEquals(8.9337, fuzzy.stabilityDays, 1e-3)
        assertTrue("fuzzy erased the memory instead of discounting it", fuzzy.stabilityDays > state.stabilityDays)
        assertTrue("fuzzy bought as much as a clean recall", recalled.stabilityDays > fuzzy.stabilityDays)
    }

    @Test
    fun aLapseSoftensInsteadOfErasing() {
        val strong = MemoryModel.fromLadderStage(day0, stage = 4, importance = 3)
        assertEquals(15.0, strong.stabilityDays, 1e-9)
        assertEquals(3.0, MemoryModel.grade(strong, RecallGrade.FORGOT, day0.plusDays(15)).stabilityDays, 1e-9)

        val weak = seeded()
        assertEquals(1.0, MemoryModel.grade(weak, RecallGrade.FORGOT, day0.plusDays(1)).stabilityDays, 1e-9)
        assertEquals(1, MemoryModel.grade(weak, RecallGrade.FORGOT, day0.plusDays(1)).lapses)
    }

    @Test
    fun difficultyRevertsToItsSeedInsteadOfRunningAway() {
        var state = seeded()
        repeat(40) { state = MemoryModel.grade(state, RecallGrade.RECALLED, day0.plusDays(it.toLong())) }
        assertEquals(3.4, state.difficulty, 0.05)

        var forgotten = seeded()
        repeat(40) { forgotten = MemoryModel.grade(forgotten, RecallGrade.FORGOT, day0.plusDays(1)) }
        assertTrue("difficulty escaped its ceiling: ${forgotten.difficulty}", forgotten.difficulty <= 10.0)
        assertTrue(
            "a run of lapses fell back to the seed instead of hardening: ${forgotten.difficulty}",
            forgotten.difficulty > seeded().difficultySeed,
        )
    }

    @Test
    fun stabilityAndDifficultyStayInsideTheirBands() {
        var state = seeded()
        var today = day0
        repeat(120) {
            val due = MemoryModel.dueOn(state, today)
            state = MemoryModel.grade(state, RecallGrade.RECALLED, due)
            today = due
        }
        assertEquals(MemoryModel.MaxStability, state.stabilityDays, 1e-9)
        assertEquals(3.4, state.difficulty, 1e-3)

        repeat(120) {
            val due = MemoryModel.dueOn(state, today)
            state = MemoryModel.grade(state, RecallGrade.FORGOT, due)
            today = due
        }
        assertEquals(MemoryModel.MinStability, state.stabilityDays, 1e-9)
        assertTrue(state.difficulty in MemoryModel.MinDifficulty..MemoryModel.MaxDifficulty)
        assertEquals(240, state.reviews)
        assertEquals(120, state.lapses)
    }

    @Test
    fun contactAloneNeverStrengthensAMemory() {
        val state = seeded()
        val touched = MemoryModel.touched(MemoryModel.touched(state))
        assertEquals(2, touched.exposures)
        assertEquals(state.stabilityDays, touched.stabilityDays, 1e-12)
        assertEquals(state.difficulty, touched.difficulty, 1e-12)
        assertEquals(MemoryModel.dueOn(state), MemoryModel.dueOn(touched))
    }

    @Test
    fun anotherRecallOnTheSameDayDoesNotInventMoreElapsedForgetting() {
        val reviewed = MemoryModel.grade(seeded(), RecallGrade.RECALLED, day0.plusDays(1))
        val again = MemoryModel.grade(reviewed, RecallGrade.RECALLED, day0.plusDays(1))
        assertEquals(reviewed.stabilityDays, again.stabilityDays, 1e-12)
        assertEquals(reviewed.reviews + 1, again.reviews)
        assertEquals(MemoryModel.dueOn(reviewed), MemoryModel.dueOn(again))
    }

    @Test
    fun personalisationStaysABoundedNudge() {
        var gain = 1.0
        repeat(60) { gain = MemoryModel.observeGain(0.95, RecallGrade.FORGOT, gain) }
        assertEquals(MemoryModel.MinGain, gain, 1e-9)

        gain = 1.0
        repeat(60) { gain = MemoryModel.observeGain(0.4, RecallGrade.RECALLED, gain) }
        assertEquals(MemoryModel.MaxGain, gain, 1e-9)

        assertEquals(1.0, MemoryModel.observeGain(0.8, RecallGrade.RECALLED, 1.0), 1e-12)
    }

    @Test
    fun aBiggerGainMakesTheTailLonger() {
        val state = MemoryModel.fromLadderStage(day0, stage = 2, importance = 3)
        val today = day0.plusDays(4)
        val plain = MemoryModel.grade(state, RecallGrade.RECALLED, today, gain = 1.0).stabilityDays
        val boosted = MemoryModel.grade(state, RecallGrade.RECALLED, today, gain = MemoryModel.MaxGain).stabilityDays
        assertTrue("gain did nothing: $plain vs $boosted", boosted > plain)
    }

    @Test
    fun theThreeBarsEachMeanSomethingDifferent() {
        val fresh = seeded(importance = 5)
        val bars = MemoryModel.bars(fresh, day0)
        assertEquals(1f, bars.importance, 1e-6f)
        assertEquals(0f, bars.stability, 1e-6f)
        assertEquals(1f, bars.retrievability, 1e-6f)

        val aged = MemoryModel.grade(seeded(importance = 1), RecallGrade.RECALLED, day0.plusDays(1))
        val agedBars = MemoryModel.bars(aged, day0.plusDays(60))
        assertEquals(0f, agedBars.importance, 1e-6f)
        assertTrue(agedBars.stability > 0f && agedBars.stability < 1f)
        assertTrue("a sixty-day-old point still read as fresh", agedBars.retrievability < 0.5f)
    }

    @Test
    fun mediumPriorityStartsAtNeutralImportance() {
        assertEquals(3, MemoryModel.importanceOf(TaskPriority.MEDIUM))
        assertEquals(0.5f, MemoryModel.importanceBar(MemoryModel.importanceOf(TaskPriority.MEDIUM)), 1e-6f)
        assertEquals(1f, MemoryModel.importanceBar(99), 1e-6f)
        assertEquals(0f, MemoryModel.importanceBar(-3), 1e-6f)
    }

    @Test
    fun pressureRisesAsAMemoryFades() {
        val state = MemoryModel.fromLadderStage(day0, stage = 2, importance = 5)
        val fresh = MemoryModel.reviewPressure(state, day0)
        val faded = MemoryModel.reviewPressure(state, day0.plusDays(40))
        assertTrue("pressure ignored the curve: $fresh then $faded", faded > fresh)
        assertEquals(0.0, fresh, 1e-9)
        assertTrue(faded <= 1.0)

        val lowImportance = MemoryModel.fromLadderStage(day0, stage = 2, importance = 1)
        assertTrue(
            "importance never mattered",
            faded > MemoryModel.reviewPressure(lowImportance, day0.plusDays(40)),
        )
    }

    @Test
    fun migratingALadderPointKeepsTheDayItWasAlreadyShown() {
        val ladder = MemoryModel.ladderDays()
        ladder.forEachIndexed { stage, days ->
            val state = MemoryModel.fromLadderStage(day0, stage, importance = 3)
            assertEquals(day0.plusDays(days.toLong()), MemoryModel.dueOn(state))
            assertEquals(stage, state.reviews)
            assertTrue(MemoryModel.isDue(state, day0.plusDays(days.toLong())))
            assertFalse(MemoryModel.isDue(state, day0.plusDays(days.toLong() - 1)))
        }
        assertEquals(15.0, MemoryModel.fromLadderStage(day0, 9, importance = 3).stabilityDays, 1e-9)
    }

    @Test
    fun anAdvisorsHardnessEstimateSeedsButNeverDictates() {
        assertEquals(MemoryModel.NeutralDifficulty, MemoryModel.difficultySeed(null), 1e-9)
        assertEquals(MemoryModel.MinDifficulty, MemoryModel.difficultySeed(0.0), 1e-9)
        assertEquals(MemoryModel.MaxDifficulty, MemoryModel.difficultySeed(1.0), 1e-9)
        assertEquals(MemoryModel.MaxDifficulty, MemoryModel.difficultySeed(7.0), 1e-9)
        val hard = MemoryModel.seed(3, day0, aiDifficulty = 0.9)
        assertEquals(9.1, hard.difficulty, 1e-9)
        assertEquals(8.575, MemoryModel.grade(hard, RecallGrade.RECALLED, day0.plusDays(1)).difficulty, 1e-3)
        assertTrue(
            "a single recall threw away the advisor's estimate",
            MemoryModel.grade(hard, RecallGrade.RECALLED, day0.plusDays(1)).difficulty > 5.5,
        )
    }
}
