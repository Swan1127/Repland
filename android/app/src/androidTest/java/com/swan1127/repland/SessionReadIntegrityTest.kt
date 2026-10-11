package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.tasks.TaskViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SessionReadIntegrityTest {
    @Test fun later_failure_preserves_round_and_refuses_all_mutations_until_retry() = failure(false, true)
    @Test fun initial_failure_does_not_claim_no_round_or_allow_mutations() = failure(true, true)
    @Test fun failed_empty_read_does_not_allow_starting_a_round() = failure(true, false)
    private fun failure(initial: Boolean, active: Boolean) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        val tasks = RoomTaskRepository(db); val plans = RoomPlanRepository(db)
        val sessions = RoomExecutionSessionRepository(db); val fault = ReadFaultSessions(sessions)
        val store = ViewModelStore(); var observer: Job? = null; var taskObserver: Job? = null
        lateinit var vm: TaskViewModel
        try {
            tasks.save(TaskDraft("own", "专注读取测试", "", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 60, null))
            plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "own", date = LocalDate.now().plusDays(1), startMinute = 600, endMinute = 660)), emptyList(), emptyList(), listOf("own")))
            val plan = plans.observeCurrentPlan().first()!!; val segment = plan.segments.single()
            if (active) sessions.start(segment.id)
            val original = sessions.observeActive().first(); val originalTasks = tasks.observeTasks().first()
            val originalLogs = tasks.observeExecutionLogs("own").first()
            fault.failed.value = initial
            withContext(Dispatchers.Main) { vm = TaskViewModel(tasks, fault); store.put("task", vm) }
            observer = launch { vm.sessionReadState.collect() }; taskObserver = launch { vm.uiState.collect() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            if (!initial) { withTimeout(10_000) { vm.sessionReadState.first { it.isTrusted } }; fault.failed.value = true }
            withTimeout(10_000) { vm.sessionReadState.first { it.error != null } }
            if (initial) assertFalse(vm.sessionReadState.value.hasLoaded) else assertEquals(original, vm.sessionReadState.value.value)
            withContext(Dispatchers.Main) {
                vm.startSession(segment.id); vm.pauseSession(original?.id ?: "missing")
                vm.resumeSession(original?.id ?: "missing"); vm.finishSession(original?.id ?: "missing", ExecutionOutcome.COMPLETED)
                vm.dismissSessionResult()
            }
            assertEquals(0, fault.mutations.get()); assertNotNull(vm.sessionReadState.value.error)
            assertFalse(vm.sessionFinished.value); assertFalse(vm.sessionBusy.value)
            assertEquals(original, sessions.observeActive().first()); assertEquals(originalTasks, tasks.observeTasks().first())
            assertEquals(originalLogs, tasks.observeExecutionLogs("own").first()); assertEquals(plan, plans.observeCurrentPlan().first())
            fault.failed.value = false
            withContext(Dispatchers.Main) { vm.retrySessionRead() }
            withTimeout(10_000) { vm.sessionReadState.first { it.isTrusted } }
            assertEquals(2, fault.sources.get()); assertEquals(original, vm.sessionReadState.value.value)
            assertEquals(original, sessions.observeActive().first())
            if (!active) {
                withContext(Dispatchers.Main) { vm.startSession(segment.id) }
                withTimeout(10_000) { combine(vm.sessionReadState, vm.sessionBusy) { read, busy -> read.value != null && !busy }.first { it } }
            }
            val session = sessions.observeActive().first()!!
            withContext(Dispatchers.Main) { vm.pauseSession(session.id) }
            withTimeout(10_000) { combine(vm.sessionReadState, vm.sessionBusy) { read, busy -> read.value?.isPaused == true && !busy }.first { it } }
            withContext(Dispatchers.Main) { vm.resumeSession(session.id) }
            withTimeout(10_000) { combine(vm.sessionReadState, vm.sessionBusy) { read, busy -> read.value?.isPaused == false && !busy }.first { it } }
            withContext(Dispatchers.Main) { vm.finishSession(session.id, ExecutionOutcome.CONTINUE) }
            withTimeout(10_000) { combine(vm.sessionReadState, vm.sessionFinished, vm.sessionBusy) { read, finished, busy -> read.value == null && finished && !busy }.first { it } }
            assertNull(sessions.observeActive().first()); assertEquals(TaskStatus.IN_PROGRESS, tasks.observeTasks().first().single().status)
            assertEquals(plan, plans.observeCurrentPlan().first())
        } finally { observer?.cancelAndJoin(); taskObserver?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; db.close() }
    }
}
