package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArrangementAssistantInterpreter] is the local first stage of the arrangement assistant.
 *
 * Its governing rule: it may report a hard time, a soft window, or nothing — but it must
 * never invent one. A fabricated 09:00 would make the preview look decisive while hiding
 * exactly the information the person still needs to decide.
 */
class ArrangementAssistantInterpreterTest {

    private fun interpret(text: String) = ArrangementAssistantInterpreter.interpret(text)

    @Test
    fun `an empty utterance yields no candidates`() {
        val result = interpret("   ")

        assertTrue(result.candidates.isEmpty())
        assertEquals("没有识别到可以安排的事项", result.confidenceLabel)
        assertEquals(ArrangementIntent.CAPTURE_TASKS, result.intent)
    }

    @Test
    fun `review, replan and arrange intents are recognised`() {
        assertEquals(ArrangementIntent.REVIEW, interpret("复盘一下今天").intent)
        assertEquals(ArrangementIntent.REPLAN, interpret("把会议推迟到明天").intent)
        assertEquals(ArrangementIntent.ARRANGE_TODAY, interpret("今天下午安排一下").intent)
        assertEquals(ArrangementIntent.CAPTURE_TASKS, interpret("复习英语").intent)
    }

    @Test
    fun `an explicit clock time becomes a user-explicit placement`() {
        val candidate = interpret("下午2点开会").candidates.single()

        assertEquals(14 * 60, candidate.timeHint.explicitStartMinute)
        assertEquals("开会", candidate.title)
        assertEquals(ArrangementPlacementSource.USER_EXPLICIT, candidate.placementSource)
        assertTrue(ArrangementClarification.TIME !in candidate.needsClarification)
        assertTrue(ArrangementClarification.DURATION in candidate.needsClarification)
    }

    @Test
    fun `a chinese-numeral time stays a window instead of being fabricated`() {
        val candidate = interpret("下午两点开会").candidates.single()

        assertNull(candidate.timeHint.explicitStartMinute)
        assertEquals("下午可安排", candidate.timeHint.windowLabel)
        assertEquals(ArrangementPlacementSource.UNSCHEDULED, candidate.placementSource)
    }

    @Test
    fun `an utterance with neither time nor duration asks for both`() {
        val candidate = interpret("复习英语").candidates.single()

        assertEquals(
            setOf(ArrangementClarification.TIME, ArrangementClarification.DURATION),
            candidate.needsClarification,
        )
        assertNull(candidate.timeHint.explicitStartMinute)
        assertNull(candidate.timeHint.windowLabel)
    }

    @Test
    fun `durations in minutes and in hours are both read`() {
        assertEquals(30, interpret("写报告30分钟").candidates.single().durationMinutes)
        assertEquals(120, interpret("跑步2小时").candidates.single().durationMinutes)
    }

    @Test
    fun `a decimal hour duration is read as written, not as the digits after the point`() {
        val candidate = interpret("复习1.5小时").candidates.single()

        assertEquals(90, candidate.durationMinutes)
        assertEquals("复习", candidate.title)
    }

    @Test
    fun `clauses separated by punctuation become separate candidates`() {
        val result = interpret("复习英语，写实验报告")

        assertEquals(listOf("复习英语", "写实验报告"), result.candidates.map { it.title })
    }

    @Test
    fun `a conjunction splits only when both sides carry their own action`() {
        assertEquals(
            listOf("复习英语", "写报告"),
            interpret("复习英语和写报告").candidates.map { it.title },
        )
        assertEquals(
            "the right side has no action word, so this stays one item",
            1,
            interpret("写作业和朋友打游戏").candidates.size,
        )
    }

    @Test
    fun `at most eight candidates are returned`() {
        val text = (1..12).joinToString("，") { "写报告$it" }

        assertEquals(8, interpret(text).candidates.size)
    }

    @Test
    fun `categories are inferred from the wording`() {
        assertEquals(TaskCategory.COURSE, interpret("复习数学").candidates.single().category)
        assertEquals(TaskCategory.OFFICE, interpret("写报告").candidates.single().category)
        assertEquals(TaskCategory.LEISURE, interpret("跑步").candidates.single().category)
        assertEquals(TaskCategory.EXTRACURRICULAR, interpret("联系朋友").candidates.single().category)
    }

    @Test
    fun `the sample utterance kept in the project notes parses into four items`() {
        val text = "我准备今天去上午复习英语，同时写实验报告，" +
            "下午我要去一两点参加一个会议，下午四五点我要去写作业和朋友打游戏"

        val result = interpret(text)

        assertEquals(4, result.candidates.size)
        assertEquals(ArrangementIntent.ARRANGE_TODAY, result.intent)
        assertEquals("已提取 4 件事项", result.confidenceLabel)
    }
}
