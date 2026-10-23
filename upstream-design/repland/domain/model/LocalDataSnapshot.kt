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
    val engagementMode: EngagementMode = EngagementMode.GUIDED,
    val usageEvents: List<UsageEvent> = emptyList(),
    /**
     * The user's tool writing, for the export only.
     *
     * Empty by default, and it stays empty when the snapshot is read straight out of Room: the
     * auxiliary store lives outside the database by design, so the caller that *does* know about
     * it ([com.swan1127.repland.ui.data.DataManagementViewModel.exportTo]) is the one that supplies
     * these. It is exported as its own array rather than attached per evidence row because the row
     * is written to disk and this is not — a file must not claim a link the database does not hold.
     */
    val toolOutputs: List<ToolOutputRecord> = emptyList(),
)
