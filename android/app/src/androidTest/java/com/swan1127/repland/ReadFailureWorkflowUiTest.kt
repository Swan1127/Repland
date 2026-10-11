package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.ui.time.TimeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import java.io.File

class ReadFailureWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = (rule.activity.application as ReplandApplication).appContainer
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun fill(tag: String, value: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun taskFault(initialFailure: Boolean = false): Pair<ReadFaultTasks, TaskViewModel> {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val fault = ReadFaultTasks(container.taskRepository).apply { failed.value = initialFailure }
        lateinit var vm: TaskViewModel
        rule.runOnUiThread {
            vm = TaskViewModel(fault, container.executionSessionRepository)
            rule.activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TaskViewModel::class.java.canonicalName}", vm)
        }
        rule.activityRule.scenario.recreate(); return fault to vm
    }
    private fun timeFault(): Pair<ReadFaultTime, TimeViewModel> {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val fault = ReadFaultTime(container.timeRepository)
        lateinit var vm: TimeViewModel
        rule.runOnUiThread {
            vm = TimeViewModel(fault, container.timetableImporter)
            rule.activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TimeViewModel::class.java.canonicalName}", vm)
        }
        rule.activityRule.scenario.recreate(); return fault to vm
    }
    private fun retryInSheet() = rule.onNode(hasTestTag("retry-data-read") and hasAnyAncestor(hasTestTag("editor-sheet")))
        .performScrollTo().performClick()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    private fun cleanupTask(id: String) {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
        }
    }

    @Test fun task_edit_keeps_input_identity_and_disabled_save_through_read_failure_and_recreation() {
        val id = UUID.randomUUID().toString(); val name = "QA-read23-$id"
        try {
            runBlocking { container.taskRepository.save(TaskDraft(id, name, "原备注", TaskCategory.COURSE, TaskPriority.MEDIUM,
                1, 30, null, scheduledForDate = LocalDate.now())) }
            val original = runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } }
            val (fault, vm) = taskFault()
            waitTag("navigation-tasks"); rule.onNodeWithTag("navigation-tasks").performClick(); waitTag("task-groups-scroll")
            expandTodayTaskGroup(rule)
            rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-card-$name"))
            rule.onNodeWithTag("task-card-$name").performClick(); waitTag("task-detail-scroll")
            rule.onNodeWithTag("task-detail-scroll").performScrollToNode(hasText("编辑任务")); rule.onNodeWithText("编辑任务").performClick()
            waitTag("task-editor-name"); fill("task-editor-description", "读失败后保留输入")
            fault.failed.value = true; rule.waitUntil(10_000) { vm.uiState.value.readError != null }
            rule.onNodeWithTag("task-editor-save").assertIsNotEnabled()
            rule.onNodeWithText("这件任务已不在任务库").assertDoesNotExist()
            rule.activityRule.scenario.recreate(); waitTag("task-editor-name")
            rule.onNodeWithTag("task-editor-description").performScrollTo().assertTextContains("读失败后保留输入")
            rule.onNodeWithTag("task-editor-save").assertIsNotEnabled()
            assertEquals(original, runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } })
            rule.onNode(hasTestTag("data-read-notice") and hasAnyAncestor(hasTestTag("editor-sheet"))).performScrollTo()
            screenshot("qa-read23-task-failure.png")
            fault.failed.value = false; retryInSheet(); rule.waitUntil(10_000) { vm.uiState.value.isTrusted }
            rule.onNodeWithTag("task-editor-save").assertIsEnabled().performClick(); gone("task-editor-name")
            val saved = runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } }
            assertEquals(original.createdAtEpochMillis, saved.createdAtEpochMillis); assertEquals("读失败后保留输入", saved.description)
        } finally { cleanupTask(id) }
    }

    @Test fun weekly_edit_retains_original_constraints_and_input_until_actual_retry_succeeds() {
        val name = "QA-time-read23-${UUID.randomUUID()}"; var cleanupId: String? = null
        try {
            runBlocking { container.timeRepository.saveWeeklyBlock(WeeklyTimeBlockDraft(title = name, kind = TimeBlockKind.COURSE,
                dayOfWeek = DayOfWeek.MONDAY, startMinute = 1380, endMinute = 1440, weekPattern = "1-16单周", trackId = "lab", note = "原备注")) }
            val original = runBlocking { container.timeRepository.observeWeeklyBlocks().first().single { it.title == name } }
            val id = original.id; cleanupId = id
            val (fault, vm) = timeFault()
            waitTag("navigation-mine"); rule.onNodeWithTag("navigation-mine").performClick()
            waitTag("mine-time-settings"); rule.onNodeWithTag("mine-time-settings").performScrollTo().performClick()
            waitTag("time-add-weekly"); rule.onNodeWithTag("weekly-edit-$id").performScrollTo().performClick()
            waitTag("weekly-block-title"); fill("weekly-block-title", "$name-edited")
            fault.failedPart.value = "weekly"; rule.waitUntil(10_000) { vm.uiState.value.readError != null }
            rule.onNodeWithTag("weekly-block-save").assertIsNotEnabled()
            rule.onNodeWithText("原时间设置已不存在").assertDoesNotExist()
            rule.activityRule.scenario.recreate(); waitTag("weekly-block-title")
            rule.onNodeWithTag("weekly-block-title").assertTextContains("$name-edited")
            rule.onNodeWithTag("weekly-block-end").performScrollTo().assertTextContains("24:00")
            rule.onNode(hasTestTag("data-read-notice") and hasAnyAncestor(hasTestTag("editor-sheet"))).performScrollTo()
            screenshot("qa-read23-time-failure.png")
            assertEquals(original, runBlocking { container.timeRepository.observeWeeklyBlocks().first().single { it.id == id } })
            fault.failedPart.value = null; retryInSheet(); rule.waitUntil(10_000) { vm.uiState.value.isTrusted }
            rule.onNodeWithTag("weekly-block-save").assertIsEnabled().performClick(); gone("weekly-block-title")
            val saved = runBlocking { container.timeRepository.observeWeeklyBlocks().first().single { it.id == id } }
            assertEquals(original.id, saved.id); assertEquals(original.createdAtEpochMillis, saved.createdAtEpochMillis)
            assertEquals(original.weekPattern, saved.weekPattern); assertEquals(original.trackId, saved.trackId); assertEquals(1440, saved.endMinute)
        } finally { cleanupId?.let { runBlocking { container.timeRepository.deleteWeeklyBlock(it) } } }
    }

    @Test fun initial_task_failure_blocks_formulate_and_preview_without_losing_composer_input() {
        val (fault, vm) = taskFault(initialFailure = true)
        rule.waitUntil(10_000) { vm.uiState.value.readError != null }
        val originalPlan = runBlocking { container.planRepository.observeCurrentPlan().first() }
        waitTag("navigation-agent"); rule.onNodeWithTag("navigation-agent").performClick(); waitTag("agent-prompt")
        fill("agent-prompt", "新增事项 复习30分钟")
        rule.onNodeWithTag("agent-formulate-plan").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("agent-preview").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("agent-add-first-task").assertDoesNotExist()
        rule.activityRule.scenario.recreate(); waitTag("agent-prompt")
        rule.onNodeWithTag("agent-prompt").performScrollTo().assertTextContains("新增事项 复习30分钟")
        assertEquals(originalPlan, runBlocking { container.planRepository.observeCurrentPlan().first() })
        fault.failed.value = false
        rule.onNodeWithTag("retry-data-read").performClick(); rule.waitUntil(10_000) { vm.uiState.value.isTrusted }
        rule.onNodeWithTag("agent-preview").performScrollTo().assertIsEnabled()
        // Leave no persistent assistant workspace for subsequent workflow cases.
        fill("agent-prompt", "")
        rule.onNodeWithTag("navigation-tasks").performClick()
    }

    @Test fun persisted_sort_preview_cannot_be_confirmed_during_failed_reads_but_cancel_remains_available() {
        val id = UUID.randomUUID().toString(); val name = "QA-sort-read23-$id"
        try {
            runBlocking { container.taskRepository.save(TaskDraft(id, name, "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)) }
            val originalPlan = runBlocking { container.planRepository.observeCurrentPlan().first() }
            val originalOrder = runBlocking { container.planRepository.observeTaskOrder().first() }
            runBlocking { container.planningOperationService.preview(PlanningPreviewKind.SORT_ONLY) }
            val (fault, vm) = taskFault(); waitTag("accept-plan-draft")
            fault.failed.value = true; rule.waitUntil(10_000) { vm.uiState.value.readError != null }
            rule.onNodeWithTag("accept-plan-draft").assertIsNotEnabled()
            val discard = rule.activity.getString(com.swan1127.repland.R.string.discard_plan_draft)
            rule.onNodeWithText(discard).assertIsEnabled()
            rule.activityRule.scenario.recreate(); waitTag("accept-plan-draft")
            rule.onNodeWithTag("accept-plan-draft").assertIsNotEnabled()
            assertEquals(originalPlan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            assertEquals(originalOrder, runBlocking { container.planRepository.observeTaskOrder().first() })
            fault.failed.value = false
            rule.onNode(hasTestTag("retry-data-read") and hasAnyAncestor(isDialog())).performClick()
            rule.waitUntil(10_000) { vm.uiState.value.isTrusted }
            rule.onNodeWithTag("accept-plan-draft").assertIsEnabled()
            rule.onNodeWithText(discard).performClick(); gone("accept-plan-draft")
            assertNull(runBlocking { container.planRepository.observeDraft().first() })
            assertEquals(originalPlan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            assertEquals(originalOrder, runBlocking { container.planRepository.observeTaskOrder().first() })
        } finally { cleanupTask(id) }
    }
}
