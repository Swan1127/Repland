package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsActions
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.tasks.TaskCaptureViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CaptureNumericWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(tag: String): SemanticsNodeInteraction {
        val hasScrollParent = hasTestTag(tag) and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        if (rule.onAllNodes(hasScrollParent).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag(tag).performScrollTo()
        return rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test fun invalid_raw_survives_close_reopen_and_recreation_until_unknown_is_explicitly_selected() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val container = (rule.activity.application as ReplandApplication).appContainer
        lateinit var vm: TaskCaptureViewModel
        rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[TaskCaptureViewModel::class.java] }
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        val originalDraft = vm.uiState.value.draft
        val tasks = runBlocking { container.taskRepository.observeTasks().first() }
        val plan = runBlocking { container.planRepository.observeCurrentPlan().first() }
        val order = runBlocking { container.planRepository.observeTaskOrder().first() }
        val id = UUID.randomUUID().toString()
        val own = TaskCaptureDraft(id = id, text = "QA-capture28-$id", stage = TaskCaptureStage.DURATION,
            isCustomDuration = true, customDurationText = "45")
        try {
            rule.runOnUiThread { vm.update(own) }
            waitTag("navigation-tasks"); rule.onNodeWithTag("navigation-tasks").performClick()
            waitTag("add-task"); rule.onNodeWithTag("add-task").performClick()
            waitTag("task-capture-duration-custom-input")
            rule.onNodeWithTag("task-capture-duration-custom-input").performScrollTo().performTextReplacement("-30")
            rule.onNodeWithTag("task-capture-duration-custom-input").assertTextContains("-30")
            rule.onNodeWithTag("task-capture-save-duration").assertIsNotEnabled().performClick()
            visible("task-capture-close").performClick()
            runBlocking { withTimeout(10_000) { container.taskCaptureRepository.observeDraft().first {
                it?.id == id && it.customDurationText == "-30"
            } } }
            rule.onNodeWithTag("add-task").performClick(); waitTag("task-capture-duration-custom-input")
            rule.onNodeWithTag("task-capture-duration-custom-input").performScrollTo().assertTextContains("-30")
            rule.activityRule.scenario.recreate(); waitTag("task-capture-duration-custom-input")
            rule.onNodeWithTag("task-capture-duration-custom-input").performScrollTo().assertTextContains("-30")
            rule.onNodeWithTag("task-capture-save-duration").assertIsNotEnabled()
            rule.onNodeWithTag("task-capture-back").performClick(); rule.onNodeWithTag("task-capture-back").performClick()
            visible("task-capture-duration-error")
            rule.onNodeWithTag("task-capture-save-inbox").performClick()
            waitTag("task-capture-duration-custom-input")
            rule.onNodeWithTag("task-capture-duration-custom-input").performScrollTo().assertTextContains("-30")
            assertEquals(tasks, runBlocking { container.taskRepository.observeTasks().first() })
            assertEquals(plan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            assertEquals(order, runBlocking { container.planRepository.observeTaskOrder().first() })
            rule.onNodeWithTag("task-capture-duration-unknown").performScrollTo().performClick()
            rule.onNodeWithTag("task-capture-save-duration").assertIsEnabled().performClick()
            rule.waitUntil(10_000) { vm.uiState.value.receipt != null && !vm.uiState.value.saving }
            val after = runBlocking { container.taskRepository.observeTasks().first() }
            assertEquals(tasks.size + 1, after.size)
            val saved = after.single { it.id == id }
            assertNull(saved.totalDurationMinutes)
            assertEquals(java.time.LocalDate.now(), saved.scheduledForDate)
            assertEquals(plan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            assertEquals(order, runBlocking { container.planRepository.observeTaskOrder().first() })
        } finally {
            rule.runOnUiThread { if (vm.uiState.value.draft.id == id || vm.uiState.value.draft.text.isEmpty()) {
                vm.dismissReceipt(); vm.update(originalDraft); vm.retain()
            } }
            android.database.sqlite.SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath,
                null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
            }
        }
    }
}
