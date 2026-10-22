package com.swan1127.repland

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.importer.XlsxTimetableImporter
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
import com.swan1127.repland.domain.ports.AiModelCatalog
import com.swan1127.repland.domain.ports.NoOpAiModelCatalog
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.AgentChatModel
import com.swan1127.repland.domain.model.NoOpAgentChatModel
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.NoOpArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.PlanningAgent
import com.swan1127.repland.domain.model.PlanningAgentWorkflow
import com.swan1127.repland.reminders.LocalReminderScheduler
import com.swan1127.repland.data.AuxiliaryStore

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
        ReplandDatabase.MIGRATION_20_21,
    ).build()

    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val timeRepository: TimeRepository = RoomTimeRepository(database)
    val planRepository: PlanRepository = RoomPlanRepository(database.planDao())
    val categoryPreferenceRepository: CategoryPreferenceRepository =
        RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
    val reminderSettingsRepository: ReminderSettingsRepository =
        RoomReminderSettingsRepository(database.reminderSettingsDao())
    val profileEvidenceRepository: ProfileEvidenceRepository = RoomProfileEvidenceRepository(database)
    val dataManagementRepository: DataManagementRepository = RoomDataManagementRepository(database)
    val aiSettingsRepository: AiSettingsRepository = RoomAiSettingsRepository(database.aiSettingsDao())
    val aiProviderConfigRepository: AiProviderConfigRepository = SecureAiProviderConfigRepository(context)
    val engagementRepository = RoomEngagementRepository(database)
    /**
     * Debug builds may use the user-configured provider, whichever OpenAI-compatible service
     * that is. Release builds remain local-only: the adapter lives in the debug source set,
     * so `Class.forName` fails there and the no-op advisor takes over.
     */
    val aiAdvisor: AiAdvisor = runCatching {
        Class.forName("com.swan1127.repland.debug.DebugOpenAiCompatibleAdvisor")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as AiAdvisor
    }.getOrDefault(NoOpAiAdvisor)
    val arrangementAssistantAdvisor: ArrangementAssistantAdvisor = runCatching {
        Class.forName("com.swan1127.repland.debug.DebugOpenAiCompatibleArrangementAdvisor")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as ArrangementAssistantAdvisor
    }.getOrDefault(NoOpArrangementAssistantAdvisor)
    /** Reads the provider's own model list, so Settings never bakes in a model name. */
    val aiModelCatalog: AiModelCatalog = runCatching {
        Class.forName("com.swan1127.repland.debug.DebugOpenAiCompatibleModelCatalog")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as AiModelCatalog
    }.getOrDefault(NoOpAiModelCatalog)
    /**
     * The group chat's prose turn. Same reflection trick as the advisors: a release build
     * finds no class and falls back to a model that fails loudly, so no release APK can
     * quietly pretend to have talked to a provider.
     */
    val agentChatModel: AgentChatModel = runCatching {
        Class.forName("com.swan1127.repland.debug.DebugOpenAiCompatibleAgentChat")
            .getDeclaredConstructor(AiProviderConfigRepository::class.java)
            .newInstance(aiProviderConfigRepository) as AgentChatModel
    }.getOrDefault(NoOpAgentChatModel)
    val planDraftGenerator: PlanDraftGenerator = PlanGenerator
    /** Local bounded workflow; it has no repository write capability. */
    val planningAgentWorkflow = PlanningAgentWorkflow(
        planningAgent = PlanningAgent(aiAdvisor, planDraftGenerator),
    )
    val timetableImporter = PdfTimetableImporter(context)

    /** Reads .xlsx timetables; shares its recognition rules with the clipboard path. */
    val xlsxTimetableImporter = XlsxTimetableImporter()

    /**
     * Derived, non-planning data introduced by the unbound features (appearance mode,
     * reflections, review points, KWL notes, timetable sets). Deliberately outside Room:
     * none of it is a scheduler input, so it needs no migration and cannot corrupt a plan.
     */
    val auxiliaryStore = AuxiliaryStore(context)
}
