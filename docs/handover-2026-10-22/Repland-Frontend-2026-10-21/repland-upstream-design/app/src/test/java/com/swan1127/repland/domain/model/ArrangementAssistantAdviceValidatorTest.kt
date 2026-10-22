package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ArrangementAssistantAdviceValidator] is the sanitising boundary for the optional assistant:
 * whatever the advisor returns is filtered here before the draft can show it. The rules are
 * small, but a hole lets the advisor place work at a time that does not exist, so each
 * "keeps" case below is paired with the "drops" case for the same rule.
 */
class ArrangementAssistantAdviceValidatorTest {

    @Test
    fun `a candidate with neither a duration nor a time is kept`() {
        val result = validate(advice(candidate()))
        assertEquals(1, result?.candidates?.size)
    }

    @Test
    fun `a blank title drops the candidate`() {
        assertNull(validate(advice(candidate(title = "   "))))
    }

    @Test
    fun `an empty candidate list yields no advice`() {
        assertNull(validate(advice()))
    }

    @Test
    fun `all-invalid candidates yield no advice`() {
        assertNull(validate(advice(candidate(title = ""), candidate(durationMinutes = 0))))
    }

    // ---------------------------------------------------------------- duration bounds

    @Test
    fun `a duration of five minutes is kept`() {
        assertEquals(1, validate(advice(candidate(durationMinutes = 5)))?.candidates?.size)
    }

    @Test
    fun `a duration of seven hundred twenty minutes is kept`() {
        assertEquals(1, validate(advice(candidate(durationMinutes = 720)))?.candidates?.size)
    }

    @Test
    fun `a duration below five minutes is dropped`() {
        assertNull(validate(advice(candidate(durationMinutes = 4))))
    }

    @Test
    fun `a duration above seven hundred twenty minutes is dropped`() {
        assertNull(validate(advice(candidate(durationMinutes = 721))))
    }

    @Test
    fun `a negative duration is dropped`() {
        assertNull(validate(advice(candidate(durationMinutes = -30))))
    }

    // ---------------------------------------------------------------- explicit start bounds

    @Test
    fun `the first and last minutes of the day are kept as explicit starts`() {
        assertEquals(1, validate(advice(candidate(explicitStartMinute = 0)))?.candidates?.size)
        assertEquals(1, validate(advice(candidate(explicitStartMinute = 1_439)))?.candidates?.size)
    }

    @Test
    fun `an explicit start at the exclusive end of the day is dropped`() {
        // 1440 is midnight of the next day, which this model has no way to express.
        assertNull(validate(advice(candidate(explicitStartMinute = 1_440))))
    }

    @Test
    fun `a negative explicit start is dropped`() {
        assertNull(validate(advice(candidate(explicitStartMinute = -1))))
    }

    @Test
    fun `a start plus duration that would run past midnight is dropped`() {
        // 1439 + 5 = 1444.
        assertNull(validate(advice(candidate(durationMinutes = 5, explicitStartMinute = 1_439))))
    }

    @Test
    fun `a start plus duration that ends exactly at midnight is kept`() {
        // 1000 + 440 = 1440, the last representable instant.
        assertEquals(1, validate(advice(candidate(durationMinutes = 440, explicitStartMinute = 1_000)))?.candidates?.size)
    }

    // ---------------------------------------------------------------- cap and order

    @Test
    fun `only the first eight valid candidates survive`() {
        val nine = (1..9).map { candidate(title = "c$it") }
        val result = validate(advice(*nine.toTypedArray()))
        assertEquals(8, result?.candidates?.size)
        assertEquals("c1", result?.candidates?.first()?.title)
        assertEquals("c8", result?.candidates?.last()?.title)
    }

    @Test
    fun `invalid candidates do not consume the eight slots`() {
        // Three blanks first, then nine valid ones: the cap counts survivors, not input rows.
        val input = (1..3).map { candidate(title = " ") } + (1..9).map { candidate(title = "v$it") }
        val result = validate(advice(*input.toTypedArray()))
        assertEquals(8, result?.candidates?.size)
        assertEquals("v1", result?.candidates?.first()?.title)
    }

    @Test
    fun `the surviving candidates keep their original order`() {
        val input = listOf(candidate(title = "a"), candidate(title = " "), candidate(title = "b"))
        assertEquals(listOf("a", "b"), validate(advice(*input.toTypedArray()))?.candidates?.map(ArrangementCandidate::title))
    }

    // ---------------------------------------------------------------- what is not checked

    @Test
    fun `the confidence label is passed through unchecked`() {
        // The label is presentation only and never reaches the plan, so the validator does not
        // police it. Pinned so the omission is a decision rather than an oversight.
        val result = validate(advice(candidate(), confidenceLabel = "   "))
        assertEquals("   ", result?.confidenceLabel)
    }

    @Test
    fun `clarification flags and placement source survive untouched`() {
        val source = ArrangementCandidate(
            title = "写报告",
            category = TaskCategory.OFFICE,
            durationMinutes = 60,
            timeHint = ArrangementTimeHint(explicitStartMinute = 600),
            needsClarification = setOf(ArrangementClarification.TIME),
            placementSource = ArrangementPlacementSource.AI_SUGGESTED,
            preferredTrackId = "focus",
        )
        val only = validate(advice(source))?.candidates?.single()
        assertEquals(setOf(ArrangementClarification.TIME), only?.needsClarification)
        assertEquals(ArrangementPlacementSource.AI_SUGGESTED, only?.placementSource)
        assertEquals("focus", only?.preferredTrackId)
    }

    // ---------------------------------------------------------------- builders

    private fun candidate(
        title: String = "复习高数",
        durationMinutes: Int? = null,
        explicitStartMinute: Int? = null,
    ): ArrangementCandidate = ArrangementCandidate(
        title = title,
        category = TaskCategory.COURSE,
        durationMinutes = durationMinutes,
        timeHint = ArrangementTimeHint(explicitStartMinute = explicitStartMinute),
        needsClarification = emptySet(),
    )

    private fun advice(
        vararg candidates: ArrangementCandidate,
        confidenceLabel: String = "中",
    ): ArrangementAssistantAdvice = ArrangementAssistantAdvice(candidates.toList(), confidenceLabel)

    private fun validate(advice: ArrangementAssistantAdvice): ArrangementAssistantAdvice? =
        ArrangementAssistantAdviceValidator.validate(advice)
}
