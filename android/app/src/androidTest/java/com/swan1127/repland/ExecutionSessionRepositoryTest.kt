package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ExecutionSessionRepositoryTest {
    private lateinit var db: ReplandDatabase
    private lateinit var repo: RoomExecutionSessionRepository
    private var clock = 100_000L
    private var slot = ""
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomExecutionSessionRepository(db) { clock }
    }
    @After fun teardown() = db.close()
    private suspend fun seed(id: String = "task") {
        RoomTaskRepository(db).save(TaskDraft(id, "复习", "复习", TaskCategory.COURSE, TaskPriority.HIGH, 1, 60, null))
        RoomPlanRepository(db).accept(PlanDraft(LocalDateTime.now(),
            listOf(PlannedSegment("slot-$id", id, LocalDate.now().plusDays(1), 540, 600)), emptyList(), emptyList(), listOf(id)))
        slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single().id
    }
    @Test fun pause_resume_recreation_and_finish_keep_exact_evidence() = runBlocking {
        seed(); repo.start(slot)
        val first = repo.observeActive().first()!!
        repo.start(slot)
        assertEquals(first.id, repo.observeActive().first()!!.id)
        clock += 120_000; repo.pause(first.id)
        clock += 600_000
        val recreated = RoomExecutionSessionRepository(db) { clock }
        assertEquals(120_000L, recreated.observeActive().first()!!.elapsedMillis(clock))
        recreated.resume(first.id); clock += 60_000
        recreated.finish(first.id, ExecutionOutcome.CONTINUE)
        assertNull(recreated.observeActive().first())
        assertEquals(TaskStatus.IN_PROGRESS.name, db.taskDao().getById("task")!!.status)
        val logs = db.executionLogDao().getForTask("task")
        assertEquals(2, logs.size); assertEquals(3, logs.last().actualDurationMinutes)
        val archived = ExecutionSessionCodec.decode(db.planningWorkspaceDao().get("execution-session-history:${first.id}")!!.payload)
        assertEquals(180_000L, archived.accumulatedMillis)
        assertEquals(ExecutionOutcome.CONTINUE, archived.outcome)
        try { recreated.finish(first.id, ExecutionOutcome.CONTINUE); fail("duplicate") } catch (_: IllegalArgumentException) { }
        assertEquals(2, db.executionLogDao().getForTask("task").size)
    }
    @Test fun invalid_partial_feedback_rolls_back_and_valid_partial_only_changes_confirmed_progress() = runBlocking {
        seed(); repo.start(slot); val s = repo.observeActive().first()!!; clock += 60_000
        try { repo.finish(s.id, ExecutionOutcome.PARTIAL, TaskFeedback(progressPercent = 50)); fail("missing content") } catch (_: IllegalArgumentException) { }
        assertNotNull(repo.observeActive().first()); assertEquals(1, db.executionLogDao().getForTask("task").size)
        repo.finish(s.id, ExecutionOutcome.PARTIAL, TaskFeedback(progressPercent = 50, completedContent = "第一章"))
        val t = db.taskDao().getById("task")!!
        assertEquals(50, t.progressPercent); assertEquals(60, t.totalDurationMinutes)
        assertEquals(TaskStatus.IN_PROGRESS.name, t.status)
    }
    @Test fun only_one_session_and_terminal_task_cannot_be_resurrected() = runBlocking {
        seed(); repo.start(slot); val s = repo.observeActive().first()!!
        try { repo.start("other"); fail("second session") } catch (_: IllegalArgumentException) { }
        RoomTaskRepository(db).confirmStatus("task", TaskStatus.COMPLETED, TaskFeedback(actualDurationMinutes = 5))
        try { repo.finish(s.id, ExecutionOutcome.SKIPPED); fail("resurrect") } catch (_: IllegalArgumentException) { }
        repo.finish(s.id, ExecutionOutcome.CONTINUE)
        assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById("task")!!.status)
        assertNull(repo.observeActive().first())
    }
    @Test fun completed_outcome_removes_active_session_and_zero_elapsed_is_not_fabricated() = runBlocking {
        seed(); repo.start(slot); val s = repo.observeActive().first()!!
        repo.finish(s.id, ExecutionOutcome.COMPLETED, TaskFeedback(progressPercent = 100))
        assertNull(repo.observeActive().first())
        assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById("task")!!.status)
        assertNull(db.executionLogDao().getForTask("task").last().actualDurationMinutes)
        val archived = ExecutionSessionCodec.decode(db.planningWorkspaceDao().get("execution-session-history:${s.id}")!!.payload)
        assertEquals(0L, archived.accumulatedMillis)
    }
    @Test fun replacement_during_round_can_end_without_mutating_original() = runBlocking {
        seed(); repo.start(slot); val s = repo.observeActive().first()!!
        RoomTaskRepository(db).replaceTask("task", TaskDraft(displayName = "新版", description = "", category = TaskCategory.COURSE,
            userPriority = TaskPriority.HIGH, estimatedDays = 1, totalDurationMinutes = 60, dueDate = null))
        repo.finish(s.id, ExecutionOutcome.CONTINUE)
        assertNull(repo.observeActive().first()); assertEquals(TaskStatus.REPLACED.name, db.taskDao().getById("task")!!.status)
    }
    @Test fun active_round_survives_database_close_and_reopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "execution-session-test-${java.util.UUID.randomUUID()}.db"
        var durable = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        try {
            durable.planningWorkspaceDao().put(PlanningWorkspaceEntity(RoomExecutionSessionRepository.KEY,
                ExecutionSessionCodec.encode(ExecutionSession("s", "task", "复习", "p", "slot", 1500, clock,
                    accumulatedMillis = 120_000, runningSinceEpochMillis = null))))
            durable.close()
            durable = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
            val restored = RoomExecutionSessionRepository(durable) { clock + 600_000 }.observeActive().first()!!
            assertTrue(restored.isPaused); assertEquals(120_000L, restored.elapsedMillis(clock + 600_000))
            assertEquals("p", restored.planId); assertEquals("slot", restored.segmentId)
        } finally { durable.close(); context.deleteDatabase(name) }
    }
    @Test fun accepting_draft_cannot_remove_a_round_started_early_even_if_task_was_already_in_progress() = runBlocking {
        seed(); repo.start(slot)
        val before = db.planDao().getCurrentPlanWithSegments()!!.plan.id
        try {
            RoomPlanRepository(db).accept(PlanDraft(LocalDateTime.now(), emptyList(), listOf("task"), emptyList(), listOf("task")))
            fail("executing placement removed")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, db.planDao().getCurrentPlanWithSegments()!!.plan.id)
        assertNotNull(repo.observeActive().first())
    }
}
