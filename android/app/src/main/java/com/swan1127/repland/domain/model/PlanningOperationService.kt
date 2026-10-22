package com.swan1127.repland.domain.model

import com.swan1127.repland.domain.planteam.ExecutionPulse
import com.swan1127.repland.domain.planteam.PlanRequestKind
import com.swan1127.repland.domain.planteam.PlanTeamContext
import com.swan1127.repland.domain.planteam.PlanTeamProfileFacts
import com.swan1127.repland.domain.planteam.PlanTeamResult
import com.swan1127.repland.domain.planteam.PlanningTeamCoordinator
import com.swan1127.repland.domain.planteam.runtime.TeamEvent
import com.swan1127.repland.domain.ports.ExecutionSessionRepository
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PlanningPreviewKind { FORMULATE, REPLAN_REMAINING, SORT_ONLY, ARRANGE_TODAY }
enum class TaskQueryScope { ALL_ACTIVE, TODAY, INBOX, OVERDUE }

data class PlanningTeamPreview(
    val events: List<TeamEvent> = emptyList(),
    val result: PlanTeamResult? = null,
    val draft: PlanDraft? = null,
    val isRunning: Boolean = false,
)

/** Finite business operations shared by page actions and assistant routing.
 * Query/explanation are read-only; preview only stores a draft; confirmation uses repository transactions.
 */
