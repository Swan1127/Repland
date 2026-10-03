package com.swan1127.repland

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.ai.decodeArrangementAdvice
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugAgnesArrangementAdvisorParserTest {
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
