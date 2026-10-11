package com.swan1127.repland

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

class HistoryRestoreWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = (rule.activity.application as ReplandApplication).appContainer
    private fun node(tag: String): SemanticsNodeInteraction {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return rule.onNodeWithTag(tag).also { runCatching { it.performScrollTo() } }.assertIsDisplayed()
    }

    @Test fun restore_from_mine_keeps_completed_task_and_execution_records_and_excludes_its_old_future_slot() {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
        val id = "qa-history-${UUID.randomUUID()}"
        val day = LocalDate.now().plusDays(6)
        val start = runBlocking {
            val entries = ScheduleTimeline.entries(day, app.timeRepository.observeWeeklyBlocks().first(),
                app.timeRepository.observeDateOverrides().first(), app.planRepository.observeCurrentPlan().first()?.segments.orEmpty(),
                app.taskRepository.observeTasks().first(), app.timeRepository.observeTimeConstraintSettings().first().semesterFirstWeekMonday)
            (0..21).map { it * 60 }.first { minute -> entries.none { minute < it.endMinute && minute + 90 > it.startMinute } }
        }
        try {
            runBlocking {
                app.taskRepository.save(TaskDraft(id = id, displayName = "QA history finished task", totalDurationMinutes = 30))
                app.planRepository.placeTask(id, day, start, start + 30, "focus")
            }
            val original = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            runBlocking {
                val segment = original.segments.single { it.taskId == id }
                app.planRepository.movePlacement(segment.id, start + 30, start + 60, "focus")
                app.taskRepository.confirmStatus(id, TaskStatus.COMPLETED)
            }
            val before = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            val task = runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } }
            val logs = runBlocking { app.taskRepository.observeExecutionLogs(id).first() }
            val session = runBlocking { app.executionSessionRepository.observeActive().first() }
            node("navigation-mine").performClick()
            node("mine-plan-history").performClick()
            rule.onNodeWithTag("history-scroll").performScrollToNode(hasTestTag("history-restore-${original.id}"))
            node("history-restore-${original.id}").performClick()
            node("restore-history-confirm").performClick()
            rule.waitUntil(20_000) { runBlocking { app.planRepository.observeCurrentPlan().first()?.id != before.id } }
            val restored = runBlocking { app.planRepository.observeCurrentPlan().first() }!!
            assertTrue(restored.segments.none { it.taskId == id })
            assertEquals(task, runBlocking { app.taskRepository.observeTasks().first().single { it.id == id } })
            assertEquals(logs, runBlocking { app.taskRepository.observeExecutionLogs(id).first() })
            assertEquals(session, runBlocking { app.executionSessionRepository.observeActive().first() })
            val historical = runBlocking { app.planRepository.observePlanHistory().first().single { it.id == original.id } }
            assertEquals(original.copy(isCurrent = false), historical)
        } finally {
            runBlocking {
                app.planRepository.observeCurrentPlan().first()?.segments?.find { it.taskId == id }?.let { app.planRepository.removePlacement(it.id) }
            }
        }
    }
}
