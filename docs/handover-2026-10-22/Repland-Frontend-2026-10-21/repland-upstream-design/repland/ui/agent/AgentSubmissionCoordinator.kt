package com.swan1127.repland.ui.agent

import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.domain.ports.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class AgentSubmissionCoordinator(
    private val taskRepository: TaskRepository,
    private val planRepository: PlanRepository,
    private val receiptDate: () -> LocalDate = { LocalDate.now() },
) {
    private val submissionMutex = Mutex()

    suspend fun submit(drafts: List<TaskDraft>, placements: List<AgentTaskPlacement>): AgentSubmissionResult =
        submissionMutex.withLock {
            val requestedIds = drafts.mapNotNull { it.id }.toSet()
            if (drafts.isEmpty() || requestedIds.size != drafts.size || requestedIds.any { it.isBlank() } || drafts.any { !TaskDraftValidator.isValid(it) }) {
                return@withLock AgentSubmissionResult.Failure("草案不完整，请检查名称、时长后重试。")
            }
            if (placements.any { it.taskId !in requestedIds || it.startMinute !in 0 until 1_440 || it.endMinute !in 1..1_440 || it.startMinute >= it.endMinute }) {
                return@withLock AgentSubmissionResult.Failure("草案时段不完整，请调整后重试。")
            }
            val date = placements.firstOrNull()?.date ?: receiptDate()
            var originalIds = emptySet<String>()
            val savedIds = linkedSetOf<String>()
            try {
                originalIds = taskRepository.observeTasks().first().map { it.id }.toSet()
                drafts.forEach { draft ->
                    taskRepository.save(draft)
                    val observedTasks = withTimeoutOrNull(5_000) {
                        taskRepository.observeTasks().first { tasks -> tasks.any { it.matches(draft) } }
                    }
                    check(observedTasks != null)
                    savedIds += requireNotNull(draft.id)
                }
                placements.forEach { placement ->
                    val existingSegments = planRepository.observeCurrentPlan().first()?.segments.orEmpty()
                        .filter { it.taskId == placement.taskId && it.date == placement.date }
                    if (existingSegments.size > 1) throw AgentSubmissionConflict()
                    if (existingSegments.none { it.matches(placement) } && existingSegments.isNotEmpty()) {
                        val existing = existingSegments.singleOrNull()
                        if (existing == null || existing.isLocked) throw AgentSubmissionConflict()
                        planRepository.movePlacement(existing.id, placement.startMinute, placement.endMinute, placement.trackId)
                    } else if (existingSegments.isEmpty()) {
                        planRepository.placeTask(placement.taskId, placement.date, placement.startMinute, placement.endMinute, placement.trackId)
                    }
                    val observedPlan = withTimeoutOrNull(5_000) {
                        planRepository.observeCurrentPlan().first { plan -> plan?.segments.orEmpty().any { it.matches(placement) } }
                    }
                    check(observedPlan != null)
                }
                val actualTasks = taskRepository.observeTasks().first().filter { it.id in savedIds }
                val actualSegments = planRepository.observeCurrentPlan().first()?.segments.orEmpty()
                check(drafts.all { draft -> actualTasks.any { it.matches(draft) } })
                check(placements.all { placement -> actualSegments.any { it.matches(placement) } })
                AgentSubmissionResult.Success(receipt(date, actualTasks, actualSegments, originalIds, true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val actualTasks = readEvidence { taskRepository.observeTasks().first() }
                    .orEmpty().filter { it.id in requestedIds }
                val actualSegments = readEvidence { planRepository.observeCurrentPlan().first()?.segments.orEmpty() }.orEmpty()
                val partial = actualTasks.takeIf { it.isNotEmpty() }?.let {
                    receipt(date, it, actualSegments, originalIds, false)
                }
                AgentSubmissionResult.Failure(
                    message = when {
                        failure is AgentSubmissionConflict -> "该事项已有锁定或多个时段，请先到日程中查看。输入与草案已保留。"
                        partial == null -> "未确认写入结果，草案已保留。请检查后重试。"
                        else -> "本次未全部完成。已保存的事项见回执，草案保留，可重试剩余安排。"
                    },
                    receipt = partial,
                    savedTaskIds = actualTasks.associate { it.id to it.id },
                )
            }
        }

    private fun receipt(
        date: LocalDate,
        tasks: List<Task>,
        segments: List<PlannedSegment>,
        originalIds: Set<String>,
        complete: Boolean,
    ): AgentActionReceipt {
        val taskTitles = tasks.associate { it.id to it.displayName }
        val placements = segments.filter { it.taskId in taskTitles }
            .sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::id))
            .map { segment ->
                AgentReceiptPlacement(
                    segmentId = segment.id,
                    taskId = segment.taskId,
                    taskTitle = taskTitles.getValue(segment.taskId),
                    date = segment.date,
                    startMinute = segment.startMinute,
                    endMinute = segment.endMinute,
                    trackId = segment.trackId,
                )
            }
        val arrangedIds = placements.map { it.taskId }.toSet()
        return AgentActionReceipt(
            date = date,
            createdCount = tasks.count { it.id !in originalIds },
            pendingCount = tasks.count { it.id !in arrangedIds },
            arrangedCount = tasks.count { it.id in arrangedIds },
            taskIds = tasks.map { it.id },
            taskTitles = tasks.map { it.displayName },
            isComplete = complete,
            placements = placements,
        )
    }
}

private class AgentSubmissionConflict : IllegalStateException()

private suspend fun <Result> readEvidence(block: suspend () -> Result): Result? = try {
    withTimeoutOrNull(5_000) { block() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private fun Task.matches(draft: TaskDraft): Boolean =
    id == draft.id && description == draft.description.trim() &&
        displayName == draft.displayName.trim() && category == draft.category &&
        totalDurationMinutes == draft.totalDurationMinutes && scheduledForDate == draft.scheduledForDate

private fun PlannedSegment.matches(placement: AgentTaskPlacement): Boolean =
    taskId == placement.taskId && date == placement.date && startMinute == placement.startMinute &&
        endMinute == placement.endMinute && trackId == placement.trackId
