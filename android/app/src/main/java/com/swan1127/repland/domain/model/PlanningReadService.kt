package com.swan1127.repland.domain.model

import com.swan1127.repland.domain.ports.*
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

data class PlanningReadSnapshot(val input: PlanGenerationInput, val current: ConfirmedPlan?, val order: List<String>) {
    val revision: String get() = PlanningRevision.of(input, current, order)
}

/** Shared read boundary; never writes facts, drafts, settings or confirmed plans. */
class PlanningReadService(
    private val tasks: TaskRepository,
    private val time: TimeRepository,
    private val plans: PlanRepository,
    private val preferences: CategoryPreferenceRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend fun snapshot(): PlanningReadSnapshot = PlanningReadSnapshot(
        PlanGenerationInput(tasks.observeTasks().first(), time.observeWeeklyBlocks().first(),
            time.observeDateOverrides().first(), time.observeTimeConstraintSettings().first().semesterFirstWeekMonday,
            categoryPreferences = preferences.observe().first()),
        plans.observeCurrentPlan().first(), plans.observeTaskOrder().first(),
    )

    suspend fun prepare(request: ArrangementAssistantAdviceRequest, snapshot: PlanningReadSnapshot): ArrangementAssistantAdviceRequest {
        val ids = request.existingTasks.map { it.id }.toSet()
        val availableTasks = snapshot.input.tasks.filter { it.id in ids && it.status.isActive }.take(50)
        val words = listOfNotNull(request.utterance, request.followUpInstruction).joinToString(" ")
        val referenced = request.draftCandidates.mapNotNull { it.existingTaskId }.toSet()
        val relevant = availableTasks.filter { it.id in referenced || words.contains(it.displayName, ignoreCase = true) }.take(5)
        val feedback = relevant.map { task ->
            val logs = tasks.observeExecutionLogs(task.id).first().sortedBy { it.createdAtEpochMillis }
            ArrangementTaskFeedback(task.id, AiRequestFactory.taskUnderstanding(task, logs, null).task.recentFeedback.take(3).map {
                it.copy(completedContent = it.completedContent?.take(200), completionResult = it.completionResult?.take(200), postponeReason = it.postponeReason?.take(200))
            })
        }.filter { it.feedback.isNotEmpty() }
        val entries = ScheduleTimeline.entries(request.date, snapshot.input.weeklyBlocks, snapshot.input.dateOverrides,
            snapshot.current?.segments.orEmpty(), snapshot.input.tasks, snapshot.input.semesterFirstWeekMonday)
        val lockedIds = snapshot.current?.segments.orEmpty().filter { it.isLocked }.map { "segment:${it.id}" }.toSet()
        return request.copy(
            existingTasks = availableTasks.map { ArrangementExistingTask(it.id, it.displayName.take(120), it.category, it.totalDurationMinutes,
                it.status, it.userPriority, it.dueDate, it.scheduledForDate, it.progressPercent, it.postponeCount, it.inputSources) },
            occupiedIntervals = entries.filter { !it.taskClosed }.map { ArrangementOccupiedInterval(it.title.take(120), it.startMinute, it.endMinute,
                it.trackId, it.kind != TimelineKind.TASK || it.id in lockedIds, it.taskId) },
            availableIntervals = ArrangementAvailability.forDay(snapshot.input, request.date, now()),
            categoryPreferences = CategoryPreferences.normalized(snapshot.input.categoryPreferences),
            taskFeedback = feedback,
            sourceRevision = snapshot.revision,
        )
    }
}
