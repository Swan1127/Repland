package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TaskCaptureDraftTest {
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
}
