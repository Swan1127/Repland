package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.ExecutionSessionRepository
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

/** Uses the existing versioned workspace table; no destructive database migration. */
class RoomExecutionSessionRepository(
    private val database: ReplandDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : ExecutionSessionRepository {
    private val workspace = database.planningWorkspaceDao()
    private val tasks = RoomTaskRepository(database)
    override fun observeActive() = workspace.observe(KEY).map { it?.let { row -> ExecutionSessionCodec.decode(row.payload) } }

    override suspend fun start(segmentId: String) = database.withTransaction {
        val existing = active()
        if (existing != null) {
            require(existing.segmentId == segmentId) { "请先结束当前专注，再开始另一项任务。" }
            return@withTransaction // Opening the same round must not restart its clock or append evidence.
        }
        val plan = requireNotNull(database.planDao().getCurrentPlanWithSegments()) { "当前计划不存在。" }
        val segment = requireNotNull(plan.segments.firstOrNull { it.id == segmentId }) { "安排已变化，请重新打开。" }
        val task = requireNotNull(database.taskDao().getById(segment.taskId)) { "任务不存在。" }
        require(TaskStatus.valueOf(task.status).isActive) { "已结束的任务不能开始专注。" }
        freezeNumericOriginal(database, task, clock())
        if (task.status != TaskStatus.IN_PROGRESS.name) tasks.confirmStatus(task.id, TaskStatus.IN_PROGRESS)
        store(ExecutionSession(UUID.randomUUID().toString(), task.id, task.displayName,
            plan.plan.id, segment.id, minOf(25 * 60, (segment.endMinute - segment.startMinute) * 60), clock()))
    }

    override suspend fun pause(sessionId: String) = database.withTransaction {
        val session = requireActive(sessionId)
        if (!session.isPaused) store(session.pause(clock()))
    }

    override suspend fun resume(sessionId: String) = database.withTransaction {
        val session = requireActive(sessionId)
        val task = requireNotNull(database.taskDao().getById(session.taskId))
        require(TaskStatus.valueOf(task.status).isActive) { "任务状态已变化，请结束本轮。" }
        if (session.isPaused) store(session.resume(clock()))
    }

    override suspend fun finish(sessionId: String, outcome: ExecutionOutcome, feedback: TaskFeedback) = database.withTransaction {
        val session = requireActive(sessionId)
        val now = clock()
        val elapsed = session.elapsedMillis(now)
        val minutes = if (elapsed == 0L) null else ((elapsed + 59_999) / 60_000).toInt()
        val evidence = feedback.copy(actualDurationMinutes = minutes, completionResult = feedback.completionResult ?: outcome.name)
        require(TaskLifecycleValidator.isValidFeedback(evidence)) { "请检查反馈内容。" }
        val task = requireNotNull(database.taskDao().getById(session.taskId))
        // A task may have been completed/cancelled on another page during this round.
        // Ending then records evidence, never resurrects or re-confirms that task.
        if (!TaskStatus.valueOf(task.status).isActive) {
            require(outcome == ExecutionOutcome.CONTINUE) { "任务已结束，请选择结束本轮以保留记录。" }
            if (task.status == TaskStatus.REPLACED.name) {
                // Replacement retains the original identity; append history without editing its snapshot.
                val logTime = maxOf(now, (database.executionLogDao().latestCreatedAtForTask(task.id) ?: 0) + 1)
                database.executionLogDao().insert(ExecutionLogEntity(UUID.randomUUID().toString(), task.id,
                    ExecutionLogEventType.FEEDBACK.name, task.status, minutes, null, null,
                    evidence.completionResult, null, null, null, logTime))
            } else tasks.recordFeedback(task.id, evidence)
        } else when (outcome) {
            ExecutionOutcome.COMPLETED -> tasks.confirmStatus(task.id, TaskStatus.COMPLETED, evidence)
            ExecutionOutcome.PARTIAL -> tasks.recordPartialCompletion(task.id, evidence)
            ExecutionOutcome.SKIPPED -> tasks.confirmStatus(task.id, TaskStatus.POSTPONED, evidence)
            ExecutionOutcome.CONTINUE -> tasks.recordFeedback(task.id, evidence)
        }
        val ended = session.copy(accumulatedMillis = elapsed, runningSinceEpochMillis = null,
            endedAtEpochMillis = now, outcome = outcome)
        val latestLog = database.executionLogDao().getForTask(session.taskId).lastOrNull()
        workspace.put(PlanningWorkspaceEntity("numeric-input:session:${session.id}", numericRow()
            .put("logs", org.json.JSONArray(listOfNotNull(latestLog?.id))).toString()))
        workspace.put(PlanningWorkspaceEntity("execution-session-history:${session.id}", ExecutionSessionCodec.encode(ended)))
        workspace.remove(KEY)
    }

    private suspend fun active() = workspace.get(KEY)?.let { ExecutionSessionCodec.decode(it.payload) }
    private suspend fun requireActive(id: String) = requireNotNull(active()?.takeIf { it.id == id }) { "本轮已结束或已变化，请重新打开。" }
    private suspend fun store(session: ExecutionSession) = workspace.put(PlanningWorkspaceEntity(KEY, ExecutionSessionCodec.encode(session)))
    companion object { const val KEY = "active-execution-session" }
}

object ExecutionSessionCodec {
    fun encode(s: ExecutionSession): String = JSONObject().apply {
        put("version", 1); put("id", s.id); put("task", s.taskId); put("title", s.taskTitle)
        put("plan", s.planId); put("segment", s.segmentId); put("target", s.targetSeconds)
        put("started", s.startedAtEpochMillis); put("elapsed", s.accumulatedMillis)
        put("running", s.runningSinceEpochMillis ?: JSONObject.NULL)
        put("ended", s.endedAtEpochMillis ?: JSONObject.NULL); put("outcome", s.outcome?.name ?: JSONObject.NULL)
    }.toString()
    fun decode(payload: String): ExecutionSession = JSONObject(payload).let {
        require(it.getInt("version") == 1)
        ExecutionSession(it.getString("id"), it.getString("task"), it.getString("title"),
            it.getString("plan"), it.getString("segment"), it.getInt("target"), it.getLong("started"),
            it.getLong("elapsed"), if (it.isNull("running")) null else it.getLong("running"),
            if (it.isNull("ended")) null else it.getLong("ended"),
            if (it.isNull("outcome")) null else ExecutionOutcome.valueOf(it.getString("outcome")))
    }
}
