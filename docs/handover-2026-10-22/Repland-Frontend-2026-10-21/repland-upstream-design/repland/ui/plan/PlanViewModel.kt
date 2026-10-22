package com.swan1127.repland.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.MemoryReference
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftGenerator
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.PlanningConstraintValidator
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.planteam.ExecutionPulse
import com.swan1127.repland.domain.planteam.PlanRequestKind
import com.swan1127.repland.domain.planteam.PlanTeamContext
import com.swan1127.repland.domain.planteam.PlanTeamProfileFacts
import com.swan1127.repland.domain.planteam.PlanTeamResult
import com.swan1127.repland.domain.planteam.PlanningTeamCoordinator
import com.swan1127.repland.domain.planteam.runtime.TeamEvent
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlanUiState(
    val currentPlan: ConfirmedPlan? = null,
    val planHistory: List<ConfirmedPlan> = emptyList(),
    val draft: PlanDraft? = null,
    val isGenerating: Boolean = false,
    val isAccepting: Boolean = false,
    val teamEvents: List<TeamEvent> = emptyList(),
    val teamResult: PlanTeamResult? = null,
    val actionError: String? = null,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
    private val planDraftGenerator: PlanDraftGenerator,
    private val planningTeam: PlanningTeamCoordinator? = null,
    private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val workState = MutableStateFlow(PlanUiState())
    private val generationToken = AtomicLong()
    private var generationJob: Job? = null
    private var acceptanceJob: Job? = null
    private var source: DraftSource? = null

    private data class DraftSource(
        val input: PlanGenerationInput,
        val current: ConfirmedPlan?,
        val memory: MemoryReference?,
    )

    val uiState: StateFlow<PlanUiState> = combine(
        planRepository.observeCurrentPlan(),
        planRepository.observePlanHistory(),
        workState,
    ) { currentPlan, planHistory, work ->
        work.copy(currentPlan = currentPlan, planHistory = planHistory)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState(),
    )

    fun generateDraft(
        tasks: List<Task>,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate?,
        lockedSegments: List<PlannedSegment>,
        categoryPreferences: Map<TaskCategory, Int>,
        manualTaskOrder: List<String>,
        memoryReference: MemoryReference? = null,
        executionLogs: List<TaskExecutionLog>? = null,
        focusMinutes: Int? = null,
    ) {
        if (workState.value.isAccepting) return
        cancelGeneration()
        val input = PlanGenerationInput(
            tasks = tasks.toList(),
            weeklyBlocks = weeklyBlocks.toList(),
            dateOverrides = dateOverrides.toList(),
            semesterFirstWeekMonday = semesterFirstWeekMonday,
            lockedSegments = lockedSegments.toList(),
            categoryPreferences = categoryPreferences.toMap(),
            manualTaskOrder = manualTaskOrder.toList(),
        )
        val team = planningTeam
        if (team == null) {
            source = null
            workState.update { it.copy(draft = planDraftGenerator.generate(input), actionError = null) }
            return
        }
        val token = generationToken.incrementAndGet()
        workState.update { it.copy(isGenerating = true, teamEvents = emptyList(), teamResult = null, actionError = null) }
        generationJob = viewModelScope.launch {
            try {
                val current = planRepository.observeCurrentPlan().first()
                val now = LocalDateTime.now(clock)
                val taskIds = input.tasks.mapTo(HashSet(), Task::id)
                val todayLogs = executionLogs?.filter { log ->
                    log.taskId in taskIds &&
                        Instant.ofEpochMilli(log.createdAtEpochMillis).atZone(clock.zone).toLocalDate() == now.toLocalDate()
                }
                val context = PlanTeamContext(
                    requestKind = PlanRequestKind.FULL_REPLAN,
                    input = input,
                    current = current,
                    targetDate = now.toLocalDate(),
                    profileFacts = PlanTeamProfileFacts(
                        isConfigured = false,
                        preferMorningForDeepWork = false,
                        focusBlockMinutes = focusMinutes?.coerceAtLeast(1) ?: 90,
                        memory = memoryReference,
                    ),
                    now = now,
                    execution = todayLogs?.let { ExecutionPulse.from(now.toLocalDate(), it) },
                )
                val result = withContext(generationDispatcher) {
                    team.run(context, NoOpAiAdvisor) { event ->
                        if (generationToken.get() == token) {
                            workState.update { state ->
                                if (generationToken.get() == token) state.copy(teamEvents = state.teamEvents + event) else state
                            }
                        }
                    }
                }
                if (generationToken.get() == token) {
                    source = DraftSource(input, current, memoryReference)
                    workState.update { it.copy(draft = result.draft, teamResult = result, actionError = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generationToken.get() == token) {
                    workState.update { it.copy(actionError = failure.message ?: "规划未完成，现有草案与计划保留。") }
                }
            } finally {
                if (generationToken.get() == token) workState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun discardDraft() {
        if (workState.value.isAccepting) return
        cancelGeneration()
        source = null
        workState.update { it.copy(draft = null, teamResult = null, teamEvents = emptyList(), actionError = null) }
    }

    /** An Agent result stays a draft and never overwrites a draft the user is already editing. */
    fun showAgentDraft(agentDraft: PlanDraft) {
        if (workState.value.draft == null && !workState.value.isAccepting) {
            cancelGeneration()
            source = null
            workState.update { it.copy(draft = agentDraft, teamResult = null, teamEvents = emptyList(), actionError = null) }
        }
    }

    /** Keeps edits in an unconfirmed draft only; the current plan is never mutated here. */
    fun updateDraft(updatedDraft: PlanDraft) {
        if (workState.value.draft != null && !workState.value.isAccepting) {
            cancelGeneration()
            workState.update { it.copy(draft = updatedDraft, teamResult = null, teamEvents = emptyList(), actionError = null) }
        }
    }

    fun cancelGeneration() {
        generationToken.incrementAndGet()
        generationJob?.cancel()
        generationJob = null
        workState.update { it.copy(isGenerating = false) }
    }

    fun clearTeamMemory() {
        cancelGeneration()
        acceptanceJob?.cancel()
        planningTeam?.memoryBank()?.clear()
        source = null
        workState.value = PlanUiState()
    }

    fun acceptDraft(
        currentInput: PlanGenerationInput? = null,
        memoryReference: MemoryReference? = null,
        onAccepted: (() -> Unit)? = null,
    ) {
        val acceptedDraft = workState.value.draft ?: return
        if (workState.value.isAccepting || workState.value.isGenerating) return
        val expectedSource = source
        val token = generationToken.get()
        workState.update { it.copy(isAccepting = true, actionError = null) }
        acceptanceJob = viewModelScope.launch {
            try {
                if (planningTeam != null || expectedSource != null || currentInput != null) {
                    val input = requireNotNull(currentInput) { "请重新读取当前任务与时间设置后确认，草案已保留。" }
                    val current = planRepository.observeCurrentPlan().first()
                    if (expectedSource != null) {
                        require(expectedSource.input == input && expectedSource.current == current && expectedSource.memory == memoryReference) {
                            "任务、时间、计划或记忆参考已变化，请重新生成；当前草案与计划保留。"
                        }
                    }
                    validateDraft(acceptedDraft, input, current)
                }
                planRepository.accept(acceptedDraft)
                if (generationToken.get() == token) {
                    source = null
                    workState.update { it.copy(draft = null, teamResult = null, actionError = null) }
                    onAccepted?.invoke()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generationToken.get() == token) {
                    workState.update { it.copy(actionError = failure.message ?: "计划未确认，草案已保留，请检查后重试。") }
                }
            } finally {
                if (generationToken.get() == token) workState.update { it.copy(isAccepting = false) }
            }
        }
    }

    private fun validateDraft(draft: PlanDraft, input: PlanGenerationInput, current: ConfirmedPlan?) {
        val now = LocalDateTime.now(clock)
        val context = PlanTeamContext(PlanRequestKind.FULL_REPLAN, input, current, now.toLocalDate(), now = now)
        require(context.protectedSegments().all { protected ->
            draft.segments.any { candidate -> candidate.samePlacement(protected) && (!protected.isLocked || candidate.isLocked) }
        }) { "草案不能移动或删除锁定、必做或进行中的安排，请先在日程中明确处理。" }
        val remaining = current?.segments.orEmpty().toMutableList()
        val retained = draft.segments.map { candidate ->
            val index = remaining.indexOfFirst { it.samePlacement(candidate) }
            if (index >= 0) remaining.removeAt(index)
            index >= 0
        }
        val tasksById = input.tasks.associateBy(Task::id)
        draft.segments.forEachIndexed { index, segment ->
            require(segment.startMinute in 0 until 1440 && segment.endMinute in 1..1440 && segment.startMinute < segment.endMinute && segment.trackId.isNotBlank()) {
                "草案包含无效时段，请调整后重试。"
            }
            if (!retained[index]) {
                val task = requireNotNull(tasksById[segment.taskId]) { "草案中的事项已不存在，请重新生成。" }
                require(task.status.isActive && task.totalDurationMinutes != null) { "草案包含已结束或缺少时长的事项，请重新生成。" }
                require(segment.date > now.toLocalDate() || segment.date == now.toLocalDate() && segment.startMinute >= now.hour * 60 + now.minute) {
                    "草案包含已过去的新增时段，请重新生成。"
                }
                require(task.dueDate?.isBefore(segment.date) != true && task.scheduledForDate?.isAfter(segment.date) != true) {
                    "草案超出事项开始日或截止日，请调整后重试。"
                }
                require((segment.startMinute until segment.endMinute step 30).all { minute ->
                    PlanningConstraintValidator.isAvailable(
                        segment.date, minute, minOf(minute + 30, segment.endMinute), input.weeklyBlocks,
                        input.dateOverrides, input.semesterFirstWeekMonday,
                    )
                }) { "草案与课程、休息或可用时间冲突，请调整后重试。" }
            }
        }
        draft.segments.forEachIndexed { index, segment ->
            draft.segments.drop(index + 1).forEachIndexed { offset, other ->
                val overlapping = segment.date == other.date && segment.startMinute < other.endMinute && segment.endMinute > other.startMinute
                require(!overlapping || retained[index] && retained[index + offset + 1]) {
                    "草案包含新增重叠时段，请调整后重试。"
                }
            }
        }
    }

    private fun PlannedSegment.samePlacement(other: PlannedSegment): Boolean =
        taskId == other.taskId && date == other.date && startMinute == other.startMinute && endMinute == other.endMinute && trackId == other.trackId

    fun restore(planId: String) {
        viewModelScope.launch { planRepository.restore(planId) }
    }

    fun clearCurrentPlan() {
        viewModelScope.launch { planRepository.clearCurrentPlan() }
    }

    fun setSegmentLocked(segmentId: String, isLocked: Boolean) {
        viewModelScope.launch { planRepository.setSegmentLocked(segmentId, isLocked) }
    }

    fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) {
        viewModelScope.launch { planRepository.placeTask(taskId, date, startMinute, endMinute, trackId) }
    }

    fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) {
        viewModelScope.launch { planRepository.movePlacement(segmentId, startMinute, endMinute, trackId) }
    }

    fun removePlacement(segmentId: String) {
        viewModelScope.launch { planRepository.removePlacement(segmentId) }
    }

    class Factory(
        private val planRepository: PlanRepository,
        private val planDraftGenerator: PlanDraftGenerator,
        private val planningTeam: PlanningTeamCoordinator = PlanningTeamCoordinator(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(PlanViewModel::class.java))
            return PlanViewModel(planRepository, planDraftGenerator, planningTeam) as T
        }
    }
}
