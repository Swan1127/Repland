package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Actual navigation and physical clicks; only the QA package is used. */
class PlanningProtectionWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(tag: String): SemanticsNodeInteraction {
        waitTag(tag)
        val node = rule.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        return node.assertIsDisplayed()
    }
    @Test fun ordinary_overlap_cancel_stale_confirmation_and_explicit_confirmation_preserve_execution_facts() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val a = "overlap-a-${UUID.randomUUID()}"; val b = "overlap-b-${UUID.randomUUID()}"
        val day = LocalDate.now().plusDays(2)
        val current = runBlocking { app.planRepository.observeCurrentPlan().first() }
        val entries = runBlocking { ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
            app.timeRepository.observeDateOverrides().first(), current?.segments.orEmpty(), app.taskRepository.observeTasks().first(),
            app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday) }
        val start = (8..19).map { it * 60 }.first { m -> entries.none { m < it.endMinute && m + 90 > it.startMinute } }
        runBlocking {
            app.taskRepository.save(TaskDraft(id = a, displayName = "重叠甲${a.takeLast(5)}", totalDurationMinutes = 30))
            app.taskRepository.save(TaskDraft(id = b, displayName = "重叠乙${b.takeLast(5)}", totalDurationMinutes = 30))
            app.planRepository.placeTask(a, day, start, start + 30, "focus")
            app.planRepository.placeTask(b, day, start + 45, start + 75, "focus")
        }
        try {
            val original = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            val segment = original.segments.single { it.taskId == a }
            visible("navigation-today").performClick()
            waitTag("today-page-scroll")
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
            visible("month-schedule").performClick()
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-$day"))
            visible("month-date-$day").performClick()
            fun request() {
                val tag = "timeline-segment:${segment.id}"
                rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag(tag))
                // Expanded cards contain a separate task-details action near their centre.
                // Tap the time header physically to request the placement editor.
                visible(tag).performTouchInput { click(Offset(center.x, 20f)) }
                visible("timeline-time-editor-segment:${segment.id}").performClick()
                visible("timeline-start-segment:${segment.id}").performTextReplacement(TimeBlockValidator.formatTime(start + 30))
                visible("timeline-end-segment:${segment.id}").performTextReplacement(TimeBlockValidator.formatTime(start + 60))
                visible("timeline-time-save-segment:${segment.id}").performClick()
                waitTag("manual-plan-confirm")
            }
            request()
            assertEquals(original, runBlocking { app.planRepository.observeCurrentPlan().first() })
            visible("manual-plan-cancel").performClick()
            assertEquals(original, runBlocking { app.planRepository.observeCurrentPlan().first() })
            request()
            runBlocking { app.planRepository.saveTaskOrder(listOf(b, a)) }
            visible("manual-plan-confirm").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("本次确认已失效", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(original, runBlocking { app.planRepository.observeCurrentPlan().first() })
            visible("manual-plan-cancel").performClick()
            request()
            visible("manual-plan-confirm").performClick()
            rule.waitUntil(15_000) { runBlocking { app.planRepository.observeCurrentPlan().first()?.id != original.id } }
            val after = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            assertEquals(start + 30, after.segments.single { it.taskId == a }.startMinute)
            assertEquals(start + 45, after.segments.single { it.taskId == b }.startMinute)
            for (id in listOf(a, b)) {
                assertTrue(runBlocking { app.taskRepository.observeExecutionLogs(id).first() }.isEmpty())
                assertEquals(TaskStatus.NOT_STARTED, runBlocking { app.taskRepository.observeTasks().first() }.single { it.id == id }.status)
            }
        } finally {
            runBlocking {
                for (id in listOf(a, b)) app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }?.let {
                    app.planRepository.removePlacement(it.id)
                }
            }
        }
    }
    @Test fun unavoidable_manual_move_can_be_cancelled_or_confirmed_and_history_lock_rejects_restore() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val app = (rule.activity.application as ReplandApplication).appContainer
        val id = "protection-${UUID.randomUUID()}"
        val day = LocalDate.now().plusDays(1)
        val tasks = runBlocking { app.taskRepository.observeTasks().first() }
        val plan = runBlocking { app.planRepository.observeCurrentPlan().first() }
        val entries = runBlocking { ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
            app.timeRepository.observeDateOverrides().first(), plan?.segments.orEmpty(), tasks,
            app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday) }
        val start = (8..20).map { it * 60 }.first { minute -> entries.none { minute < it.endMinute && minute + 60 > it.startMinute } }
        runBlocking {
            app.taskRepository.save(TaskDraft(id = id, displayName = "保护验证${id.takeLast(6)}", userPriority = TaskPriority.REQUIRED, totalDurationMinutes = 30))
            app.planRepository.placeTask(id, day, start, start + 30, "focus")
        }
        try {
            val original = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            val segment = original.segments.single { it.taskId == id }
            visible("navigation-today").performClick()
            waitTag("today-page-scroll")
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
            visible("month-schedule").performClick()
            rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-$day"))
            visible("month-date-$day").performClick()
            fun requestMove() {
                val tag = "timeline-segment:${segment.id}"
                rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag(tag))
                visible(tag).performTouchInput { click(Offset(center.x, 20f)) }
                visible("timeline-time-editor-segment:${segment.id}").performClick()
                visible("timeline-start-segment:${segment.id}").performTextReplacement(TimeBlockValidator.formatTime(start + 15))
                visible("timeline-end-segment:${segment.id}").performTextReplacement(TimeBlockValidator.formatTime(start + 45))
                visible("timeline-time-save-segment:${segment.id}").performClick()
                waitTag("manual-plan-confirm")
            }
            requestMove()
            assertEquals(original, runBlocking { app.planRepository.observeCurrentPlan().first() })
            visible("manual-plan-cancel").performClick()
            assertEquals(original, runBlocking { app.planRepository.observeCurrentPlan().first() })
            requestMove()
            visible("manual-plan-confirm").performClick()
            rule.waitUntil(15_000) { runBlocking { app.planRepository.observeCurrentPlan().first()?.id != original.id } }
            val moved = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            val target = moved.segments.single { it.taskId == id }
            assertEquals(start + 15, target.startMinute); assertFalse(target.isLocked)
            assertTrue(runBlocking { app.taskRepository.observeExecutionLogs(id).first() }.isEmpty())
            visible("navigation-mine").performClick()
            visible("mine-plan-history").performClick()
            rule.onNodeWithTag("history-scroll").performScrollToNode(hasTestTag("history-lock-${target.id}"))
            visible("history-lock-${target.id}").performClick()
            rule.waitUntil(15_000) { runBlocking { app.planRepository.observeCurrentPlan().first()?.segments?.any { it.taskId == id && it.isLocked } == true } }
            val locked = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            rule.onNodeWithTag("history-scroll").performScrollToNode(hasTestTag("history-restore-${original.id}"))
            visible("history-restore-${original.id}").performClick()
            visible("restore-history-confirm").performClick()
            waitTag("history-error")
            assertEquals(locked, runBlocking { app.planRepository.observeCurrentPlan().first() })
            rule.onNodeWithTag("history-scroll").assertExists()
        } finally {
            // Remove only this test's protected placement, leaving prior QA work and histories intact.
            runBlocking {
                var own = app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }
                if (own?.isLocked == true) {
                    app.planRepository.setSegmentLocked(own.id, false)
                    own = app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }
                }
                own?.let {
                    val change = ManualPlanChange.Remove(it.id)
                    val consent = runCatching { app.planRepository.applyManualChange(change) }.exceptionOrNull()
                    if (consent is PlanChangeConfirmationRequired) app.planRepository.applyManualChange(change, consent.revision)
                }
            }
        }
    }
}
