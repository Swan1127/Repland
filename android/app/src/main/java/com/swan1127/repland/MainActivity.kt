package com.swan1127.repland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swan1127.repland.ui.ReplandApp
import com.swan1127.repland.ui.plan.PlanViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import com.swan1127.repland.ui.reminders.ReminderSettingsViewModel
import com.swan1127.repland.ui.profile.ProfileEvidenceViewModel
import com.swan1127.repland.ui.data.DataManagementViewModel
import com.swan1127.repland.ui.ai.PlanningAgentViewModel
import com.swan1127.repland.ui.time.TimeViewModel
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.ui.theme.ReplandTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as ReplandApplication).appContainer
        setContent {
            ReplandTheme {
                val taskViewModel: TaskViewModel = viewModel(
                    factory = TaskViewModel.Factory(appContainer.taskRepository),
                )
                val timeViewModel: TimeViewModel = viewModel(
                    factory = TimeViewModel.Factory(
                        appContainer.timeRepository,
                        appContainer.timetableImporter,
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
                ReplandApp(
                    taskViewModel,
                    timeViewModel,
                    planViewModel,
                    categoryPreferenceViewModel,
                    reminderSettingsViewModel,
                    profileEvidenceViewModel,
                    dataManagementViewModel,
                    planningAgentViewModel,
                )
            }
        }
    }
}
