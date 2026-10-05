package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.plan.PlanViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DraftSaveWorkflowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = (rule.activity.application as ReplandApplication).appContainer.also {
        check(rule.activity.packageName == "com.swan1127.repland.qa")
    }
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun fill(tag: String, value: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    @Test fun failed_segment_write_keeps_editor_input_and_original_plan() {
        val id = UUID.randomUUID().toString(); val segmentId = UUID.randomUUID().toString()
        try {
            runBlocking { container.taskRepository.save(TaskDraft(id, "QA-draft27-$id", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 60, null)) }
            val originalPlan = runBlocking { container.planRepository.observeCurrentPlan().first() }
            val originalOrder = runBlocking { container.planRepository.observeTaskOrder().first() }
            val draft = PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(segmentId, id, LocalDate.now().plusDays(1), 600, 660)), emptyList(), emptyList(), listOf(id))
            runBlocking { container.planRepository.saveDraft(draft) }
            val source = ReadFaultPlans(container.planRepository).apply { failDraftWrite = true }
            lateinit var vm: PlanViewModel
            rule.runOnUiThread {
                vm = PlanViewModel(source, container.planDraftGenerator, container.timeRepository, container.taskRepository, container.planningOperationService)
                rule.activity.viewModelStore.put("androidx.lifecycle.ViewModelProvider.DefaultKey:${PlanViewModel::class.java.canonicalName}", vm)
            }
            rule.activityRule.scenario.recreate(); waitTag("draft-segment-summary-$segmentId")
            rule.onNodeWithTag("draft-segment-summary-$segmentId").performScrollTo().performClick(); waitTag("plan-segment-start")
            fill("plan-segment-start", "11:00"); fill("plan-segment-end", "12:00")
            rule.onNodeWithTag("plan-segment-save").performClick()
            rule.waitUntil(10_000) { vm.uiState.value.errorMessage != null && !vm.uiState.value.isWorking }
            rule.onNodeWithTag("plan-segment-start").assertExists().assertTextContains("11:00")
            rule.onNodeWithTag("plan-segment-end").assertTextContains("12:00")
            assertEquals(draft, runBlocking { container.planRepository.observeDraft().first() })
            assertEquals(originalPlan, runBlocking { container.planRepository.observeCurrentPlan().first() })
            assertEquals(originalOrder, runBlocking { container.planRepository.observeTaskOrder().first() })
        } finally {
            runBlocking { if (container.planRepository.observeDraft().first()?.orderedTaskIds?.contains(id) == true) container.planRepository.saveDraft(null) }
            android.database.sqlite.SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("repland.db").absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
            }
        }
    }
}
