package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.plan.PlanViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ContextReadIntegrityTest {
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    @Test fun failed_current_read_keeps_plan_draft_order_and_retries() = planFailure("current")
    @Test fun failed_history_read_keeps_plan_draft_order_and_retries() = planFailure("history")
    @Test fun failed_draft_read_keeps_plan_draft_order_and_retries() = planFailure("draft")
    @Test fun failed_order_read_keeps_plan_draft_order_and_retries() = planFailure("order")
    @Test fun failed_tracks_read_keeps_workspace_and_retries() = planFailure("tracks")
    @Test fun failed_assistant_read_keeps_workspace_and_retries() = planFailure("assistant")
    @Test fun failed_undo_read_keeps_workspace_and_retries() = planFailure("undo")
    @Test fun initial_plan_failure_is_not_no_plan_and_does_not_end_scope() = planFailure("current", initialFailure = true)
    @Test fun initial_workspace_failure_is_not_default_tracks_and_does_not_end_scope() = planFailure("tracks", initialFailure = true)

    private fun planFailure(part: String, initialFailure: Boolean = false) = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomPlanRepository(database)
        val tasks = RoomTaskRepository(database); val fault = ReadFaultPlans(base)
        lateinit var vm: PlanViewModel; val observers = mutableListOf<Job>()
        var phase = "seed"
        try {
            tasks.save(TaskDraft("original", "原任务", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null))
            base.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "original", date = LocalDate.now().plusDays(1),
                startMinute = 600, endMinute = 630)), emptyList(), emptyList(), listOf("original")))
            val draft = PlanDraft(LocalDateTime.now(), emptyList(), emptyList(), emptyList(), listOf("original"), orderOnly = true)
            base.saveDraft(draft)
            val tracks = RhythmTracks.defaults + RhythmTrack("custom", "自定义轨道")
            val workspace = AssistantWorkspace(LocalDate.now(), prompt = "原助手文字")
            base.saveTracks(tracks); base.saveAssistantWorkspace(workspace)
            val plan = base.observeCurrentPlan().first(); val history = base.observePlanHistory().first()
            val order = base.observeTaskOrder().first(); val canUndo = base.observeCanUndoTaskOrder().first()
            if (initialFailure) fault.failedPart.value = part
            withContext(Dispatchers.Main) { vm = PlanViewModel(fault, PlanGenerator); store.put("plan", vm) }
            observers += launch { vm.uiState.collect() }; observers += launch { vm.workspaceUiState.collect() }
            val mainFailure = part in setOf("current", "history", "draft", "order")
            if (!initialFailure) {
                phase = "initial trusted snapshot"
                withTimeout(10_000) { combine(vm.uiState, vm.workspaceUiState) { main, workspace -> main.isTrusted && workspace.isTrusted }.first { it } }
                fault.failedPart.value = part
            }
            phase = "failure snapshot"
            withTimeout(10_000) {
                if (mainFailure) vm.uiState.first { it.readError != null } else vm.workspaceUiState.first { it.readError != null }
            }
            if (initialFailure) {
                assertFalse(if (mainFailure) vm.uiState.value.hasLoaded else vm.workspaceUiState.value.hasLoaded)
            } else if (mainFailure) {
                assertEquals(plan, vm.uiState.value.currentPlan); assertEquals(history, vm.uiState.value.planHistory)
                assertEquals(draft, vm.uiState.value.draft); assertEquals(order, vm.uiState.value.taskOrder)
            } else {
                assertEquals(tracks, vm.workspaceUiState.value.tracks); assertEquals(workspace, vm.workspaceUiState.value.assistant)
                assertEquals(canUndo, vm.workspaceUiState.value.canUndoOrder)
            }
            withContext(Dispatchers.Main) {
                vm.discardDraft(); vm.clearCurrentPlan(); vm.saveAssistantWorkspace(workspace.copy(prompt = "不能覆盖"))
                vm.undoTaskOrder(); vm.dismissError(); vm.dismissAssistantReceipt()
            }
            assertEquals(plan, base.observeCurrentPlan().first()); assertEquals(draft, base.observeDraft().first())
            assertEquals(order, base.observeTaskOrder().first()); assertEquals(workspace, base.observeAssistantWorkspace().first())
            assertNotNull(if (mainFailure) vm.uiState.value.readError else vm.workspaceUiState.value.readError)
            fault.failedPart.value = null
            withContext(Dispatchers.Main) { vm.retryRead() }
            phase = "retry trusted snapshot"
            withTimeout(10_000) { combine(vm.uiState, vm.workspaceUiState) { main, workspace -> main.isTrusted && workspace.isTrusted }.first { it } }
            assertEquals(2, fault.mainSources.get()); assertEquals(2, fault.workspaceSources.get())
            assertEquals(plan, vm.uiState.value.currentPlan); assertEquals(draft, vm.uiState.value.draft)
            withContext(Dispatchers.Main) { vm.discardDraft() }
            phase = "recovered discard"
            withTimeout(10_000) { vm.uiState.first { it.draft == null && !it.isWorking } }
            assertEquals(plan, base.observeCurrentPlan().first()); assertNull(base.observeDraft().first())
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("$part/$initialFailure timed out at $phase; main=${vm.uiState.value}; workspace=${vm.workspaceUiState.value}", timeout)
        } finally { observers.forEach { it.cancelAndJoin() }; withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun initial_preferences_failure_is_not_default_success_and_blocks_save_until_read_retry() = preferencesFailure(true)
    @Test fun later_preferences_failure_keeps_saved_weights_and_write_error_cannot_heal_it() = preferencesFailure(false)
    private fun preferencesFailure(initialFailure: Boolean) = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
        val original = CategoryPreferences.defaults + (TaskCategory.COURSE to 67)
        val fault = ReadFaultPreferences(base).apply { failed.value = initialFailure }
        lateinit var vm: CategoryPreferenceViewModel; var observer: Job? = null
        try {
            base.save(original)
            withContext(Dispatchers.Main) { vm = CategoryPreferenceViewModel(fault); store.put("preferences", vm) }
            observer = launch { vm.uiState.collect() }
            if (!initialFailure) { withTimeout(10_000) { vm.uiState.first { it.isTrusted } }; fault.failed.value = true }
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            if (initialFailure) assertFalse(vm.uiState.value.hasLoaded) else assertEquals(original, vm.uiState.value.weights)
            withContext(Dispatchers.Main) { vm.save(CategoryPreferences.defaults) }
            withTimeout(10_000) { vm.uiState.first { it.saveError != null } }
            assertNotNull(vm.uiState.value.readError); assertNull(vm.uiState.value.saveReceipt)
            assertEquals(original, base.observe().first()); assertEquals(0, fault.commits.get())
            fault.failed.value = false
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            assertEquals(original, vm.uiState.value.weights); assertEquals(2, fault.sources.get())
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun preferences_write_failure_duplicate_and_success_follow_the_real_commit() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
        val fault = ReadFaultPreferences(base).apply { failWrite = true }; val proposed = CategoryPreferences.defaults + (TaskCategory.COURSE to 67)
        lateinit var vm: CategoryPreferenceViewModel; var observer: Job? = null
        try {
            withContext(Dispatchers.Main) { vm = CategoryPreferenceViewModel(fault); store.put("preferences", vm) }
            observer = launch { vm.uiState.collect() }; withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            val original = base.observe().first()
            withContext(Dispatchers.Main) { vm.save(proposed) }
            withTimeout(10_000) { vm.uiState.first { it.saveError != null && !it.isSaving } }
            assertNull(vm.uiState.value.saveReceipt); assertEquals(original, base.observe().first()); assertTrue(vm.uiState.value.isTrusted)
            fault.failWrite = false; fault.gate = CompletableDeferred()
            withContext(Dispatchers.Main) { vm.save(proposed); vm.save(proposed) }
            withTimeout(10_000) { vm.uiState.first { it.isSaving } }
            assertNull(vm.uiState.value.saveReceipt); assertEquals(0, fault.commits.get())
            fault.gate!!.complete(Unit)
            withTimeout(10_000) { vm.uiState.first { it.saveReceipt != null && !it.isSaving } }
            assertEquals(1, fault.commits.get()); assertEquals(proposed, base.observe().first())
            withContext(Dispatchers.Main) { vm.save(proposed + (TaskCategory.COURSE to -30)) }
            withTimeout(10_000) { vm.uiState.first { it.saveError != null } }
            assertEquals(1, fault.commits.get()); assertEquals(proposed, base.observe().first())
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun cancelling_pending_preferences_save_does_not_commit_or_create_a_receipt() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomCategoryPreferenceRepository(database.categoryPreferenceDao())
        val fault = ReadFaultPreferences(base).apply { gate = CompletableDeferred() }
        lateinit var vm: CategoryPreferenceViewModel; var observer: Job? = null
        try {
            withContext(Dispatchers.Main) { vm = CategoryPreferenceViewModel(fault); store.put("preferences", vm) }
            observer = launch { vm.uiState.collect() }; withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            val original = base.observe().first()
            withContext(Dispatchers.Main) { vm.save(original + (TaskCategory.COURSE to 67)) }
            withTimeout(10_000) { vm.uiState.first { it.isSaving } }
            withContext(Dispatchers.Main) { store.clear() }
            withTimeout(10_000) { fault.cancelledWrite.await() }; fault.gate!!.complete(Unit)
            assertEquals(0, fault.commits.get()); assertEquals(original, base.observe().first()); assertNull(vm.uiState.value.saveReceipt)
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }
}
