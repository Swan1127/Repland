package com.swan1127.repland.ui.tasks

import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.planDraft
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.ports.ArrangementCommitRepository
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.testkit.FakePlanDraftGenerator
import com.swan1127.repland.testkit.FakePlanRepository
import com.swan1127.repland.testkit.FakeTaskRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.withCollected
import com.swan1127.repland.ui.plan.PlanViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MutationCancellationTest : ViewModelTestBase() {
    @Test
    fun `closing the calling preview cancels its pending arrangement write`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var committed = false
        val model = TaskViewModel(FakeTaskRepository(), ArrangementCommitRepository { _, _ ->
            gate.await()
            committed = true
        })
        val draft = TaskDraft("new", "报告", "报告", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
        val pending = async { model.commitArrangement(listOf(draft), emptyList()) }
        runCurrent()

        pending.cancel()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertFalse(committed)
    }

    @Test
    fun `clear cancels an in-flight arrangement while observation and future commits remain usable`() = runTest(dispatcher) {
        val tasks = FakeTaskRepository()
        val gate = CompletableDeferred<Unit>()
        var committed = 0
        val model = TaskViewModel(tasks, ArrangementCommitRepository { _, _ ->
            gate.await()
            committed += 1
        })
        val draft = TaskDraft("new", "报告", "报告", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
        withCollected(model.uiState) {
            val pending = async { model.commitArrangement(listOf(draft), emptyList()) }
            runCurrent()
            assertFalse(pending.isCompleted)

            model.cancelPendingMutations()
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertTrue(pending.isCancelled)
            assertEquals(0, committed)
            tasks.tasks.value = listOf(task("after-clear"))
            runCurrent()
            assertEquals("after-clear", model.uiState.value.tasks.single().id)
            assertTrue(model.commitArrangement(listOf(draft), emptyList()))
            assertEquals(1, committed)
        }
    }

    @Test
    fun `clear cancels a pending plan write without losing the draft or stopping plan observation`() = runTest(dispatcher) {
        val backing = FakePlanRepository()
        val gate = CompletableDeferred<Unit>()
        val repository = object : PlanRepository by backing {
            override suspend fun accept(draft: com.swan1127.repland.domain.model.PlanDraft) {
                gate.await()
                backing.accept(draft)
            }
        }
        val model = PlanViewModel(repository, FakePlanDraftGenerator())
        model.showAgentDraft(planDraft())
        withCollected(model.uiState) {
            model.acceptDraft()
            runCurrent()
            model.cancelPendingMutations()
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(backing.acceptedDrafts.isEmpty())
            assertTrue(model.uiState.value.draft != null)
            model.acceptDraft()
            advanceUntilIdle()
            assertEquals(1, backing.acceptedDrafts.size)
            assertEquals(null, model.uiState.value.draft)
        }
    }
}
