package com.swan1127.repland.domain.model

/** A complete local-only export snapshot. Export never mutates the underlying data. */
data class LocalDataSnapshot(
    val generatedAtEpochMillis: Long,
    val tasks: List<Task>,
    val executionLogs: List<TaskExecutionLog>,
    val weeklyTimeBlocks: List<WeeklyTimeBlock>,
    val dateOverrides: List<DateOverride>,
    val timeConstraintSettings: TimeConstraintSettings,
    val planHistory: List<ConfirmedPlan>,
    val categoryPreferences: Map<TaskCategory, Int>,
    val reminderPreferences: ReminderPreferences,
    val aiPreferences: AiPreferences,
    val profileEvidence: List<ProfileEvidence>,
)
