package com.swan1127.repland.domain.model

data class TaskScheduleChange(val taskId: String, val before: List<PlannedSegment>, val after: List<PlannedSegment>)
data class TaskOrderChange(val taskId: String, val before: Int?, val after: Int)
data class PlanChanges(val schedules: List<TaskScheduleChange>, val order: List<TaskOrderChange>)

/** Compare business placements, not regenerated UUIDs. No writes to the confirmed plan. */
object PlanDraftReview {
    private fun signature(s: PlannedSegment) = listOf(s.date, s.startMinute, s.endMinute, s.trackId, s.isLocked)
    fun changes(draft: PlanDraft, current: ConfirmedPlan?, currentOrder: List<String>): PlanChanges {
        val old = current?.segments.orEmpty().groupBy { it.taskId }
        val next = draft.segments.groupBy { it.taskId }
        val schedules = if (draft.orderOnly) emptyList() else (old.keys + next.keys).mapNotNull { id ->
            val before = old[id].orEmpty().sortedWith(compareBy({ it.date }, { it.startMinute }, { it.endMinute }, { it.trackId }))
            val after = next[id].orEmpty().sortedWith(compareBy({ it.date }, { it.startMinute }, { it.endMinute }, { it.trackId }))
            if (before.map(::signature) == after.map(::signature)) null else TaskScheduleChange(id, before, after)
        }
        val baseline = currentOrder.distinct().ifEmpty { current?.orderedTaskIds.orEmpty().distinct() }
        val order = draft.orderedTaskIds.distinct().mapIndexedNotNull { index, id ->
            val previous = baseline.indexOf(id).takeIf { it >= 0 }?.plus(1)
            if (previous == index + 1) null else TaskOrderChange(id, previous, index + 1)
        }
        return PlanChanges(schedules, order)
    }

    /** Explicitly defer only this proposal's unprotected placements, leaving task facts intact. */
    fun defer(draft: PlanDraft, taskId: String, protectedIds: Set<String> = emptySet()): PlanDraft {
        require(!draft.orderOnly) { "排序草案不修改时段。" }
        require(taskId in draft.orderedTaskIds || taskId in draft.pendingTaskIds || draft.segments.any { it.taskId == taskId }) { "任务不属于此草案。" }
        val placements = draft.segments.filter { it.taskId == taskId }
        require(placements.none { it.isLocked || it.id in protectedIds }) { "锁定或已开始的安排不能暂不安排。" }
        val old = draft.unscheduledTasks.firstOrNull { it.taskId == taskId }
        val remaining = placements.sumOf { it.endMinute - it.startMinute } + (old?.remainingMinutes ?: 0)
        return draft.copy(
            segments = draft.segments.filterNot { it.taskId == taskId },
            pendingTaskIds = (draft.pendingTaskIds + taskId).distinct(),
            unscheduledTasks = draft.unscheduledTasks.filterNot { it.taskId == taskId } +
                UnscheduledTask(taskId, remaining, UnscheduledReason.USER_DEFERRED),
        )
    }
}
