package com.swan1127.repland.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TaskCaptureRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TaskCaptureUiState(
    val draft: TaskCaptureDraft = TaskCaptureDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val persisted: Boolean = false,
    val error: String? = null,
    val receipt: String? = null,
    val loadFailed: Boolean = false,
)

class TaskCaptureViewModel(private val repository: TaskCaptureRepository) : ViewModel() {
    val uiState = MutableStateFlow(TaskCaptureUiState())
    private val mutex = Mutex()
    private var autosave: Job? = null
    private var pausedForDataClear = false

    init { load() }
    fun retryLoad() { if (!uiState.value.loading) { uiState.value = uiState.value.copy(loading = true); load() } }
    private fun load() {
        viewModelScope.launch {
            try {
                val saved = repository.observeDraft().first()
                uiState.value = uiState.value.copy(draft = saved ?: uiState.value.draft, loading = false, persisted = saved != null, loadFailed = false, error = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { uiState.value = uiState.value.copy(loading = false, loadFailed = true, error = "草稿读取失败，请重试；未覆盖已有草稿。") }
        }
    }

    fun update(draft: TaskCaptureDraft) {
        if (uiState.value.loading || uiState.value.saving || uiState.value.loadFailed) return
        uiState.value = uiState.value.copy(draft = draft, error = null, persisted = false)
        persistSoon()
    }

    fun retain() { if (!uiState.value.loading && !uiState.value.saving && !uiState.value.loadFailed) persistSoon(immediate = true) }

    private fun persistSoon(immediate: Boolean = false) {
        autosave?.cancel()
        val draft = uiState.value.draft
        autosave = viewModelScope.launch {
            if (!immediate) delay(150)
            try {
                mutex.withLock { repository.saveDraft(draft) }
                if (uiState.value.draft == draft) uiState.value = uiState.value.copy(persisted = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { uiState.value = uiState.value.copy(error = "草稿尚未保存到本地，文字仍在，请重试。") }
        }
    }

    fun save(task: TaskDraft) {
        val current = uiState.value
        if (current.loading || current.saving || current.loadFailed) return
        if (task.id != current.draft.id || !current.draft.durationIsValid || !TaskDraftValidator.isValid(task)) {
            uiState.value = current.copy(error = "请先填写任务内容并检查时长。")
            return
        }
        autosave?.cancel()
        uiState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                mutex.withLock {
                    repository.saveDraft(current.draft)
                    repository.commit(current.draft, task)
                }
                val today = java.time.LocalDate.now()
                val destination = when {
                    task.scheduledForDate == today || task.dueDate == today -> "今日清单（未占具体时段）"
                    task.scheduledForDate == null && task.dueDate == null -> "待安排"
                    else -> "任务库（已设日期，未占具体时段）"
                }
                uiState.value = TaskCaptureUiState(loading = false, receipt = "已保存“${task.displayName}”到$destination。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { uiState.value = current.copy(saving = false, error = "保存未成功，草稿和选项仍保留，请重试。") }
        }
    }

    fun discard() {
        if (uiState.value.loading || uiState.value.saving || uiState.value.loadFailed) return
        val old = uiState.value.draft
        autosave?.cancel()
        uiState.value = uiState.value.copy(saving = true)
        viewModelScope.launch {
            try {
                mutex.withLock { repository.discard(old.id) }
                uiState.value = TaskCaptureUiState(loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { uiState.value = uiState.value.copy(saving = false, error = "草稿未放弃，请重试。") }
        }
    }

    fun dismissReceipt() { uiState.value = uiState.value.copy(receipt = null) }
    suspend fun prepareForDataClear() {
        check(!uiState.value.saving) { "请等待任务保存完成。" }
        pausedForDataClear = true
        uiState.value = uiState.value.copy(loading = true)
        autosave?.cancelAndJoin()
        mutex.withLock { /* Wait until any queued draft write has finished. */ }
    }
    fun finishDataClear(succeeded: Boolean) {
        if (!pausedForDataClear) return
        pausedForDataClear = false
        uiState.value = if (succeeded) TaskCaptureUiState(loading = false)
            else uiState.value.copy(loading = false, error = "清除未成功，原草稿仍保留。")
    }
    class Factory(private val repository: TaskCaptureRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskCaptureViewModel(repository) as T
    }
}
