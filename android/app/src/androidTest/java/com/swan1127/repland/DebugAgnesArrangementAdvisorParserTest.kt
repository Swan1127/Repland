package com.swan1127.repland

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.ai.decodeArrangementAdvice
import com.swan1127.repland.data.ai.toWire
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugAgnesArrangementAdvisorParserTest {
    @Test fun structured_queries_and_explanations_decode_without_candidate_creation() {
        val query = decodeArrangementAdvice("""{"operation":"QUERY_TASKS","queryScope":"TODAY","candidates":[]}""")
        assertEquals(com.swan1127.repland.domain.model.ArrangementAdviceOperation.QUERY_TASKS, query.operation)
        assertEquals(com.swan1127.repland.domain.model.TaskQueryScope.TODAY, query.queryScope)
        assertEquals(0, query.candidates.size)
        val explain = decodeArrangementAdvice("""{"operation":"EXPLAIN_ORDER","taskReference":"a","candidates":[]}""")
        assertEquals("a", explain.taskReference)
    }
    @Test fun unknown_operations_scopes_and_mixed_writes_are_rejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { decodeArrangementAdvice("""{"operation":"DELETE_ALL","candidates":[]}""") }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { decodeArrangementAdvice("""{"operation":"QUERY_TASKS","queryScope":"DELETE","candidates":[]}""") }
        org.junit.Assert.assertThrows(org.json.JSONException::class.java) { decodeArrangementAdvice("""{"operation":"QUERY_TASKS","queryScope":"TODAY","tasks":[{"title":"新建"}]}""") }
    }
    @Test fun context_wire_contains_bounded_facts_but_no_local_revision_or_notes() {
        val request = com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest("英语", java.time.LocalDate.of(2026, 10, 3), emptyList(),
            listOf(com.swan1127.repland.domain.model.ArrangementExistingTask("a", "英语", com.swan1127.repland.domain.model.TaskCategory.COURSE, 60,
                com.swan1127.repland.domain.model.TaskStatus.IN_PROGRESS, com.swan1127.repland.domain.model.TaskPriority.HIGH, progressPercent = 40)),
            categoryPreferences = mapOf(com.swan1127.repland.domain.model.TaskCategory.COURSE to 70),
            taskFeedback = listOf(com.swan1127.repland.domain.model.ArrangementTaskFeedback("a", listOf(com.swan1127.repland.domain.model.AiFeedbackContext(10, 40, "第一章", null, null)))), sourceRevision = "local-revision")
        val wire = request.toWire()
        assertEquals("IN_PROGRESS", wire.getJSONArray("existingTasks").getJSONObject(0).getString("status"))
        assertEquals(40, wire.getJSONArray("existingTasks").getJSONObject(0).getInt("progressPercent"))
        assertEquals("第一章", wire.getJSONArray("taskFeedback").getJSONObject(0).getJSONArray("feedback").getJSONObject(0).getString("completedContent"))
        assertFalse(wire.has("sourceRevision")); assertFalse(wire.toString().contains("local-revision"))
        assertFalse(wire.has("apiKey")); assertFalse(wire.getJSONArray("existingTasks").getJSONObject(0).has("description"))
    }
    @Test fun proposal_id_is_decoded_for_continuous_refinement() {
        val result = decodeArrangementAdvice("""{"confidenceLabel":"test","candidates":[{"title":"英语","proposalId":"p","category":"COURSE","startMinute":960,"durationMinutes":30}]}""")
        assertEquals("p", result.candidates.single().proposalId)
    }
    @Test fun existing_task_id_is_decoded_without_converting_it_to_a_new_task() {
        val result = decodeArrangementAdvice("""{"confidenceLabel":"test","candidates":[{"title":"英语","existingTaskId":"task-a","category":"COURSE","startMinute":720,"durationMinutes":30}]}""")
        assertEquals("task-a", result.candidates.single().existingTaskId)
    }
    @Test
    fun wrapped_and_alias_shaped_model_json_is_still_an_editable_plan() {
        val advice = decodeArrangementAdvice(
            """
            我已根据今日安排生成建议：
            ```json
            {"plan":{"summary":"两件事，先复习后写作","tasks":[
              {"name":"复习英语","type":"学习","startTime":"10:00","estimatedMinutes":45,"track":"focus"},
              {"task":"写报告","category":"OFFICE","start":690,"duration":60,"preferredTrackId":"focus"}
            ]}}
            ```
            """.trimIndent(),
        )

        assertEquals("两件事，先复习后写作", advice.confidenceLabel)
        assertEquals(listOf("复习英语", "写报告"), advice.candidates.map { it.title })
        assertEquals(TaskCategory.COURSE, advice.candidates.first().category)
        assertEquals(10 * 60, advice.candidates.first().timeHint.explicitStartMinute)
        assertEquals(45, advice.candidates.first().durationMinutes)
        assertEquals(ArrangementPlacementSource.AI_SUGGESTED, advice.candidates.first().placementSource)
    }
}
