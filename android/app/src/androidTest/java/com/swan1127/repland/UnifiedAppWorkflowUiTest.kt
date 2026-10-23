package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.TaskInputSource
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.planteam.runtime.AcceptanceFinished
import com.swan1127.repland.domain.planteam.runtime.DraftReady
import com.swan1127.repland.ui.tasks.TaskCaptureViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against the disposable QA installation on the dedicated test emulator. */
@RunWith(AndroidJUnit4::class)
class UnifiedAppWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = (rule.activity.application as ReplandApplication).appContainer

    @Before fun resetTheIsolatedQaInstallation() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        runBlocking {
            app.autonomyRepository.setEnabled(false)
            val capture = withContext(Dispatchers.Main) {
                ViewModelProvider(rule.activity, TaskCaptureViewModel.Factory(app.taskCaptureRepository))
                    .get(TaskCaptureViewModel::class.java)
            }
            withTimeout(10_000) { capture.uiState.first { !it.loading } }
            withContext(Dispatchers.Main) { capture.prepareForDataClear() }
            var cleared = false
            try {
                app.dataManagementRepository.clearAllLocalData()
                cleared = true
                app.autonomyRepository.setEnabled(false)
                app.aiSettingsRepository.setEnabled(false)
            } finally {
                withContext(Dispatchers.Main) { capture.finishDataClear(cleared) }
            }
        }
        // Recreation retains ViewModels; reset their pending draft writes before clearing storage.
        rule.activityRule.scenario.recreate()
        waitTag("navigation-today")
    }

    private fun waitTag(tag: String) = rule.waitUntil(15_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun hideImeIfVisible() {
        // Let Compose commit the replacement before native Back can trigger a blur callback.
        rule.waitForIdle()
        if (NativeSheetTestInput.shell("dumpsys input_method").contains("mWindowVisible=true")) {
            NativeSheetTestInput.shell("input keyevent 4")
            rule.waitUntil(10_000) {
                !NativeSheetTestInput.shell("dumpsys input_method").contains("mWindowVisible=true")
            }
        }
        rule.waitForIdle()
    }

    private fun clickCapture(tag: String) {
        hideImeIfVisible()
        NativeSheetTestInput.awaitFocusedDialog(rule, "新任务")
        val node = rule.onNodeWithTag(tag)
        var parent = node.fetchSemanticsNode().parent
        var scrollable = false
        while (parent != null) {
            if (parent.config.contains(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)) scrollable = true
            parent = parent.parent
        }
        // On API 26's compact display the entire sheet scrolls, including its footer.
        if (scrollable) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performClick()
    }

    @Test fun realTaskAndAvailabilityReachTheTeamAndLearningMemoryThroughTheApp() {
        val name = "融合流程英语复习"
        val content = "recall 是主动提取，recognition 是看到后识别"
        rule.onNodeWithTag("navigation-tasks").performClick()
        waitTag("add-task")
        rule.onNodeWithTag("add-task").performClick()
        waitTag("task-capture-input")
        NativeSheetTestInput.awaitFocusedDialog(rule, "新任务")
        rule.onNodeWithTag("task-capture-input").performClick().performTextReplacement(name)
        rule.waitForIdle()
        rule.onNodeWithTag("task-capture-input").assertTextContains(name)
        clickCapture("task-capture-arrange-today")
        waitTag("task-capture-choose-duration")
        clickCapture("task-capture-choose-duration")
        waitTag("task-capture-duration-30")
        clickCapture("task-capture-duration-30")
        clickCapture("task-capture-save-duration")
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("task-capture-input").fetchSemanticsNodes().isEmpty() &&
                runBlocking { app.taskRepository.observeTasks().first().size == 1 }
        }
        val task = runBlocking { app.taskRepository.observeTasks().first().single() }
        assertEquals(name, task.displayName)
        assertEquals(30, task.totalDurationMinutes)
        assertEquals(TaskInputSource.USER_INPUT, task.inputSources.duration)
        assertEquals(TaskStatus.NOT_STARTED, task.status)

        val availableDay = LocalDate.now().plusDays(1)
        runBlocking {
            app.timeRepository.saveDateOverride(DateOverrideDraft(
                title = "集成测试可用时间", date = availableDay, type = DateOverrideType.AVAILABLE,
                startMinute = 9 * 60, endMinute = 12 * 60,
            ))
        }
        assertNull(runBlocking { app.planRepository.observeCurrentPlan().first() })
        rule.onNodeWithTag("navigation-today").performClick()
        waitTag("preview-task-plan")
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("preview-task-plan"))
        rule.onNodeWithTag("preview-task-plan").assertIsEnabled().performClick()
        waitTag("accept-plan-draft")
        waitTag("team-log")
        rule.onNodeWithTag("team-log").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("验收通过，仍需你确认应用").assertExists()
        val preview = runBlocking { requireNotNull(app.planRepository.observeDraft().first()) }
        val team = app.planningOperationService.teamPreview.value
        assertEquals(6, team.result!!.selectedAgents.size)
        assertTrue(team.events.last() is DraftReady)
        assertTrue(team.events.filterIsInstance<AcceptanceFinished>().single().passed)
        assertEquals(preview, team.draft)
        assertTrue(preview.segments.any { it.taskId == task.id && it.date == availableDay })
        assertNull(runBlocking { app.planRepository.observeCurrentPlan().first() })
        rule.onNodeWithTag("accept-plan-draft").assertIsEnabled().performClick()
        rule.waitUntil(15_000) {
            runBlocking { app.planRepository.observeCurrentPlan().first() != null &&
                app.planRepository.observeDraft().first() == null }
        }
        val confirmed = runBlocking { app.planRepository.observeCurrentPlan().first()!! }
        // Confirmation replaces provisional draft IDs with persistent placement identities.
        assertEquals(preview.segments.map { it.copy(id = "") }, confirmed.segments.map { it.copy(id = "") })
        assertTrue(confirmed.segments.all { it.id.isNotBlank() && !it.id.startsWith("draft:") })
        assertTrue(app.auxiliaryStore.reviewPoints().isEmpty())
        assertFalse(app.auxiliaryStore.memoryReferenceEnabled)

        rule.onNodeWithTag("navigation-mine").performClick()
        waitTag("profile-learning-review")
        rule.onNodeWithTag("profile-learning-review").performScrollTo().performClick()
        waitTag("memory-capture-toggle")
        rule.onNodeWithTag("memory-capture-toggle").performClick()
        rule.onNodeWithTag("memory-capture-task").performScrollTo().assertTextContains(name)
        rule.onNodeWithTag("memory-capture-content").performScrollTo().performTextReplacement(content)
        hideImeIfVisible()
        rule.onNodeWithTag("memory-capture-save").performScrollTo().performClick()
        rule.onNodeWithText("已保存复习点").assertExists()
        val point = app.auxiliaryStore.reviewPoints().single()
        assertEquals(task.id, point.taskId)
        assertEquals(content, point.content)
        assertNotNull(point.memory)
        assertNull(app.auxiliaryStore.memoryReference())

        rule.onNodeWithTag("tool-close").performClick()
        rule.onNodeWithTag("mine-settings").performScrollTo().performClick()
        waitTag("mine-settings-page")
        rule.onNodeWithTag("learning-memory-reference-switch").performScrollTo().assertIsOff().performClick()
        rule.onNodeWithTag("learning-memory-reference-switch").assertIsOn()
        assertEquals(content, app.auxiliaryStore.memoryReference()!!.forTask(task.id)!!.content)
        rule.activityRule.scenario.recreate()
        waitTag("mine-settings-page")
        rule.onNodeWithTag("learning-memory-reference-switch").performScrollTo().assertIsOn()
        assertEquals(point, app.auxiliaryStore.reviewPoints().single())
        assertEquals(confirmed, runBlocking { app.planRepository.observeCurrentPlan().first() })
        assertEquals(TaskStatus.NOT_STARTED, runBlocking { app.taskRepository.observeTasks().first().single().status })
    }
}
