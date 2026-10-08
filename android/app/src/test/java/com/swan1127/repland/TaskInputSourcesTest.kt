package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskInputSourcesTest {
    @Test fun unknown_and_explicit_choices_are_distinct_and_round_trip() {
        val unknown = TaskCaptureDraft(text = "事项").toTaskDraft()
        assertEquals(TaskInputSource.UNKNOWN, unknown.inputSources.category)
        assertEquals(TaskInputSource.UNKNOWN, unknown.inputSources.priority)
        assertEquals(TaskInputSource.UNKNOWN, unknown.inputSources.days)
        val explicit = TaskCaptureDraft(text = "事项", category = TaskCategory.COURSE, priority = TaskPriority.MEDIUM).toTaskDraft()
        assertEquals(TaskInputSource.USER_INPUT, explicit.inputSources.category)
        assertEquals(TaskInputSource.USER_INPUT, explicit.inputSources.priority)
        assertEquals(explicit.inputSources, TaskInputSources.decode(explicit.inputSources.encode()))
        assertEquals(TaskInputSources.legacy, TaskInputSources.decode(""))
    }

    @Test fun neutral_sorting_is_explainable_without_creating_a_medium_choice_or_category() {
        val capture = TaskCaptureDraft(text = "事项").toTaskDraft()
        val task = Task("unknown", capture.description, capture.displayName, capture.category, capture.userPriority,
            null, null, null, TaskStatus.NOT_STARTED, null, null, null, 0, 1, 1)
        val ranked = LocalPriorityRanker.rank(listOf(task), today = LocalDate.of(2026, 10, 8)).single()
        assertNull(ranked.reasons.single { it.kind == PriorityReasonKind.INITIAL_PRIORITY }.value)
        assertNull(ranked.reasons.single { it.kind == PriorityReasonKind.CATEGORY_PREFERENCE }.value)
        assertEquals(TaskPriority.UNSPECIFIED, task.userPriority)
        assertEquals(4, CategoryPreferences.defaults.size)
        assertFalse(CategoryPreferences.defaults.containsKey(TaskCategory.UNSPECIFIED))
    }

    @Test fun unknown_days_are_valid_but_invalid_numbers_are_not_unknown() {
        val base = TaskCaptureDraft(text = "事项").toTaskDraft()
        assertTrue(TaskDraftValidator.isValid(base))
        listOf(-1, 0, 31).forEach { assertFalse(TaskDraftValidator.isValid(base.copy(estimatedDays = it))) }
        assertTrue(TaskDraftValidator.isValid(base.copy(estimatedDays = 30)))
        assertFalse(TaskDraftValidator.isValid(base.copy(displayName = "", description = "")))
        assertTrue(TaskDraftValidator.isValid(TaskDraft(displayName = "标题即可")))
        assertTrue(TaskDraftValidator.isValid(TaskDraft(description = "描述即可")))
    }
}
