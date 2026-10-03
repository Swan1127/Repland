package com.swan1127.repland

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomCategoryPreferenceRepository
import com.swan1127.repland.data.room.RoomTaskRepository
import com.swan1127.repland.data.room.RoomTimeRepository
import com.swan1127.repland.data.room.RoomPlanRepository
import com.swan1127.repland.data.room.RoomReminderSettingsRepository
import com.swan1127.repland.data.room.RoomProfileEvidenceRepository
import com.swan1127.repland.data.room.RoomDataManagementRepository
import com.swan1127.repland.data.room.RoomAiSettingsRepository
import com.swan1127.repland.data.room.RoomEngagementRepository
import com.swan1127.repland.data.security.SecureAiProviderConfigRepository
import com.swan1127.repland.domain.model.PlanDraftGenerator
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.domain.ports.CategoryPreferenceRepository
import com.swan1127.repland.domain.ports.TaskRepository
import com.swan1127.repland.domain.ports.TimeRepository
import com.swan1127.repland.domain.ports.ReminderSettingsRepository
import com.swan1127.repland.domain.ports.ProfileEvidenceRepository
import com.swan1127.repland.domain.ports.DataManagementRepository
import com.swan1127.repland.domain.ports.AiSettingsRepository
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.NoOpArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.PlanningAgent
import com.swan1127.repland.domain.model.PlanningAgentWorkflow
import com.swan1127.repland.reminders.LocalReminderScheduler

class ReplandApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        LocalReminderScheduler.createNotificationChannel(this)
    }
}

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        ReplandDatabase::class.java,
        "repland.db",
    ).addMigrations(
        ReplandDatabase.MIGRATION_1_2,
        ReplandDatabase.MIGRATION_2_3,
        ReplandDatabase.MIGRATION_3_4,
        ReplandDatabase.MIGRATION_4_5,
        ReplandDatabase.MIGRATION_5_6,
        ReplandDatabase.MIGRATION_6_7,
        ReplandDatabase.MIGRATION_7_8,
        ReplandDatabase.MIGRATION_8_9,
        ReplandDatabase.MIGRATION_9_10,
        ReplandDatabase.MIGRATION_10_11,
        ReplandDatabase.MIGRATION_11_12,
        ReplandDatabase.MIGRATION_12_13,
        ReplandDatabase.MIGRATION_13_14,
        ReplandDatabase.MIGRATION_14_15,
        ReplandDatabase.MIGRATION_15_16,
        ReplandDatabase.MIGRATION_16_17,
        ReplandDatabase.MIGRATION_17_18,
        ReplandDatabase.MIGRATION_18_19,
        ReplandDatabase.MIGRATION_19_20,
    ).build()

    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val executionSessionRepository = com.swan1127.repland.data.room.RoomExecutionSessionRepository(database)
    val timeRepository: TimeRepository = RoomTimeRepository(database.timeDao())
    val planRepository: PlanRepository = RoomPlanRepository(database)
    val categoryPreferenceRepository: CategoryPreferenceRepository =
        RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
    val reminderSettingsRepository: ReminderSettingsRepository =
        RoomReminderSettingsRepository(database.reminderSettingsDao())
    val profileEvidenceRepository: ProfileEvidenceRepository = RoomProfileEvidenceRepository(database)
    val dataManagementRepository: DataManagementRepository = RoomDataManagementRepository(database)
    val aiSettingsRepository: AiSettingsRepository = RoomAiSettingsRepository(database.aiSettingsDao())
    val aiProviderConfigRepository: AiProviderConfigRepository = SecureAiProviderConfigRepository(context)
    val engagementRepository = RoomEngagementRepository(database)
    /** QA/internal share the optional BYOK provider; release stays local-only. */
    val aiAdvisor: AiAdvisor = runCatching {
        Class.forName("com.swan1127.repland.data.ai.CompatibleAiAdvisor")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as AiAdvisor
    }.getOrDefault(NoOpAiAdvisor)
    val arrangementAssistantAdvisor: ArrangementAssistantAdvisor = runCatching {
        Class.forName("com.swan1127.repland.data.ai.CompatibleArrangementAdvisor")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as ArrangementAssistantAdvisor
    }.getOrDefault(NoOpArrangementAssistantAdvisor)
    val planDraftGenerator: PlanDraftGenerator = PlanGenerator
    /** Local bounded workflow; it has no repository write capability. */
    val planningAgentWorkflow = PlanningAgentWorkflow(
        planningAgent = PlanningAgent(aiAdvisor, planDraftGenerator),
    )
    val timetableImporter = PdfTimetableImporter(context)
}
