package com.swan1127.repland.domain.model

import com.swan1127.repland.domain.ports.ExecutionSessionRepository
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class PlanningPreviewKind { FORMULATE, REPLAN_REMAINING, SORT_ONLY, ARRANGE_TODAY }
enum class TaskQueryScope { ALL_ACTIVE, TODAY, INBOX, OVERDUE }

/** Finite business operations shared by page actions and assistant routing.
 * Query/explanation are read-only; preview only stores a draft; confirmation uses repository transactions.
 */
class PlanningOperationService(
    private val reads: PlanningReadService,
    private val plans: PlanRepository,
    private val generator: PlanDraftGenerator,
    private val sessions: ExecutionSessionRepository? = null,
) {
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

    suspend fun preview(kind: PlanningPreviewKind): PlanDraft {
        val source = reads.snapshot()
        val sessionTask = sessions?.observeActive()?.first()?.taskId
        val input = source.input.copy(
            lockedSegments = source.current?.segments.orEmpty().filter { it.isLocked || it.taskId == sessionTask },
            manualTaskOrder = if (kind in setOf(PlanningPreviewKind.FORMULATE, PlanningPreviewKind.SORT_ONLY)) emptyList()
                else source.order.ifEmpty { source.current?.takeIf { it.hasManualTaskOrder }?.orderedTaskIds.orEmpty() },
        )
        val result = withContext(Dispatchers.Default) {
            if (kind == PlanningPreviewKind.ARRANGE_TODAY) PlanGenerator.generateToday(input, source.current)
            else generator.generateReplan(input, source.current)
        }.copy(sourceRevision = source.revision, orderOnly = kind == PlanningPreviewKind.SORT_ONLY)
        val draft = if (result.orderOnly) result.copy(segments = emptyList(), unscheduledTasks = emptyList()) else result
        require(reads.snapshot().revision == source.revision) { "生成期间任务、时间或偏好已变化，请重新预览。" }
        plans.saveDraft(draft)
        return draft
    }

    suspend fun confirm(draft: PlanDraft) {
        // No fallback empty context at the write boundary. Repository transactions
        // still revalidate revisions and hard constraints immediately before commit.
        reads.snapshot()
        plans.accept(draft)
    }
    suspend fun confirmChanges(tasks: List<TaskDraft>, segments: List<PlannedSegment>, existingIds: Set<String>, date: LocalDate): AssistantSaveResult =
        run {
            reads.snapshot()
            plans.saveAssistantChanges(tasks, segments, existingIds, date)
        }
}
