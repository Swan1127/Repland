package com.swan1127.repland

import com.swan1127.repland.domain.model.ArrangementAssistantInterpreter
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementAssistantInterpreterTest {

    @Test fun ascii_minute_suffix_is_parsed_as_an_explicit_duration() {
        val result = ArrangementAssistantInterpreter.interpret("15:00 study 90min")
        assertEquals(900, result.candidates.single().timeHint.explicitStartMinute)
        assertEquals(90, result.candidates.single().durationMinutes)
    }
    @Test
    fun explicit_times_become_a_real_preview_anchor_not_an_invented_slot() {
        val result = ArrangementAssistantInterpreter.interpret(
            "今天下午3点到4点半复习数据结构 90分钟；晚上8点跑步40分钟",
        )

        assertEquals(ArrangementIntent.ARRANGE_TODAY, result.intent)
        assertEquals(2, result.candidates.size)
        assertEquals(15 * 60, result.candidates[0].timeHint.explicitStartMinute)
        assertEquals(90, result.candidates[0].durationMinutes)
        assertEquals(20 * 60, result.candidates[1].timeHint.explicitStartMinute)
        assertFalse(result.needsClarification)
    }

    @Test
    fun soft_time_without_duration_asks_only_for_the_missing_decision() {
        val result = ArrangementAssistantInterpreter.interpret("下午处理毕业设计")
        val item = result.candidates.single()

        assertEquals("下午可安排", item.timeHint.windowLabel)
        assertEquals(null, item.timeHint.explicitStartMinute)
        assertTrue(item.needsClarification.contains(ArrangementClarification.DURATION))
        assertFalse(item.needsClarification.contains(ArrangementClarification.TIME))
    }

    @Test
    fun a_spoken_time_range_derives_duration_without_inventing_another_time() {
        val item = ArrangementAssistantInterpreter
            .interpret("下午3点到4点看论文")
            .candidates
            .single()

        assertEquals(15 * 60, item.timeHint.explicitStartMinute)
        assertEquals(60, item.durationMinutes)
        assertTrue(item.needsClarification.isEmpty())
    }

    @Test
    fun capture_without_time_or_duration_never_fakes_a_day_timeline() {
        val result = ArrangementAssistantInterpreter.interpret("整理英语错题")
        val item = result.candidates.single()

        assertEquals(ArrangementIntent.CAPTURE_TASKS, result.intent)
        assertEquals(null, item.timeHint.explicitStartMinute)
        assertEquals(null, item.timeHint.windowLabel)
        assertEquals(setOf(ArrangementClarification.TIME, ArrangementClarification.DURATION), item.needsClarification)
    }

    @Test
    fun conjunction_splits_independent_actions_but_not_a_compound_subject() {
        val independent = ArrangementAssistantInterpreter.interpret("我想复习英语和写报告")
        val compound = ArrangementAssistantInterpreter.interpret("数学和英语复习")

        assertEquals(listOf("复习英语", "写报告"), independent.candidates.map { it.title })
        assertEquals(listOf("数学和英语复习"), compound.candidates.map { it.title })
    }
}
