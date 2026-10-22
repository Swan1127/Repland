package com.swan1127.repland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swan1127.repland.data.AppearanceMode
import com.swan1127.repland.domain.model.ThemeAccent
import com.swan1127.repland.ui.ReplandApp
import com.swan1127.repland.ui.plan.PlanViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import com.swan1127.repland.ui.reminders.ReminderSettingsViewModel
import com.swan1127.repland.ui.profile.ProfileEvidenceViewModel
import com.swan1127.repland.ui.data.DataManagementViewModel
import com.swan1127.repland.ui.ai.PlanningAgentViewModel
import com.swan1127.repland.ui.ai.AiProviderConfigViewModel
import com.swan1127.repland.ui.agent.ArrangementAssistantViewModel
import com.swan1127.repland.ui.time.TimeViewModel
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.ui.theme.ReplandTheme
import com.swan1127.repland.ui.engagement.EngagementViewModel
import com.swan1127.repland.ui.widget.HomeWidgetProvider

class MainActivity : ComponentActivity() {
    /**
     * The home-screen widget is redrawn as the user leaves, not as they edit.
     *
     * Everything that changes the widget's answer — the plan, the tasks, the timetable — is
     * written through a repository, so the widget could subscribe to those flows instead. It does
     * not, deliberately: a repository write fires on every keystroke in a task editor, which would
     * redraw a widget nobody is looking at while the user is still typing. `onStop` is the one
     * moment that is both cheap and certain, because the next thing the user sees is the home
     * screen the widget is on.
     *
     * `onStop` and not `onPause`: pausing happens for a dialog and for the notification shade, and
     * neither of those means the user is finished editing.
     */
    override fun onStop() {
        super.onStop()
        HomeWidgetProvider.requestRefresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as ReplandApplication).appContainer
        setContent {
            // D6: the appearance mode is owned here because it decides which colour scheme
            // wraps the whole tree, and is persisted in the auxiliary store.
            var appearanceMode by remember { mutableStateOf(appContainer.auxiliaryStore.appearanceMode) }
            // D6b: the brand identity is a second, independent axis from light/dark. Owned here for
            // the same reason the mode is — it decides which colour scheme wraps the whole tree —
            // and persisted in the same auxiliary store.
            var themeAccent by remember { mutableStateOf(appContainer.auxiliaryStore.themeAccent) }
            val darkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            ReplandTheme(darkTheme = darkTheme, accent = themeAccent) {
                val taskViewModel: TaskViewModel = viewModel(
                    factory = TaskViewModel.Factory(appContainer.taskRepository),
                )
                val timeViewModel: TimeViewModel = viewModel(
                    factory = TimeViewModel.Factory(
                        appContainer.timeRepository,
                        appContainer.timetableImporter,
                        appContainer.xlsxTimetableImporter,
                    ),
                )
                val planViewModel: PlanViewModel = viewModel(
                    factory = PlanViewModel.Factory(
                        appContainer.planRepository,
                        appContainer.planDraftGenerator,
                    ),
                )
                val categoryPreferenceViewModel: CategoryPreferenceViewModel = viewModel(
                    factory = CategoryPreferenceViewModel.Factory(appContainer.categoryPreferenceRepository),
                )
                val reminderSettingsViewModel: ReminderSettingsViewModel = viewModel(
                    factory = ReminderSettingsViewModel.Factory(appContainer.reminderSettingsRepository),
                )
                val profileEvidenceViewModel: ProfileEvidenceViewModel = viewModel(
                    factory = ProfileEvidenceViewModel.Factory(appContainer.profileEvidenceRepository),
                )
                val dataManagementViewModel: DataManagementViewModel = viewModel(
                    factory = DataManagementViewModel.Factory(appContainer.dataManagementRepository),
                )
                val planningAgentViewModel: PlanningAgentViewModel = viewModel(
                    factory = PlanningAgentViewModel.Factory(
                        appContainer.aiSettingsRepository,
                        appContainer.planningAgentWorkflow,
                    ),
                )
                val aiProviderConfigViewModel: AiProviderConfigViewModel = viewModel(
                    factory = AiProviderConfigViewModel.Factory(
                        appContainer.aiProviderConfigRepository,
                        appContainer.arrangementAssistantAdvisor,
                        appContainer.aiModelCatalog,
                    ),
                )
                val arrangementAssistantViewModel: ArrangementAssistantViewModel = viewModel(
                    factory = ArrangementAssistantViewModel.Factory(
                        appContainer.aiSettingsRepository,
                        appContainer.arrangementAssistantAdvisor,
                    ),
                )
                val engagementViewModel: EngagementViewModel = viewModel(
                    factory = EngagementViewModel.Factory(appContainer.engagementRepository),
                )
                ReplandApp(
                    taskViewModel,
                    timeViewModel,
                    planViewModel,
                    categoryPreferenceViewModel,
                    reminderSettingsViewModel,
                    profileEvidenceViewModel,
                    dataManagementViewModel,
                    planningAgentViewModel,
                    aiProviderConfigViewModel,
                    arrangementAssistantViewModel,
                    engagementViewModel,
                    agentChatModel = appContainer.agentChatModel,
                    appearanceMode = appearanceMode,
                    onAppearanceModeChange = { mode ->
                        appContainer.auxiliaryStore.appearanceMode = mode
                        appearanceMode = mode
                    },
                    themeAccent = themeAccent,
                    onThemeAccentChange = { accent ->
                        appContainer.auxiliaryStore.themeAccent = accent
                        themeAccent = accent
                    },
                )
            }
        }
    }
}
