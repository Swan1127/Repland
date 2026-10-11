package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TaskCaptureDraftTest {
    @Test fun minimal_capture_does_not_invent_optional_user_choices() {
        val task = TaskCaptureDraft(text = "只记一件事").toTaskDraft()
        assertEquals("只记一件事", task.displayName)
        assertEquals("UNSPECIFIED", task.category.name)
        assertEquals("UNSPECIFIED", task.userPriority.name)
        assertNull(task.estimatedDays)
        assertTrue(TaskDraftValidator.isValid(task))
    }

    @Test fun description_alone_is_a_valid_task_input() {
        val task = TaskCaptureDraft(text = "描述形成标题").toTaskDraft().copy(displayName = "")
        assertTrue(TaskDraftValidator.isValid(task))
    }
    @Test fun unknown_duration_and_dates_stay_unknown() {
        val task = TaskCaptureDraft(text = " 一件事 ").toTaskDraft()
        assertNull(task.totalDurationMinutes); assertNull(task.dueDate); assertNull(task.scheduledForDate)
        assertEquals("一件事", task.description)
    }
    @Test fun custom_duration_is_preserved_and_validated() {
        assertEquals(45, TaskCaptureDraft(isCustomDuration = true, customDurationText = "45").selectedDuration)
        listOf("", "0", "1441", "999999999999").forEach {
            assertFalse(TaskCaptureDraft(isCustomDuration = true, customDurationText = it).durationIsValid)
        }
    }
    @Test fun backing_out_of_duration_does_not_implicitly_schedule_today() {
        val capture = TaskCaptureDraft(text = "复习", stage = TaskCaptureStage.DURATION, duration = 30)
        assertNull(capture.copy(stage = TaskCaptureStage.CAPTURE).toTaskDraft().scheduledForDate)
    }
    @Test fun custom_duration_rejects_non_ascii_integer_without_rewriting_raw_input() {
        listOf("-30", "+30", "3.5", " 30", "30 ", "３0", "٣٠", "999999999999", "0", "1441", "").forEach { raw ->
            val draft = TaskCaptureDraft(isCustomDuration = true, customDurationText = raw)
            assertEquals(raw, draft.customDurationText)
            assertFalse("invalid raw: $raw", draft.durationIsValid)
            assertNull(draft.selectedDuration)
        }
        listOf("1" to 1, "45" to 45, "1440" to 1440, "0030" to 30).forEach { (raw, expected) ->
            assertEquals(expected, TaskCaptureDraft(isCustomDuration = true, customDurationText = raw).selectedDuration)
        }
    }
}
