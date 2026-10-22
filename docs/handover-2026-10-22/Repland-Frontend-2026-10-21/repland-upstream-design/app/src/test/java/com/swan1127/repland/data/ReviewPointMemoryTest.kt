package com.swan1127.repland.data

import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.RecallGrade
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPointMemoryTest {
    private val capturedOn = LocalDate.of(2026, 10, 22)

    @Test
    fun legacyMigrationPreservesEveryPreviouslyAdvertisedDueDay() {
        ReviewPoint.EBBINGHAUS_DAYS.indices.forEach { stage ->
            val point = legacy(stage)
            assertEquals(point.dueOn, MemoryModel.dueOn(point.memoryState()))
            assertFalse(point.isGraduated)
            assertTrue(point.isDueOn(point.dueOn))
        }
    }

    @Test
    fun graduatedLegacyPointsAreNotResurrectedByTheNewModel() {
        listOf(5, 6, 99).forEach { stage ->
            val point = legacy(stage)
            assertTrue(point.isGraduated)
            assertFalse(point.isDueOn(capturedOn.plusYears(10)))
            assertSame(point, point.graded(RecallGrade.FORGOT, capturedOn.plusYears(10)))
        }
    }

    @Test
    fun theFirstGradeTransitionsAnActiveLegacyPointWithoutLosingItsText() {
        val point = legacy(3)
        val updated = point.graded(RecallGrade.RECALLED, point.dueOn)
        assertEquals(point.id, updated.id)
        assertEquals(point.taskId, updated.taskId)
        assertEquals(point.content, updated.content)
        assertEquals(point.createdOn, updated.createdOn)
        assertEquals(point.stage, updated.stage)
        assertEquals(point.dueOn, updated.memory?.lastEventOn)
        assertEquals(4, updated.memory?.reviews)
        assertTrue(updated.dueOn.isAfter(point.dueOn))
    }

    @Test
    fun threeRecallGradesProduceDistinctFutureIntervals() {
        val point = legacy(3)
        val forgot = point.graded(RecallGrade.FORGOT, point.dueOn)
        val fuzzy = point.graded(RecallGrade.FUZZY, point.dueOn)
        val recalled = point.graded(RecallGrade.RECALLED, point.dueOn)
        assertTrue(forgot.dueOn.isBefore(fuzzy.dueOn))
        assertTrue(fuzzy.dueOn.isBefore(recalled.dueOn))
        assertEquals(1, forgot.memory?.lapses)
        assertEquals(0, recalled.memory?.lapses)
    }

    @Test
    fun modelBasedPointsKeepReviewingRatherThanGraduatingAtFiveAnswers() {
        var point = legacy(0).copy(memory = MemoryModel.seed(3, capturedOn))
        repeat(7) {
            point = point.graded(RecallGrade.RECALLED, point.dueOn)
        }
        assertEquals(7, point.memory?.reviews)
        assertFalse(point.isGraduated)
        assertTrue(point.isDueOn(point.dueOn))
    }

    @Test
    fun readingADifferentDayNeverPushesTheStoredDueDateAway() {
        val point = legacy(0).copy(memory = MemoryModel.seed(3, capturedOn))
        val due = point.dueOn
        assertFalse(point.isDueOn(capturedOn))
        assertTrue(point.isDueOn(capturedOn.plusDays(100)))
        assertEquals(due, point.dueOn)
    }

    private fun legacy(stage: Int): ReviewPoint = ReviewPoint(
        id = "review-point",
        taskId = "task",
        taskTitle = "微积分",
        content = "泰勒展开余项",
        createdOn = capturedOn,
        stage = stage,
    )
}
