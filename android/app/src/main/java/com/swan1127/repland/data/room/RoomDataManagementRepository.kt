package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.ports.DataManagementRepository

class RoomDataManagementRepository(
    private val database: ReplandDatabase,
) : DataManagementRepository {
    override suspend fun snapshot(): LocalDataSnapshot = database.withTransaction {
        val tasks = database.taskDao().getAll().map(TaskEntity::toDomain)
        val logs = database.executionLogDao().getAll().map(ExecutionLogEntity::toDomain)
        val settings = database.timeDao().getSemesterSettings()
        LocalDataSnapshot(
            generatedAtEpochMillis = System.currentTimeMillis(),
            tasks = tasks,
            executionLogs = logs,
            weeklyTimeBlocks = database.timeDao().getAllWeeklyBlocks().map(WeeklyTimeBlockEntity::toDomain),
            dateOverrides = database.timeDao().getAllDateOverrides().map(DateOverrideEntity::toDomain),
            timeConstraintSettings = TimeConstraintSettings(
                semesterFirstWeekMonday = settings?.firstWeekMondayEpochDay?.let(java.time.LocalDate::ofEpochDay),
                updatedAtEpochMillis = settings?.updatedAtEpochMillis ?: 0L,
            ),
            planHistory = database.planDao().getAllPlansWithSegments().map(PlanWithSegments::toDomain),
            categoryPreferences = CategoryPreferences.normalized(
                database.categoryPreferenceDao().getAll().associate(CategoryPreferenceEntity::toDomainPair),
            ),
            reminderPreferences = database.reminderSettingsDao().get()?.toDomain() ?: ReminderPreferences(),
            aiPreferences = database.aiSettingsDao().get()?.toDomain()
                ?: com.swan1127.repland.domain.model.AiPreferences(),
            profileEvidence = database.profileEvidenceDao().getAll().toDomainEvidence(logs, tasks),
        )
    }

    override suspend fun clearAllLocalData() {
        // This is intentionally available only behind the explicit UI confirmation.
        // Room keeps its schema metadata while removing every application-owned table.
        database.clearAllTables()
    }
}
