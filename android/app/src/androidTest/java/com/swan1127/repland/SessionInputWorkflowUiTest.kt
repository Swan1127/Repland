package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.tasks.TaskViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import java.io.File

class SessionInputWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = (rule.activity.application as ReplandApplication).appContainer.also { check(rule.activity.packageName == "com.swan1127.repland.qa") }
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun fill(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    private fun fault(initial: Boolean = false): Pair<ReadFaultSessions, TaskViewModel> {
        val source = ReadFaultSessions(container.executionSessionRepository).apply { failed.value = initial }; lateinit var vm: TaskViewModel
        rule.runOnUiThread {
            vm = TaskViewModel(container.taskRepository, source)
            rule.activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${TaskViewModel::class.java.canonicalName}", vm)
        }
        rule.activityRule.scenario.recreate(); return source to vm
    }
    private fun seed(id: String) = runBlocking {
        check(container.executionSessionRepository.observeActive().first() == null) { "QA fixture must not replace an unrelated round" }
        container.taskRepository.save(TaskDraft(id, "QA-session25-$id", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 60, null))
        val existing = container.planRepository.observeCurrentPlan().first()?.segments.orEmpty()
        val weekly = container.timeRepository.observeWeeklyBlocks().first()
        val overrides = container.timeRepository.observeDateOverrides().first()
        val semester = container.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday
        val candidate = (1L..30L).asSequence().flatMap { day ->
            (0..1380 step 60).asSequence().map { start -> PlannedSegment(taskId = id, date = LocalDate.now().plusDays(day), startMinute = start, endMinute = start + 60) }
        }.first { runCatching { PlacementValidator.requireValid(it, existing, weekly, overrides, semester) }.isSuccess }
        container.planRepository.placeTask(id, candidate.date, candidate.startMinute, candidate.endMinute, candidate.trackId)
        val segment = container.planRepository.observeCurrentPlan().first()!!.segments.first { it.taskId == id }
        container.executionSessionRepository.start(segment.id)
        container.executionSessionRepository.pause(container.executionSessionRepository.observeActive().first()!!.id)
    }
    private fun cleanup(id: String) = runBlocking {
        container.executionSessionRepository.observeActive().first()?.takeIf { it.taskId == id }?.let {
            container.executionSessionRepository.finish(it.id, ExecutionOutcome.CONTINUE, TaskFeedback())
        }
        container.planRepository.observeCurrentPlan().first()?.segments?.filter { it.taskId == id }?.forEach {
            container.planRepository.removePlacement(it.id)
        }
    }
    @Test fun failed_round_read_keeps_partial_input_and_identity_through_recreation_and_retry() {
        partialWorkflow(false)
    }
    @Test fun rotating_open_round_keeps_native_viewport_and_partial_input() {
        partialWorkflow(true)
    }
    private fun partialWorkflow(rotateWhileOpen: Boolean) {
        val id = UUID.randomUUID().toString()
        try {
            seed(id); val original = runBlocking { container.executionSessionRepository.observeActive().first() }!!
            val plan = runBlocking { container.planRepository.observeCurrentPlan().first() }
            val (source, vm) = fault(); waitTag("resume-execution")
            rule.onNodeWithTag("resume-execution").performScrollTo().performClick(); waitTag("execution-pause-resume")
            NativeSheetTestInput.awaitFocusedDialog(rule, "QA-session25-$id")
            NativeSheetTestInput.assertDialogWithinDisplay(rule, "qa-focus26-open")
            if (rotateWhileOpen) {
                rule.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                waitTag("execution-pause-resume")
                NativeSheetTestInput.awaitFocusedDialog(rule, "QA-session25-$id")
                NativeSheetTestInput.assertDialogWithinDisplay(rule, "qa-focus26-rotated")
            }
            rule.onNodeWithText("部分完成").performScrollTo().performClick(); fill("execution-content", "保留第一章")
            listOf("-30", "3.5", "99999999999999999999").forEach {
                fill("execution-progress", it); rule.onNodeWithTag("execution-progress").assertTextContains(it)
                rule.onNodeWithTag("execution-partial-save").performScrollTo().assertIsNotEnabled()
            }
            fill("execution-progress", "40"); source.failed.value = true
            rule.waitUntil(10_000) { vm.sessionReadState.value.error != null }
            rule.onNodeWithTag("execution-partial-save").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("execution-pause-resume").performScrollTo().assertIsNotEnabled()
            assertEquals(original, runBlocking { container.executionSessionRepository.observeActive().first() })
            rule.activityRule.scenario.recreate(); waitTag("execution-progress")
            rule.onNodeWithTag("execution-progress").performScrollTo().assertTextContains("40")
            rule.onNodeWithTag("execution-content").performScrollTo().assertTextContains("保留第一章")
            NativeSheetTestInput.assertDialogWithinDisplay(rule, "qa-focus26-recreated")
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try { File(rule.activity.getExternalFilesDir(null), "qa-session25-read-failure.png").outputStream().use { check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { screenshot.recycle() }
            source.failed.value = false
            rule.onNode(hasTestTag("retry-data-read") and hasAnyAncestor(hasTestTag("execution-session"))).performScrollTo().performClick()
            rule.waitUntil(10_000) { vm.sessionReadState.value.isTrusted }
            rule.onNodeWithTag("execution-partial-save").performScrollTo().assertIsEnabled()
            NativeSheetTestInput.assertControlWithinWindow(rule, "execution-partial-save")
            rule.onNodeWithTag("execution-return").performScrollTo().assertIsDisplayed()
            NativeSheetTestInput.assertControlWithinWindow(rule, "execution-return")
            assertEquals(original, runBlocking { container.executionSessionRepository.observeActive().first() })
            assertEquals(plan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            rule.onNodeWithTag("execution-partial-save").performScrollTo().performClick(); gone("execution-progress")
            rule.waitUntil(10_000) { runBlocking { container.executionSessionRepository.observeActive().first() == null } }
            val task = runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } }
            assertEquals(40, task.progressPercent); assertEquals(TaskStatus.IN_PROGRESS, task.status)
            assertEquals(plan, runBlocking { container.planRepository.observeCurrentPlan().first() })
        } finally {
            cleanup(id)
            if (rotateWhileOpen) rule.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    @Test fun initially_failed_round_read_has_retry_and_does_not_offer_a_false_resume() {
        val id = UUID.randomUUID().toString()
        try {
            seed(id); val original = runBlocking { container.executionSessionRepository.observeActive().first() }
            val (source, vm) = fault(true)
            rule.waitUntil(10_000) { vm.sessionReadState.value.error != null }
            rule.onNodeWithTag("resume-execution").assertDoesNotExist()
            waitTag("retry-data-read"); source.failed.value = false
            rule.onNodeWithTag("retry-data-read").performClick(); waitTag("resume-execution")
            assertEquals(original, runBlocking { container.executionSessionRepository.observeActive().first() })
            assertEquals(0, source.mutations.get())
            rule.onNodeWithTag("resume-execution").performScrollTo().performClick(); waitTag("execution-pause-resume")
            rule.onNodeWithText("返回页面 · 保留本轮").performScrollTo().performClick()
            assertEquals(original, runBlocking { container.executionSessionRepository.observeActive().first() })
        } finally { cleanup(id) }
    }
    @Test fun task_editor_keeps_illegal_raw_numbers_and_unknown_duration_without_changing_identity() {
        val id = UUID.randomUUID().toString(); val name = "QA-numbers25-$id"
        runBlocking { container.taskRepository.save(TaskDraft(id, name, "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 60, null)) }
        val original = runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } }
        val (_, vm) = fault()
        rule.waitUntil(10_000) { vm.uiState.value.isTrusted && vm.uiState.value.tasks.any { it.id == id } }
        waitTag("navigation-tasks"); rule.onNodeWithTag("navigation-tasks").performClick(); waitTag("task-groups-scroll")
        rule.onNodeWithTag("task-filter-INBOX").performClick(); waitTag("task-list-scroll")
        rule.onNodeWithTag("task-list-scroll").performScrollToNode(hasTestTag("task-card-$name"))
        rule.onNodeWithTag("task-card-$name").performClick(); waitTag("task-detail-scroll")
        rule.onNodeWithTag("task-detail-scroll").performScrollToNode(hasText("编辑任务")); rule.onNodeWithText("编辑任务").performClick()
        waitTag("task-editor-duration")
        listOf("-30", "3.5", "999999999999999999999").forEach {
            fill("task-editor-duration", it); rule.onNodeWithTag("task-editor-save").performClick()
            rule.onNodeWithTag("task-editor-duration").performScrollTo().assertTextContains(it)
            assertEquals(original, runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } })
        }
        fill("task-editor-duration", ""); fill("task-editor-days", "-2")
        rule.activityRule.scenario.recreate(); waitTag("task-editor-days")
        rule.onNodeWithTag("task-editor-days").performScrollTo().assertTextContains("-2")
        rule.onNodeWithTag("task-editor-save").performClick()
        assertEquals(original, runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } })
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(rule.activity.getExternalFilesDir(null), "qa-numbers25-invalid.png").outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { image.recycle() }
        fill("task-editor-days", "2"); rule.onNodeWithTag("task-editor-save").performClick(); gone("task-editor-days")
        val saved = runBlocking { container.taskRepository.observeTasks().first().single { it.id == id } }
        assertEquals(2, saved.estimatedDays); assertNull(saved.totalDurationMinutes); assertEquals(original.userPriority, saved.userPriority)
    }
}
