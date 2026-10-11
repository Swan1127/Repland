package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun retain_history_but_remove_previous_placements_owned_by_this_qa_fixture() = runBlocking {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val activeTask = app.executionSessionRepository.observeActive().first()?.taskId
        // Repeated runs retain the QA database. Use the real guarded operation only
        // for this fixture's ordinary placements; keep tasks, logs and plan history.
        // Each change creates a new plan and new segment IDs; reread before removal.
        while (true) {
            val owned = app.planRepository.observeCurrentPlan().first()?.segments.orEmpty().firstOrNull {
                (it.taskId.startsWith("qa-header-") || it.taskId.startsWith("qa-execution-")) && it.taskId != activeTask
            } ?: break
            app.planRepository.removePlacement(owned.id)
        }
    }
    private suspend fun freeSlot(id: String, firstOnDay: Boolean = false): PlannedSegment {
        val app = (rule.activity.application as ReplandApplication).appContainer
        val tasks = app.taskRepository.observeTasks().first()
        val plan = app.planRepository.observeCurrentPlan().first()
        val weekly = app.timeRepository.observeWeeklyBlocks().first()
        val overrides = app.timeRepository.observeDateOverrides().first()
        val semester = app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday
        return (1L..20L).asSequence().flatMap { offset ->
            val day = LocalDate.now().plusDays(offset)
            val entries = ScheduleTimeline.entries(day, weekly, overrides, plan?.segments.orEmpty(), tasks, semester)
            (0..1380 step 30).asSequence().filter { start -> !firstOnDay || entries.none { it.startMinute < start + 30 } }
                .map { start -> PlannedSegment(taskId = id, date = day, startMinute = start, endMinute = start + 30) }
        }.first { candidate -> runCatching {
            PlacementValidator.requireValid(candidate, plan?.segments.orEmpty(), weekly, overrides, semester,
                tasks.filter { it.userPriority == TaskPriority.REQUIRED }.map { it.id }.toSet())
        }.isSuccess }
    }
    @Test fun selected_day_header_starts_confirmed_slot_and_keeps_plan_on_return() {
        val app = (rule.activity.application as ReplandApplication).appContainer
        val id = "qa-header-${System.currentTimeMillis()}"
        val slot = runBlocking {
            app.taskRepository.save(TaskDraft(id, "今日开始入口", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 30, null))
            val candidate = freeSlot(id, firstOnDay = true)
            app.planRepository.placeTask(id, candidate.date, candidate.startMinute, candidate.endMinute, "focus")
            app.planRepository.observeCurrentPlan().first()!!.segments.first { it.taskId == id }
        }
        val planId = runBlocking { app.planRepository.observeCurrentPlan().first()!!.id }
        val draftBefore = runBlocking { app.planRepository.observeDraft().first() }
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
        rule.onNodeWithTag("month-schedule").performClick()
        rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-${slot.date}"))
        rule.onNodeWithTag("month-date-${slot.date}").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("today-focus-start").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("today-focus-start").performScrollTo().performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it?.segmentId == slot.id } } }
        rule.onNodeWithTag("execution-pause-resume").performClick()
        rule.onNodeWithText("返回页面 · 保留本轮").performScrollTo().performClick()
        rule.onNodeWithTag("today-focus-start").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("resume-execution").performScrollTo().performClick()
        rule.onNodeWithTag("execution-continue").performScrollTo().performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it == null } } }
        assertEquals(planId, runBlocking { app.planRepository.observeCurrentPlan().first()!!.id })
        assertEquals(draftBefore, runBlocking { app.planRepository.observeDraft().first() })
        assertEquals(TaskStatus.IN_PROGRESS, runBlocking { app.taskRepository.observeTasks().first().first { it.id == id }.status })
    }
    @Test fun activity_recreation_restores_round_and_finishing_offers_preview_without_completing_task() {
        val app = (rule.activity.application as ReplandApplication).appContainer
        org.junit.Assume.assumeTrue("This workflow requires a QA device without an unrelated active round.",
            runBlocking { app.executionSessionRepository.observeActive().first() == null })
        val id = "qa-execution-${System.currentTimeMillis()}"
        runBlocking {
            app.taskRepository.save(TaskDraft(id, "执行闭环测试", "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
            val candidate = freeSlot(id)
            app.planRepository.placeTask(id, candidate.date, candidate.startMinute, candidate.endMinute, "focus")
            val slot = app.planRepository.observeCurrentPlan().first()!!.segments.first { it.taskId == id }
            app.executionSessionRepository.start(slot.id)
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("resume-execution").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("accept-plan-draft").assertDoesNotExist()
        assertNull(runBlocking { app.planRepository.observeDraft().first() })
        rule.onNodeWithTag("resume-execution").performScrollTo().performClick()
        rule.onNodeWithTag("execution-pause-resume").performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it?.isPaused == true } } }
        rule.onNodeWithText("返回页面 · 保留本轮").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("resume-execution").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("resume-execution").performScrollTo().performClick()
        rule.onNodeWithText("本轮已暂停").assertExists()
        rule.onNodeWithTag("accept-plan-draft").assertDoesNotExist()
        rule.onNodeWithTag("execution-continue").performScrollTo().performClick()
        runBlocking { withTimeout(5_000) { app.executionSessionRepository.observeActive().first { it == null } } }
        assertEquals(TaskStatus.IN_PROGRESS, runBlocking { app.taskRepository.observeTasks().first().first { it.id == id }.status })
        val planBefore = runBlocking { app.planRepository.observeCurrentPlan().first()!!.id }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("execution-replan").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("execution-replan").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("accept-plan-draft").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(planBefore, runBlocking { app.planRepository.observeCurrentPlan().first()!!.id })
        // Finish the user's cancel branch rather than leave a modal QA proposal over
        // the next Activity. Later page-navigation tests must target the page window.
        rule.onNodeWithText(rule.activity.getString(R.string.discard_plan_draft)).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("accept-plan-draft").fetchSemanticsNodes().isEmpty() }
        assertNull(runBlocking { app.planRepository.observeDraft().first() })
        assertEquals(planBefore, runBlocking { app.planRepository.observeCurrentPlan().first()!!.id })
    }
}
