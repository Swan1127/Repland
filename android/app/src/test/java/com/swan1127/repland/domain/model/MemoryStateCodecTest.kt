package com.swan1127.repland.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStateCodecTest {
    private val capturedOn = LocalDate.of(2026, 10, 22)

    @Test
    fun freshStateRoundTripsEveryField() {
        val state = MemoryModel.seed(importance = 4, on = capturedOn, aiDifficulty = 0.7)
        assertEquals(state, MemoryStateCodec.decode(MemoryStateCodec.encode(state)))
    }

    @Test
    fun reviewedStateRoundTripsEveryField() {
        val state = MemoryModel.grade(
            MemoryModel.seed(importance = 5, on = capturedOn),
            RecallGrade.FORGOT,
            capturedOn.plusDays(4),
        ).copy(exposures = 9)
        assertEquals(state, MemoryStateCodec.decode(MemoryStateCodec.encode(state)))
    }

    @Test
    fun unknownVersionsDoNotPretendToBeCurrentStates() {
        val record = MemoryStateCodec.encode(MemoryModel.seed(3, capturedOn))
        assertNull(MemoryStateCodec.decode(record - "version"))
        assertNull(MemoryStateCodec.decode(record + ("version" to "2")))
    }

    @Test
    fun invalidDatesDoNotBecomeValidMemories() {
        val record = MemoryStateCodec.encode(MemoryModel.seed(3, capturedOn))
        assertNull(MemoryStateCodec.decode(record + ("capturedOn" to "2026-02-31")))
        assertNull(MemoryStateCodec.decode(record + ("lastEventOn" to "not-a-date")))
    }

    @Test
    fun missingNumbersDoNotResetAnExistingReviewHistory() {
        val record = MemoryStateCodec.encode(MemoryModel.seed(3, capturedOn))
        assertNull(MemoryStateCodec.decode(record - "stabilityDays"))
        assertNull(MemoryStateCodec.decode(record - "reviews"))
    }

    @Test
    fun nonFiniteNumbersAreRejectedInsteadOfEnteringTheCurve() {
        val record = MemoryStateCodec.encode(MemoryModel.seed(3, capturedOn))
        listOf("NaN", "Infinity", "-Infinity", "1e999", "bad").forEach { invalid ->
            listOf("stabilityDays", "difficulty", "difficultySeed").forEach { field ->
                assertNull("$field=$invalid", MemoryStateCodec.decode(record + (field to invalid)))
            }
        }
    }

    @Test
    fun finiteOutliersAreBoundedWithoutDiscardingTheirCaptureDay() {
        val record = MemoryStateCodec.encode(MemoryModel.seed(3, capturedOn)) + mapOf(
            "stabilityDays" to "10000",
            "difficulty" to "-9",
            "difficultySeed" to "100",
            "importance" to "-2",
            "reviews" to "-5",
            "lapses" to "99",
            "exposures" to "-1",
        )
        val state = requireNotNull(MemoryStateCodec.decode(record))
        assertEquals(capturedOn, state.capturedOn)
        assertEquals(MemoryModel.MaxStability, state.stabilityDays, 0.0)
        assertEquals(MemoryModel.MinDifficulty, state.difficulty, 0.0)
        assertEquals(MemoryModel.MaxDifficulty, state.difficultySeed, 0.0)
        assertEquals(1, state.importance)
        assertEquals(0, state.reviews)
        assertEquals(0, state.lapses)
        assertEquals(0, state.exposures)
    }

    @Test
    fun aCorruptPersonalisationGainCannotPoisonFutureReviews() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(1.0, MemoryStateCodec.gain(invalid), 0.0)
            assertEquals(1.0, MemoryModel.observeGain(0.8, RecallGrade.RECALLED, invalid), 0.0)
        }
        assertEquals(MemoryModel.MinGain, MemoryStateCodec.gain(-100.0), 0.0)
        assertEquals(MemoryModel.MaxGain, MemoryStateCodec.gain(100.0), 0.0)
    }

    @Test
    fun aNonFiniteAiEstimateFallsBackToANeutralPrior() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(MemoryModel.NeutralDifficulty, MemoryModel.difficultySeed(invalid), 0.0)
        }
    }

    @Test
    fun countersSaturateInsteadOfWrappingNegative() {
        val state = MemoryModel.seed(3, capturedOn).copy(
            reviews = Int.MAX_VALUE,
            lapses = Int.MAX_VALUE,
            exposures = Int.MAX_VALUE,
        )
        val reviewed = MemoryModel.grade(state, RecallGrade.FORGOT, capturedOn.plusDays(1))
        assertEquals(Int.MAX_VALUE, reviewed.reviews)
        assertEquals(Int.MAX_VALUE, reviewed.lapses)
        assertEquals(Int.MAX_VALUE, MemoryModel.touched(state).exposures)
        assertTrue(reviewed.stabilityDays.isFinite())
    }
}
