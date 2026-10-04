package com.swan1127.repland

import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TaskCaptureRepository
import com.swan1127.repland.ui.tasks.TaskCaptureViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class TaskCaptureViewModelTest {
    private class Fake : TaskCaptureRepository {
        val saved = MutableStateFlow<TaskCaptureDraft?>(null)
        var failCommit = false
        var failRead = false
        var commits = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeDraft(): Flow<TaskCaptureDraft?> = if (failRead) flow { error("read failed") } else saved
        override suspend fun saveDraft(draft: TaskCaptureDraft) { saved.value = draft }
        override suspend fun discard(id: String) { saved.value = null }
        override suspend fun commit(capture: TaskCaptureDraft, task: TaskDraft) {
            gate?.await()
            if (failCommit) error("test failure")
            commits++; saved.value = null
        }
    }
    private fun withVm(repo: Fake, block: suspend (TaskCaptureViewModel) -> Unit) = runBlocking {
        val store = ViewModelStore()
        lateinit var vm: TaskCaptureViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = TaskCaptureViewModel(repo); store.put("capture", vm) }
        try { withTimeout(10000) { vm.uiState.first { !it.loading }; block(vm) } }
        finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    }
    @Test fun failure_keeps_input_and_options_and_retry_has_real_receipt() {
        val repo = Fake().apply { failCommit = true }
        withVm(repo) { vm ->
        val repoDraft = vm.uiState.value.draft.copy(text = "复习", stage = TaskCaptureStage.DURATION, isCustomDuration = true, customDurationText = "45")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.update(repoDraft); vm.save(repoDraft.toTaskDraft()) }
        vm.uiState.first { it.error != null && !it.saving }
        assertEquals(repoDraft, vm.uiState.value.draft)
        assertNull(vm.uiState.value.receipt)
        repo.failCommit = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.save(repoDraft.toTaskDraft()) }
        vm.uiState.first { it.receipt != null }
        assertEquals(1, repo.commits)
        assertNull(repo.saved.value)
        assertTrue(vm.uiState.value.receipt!!.contains("待安排"))
        }
    }
    @Test fun duplicate_save_is_ignored_while_pending_and_receipt_only_follows_commit() {
        val repo = Fake().apply { gate = CompletableDeferred() }
        withVm(repo) { vm ->
            val capture = vm.uiState.value.draft.copy(text = "一件事")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.update(capture); vm.save(capture.toTaskDraft()); vm.save(capture.toTaskDraft()) }
            assertTrue(vm.uiState.value.saving); assertNull(vm.uiState.value.receipt)
            repo.gate!!.complete(Unit)
            vm.uiState.first { it.receipt != null }
            assertEquals(1, repo.commits); assertEquals("", vm.uiState.value.draft.text)
        }
    }
    @Test fun load_failure_does_not_overwrite_existing_draft() {
        val repo = Fake().apply { failRead = true }
        withVm(repo) { vm ->
            assertTrue(vm.uiState.value.loadFailed)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.update(TaskCaptureDraft(text = "不能覆盖")); vm.retain() }
            assertNull(repo.saved.value)
            repo.failRead = false
            val saved = TaskCaptureDraft(text = "原草稿")
            repo.saved.value = saved
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.retryLoad() }
            vm.uiState.first { !it.loading && !it.loadFailed }
            assertEquals(saved, vm.uiState.value.draft)
        }
    }
    @Test fun invalid_custom_duration_cannot_be_silently_saved_as_unknown_from_another_step() {
        val repo = Fake()
        withVm(repo) { vm ->
            val draft = vm.uiState.value.draft.copy(text = "保留输入", isCustomDuration = true,
                customDurationText = "1441", stage = TaskCaptureStage.CAPTURE)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.update(draft); vm.save(draft.toTaskDraft()) }
            assertNotNull(vm.uiState.value.error)
            assertEquals(draft, vm.uiState.value.draft)
            assertEquals(0, repo.commits)
            assertNull(vm.uiState.value.receipt)
        }
    }
    @Test fun successful_data_clear_cancels_pending_autosave_and_forgets_in_memory_draft() {
        val repo = Fake()
        withVm(repo) { vm ->
            val original = vm.uiState.value.draft.copy(text = "将清除的草稿")
            withContext(Dispatchers.Main) {
                vm.update(original)
                vm.prepareForDataClear()
                repo.saved.value = null // Same boundary as a successful repository wipe.
                vm.finishDataClear(true)
                vm.retain()
            }
            vm.uiState.first { it.persisted }
            assertEquals("", vm.uiState.value.draft.text)
            assertNotEquals(original.id, vm.uiState.value.draft.id)
            assertEquals("", repo.saved.value!!.text)
            assertEquals(0, repo.commits)
        }
    }
    @Test fun failed_data_clear_keeps_original_draft_and_allows_editing_again() {
        withVm(Fake()) { vm ->
            val original = vm.uiState.value.draft.copy(text = "清除失败后保留", isCustomDuration = true, customDurationText = "45")
            withContext(Dispatchers.Main) {
                vm.update(original); vm.prepareForDataClear(); vm.finishDataClear(false)
            }
            assertEquals(original, vm.uiState.value.draft)
            assertFalse(vm.uiState.value.loading)
            assertNotNull(vm.uiState.value.error)
            withContext(Dispatchers.Main) { vm.update(original.copy(text = "仍可编辑")) }
            assertEquals("仍可编辑", vm.uiState.value.draft.text)
        }
    }
}