class PlanningOperationService(
    private val reads: PlanningReadService,
    private val plans: PlanRepository,
    private val generator: PlanDraftGenerator,
    private val sessions: ExecutionSessionRepository? = null,
    private val numericProfile: com.swan1127.repland.domain.ports.NumericProfileRepository? = null,
    private val planningTeam: PlanningTeamCoordinator = PlanningTeamCoordinator(),
    private val memoryReference: (LocalDate) -> MemoryReference? = { null },
    private val executionLogs: suspend (LocalDate) -> List<TaskExecutionLog>? = { null },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val planningDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val previewMutex = Mutex()
    private val mutableTeamPreview = MutableStateFlow(PlanningTeamPreview())
    val teamPreview: StateFlow<PlanningTeamPreview> = mutableTeamPreview.asStateFlow()
    private var memoryGuard: Pair<PlanDraft, MemoryReference?>? = null

    fun clearTeamMemory() {
        planningTeam.memoryBank().clear()
        mutableTeamPreview.value = PlanningTeamPreview()
        memoryGuard = null
    }

    suspend fun query(scope: TaskQueryScope, date: LocalDate = LocalDate.now()): List<Task> {
        val source = reads.snapshot()
        val segments = source.current?.segments.orEmpty()
        val active = source.input.tasks.filter { it.status.isActive }
        val manualOrder = source.order.ifEmpty { source.current?.takeIf { it.hasManualTaskOrder }?.orderedTaskIds.orEmpty() }
        val order = LocalPriorityRanker.rank(active, source.input.categoryPreferences, LocalDate.now(), manualOrder).map { it.taskId }
        val byId = active.associateBy { it.id }
        return order.mapNotNull(byId::get).filter {
            when (scope) {
                TaskQueryScope.ALL_ACTIVE -> true
                TaskQueryScope.TODAY -> TaskPlanMembership.isToday(it, segments, date)
                TaskQueryScope.INBOX -> TaskPlanMembership.isInbox(it, segments, date)
                TaskQueryScope.OVERDUE -> it.dueDate?.isBefore(date) == true
            }
        }
    }

    suspend fun explainOrder(taskId: String): LocalPriorityAssessment {
        val source = reads.snapshot()
        val task = source.input.tasks.singleOrNull { it.id == taskId && it.status.isActive }
        requireNotNull(task) { "任务已结束或不存在，请刷新后重试。" }
        return LocalPriorityRanker.rank(listOf(task), source.input.categoryPreferences, LocalDate.now()).single()
    }

    suspend fun preview(kind: PlanningPreviewKind): PlanDraft = previewMutex.withLock {
        mutableTeamPreview.value = PlanningTeamPreview(isRunning = kind != PlanningPreviewKind.SORT_ONLY)
        try {
        val source = reads.snapshot()
        val previousDraft = plans.observeDraft().first()
        val sessionTask = sessions?.observeActive()?.first()?.taskId
        val now = LocalDateTime.now(clock)
        val memory = if (kind == PlanningPreviewKind.SORT_ONLY) null else memoryReference(now.toLocalDate())
        val input = source.input.copy(
            lockedSegments = source.current?.segments.orEmpty().filter { it.isLocked || it.taskId == sessionTask },
            manualTaskOrder = if (kind in setOf(PlanningPreviewKind.FORMULATE, PlanningPreviewKind.SORT_ONLY)) emptyList()
                else source.order.ifEmpty { source.current?.takeIf { it.hasManualTaskOrder }?.orderedTaskIds.orEmpty() },
        )
        var teamResult: PlanTeamResult? = null
        val result = withContext(planningDispatcher) {
            if (kind == PlanningPreviewKind.SORT_ONLY) generator.generateReplan(input, source.current)
            else {
                val taskIds = input.tasks.mapTo(HashSet(), Task::id)
                val logs = executionLogs(now.toLocalDate())?.filter { log ->
                    log.taskId in taskIds && Instant.ofEpochMilli(log.createdAtEpochMillis).atZone(clock.zone).toLocalDate() == now.toLocalDate()
                }
                planningTeam.run(
                    PlanTeamContext(
                        requestKind = PlanRequestKind.FULL_REPLAN,
                        input = input,
                        current = source.current,
                        targetDate = now.toLocalDate(),
                        profileFacts = PlanTeamProfileFacts(isConfigured = false, preferMorningForDeepWork = false, memory = memory),
                        now = now,
                        runningTaskId = sessionTask,
                        execution = logs?.let { ExecutionPulse.from(now.toLocalDate(), it) },
                        generator = generator,
                        todayOnly = kind == PlanningPreviewKind.ARRANGE_TODAY,
                    ),
                    NoOpAiAdvisor,
                ) { event -> mutableTeamPreview.update { it.copy(events = it.events + event) } }
                    .also { teamResult = it }.draft
            }
        }.copy(sourceRevision = source.revision, orderOnly = kind == PlanningPreviewKind.SORT_ONLY)
        var draft = if (result.orderOnly) result.copy(segments = emptyList(), unscheduledTasks = emptyList()) else result
        // Optional read-only advice. A failed/disabled profile cannot prevent basic local planning.
        if (!draft.orderOnly && numericProfile != null) {
            val profile = try { numericProfile.refresh() } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { null }
            if (profile?.enabled == true) {
                val estimates = source.input.tasks.filter { it.status.isActive }.mapNotNull { task ->
                    try { numericProfile.durationAdvice(task.id) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { null }
                }.filter { it.profileVersion == profile.version }
                val round = profile.parameters.singleOrNull { it.id == "rounds" && it.availability == NumericAvailability.USABLE }
                val used = estimates.map { it.parameterId }.distinct() + listOfNotNull(round?.id)
                if (used.isNotEmpty()) draft = draft.copy(numericProfileVersion = profile.version, numericParameters = used,
                    numericAdvice = estimates.map { "任务 ${it.taskId}：未修正基准 ${it.originalMinutes} 分钟 × ${it.factor} = ${it.suggestedMinutes} 分钟；请在画像来源页单独采纳后重新生成。本草案沿用当前已确认输入。" } +
                        listOfNotNull(round?.let { "可选拆分参考：实际执行段中位 ${it.value} 分钟，${it.count} 个样本；不是偏好，不自动改变时段。" }))
            }
        }
        require(reads.snapshot().revision == source.revision) { "生成期间任务、时间或偏好已变化，请重新预览。" }
        require(kind == PlanningPreviewKind.SORT_ONLY || memoryReference(LocalDate.now(clock)) == memory) { "复习反馈或记忆参考设置已变化，请重新预览；原草案和计划保留。" }
        require(plans.observeDraft().first() == previousDraft) { "生成期间草案已被修改，请先查看当前预览；本次未覆盖。" }
        plans.saveDraft(draft)
        memoryGuard = if (kind == PlanningPreviewKind.SORT_ONLY) null else draft to memory
        mutableTeamPreview.update { it.copy(result = teamResult?.copy(draft = draft), draft = draft, isRunning = false) }
        draft
        } finally {
            mutableTeamPreview.update { it.copy(isRunning = false) }
        }
    }

    suspend fun confirm(draft: PlanDraft) {
        // No fallback empty context at the write boundary. Repository transactions
        // still revalidate revisions and hard constraints immediately before commit.
        reads.snapshot()
        require(draft.sourceRevision != null) { "这份旧草案缺少来源版本，请重新生成预览；原草案和计划仍保留。" }
        memoryGuard?.takeIf { (original, _) -> original.generatedAt == draft.generatedAt && original.sourceRevision == draft.sourceRevision }?.let { (_, memory) ->
            require(memoryReference(LocalDate.now(clock)) == memory) { "复习反馈或记忆参考设置已变化，请重新预览；当前计划保留。" }
        }
        plans.accept(draft)
    }
    suspend fun confirmChanges(tasks: List<TaskDraft>, segments: List<PlannedSegment>, existingIds: Set<String>, date: LocalDate): AssistantSaveResult =
        run {
            reads.snapshot()
            plans.saveAssistantChanges(tasks, segments, existingIds, date)
        }
}
