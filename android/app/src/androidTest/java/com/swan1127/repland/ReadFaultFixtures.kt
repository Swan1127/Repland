package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/** Tests only: real repositories retain transaction semantics, observations can fail. */
internal class ReadFaultTasks(private val base: TaskRepository) : TaskRepository by base {
    val failed = MutableStateFlow(false)
    val sources = AtomicInteger()
    override fun observeTasks(): Flow<List<Task>> {
        sources.incrementAndGet()
        return base.observeTasks().combine(failed) { value, fail -> check(!fail) { "QA read failure" }; value }
    }
}

internal class ReadFaultTime(private val base: TimeRepository) : TimeRepository by base {
    val failedPart = MutableStateFlow<String?>(null)
    val sources = AtomicInteger()
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> {
        sources.incrementAndGet()
        return guard("weekly", base.observeWeeklyBlocks())
    }
    override fun observeDateOverrides() = guard("dates", base.observeDateOverrides())
    override fun observeTimeConstraintSettings() = guard("settings", base.observeTimeConstraintSettings())
    private fun <T> guard(part: String, source: Flow<T>) = source.combine(failedPart) { value, failed ->
        check(part != failed) { "QA read failure" }; value
    }
}

internal class ReadFaultPlans(private val base: PlanRepository) : PlanRepository by base {
    @Volatile var failDraftWrite = false
    var draftWriteGate: CompletableDeferred<Unit>? = null
    val draftWrites = AtomicInteger()
    val draftWriteStarted = CompletableDeferred<Unit>()
    val draftWriteCancelled = CompletableDeferred<Unit>()
    override suspend fun saveDraft(draft: PlanDraft?) {
        check(!failDraftWrite) { "QA draft write failure" }
        base.saveDraft(draft); draftWrites.incrementAndGet()
    }
    override suspend fun replaceDraftIfCurrent(expected: PlanDraft, updated: PlanDraft) {
        draftWriteStarted.complete(Unit)
        try {
            draftWriteGate?.await()
            check(!failDraftWrite) { "QA draft write failure" }
            base.replaceDraftIfCurrent(expected, updated); draftWrites.incrementAndGet()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            draftWriteCancelled.complete(Unit); throw cancelled
        }
    }
    val failedPart = MutableStateFlow<String?>(null)
    val mainSources = AtomicInteger()
    val workspaceSources = AtomicInteger()
    override fun observeCurrentPlan(): Flow<ConfirmedPlan?> { mainSources.incrementAndGet(); return guard("current", base.observeCurrentPlan()) }
    override fun observePlanHistory() = guard("history", base.observePlanHistory())
    override fun observeDraft() = guard("draft", base.observeDraft())
    override fun observeTaskOrder() = guard("order", base.observeTaskOrder())
    override fun observeTracks(): Flow<List<RhythmTrack>> { workspaceSources.incrementAndGet(); return guard("tracks", base.observeTracks()) }
    override fun observeAssistantWorkspace() = guard("assistant", base.observeAssistantWorkspace())
    override fun observeCanUndoTaskOrder() = guard("undo", base.observeCanUndoTaskOrder())
    private fun <T> guard(part: String, source: Flow<T>) = source.combine(failedPart) { value, failed ->
        check(part != failed) { "QA read failure" }; value
    }
}

internal class ReadFaultPreferences(private val base: CategoryPreferenceRepository) : CategoryPreferenceRepository by base {
    val failed = MutableStateFlow(false)
    val sources = AtomicInteger()
    val commits = AtomicInteger()
    val cancelledWrite = CompletableDeferred<Unit>()
    @Volatile var failWrite = false
    var gate: CompletableDeferred<Unit>? = null
    override fun observe(): Flow<Map<TaskCategory, Int>> {
        sources.incrementAndGet()
        return base.observe().combine(failed) { weights, fail -> check(!fail) { "QA read failure" }; weights }
    }
    override suspend fun save(weights: Map<TaskCategory, Int>) {
        try {
            gate?.await()
            check(!failWrite) { "QA write failure" }
            base.save(weights); commits.incrementAndGet()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cancelledWrite.complete(Unit)
            throw cancelled
        }
    }
}

internal class ReadFaultSessions(private val base: ExecutionSessionRepository) : ExecutionSessionRepository by base {
    val failed = MutableStateFlow(false)
    val sources = AtomicInteger()
    val mutations = AtomicInteger()
    override fun observeActive(): Flow<ExecutionSession?> {
        sources.incrementAndGet()
        return base.observeActive().combine(failed) { value, fail -> check(!fail) { "QA session read failure" }; value }
    }
    override suspend fun start(segmentId: String) { mutations.incrementAndGet(); base.start(segmentId) }
    override suspend fun pause(sessionId: String) { mutations.incrementAndGet(); base.pause(sessionId) }
    override suspend fun resume(sessionId: String) { mutations.incrementAndGet(); base.resume(sessionId) }
    override suspend fun finish(sessionId: String, outcome: ExecutionOutcome, feedback: TaskFeedback) { mutations.incrementAndGet(); base.finish(sessionId, outcome, feedback) }
}
