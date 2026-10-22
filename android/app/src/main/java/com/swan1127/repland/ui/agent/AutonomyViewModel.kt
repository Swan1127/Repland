package com.swan1127.repland.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.AutonomyRepository
import com.swan1127.repland.domain.ports.AutonomyState
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutonomyUiState(
    val ready: Boolean = false,
    val delegation: AutonomyState = AutonomyState(),
    val busy: Boolean = false,
    val error: String? = null,
    val response: String? = null,
    val queryResults: List<Task>? = null,
    val submission: Long = 0,
    val completedAttempts: Long = 0,
)

/** Remote understanding is optional; local execution and its guards always remain available. */
@OptIn(FlowPreview::class)
class AutonomyViewModel(
    private val repository: AutonomyRepository,
    private val reads: PlanningReadService,
    private val operations: PlanningOperationService,
) : ViewModel() {
    private val mutable = MutableStateFlow(AutonomyUiState())
    val state: StateFlow<AutonomyUiState> = mutable.asStateFlow()
    private val work = Mutex()

    init {
        viewModelScope.launch {
            repository.observe().catch { fail("无法读取自动安排设置，请重新打开 App。") }
                .collect { setting -> mutable.update { it.copy(ready = true, delegation = setting) } }
        }
        viewModelScope.launch {
            repository.observeInputRevision().distinctUntilChanged().debounce(650)
                .catch { fail("暂时无法读取计划变化，现有数据保留。") }
                .collect { reconcile() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setEnabled(enabled)
                mutable.update { it.copy(error = null) }
                if (enabled) reconcile()
            } catch (c: CancellationException) { throw c }
            catch (_: Exception) { fail("自动安排设置未保存，请重试。") }
        }
    }

    fun onForeground() { viewModelScope.launch { reconcile() } }

    private suspend fun reconcile() = work.withLock {
        try {
            val result = repository.reconcile()
            // Publish the receipt at the same serialized boundary as explicit commands.
            // The repository observer may arrive later, so it only refreshes delegation;
            // it must not overwrite a newer query or the capture's understanding prefix.
            if (result != null) mutable.update { it.copy(response = result, queryResults = null, error = null) }
        } catch (c: CancellationException) { throw c }
        catch (e: Exception) { fail(e.message ?: "本次自动调整未完成，原数据保留。") }
    }

    fun submit(text: String, canUseRemote: Boolean,
               refine: suspend (ArrangementAssistantAdviceRequest) -> ArrangementAssistantAdviceResult) {
        if (text.isBlank() || mutable.value.busy || !mutable.value.ready) return
        mutable.update { it.copy(busy = true, error = null, queryResults = null) }
        viewModelScope.launch {
            work.withLock {
                try {
                    val today = LocalDate.now()
                    val operationId = UUID.randomUUID().toString()
                    val response = when (val intent = AutonomousTaskIntentParser.parse(text)) {
                        is AutonomousTaskIntent.NeedsClarification -> throw IllegalArgumentException(when (intent.reason) {
                            AutonomousIntentAmbiguity.EMPTY_INPUT -> "请先写下要处理的事。"
                            AutonomousIntentAmbiguity.INVALID_PROGRESS -> "请用 0% 到 100% 表达任务进度。"
                            AutonomousIntentAmbiguity.NEGATED_STATUS -> "已保留任务状态。需要新增待办时，请写“新增任务：…”；更新进度时请说明具体结果。"
                            AutonomousIntentAmbiguity.MIXED_OPERATIONS -> "请分开发送进度更新和新增任务，避免更新错事项。"
                            AutonomousIntentAmbiguity.MULTIPLE_TARGETS, AutonomousIntentAmbiguity.CONFLICTING_COMMANDS -> "请分别写明每项任务的名称和结果。"
                            else -> "这句话还不能确定为执行指令，请说明具体任务和要做的操作。"
                        })
                        is AutonomousTaskIntent.StatusUpdates -> repository.updateStatus(intent.commands, operationId)
                        is AutonomousTaskIntent.Arrange -> repository.arrange(day(intent.day, today), operationId,
                            if (intent.day == AutonomousDay.TONIGHT) 18 * 60 else 0)
                        is AutonomousTaskIntent.Query -> {
                            val scope = when (intent.scope) {
                                AutonomousQueryScope.ACTIVE -> TaskQueryScope.ALL_ACTIVE
                                AutonomousQueryScope.SCHEDULED -> TaskQueryScope.TODAY
                                AutonomousQueryScope.INBOX -> TaskQueryScope.INBOX
                                AutonomousQueryScope.OVERDUE -> TaskQueryScope.OVERDUE
                            }
                            var tasks = operations.query(scope, day(intent.day ?: AutonomousDay.TODAY, today))
                            if (intent.day == AutonomousDay.TONIGHT) {
                                val eveningIds = reads.snapshot().current?.segments.orEmpty()
                                    .filter { it.date == today && it.endMinute > 18 * 60 }.map { it.taskId }.toSet()
                                tasks = tasks.filter { it.id in eveningIds }
                            }
                            mutable.update { it.copy(queryResults = tasks) }
                            if (tasks.isEmpty()) "没有符合条件的任务。" else "找到 ${tasks.size} 件任务，点开可查看详情。"
                        }
                        is AutonomousTaskIntent.Capture -> {
                            val selection = AutonomousCaptureDate.resolve(intent.text, today)
                            val date = selection.date
                            val source = reads.snapshot()
                            val local = ArrangementAssistantInterpreter.interpret(intent.text).candidates
                            var candidates = local
                            var usedRemote = false
                            if (canUseRemote) {
                                val request = ArrangementAssistantAdviceRequest(intent.text, date, emptyList(),
                                    existingTasks = source.input.tasks.filter { it.status.isActive }.take(50).map {
                                        ArrangementExistingTask(it.id, it.displayName, it.category, it.totalDurationMinutes)
                                    }, sourceRevision = source.revision)
                                val result = refine(request)
                                if (result is ArrangementAssistantAdviceResult.Advice &&
                                    result.advice.candidates.isNotEmpty() && result.advice.candidates.none { it.existingTaskId != null }) {
                                    candidates = AutonomousCaptureFacts.reconcile(local, result.advice.candidates)
                                    usedRemote = true
                                } else if (result is ArrangementAssistantAdviceResult.Failed &&
                                    result.reason == AiAdvisorFailureReason.SOURCE_CHANGED) {
                                    throw IllegalStateException("理解期间任务或时间发生变化，请重新发送。")
                                }
                            }
                            val receipt = repository.capture(candidates, date,
                                selection.explicit,
                                operationId, source.revision, intent.text)
                            (if (usedRemote) "AI 已理解。" else "已用本地规则处理。") + receipt
                        }
                    }
                    mutable.update { it.copy(response = response, submission = it.submission + 1, error = null) }
                } catch (c: CancellationException) { throw c }
                catch (e: Exception) { fail(e.message ?: "本次未完成，请重试。") }
                finally { mutable.update { it.copy(busy = false, completedAttempts = it.completedAttempts + 1) } }
            }
        }
    }

    fun undo() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            work.withLock {
                try {
                    val result = repository.undo()
                    mutable.update { it.copy(response = result, queryResults = null) }
                } catch (c: CancellationException) { throw c }
                catch (e: Exception) { fail(e.message ?: "撤销未完成，当前数据保留。") }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }

    private fun fail(message: String) { mutable.update { it.copy(error = message) } }
    private fun day(day: AutonomousDay, today: LocalDate): LocalDate =
        if (day == AutonomousDay.TOMORROW) today.plusDays(1) else today

    class Factory(private val repository: AutonomyRepository, private val reads: PlanningReadService,
                  private val operations: PlanningOperationService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AutonomyViewModel::class.java))
            return AutonomyViewModel(repository, reads, operations) as T
        }
    }
}
