package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.plan.PlanViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DraftSaveIntegrityTest {
    @Test fun failed_write_has_no_receipt_and_preserves_original_preview() = scenario("failure")
    @Test fun duplicate_click_commits_once_and_only_then_emits_receipt() = scenario("duplicate")
    @Test fun replaced_preview_is_not_overwritten_by_pending_old_edit() = scenario("replacement")
    @Test fun cancellation_before_commit_keeps_preview_and_does_not_emit_receipt() = scenario("cancel")
    @Test fun queued_generic_update_does_not_clear_pending_edit_busy_state() = scenario("queued")

    private fun scenario(mode: String) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val store = ViewModelStore(); val repo = RoomPlanRepository(db); val tasks = RoomTaskRepository(db)
        val source = ReadFaultPlans(repo); val observers = mutableListOf<Job>()
        lateinit var vm: PlanViewModel
        try {
            tasks.save(TaskDraft("draft-task", "草案回执样例", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 60, null))
            val segment = PlannedSegment("draft-segment", "draft-task", LocalDate.now().plusDays(1), 600, 660)
            val draft = PlanDraft(LocalDateTime.now(), listOf(segment), emptyList(), emptyList(), listOf("draft-task"))
            repo.accept(draft); repo.saveDraft(draft)
            val current = repo.observeCurrentPlan().first(); val order = repo.observeTaskOrder().first()
            val history = repo.observePlanHistory().first(); val originalTasks = tasks.observeTasks().first()
            val changed = PlanDraftEditor.moveSegment(draft, segment.copy(startMinute = 660, endMinute = 720))
            withContext(Dispatchers.Main) { vm = PlanViewModel(source, PlanGenerator); store.put("draft", vm) }
            observers += launch { vm.uiState.collect() }; observers += launch { vm.workspaceUiState.collect() }
            withTimeout(10_000) { combine(vm.uiState, vm.workspaceUiState) { a, b -> a.isTrusted && b.isTrusted }.first { it } }
            source.failDraftWrite = mode == "failure"
            source.draftWriteGate = CompletableDeferred()
            withContext(Dispatchers.Main) {
                if (mode == "queued") vm.updateDraft(draft)
                vm.saveDraftEdit(draft, changed, "first"); vm.saveDraftEdit(draft, changed, "duplicate")
            }
            withTimeout(10_000) { source.draftWriteStarted.await() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(vm.uiState.value.isWorking)
            assertEquals(if (mode == "queued") 1 else 0, source.draftWrites.get()); assertNull(vm.uiState.value.draftEditReceipt)
            assertEquals(draft, repo.observeDraft().first())
            val replacement = draft.copy(generatedAt = draft.generatedAt.plusSeconds(1))
            if (mode == "replacement") repo.saveDraft(replacement)
            if (mode == "cancel") {
                withContext(Dispatchers.Main) { store.clear() }
                withTimeout(10_000) { source.draftWriteCancelled.await() }
                assertNull(vm.uiState.value.draftEditReceipt); assertEquals(0, source.draftWrites.get())
                assertEquals(draft, repo.observeDraft().first())
            } else {
                source.draftWriteGate!!.complete(Unit)
                withTimeout(10_000) { vm.uiState.first { !it.isWorking && (it.draftEditReceipt != null || it.draftEditError != null) } }
                if (mode == "duplicate" || mode == "queued") {
                    assertEquals("first", vm.uiState.value.draftEditReceipt); assertNull(vm.uiState.value.draftEditError)
                    assertEquals(if (mode == "queued") 2 else 1, source.draftWrites.get()); assertEquals(changed, repo.observeDraft().first())
                } else {
                    assertNotNull(vm.uiState.value.draftEditError); assertNull(vm.uiState.value.draftEditReceipt)
                    assertEquals(0, source.draftWrites.get())
                    assertEquals(if (mode == "replacement") replacement else draft, repo.observeDraft().first())
                }
            }
            assertEquals(current, repo.observeCurrentPlan().first()); assertEquals(order, repo.observeTaskOrder().first())
            assertEquals(history, repo.observePlanHistory().first()); assertEquals(originalTasks, tasks.observeTasks().first())
        } finally {
            observers.forEach { it.cancelAndJoin() }; withContext(Dispatchers.Main) { store.clear() }; db.close()
        }
    }
}
