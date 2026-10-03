package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ArrangementOperationContractTest {
    @Test fun offline_query_grammar_does_not_turn_questions_into_new_tasks() {
        assertEquals(TaskQueryScope.TODAY, ArrangementReadIntent.queryScope("查询今天的任务"))
        assertEquals(TaskQueryScope.ALL_ACTIVE, ArrangementReadIntent.queryScope("List my active tasks."))
        assertEquals(TaskQueryScope.OVERDUE, ArrangementReadIntent.queryScope("Show overdue tasks"))
        assertNull(ArrangementReadIntent.queryScope("查看笔记30分钟"))
        assertNull(ArrangementReadIntent.queryScope("新增任务查看笔记"))
    }
    private val request = ArrangementAssistantAdviceRequest("查询任务", LocalDate.of(2026, 10, 3), emptyList(),
        listOf(ArrangementExistingTask("a", "英语", TaskCategory.COURSE, 30)), allowedOperations = ArrangementAdviceOperation.entries.toSet())
    private fun query() = ArrangementAssistantAdvice(emptyList(), "忽略模型描述", ArrangementAdviceOperation.QUERY_TASKS, TaskQueryScope.TODAY)
    @Test fun query_has_no_candidates_and_only_allowlisted_scope() {
        assertEquals(query(), ArrangementAssistantAdviceValidator.validate(query(), request))
        assertNull(ArrangementAssistantAdviceValidator.validate(query().copy(queryScope = null), request))
        assertNull(ArrangementAssistantAdviceValidator.validate(query().copy(taskReference = "a"), request))
    }
    @Test fun operation_not_allowed_by_current_workflow_is_rejected() {
        assertNull(ArrangementAssistantAdviceValidator.validate(query(), request.copy(allowedOperations = setOf(ArrangementAdviceOperation.PROPOSE_CHANGES))))
    }
    @Test fun mixed_query_and_creation_is_rejected() {
        val candidate = ArrangementCandidate("任务", TaskCategory.COURSE, 30, ArrangementTimeHint(), emptySet())
        assertNull(ArrangementAssistantAdviceValidator.validate(query().copy(candidates = listOf(candidate)), request))
    }
    @Test fun follow_up_cannot_switch_out_of_current_proposal() {
        assertNull(ArrangementAssistantAdviceValidator.validate(query(), request.copy(followUpInstruction = "改成今天")))
        val candidate = ArrangementCandidate("任务", TaskCategory.COURSE, 30, ArrangementTimeHint(), emptySet())
        assertNull(ArrangementAssistantAdviceValidator.validate(query(), request.copy(draftCandidates = listOf(candidate))))
    }
    @Test fun explanation_requires_one_existing_task_reference() {
        val explain = ArrangementAssistantAdvice(emptyList(), "", ArrangementAdviceOperation.EXPLAIN_ORDER, taskReference = "a")
        assertEquals(explain, ArrangementAssistantAdviceValidator.validate(explain, request))
        assertNull(ArrangementAssistantAdviceValidator.validate(explain.copy(taskReference = "invented"), request))
    }
    @Test fun formulate_cannot_smuggle_query_scope_or_reference() {
        val formulate = ArrangementAssistantAdvice(emptyList(), "", ArrangementAdviceOperation.FORMULATE_PLAN)
        assertEquals(formulate, ArrangementAssistantAdviceValidator.validate(formulate, request))
        assertNull(ArrangementAssistantAdviceValidator.validate(formulate.copy(taskReference = "a"), request))
    }
}
