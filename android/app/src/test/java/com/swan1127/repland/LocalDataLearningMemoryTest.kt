package com.swan1127.repland

import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.ExecutionSession
import com.swan1127.repland.domain.model.LearningMemoryPointSnapshot
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.RecallGrade
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.TimeConstraintSettings
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataLearningMemoryTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun memoryExportIncludesSettingsAndAllStateFieldsWithoutReplacingUpstreamSections() {
        val state = MemoryModel.grade(MemoryModel.seed(4, today), RecallGrade.FORGOT, today.plusDays(1))
            .copy(exposures = 3)
        val memory = LearningMemorySnapshot(
            referenceEnabled = true,
            gain = 0.85,
            points = listOf(LearningMemoryPointSnapshot("point", "task", "title", "a\"b\nline", today, 0, state)),
        )
        val snapshot = snapshot().copy(
            learningMemory = memory,
            executionSessions = listOf(ExecutionSession("session", "task", "title", "plan", "segment", 1500, 1)),
            numericProfileRecords = mapOf("profile" to "numeric-payload"),
            autonomyRecords = mapOf("autonomy" to "autonomy-payload"),
            usageSummaryRecords = mapOf("summary" to "summary-payload"),
        )
        val json = LocalDataJsonExporter.export(snapshot)
        listOf(
            "\"learningMemory\":{\"version\":1",
            "\"referenceEnabled\":true",
            "\"gain\":0.85",
            "\"capturedOn\":\"2026-10-22\"",
            "\"lastEventOn\":\"2026-10-23\"",
            "\"stabilityDays\":1.0",
            "\"difficulty\":",
            "\"difficultySeed\":5.5",
            "\"importance\":4",
            "\"reviews\":1",
            "\"lapses\":1",
            "\"exposures\":3",
            "a\\\"b\\nline",
            "\"executionSessions\":[{\"id\":\"session\"",
            "numeric-payload",
            "autonomy-payload",
            "summary-payload",
        ).forEach { expected -> assertTrue(expected, json.contains(expected)) }
    }

    @Test
    fun legacyGraduatedPointsKeepTheirStageAndNullMemory() {
        val memory = LearningMemorySnapshot(
            referenceEnabled = false,
            gain = 1.0,
            points = listOf(LearningMemoryPointSnapshot("legacy", "task", "title", "content", today, 5)),
        )
        val json = LocalDataJsonExporter.export(snapshot().copy(learningMemory = memory))
        assertTrue(json.contains("\"stage\":5,\"memory\":null"))
        assertTrue(json.contains("\"referenceEnabled\":false"))
    }

    @Test
    fun absentMemoryKeepsTheExistingExportContract() {
        val json = LocalDataJsonExporter.export(snapshot())
        assertFalse(json.contains("\"learningMemory\""))
        assertTrue(json.contains("\"executionSessions\":[]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteGainCannotProduceInvalidJson() {
        LocalDataJsonExporter.export(snapshot().copy(
            learningMemory = LearningMemorySnapshot(referenceEnabled = false, gain = Double.NaN, points = emptyList()),
        ))
    }

    private fun snapshot() = LocalDataSnapshot(
        generatedAtEpochMillis = 1,
        tasks = emptyList(),
        executionLogs = emptyList(),
        weeklyTimeBlocks = emptyList(),
        dateOverrides = emptyList(),
        timeConstraintSettings = TimeConstraintSettings(null, 0),
        planHistory = emptyList(),
        categoryPreferences = CategoryPreferences.defaults,
        reminderPreferences = ReminderPreferences(),
        aiPreferences = AiPreferences(),
        profileEvidence = emptyList(),
    )
}
