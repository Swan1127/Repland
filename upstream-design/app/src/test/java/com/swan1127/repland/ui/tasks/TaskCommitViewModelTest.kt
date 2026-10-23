package com.swan1127.repland.ui.tasks

import com.swan1127.repland.domain.model.ArrangementConflictException
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.ports.ArrangementCommitRepository
import com.swan1127.repland.testkit.FakeTaskRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.withCollected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCommitViewModelTest : ViewModelTestBase() {
    private val tasks = FakeTaskRepository()
    private val draft = TaskDraft(
        id = "proposal-1",
        displayName = "报告",
        description = "完成报告",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 1,
        totalDurationMinutes = 30,
        dueDate = null,
    )
    private val placement = TaskPlacement("proposal-1", TEST_MONDAY, 540, 570)

    @Test
    fun `arrangement success is returned only after the entire commit finishes`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        var submittedDrafts = emptyList<TaskDraft>()
        var submittedPlacements = emptyList<TaskPlacement>()
        val viewModel = TaskViewModel(tasks, ArrangementCommitRepository { drafts, placements ->
            submittedDrafts = drafts
            submittedPlacements = placements
            finish.await()
        })

        val result = async { viewModel.commitArrangement(listOf(draft), listOf(placement)) }
        runCurrent()

        assertFalse(result.isCompleted)
        assertEquals(listOf(draft), submittedDrafts)
        assertEquals(listOf(placement), submittedPlacements)
        assertTrue(tasks.savedDrafts.isEmpty())
        finish.complete(Unit)
        assertTrue(result.await())
    }

    @Test
    fun `failed arrangement returns false and can be retried with the original preview`() = runTest(dispatcher) {
        var attempts = 0
        val viewModel = TaskViewModel(tasks, ArrangementCommitRepository { _, _ ->
            attempts += 1
            if (attempts == 1) error("disk unavailable")
        })
        withCollected(viewModel.uiState) {
            assertFalse(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)

            assertTrue(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertEquals(2, attempts)
            assertNull(viewModel.uiState.value.error)
        }
    }

    @Test
    fun `arrangement conflict is actionable while other failures are private and success clears it`() = runTest(dispatcher) {
        var attempts = 0
        val viewModel = TaskViewModel(tasks, ArrangementCommitRepository { _, _ ->
            when (++attempts) {
                1, 3, 5 -> throw ArrangementConflictException()
                2 -> error("SQLITE_BUSY: private database path and SQL statement")
                else -> Unit
            }
        })
        withCollected(viewModel.uiState) {
            assertFalse(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertEquals(ArrangementConflictException.USER_MESSAGE, viewModel.uiState.value.arrangementFailureMessage)
            assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)

            assertFalse(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.arrangementFailureMessage)
            assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)

            assertFalse(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertEquals(ArrangementConflictException.USER_MESSAGE, viewModel.uiState.value.arrangementFailureMessage)

            assertTrue(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.arrangementFailureMessage)
            assertNull(viewModel.uiState.value.error)

            assertFalse(viewModel.commitArrangement(listOf(draft), listOf(placement)))
            advanceUntilIdle()
            assertEquals(ArrangementConflictException.USER_MESSAGE, viewModel.uiState.value.arrangementFailureMessage)
            viewModel.cancelPendingMutations()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.arrangementFailureMessage)
        }
    }

    @Test
    fun `invalid arrangement never calls the commit repository`() = runTest(dispatcher) {
        var called = false
        val viewModel = TaskViewModel(tasks, ArrangementCommitRepository { _, _ -> called = true })

        assertFalse(viewModel.commitArrangement(listOf(draft.copy(displayName = "")), listOf(placement)))
        assertFalse(called)
    }

    @Test
    fun `cancelling a commit propagates cancellation to its caller`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(tasks, ArrangementCommitRepository { _, _ ->
            throw CancellationException("preview closed")
        })

        var wasCancelled = false
        try {
            viewModel.commitArrangement(listOf(draft), listOf(placement))
        } catch (_: CancellationException) {
            wasCancelled = true
        }
        assertTrue(wasCancelled)
    }

    @Test
    fun `recording a focus round writes feedback without completing the task`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(tasks)

        assertTrue(viewModel.commitFocusRound("task-1", 25))

        assertTrue(tasks.statusChanges.isEmpty())
        assertEquals("task-1", tasks.recordedFeedback.single().first)
        assertEquals(25, tasks.recordedFeedback.single().second.actualDurationMinutes)
        assertEquals("focus_round", tasks.recordedFeedback.single().second.completionResult)
    }

    @Test
    fun `a separately confirmed focus completion changes the task lifecycle`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(tasks)

        assertTrue(viewModel.commitFocusRound("task-1", 25, completeTask = true))

        assertTrue(tasks.recordedFeedback.isEmpty())
        assertEquals(TaskStatus.COMPLETED, tasks.statusChanges.single().second)
        assertEquals(25, tasks.statusChanges.single().third.actualDurationMinutes)
    }

    @Test
    fun `focus write failure returns false instead of acknowledging a saved round`() = runTest(dispatcher) {
        tasks.failWith = IllegalStateException("disk unavailable")
        val viewModel = TaskViewModel(tasks)

        assertFalse(viewModel.commitFocusRound("task-1", 25))
        assertTrue(tasks.recordedFeedback.isEmpty())
    }
}
