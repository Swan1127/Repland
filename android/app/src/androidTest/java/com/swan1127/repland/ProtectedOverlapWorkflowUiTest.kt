package com.swan1127.repland

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** All five protected constraints through the real time editor and Room. */
class ProtectedOverlapWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = (rule.activity.application as ReplandApplication).appContainer
    private fun node(tag: String): SemanticsNodeInteraction {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return rule.onNodeWithTag(tag).also { runCatching { it.performScrollTo() } }.assertIsDisplayed()
    }

    @Test fun course_fixed_rest_locked_and_unavoidable_overlap_are_rejected_without_execution_changes() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val id = "qa-protected-${UUID.randomUUID()}"
        val protectedId = "protected-$id"
        val title = "QA hard constraint $id"
        val day = LocalDate.now().plusDays(5)
        val start = runBlocking {
            val entries = ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
                app.timeRepository.observeDateOverrides().first(), app.planRepository.observeCurrentPlan().first()?.segments.orEmpty(),
                app.taskRepository.observeTasks().first(), app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday)
            (0..21).map { it * 60 }.first { minute -> entries.none { minute < it.endMinute && minute + 120 > it.startMinute } }
        }
        var weeklyId: String? = null
        var dayOpened = false
        runBlocking {
            app.taskRepository.save(TaskDraft(id = id, displayName = "QA protected move", totalDurationMinutes = 30))
            app.planRepository.placeTask(id, day, start, start + 30, "focus")
        }
        try {
            for (kind in listOf("course", "fixed", "rest", "locked", "unavoidable")) {
                runBlocking {
                    if (kind in listOf("course", "fixed", "rest")) {
                        val blockKind = when (kind) { "course" -> TimeBlockKind.COURSE; "fixed" -> TimeBlockKind.OTHER; else -> TimeBlockKind.REST }
                        app.timeRepository.saveWeeklyBlock(WeeklyTimeBlockDraft(id = weeklyId, title = title, kind = blockKind,
                            dayOfWeek = day.dayOfWeek, startMinute = start + 60, endMinute = start + 90))
                        weeklyId = app.timeRepository.observeWeeklyBlocks().first().single { it.title == title }.id
                    } else {
                        weeklyId?.let { app.timeRepository.deleteWeeklyBlock(it) }; weeklyId = null
                        app.taskRepository.save(TaskDraft(id = protectedId, displayName = "QA $kind",
                            userPriority = if (kind == "unavoidable") TaskPriority.REQUIRED else TaskPriority.UNSPECIFIED, totalDurationMinutes = 30))
                        if (kind == "locked") {
                            app.planRepository.placeTask(protectedId, day, start + 60, start + 90, "parallel-2")
                            val segment = app.planRepository.observeCurrentPlan().first()!!.segments.single { it.taskId == protectedId }
                            app.planRepository.setSegmentLocked(segment.id, true)
                        } else {
                            val segment = app.planRepository.observeCurrentPlan().first()!!.segments.single { it.taskId == protectedId }
                            app.planRepository.setSegmentLocked(segment.id, false)
                        }
                    }
                }
                val before = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
                val tasks = runBlocking { app.taskRepository.observeTasks().first() }
                val logs = runBlocking { app.taskRepository.observeExecutionLogs(id).first() }
                val entry = "segment:${before.segments.single { it.taskId == id }.id}"
                if (!dayOpened) {
                    node("navigation-today").performClick()
                    rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-schedule"))
                    node("month-schedule").performClick()
                    rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("month-date-$day"))
                    node("month-date-$day").performClick()
                    dayOpened = true
                }
                if (rule.onAllNodesWithTag("timeline-entry-detail-$entry").fetchSemanticsNodes().isEmpty()) {
                    rule.onNodeWithTag("today-page-scroll").performScrollToNode(hasTestTag("timeline-$entry"))
                    node("timeline-$entry").performTouchInput { click(Offset(center.x, 20f)) }
                }
                node("timeline-time-editor-$entry").performClick()
                node("timeline-start-$entry").performTextReplacement(TimeBlockValidator.formatTime(start + 60))
                node("timeline-end-$entry").performTextReplacement(TimeBlockValidator.formatTime(start + 90))
                node("timeline-time-save-$entry").performClick()
                val error = if (kind in listOf("locked", "unavoidable")) "这个时间有锁定或不可避免安排，请先解除保护或调整时间。"
                    else "这个时间与受保护安排冲突，请调整时间。"
                rule.waitUntil(20_000) { rule.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                node("timeline-start-$entry").assertTextContains(TimeBlockValidator.formatTime(start + 60))
                rule.onNodeWithTag("manual-plan-confirm").assertDoesNotExist()
                assertEquals(kind, before, runBlocking { app.planRepository.observeCurrentPlan().first() })
                assertEquals(tasks, runBlocking { app.taskRepository.observeTasks().first() })
                assertEquals(logs, runBlocking { app.taskRepository.observeExecutionLogs(id).first() })
                rule.onNodeWithText("取消").performClick()
                rule.waitUntil(20_000) { rule.onAllNodesWithTag("timeline-start-$entry").fetchSemanticsNodes().isEmpty() }
            }
        } finally {
            runBlocking {
                weeklyId?.let { app.timeRepository.deleteWeeklyBlock(it) }
                var own = app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == protectedId }
                if (own?.isLocked == true) {
                    app.planRepository.setSegmentLocked(own.id, false)
                    own = app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == protectedId }
                }
                own?.let {
                    val change = ManualPlanChange.Remove(it.id)
                    val consent = runCatching { app.planRepository.applyManualChange(change) }.exceptionOrNull()
                    if (consent is PlanChangeConfirmationRequired) app.planRepository.applyManualChange(change, consent.revision)
                }
                app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }?.let { app.planRepository.removePlacement(it.id) }
            }
        }
    }
}
