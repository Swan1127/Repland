package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TaskRepository
import com.swan1127.repland.ui.tasks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TaskFeedbackIntegrityTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    private fun draft(id: String = "feedback21") = TaskDraft(id = id, displayName = "反馈测试", description = "原备注",
        category = TaskCategory.COURSE, userPriority = TaskPriority.HIGH, estimatedDays = 1, totalDurationMinutes = 30, dueDate = null)
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    @Test fun failure_retains_target_retry_is_exact_and_pending_duplicates_make_one_log() = runBlocking {
        val db = database(); val store = ViewModelStore(); val base = RoomTaskRepository(db)
        var failWrite = true; var writes = 0; var gate: CompletableDeferred<Unit>? = null
        val repo = object : TaskRepository by base {
            override suspend fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) {
                gate?.await(); if (failWrite) error("injected")
                base.recordPartialCompletion(taskId, feedback); writes++
            }
        }
        lateinit var vm: TaskViewModel
        try {
            base.save(draft()); val original = base.observeTasks().first()
            main { vm = TaskViewModel(repo); store.put("feedback", vm) }
            val feedback = TaskFeedback(completedContent = "完成一部分", actualDurationMinutes = 20, progressPercent = 40)
            main { vm.recordPartialCompletion("feedback21", feedback) }
            withTimeout(10_000) { vm.mutationState.first { it.error != null } }
            assertEquals(TaskMutationKind.PARTIAL, vm.mutationState.value.kind)
            assertEquals("feedback21", vm.mutationState.value.taskId); assertNull(vm.mutationState.value.receipt)
            assertEquals(original, base.observeTasks().first()); assertTrue(base.observeExecutionLogs("feedback21").first().isEmpty())
            failWrite = false; gate = CompletableDeferred()
            main { vm.retryMutation(); vm.retryMutation(); vm.recordFeedback("feedback21", TaskFeedback(progressPercent = 90)); vm.resetMutation() }
            assertTrue(vm.mutationState.value.busy); assertNull(vm.mutationState.value.receipt)
            gate!!.complete(Unit)
            withTimeout(10_000) { vm.mutationState.first { it.receipt != null } }
            assertEquals(1, writes); assertEquals(1, base.observeExecutionLogs("feedback21").first().size)
            assertEquals(feedback, base.observeExecutionLogs("feedback21").first().single().feedback)
            assertEquals(TaskStatus.IN_PROGRESS, base.observeTasks().first().single().status)
            main { vm.recordPartialCompletion("feedback21", feedback); vm.retryMutation() }; assertEquals(1, writes)
        } finally { main { store.clear() }; db.close() }
    }

    @Test fun postponement_duplicate_is_one_explicit_delay_and_cancellation_is_not_failure() = runBlocking {
        val db = database(); val store = ViewModelStore(); val base = RoomTaskRepository(db)
        val gate = CompletableDeferred<Unit>(); var cancel = false
        val repo = object : TaskRepository by base {
            override suspend fun confirmStatus(taskId: String, status: TaskStatus, feedback: TaskFeedback) {
                if (cancel) throw CancellationException("injected cancellation")
                gate.await(); base.confirmStatus(taskId, status, feedback)
            }
        }
        lateinit var vm: TaskViewModel
        try {
            base.save(draft()); main { vm = TaskViewModel(repo); store.put("feedback", vm); vm.postponeTask("feedback21", "需要资料"); vm.postponeTask("feedback21", "重复") }
            assertTrue(vm.mutationState.value.busy); gate.complete(Unit)
            withTimeout(10_000) { vm.mutationState.first { it.receipt != null } }
            assertEquals(1, base.observeTasks().first().single().postponeCount)
            assertEquals("需要资料", base.observeExecutionLogs("feedback21").first().single().feedback.postponeReason)
            cancel = true; main { vm.resetMutation(); vm.startTask("feedback21") }
            withTimeout(10_000) { vm.mutationState.first { !it.busy } }
            assertNull(vm.mutationState.value.error); assertNull(vm.mutationState.value.receipt)
            assertEquals(1, base.observeExecutionLogs("feedback21").first().size)
        } finally { main { store.clear() }; db.close() }
    }

    @Test fun log_insert_failure_rolls_back_all_lifecycle_mutations_and_replacement() = runBlocking {
        val db = database()
        try {
            val repo = RoomTaskRepository(db); repo.save(draft()); repo.recordFeedback("feedback21", TaskFeedback(completedContent = "原记录"))
            val originalTasks = repo.observeTasks().first(); val originalLogs = repo.observeExecutionLogs("feedback21").first()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_feedback_atomic_failure BEFORE INSERT ON execution_logs BEGIN SELECT RAISE(ABORT, 'qa failure'); END")
            val actions: List<suspend () -> Unit> = listOf(
                { repo.confirmStatus("feedback21", TaskStatus.POSTPONED, TaskFeedback(postponeReason = "资料")) },
                { repo.confirmStatus("feedback21", TaskStatus.CANCELLED) },
                { repo.confirmStatus("feedback21", TaskStatus.COMPLETED) },
                { repo.recordPartialCompletion("feedback21", TaskFeedback(completedContent = "部分", progressPercent = 25)) },
                { repo.recordFeedback("feedback21", TaskFeedback(progressPercent = 30)) },
                { repo.correctExecutionLog("feedback21", originalLogs.single().id, TaskFeedback(progressPercent = 40)) },
                { repo.replaceTask("feedback21", draft().copy(id = null, displayName = "替换")); Unit },
            )
            for (action in actions) {
                try { action(); fail("must fail atomically") } catch (_: android.database.sqlite.SQLiteException) { }
                assertEquals(originalTasks, repo.observeTasks().first()); assertEquals(originalLogs, repo.observeExecutionLogs("feedback21").first())
            }
        } finally { db.close() }
    }

    @Test fun correction_rejects_foreign_or_missing_identity_and_keeps_immutable_history() = runBlocking {
        val db = database()
        try {
            val repo = RoomTaskRepository(db); repo.save(draft()); repo.save(draft("other21"))
            repo.recordFeedback("feedback21", TaskFeedback(completedContent = "原记录"))
            val original = repo.observeExecutionLogs("feedback21").first().single()
            for ((taskId, logId) in listOf("other21" to original.id, "feedback21" to "missing")) {
                try { repo.correctExecutionLog(taskId, logId, TaskFeedback(progressPercent = 30)); fail("wrong identity") } catch (_: IllegalArgumentException) { }
            }
            repo.correctExecutionLog("feedback21", original.id, TaskFeedback(progressPercent = 30))
            val logs = repo.observeExecutionLogs("feedback21").first()
            assertEquals(2, logs.size); assertEquals(original, logs.single { it.id == original.id })
            assertEquals(original.id, logs.single { it.eventType == ExecutionLogEventType.CORRECTION }.correctedLogId)
            assertEquals(TaskStatus.NOT_STARTED, repo.observeTasks().first().single { it.id == "feedback21" }.status)
        } finally { db.close() }
    }
}
