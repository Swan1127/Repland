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
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
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
    ).build()

    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val timeRepository: TimeRepository = RoomTimeRepository(database.timeDao())
    val planRepository: PlanRepository = RoomPlanRepository(database.planDao())
    val categoryPreferenceRepository: CategoryPreferenceRepository =
        RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
    val reminderSettingsRepository: ReminderSettingsRepository =
        RoomReminderSettingsRepository(database.reminderSettingsDao())
    val profileEvidenceRepository: ProfileEvidenceRepository = RoomProfileEvidenceRepository(database)
    val dataManagementRepository: DataManagementRepository = RoomDataManagementRepository(database)
    val aiSettingsRepository: AiSettingsRepository = RoomAiSettingsRepository(database.aiSettingsDao())
    /** ADR 0001: no network provider or API key is bundled with the app. */
    val aiAdvisor: AiAdvisor = NoOpAiAdvisor
    val planDraftGenerator: PlanDraftGenerator = PlanGenerator
    /** Local bounded workflow; it has no repository write capability. */
    val planningAgentWorkflow = PlanningAgentWorkflow(
        planningAgent = PlanningAgent(aiAdvisor, planDraftGenerator),
    )
    val timetableImporter = PdfTimetableImporter(context)
}
