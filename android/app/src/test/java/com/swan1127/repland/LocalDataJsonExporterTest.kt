package com.swan1127.repland

import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeConstraintSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataJsonExporterTest {
    @Test
    fun writes_versioned_json_and_escapes_user_text() {
        val json = LocalDataJsonExporter.export(
            LocalDataSnapshot(
                generatedAtEpochMillis = 1,
                tasks = listOf(
                    Task(
                        id = "task-1",
                        description = "包含\"引号\"\n换行",
                        displayName = "任务",
                        category = TaskCategory.COURSE,
                        userPriority = TaskPriority.HIGH,
                        estimatedDays = 1,
                        totalDurationMinutes = null,
                        dueDate = null,
                        status = TaskStatus.NOT_STARTED,
                        completionSummary = null,
                        actualDurationMinutes = null,
                        progressPercent = null,
                        postponeCount = 0,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    ),
                ),
                executionLogs = emptyList(),
                weeklyTimeBlocks = emptyList(),
                dateOverrides = emptyList(),
                timeConstraintSettings = TimeConstraintSettings(null, 0),
                planHistory = emptyList(),
                categoryPreferences = CategoryPreferences.defaults,
                reminderPreferences = ReminderPreferences(),
                aiPreferences = AiPreferences(),
                profileEvidence = emptyList(),
            ),
        )

        assertTrue(json.startsWith("{\"format\":\"${LocalDataJsonExporter.FORMAT}\""))
        assertTrue(json.contains("包含\\\"引号\\\"\\n换行"))
        assertTrue(json.contains("\"executionLogs\":[]"))
        assertTrue(json.contains("\"profileEvidence\":[]"))
    }
}
