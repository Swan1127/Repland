package com.swan1127.repland.ui.tasks

import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TEST_MONDAY
import com.swan1127.repland.domain.model.feedback
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.testkit.FakeTaskRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import com.swan1127.repland.domain.ports.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest : ViewModelTestBase() {

    private val repository = FakeTaskRepository()

    private fun draft(
        displayName: String = "写实验报告",
        estimatedDays: Int = 2,
        totalDurationMinutes: Int? = 90,
    ) = TaskDraft(
        displayName = displayName,
        description = "描述",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = estimatedDays,
        totalDurationMinutes = totalDurationMinutes,
        dueDate = TEST_MONDAY,
    )

    @Test
    fun `before collection the state is empty, loading and error free`() = runTest(dispatcher) {
        repository.tasks.value = listOf(task("t1"))

        val viewModel = TaskViewModel(repository)

        assertEquals(emptyList<Any>(), viewModel.uiState.value.tasks)
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `collecting reports the stored tasks and stops loading`() = runTest(dispatcher) {
        repository.tasks.value = listOf(task("t1"), task("t2"))
        val viewModel = TaskViewModel(repository)

        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(listOf("t1", "t2"), viewModel.uiState.value.tasks.map { it.id })
        assertEquals(false, viewModel.uiState.value.isLoading)
        job.cancel()
    }

    // --- saveTask: the validator gate ---------------------------------------------------------

    @Test
    fun `a blank name is refused and never reaches the repository`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.saveTask(draft(displayName = "   "))
        advanceUntilIdle()

        assertEquals(emptyList<TaskDraft>(), repository.savedDrafts)
        assertEquals(TaskError.INVALID_DRAFT, viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `zero estimated days is refused`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.saveTask(draft(estimatedDays = 0))
        advanceUntilIdle()

        assertEquals(emptyList<TaskDraft>(), repository.savedDrafts)
    }

    @Test
    fun `estimated days over the cap is refused`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.saveTask(draft(estimatedDays = 31))
        advanceUntilIdle()

        assertEquals(emptyList<TaskDraft>(), repository.savedDrafts)
    }

    @Test
    fun `a duration over one day is refused`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.saveTask(draft(totalDurationMinutes = 1_441))
        advanceUntilIdle()

        assertEquals(emptyList<TaskDraft>(), repository.savedDrafts)
    }

    @Test
    fun `a null duration is accepted, because duration is optional`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.saveTask(draft(totalDurationMinutes = null))
        advanceUntilIdle()

        assertEquals(1, repository.savedDrafts.size)
        assertNull(repository.savedDrafts.single().totalDurationMinutes)
    }

    @Test
    fun `a valid draft is forwarded unchanged and leaves no error`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()
        val valid = draft()

        viewModel.saveTask(valid)
        advanceUntilIdle()

        assertEquals(listOf(valid), repository.savedDrafts)
        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `a save failure reports SAVE_FAILED`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("disk full")
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.saveTask(draft())
        advanceUntilIdle()

        assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `a successful save clears a previous failure`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("disk full")
        viewModel.saveTask(draft())
        advanceUntilIdle()
        assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)

        repository.failWith = null
        viewModel.saveTask(draft())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `awaited save returns only after the repository finishes`() = runTest(dispatcher) {
        val releaseSave = CompletableDeferred<Unit>()
        val gatedRepository = object : TaskRepository by repository {
            override suspend fun save(draft: TaskDraft) {
                releaseSave.await()
                repository.save(draft)
            }
        }
        val viewModel = TaskViewModel(gatedRepository)
        val valid = draft()

        val saving = async { viewModel.saveTaskAwait(valid) }
        advanceUntilIdle()

        assertFalse(saving.isCompleted)
        assertTrue(repository.savedDrafts.isEmpty())

        releaseSave.complete(Unit)
        assertTrue(saving.await())
        assertEquals(listOf(valid), repository.savedDrafts)
    }

    @Test
    fun `awaited save rejects an invalid draft without writing`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val collector = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        assertFalse(viewModel.saveTaskAwait(draft(displayName = " ")))
        advanceUntilIdle()

        assertTrue(repository.savedDrafts.isEmpty())
        assertEquals(TaskError.INVALID_DRAFT, viewModel.uiState.value.error)
        collector.cancel()
    }

    @Test
    fun `awaited save reports a failure and a successful retry clears it`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val collector = collectInBackground(viewModel.uiState)
        advanceUntilIdle()
        repository.failWith = IllegalStateException("disk full")

        assertFalse(viewModel.saveTaskAwait(draft()))
        advanceUntilIdle()
        assertTrue(repository.savedDrafts.isEmpty())
        assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)

        repository.failWith = null
        assertTrue(viewModel.saveTaskAwait(draft()))
        advanceUntilIdle()
        assertEquals(1, repository.savedDrafts.size)
        assertNull(viewModel.uiState.value.error)
        collector.cancel()
    }

    @Test
    fun `awaited save propagates cancellation without reporting a save failure`() = runTest(dispatcher) {
        val cancellation = CancellationException("screen closed")
        repository.failWith = cancellation
        val viewModel = TaskViewModel(repository)
        val collector = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        try {
            viewModel.saveTaskAwait(draft())
            fail("Cancellation must reach the caller")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        advanceUntilIdle()

        assertTrue(repository.savedDrafts.isEmpty())
        assertNull(viewModel.uiState.value.error)
        collector.cancel()
    }

    // --- lifecycle transitions -----------------------------------------------------------------

    @Test
    fun `startTask confirms IN_PROGRESS with empty feedback`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.startTask("t1")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("t1", TaskStatus.IN_PROGRESS, TaskFeedback())),
            repository.statusChanges,
        )
    }

    @Test
    fun `postponeTask carries the reason into the feedback`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.postponeTask("t1", "等资料")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("t1", TaskStatus.POSTPONED, TaskFeedback(postponeReason = "等资料"))),
            repository.statusChanges,
        )
    }

    @Test
    fun `postponeTask accepts a null reason`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.postponeTask("t1", null)
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("t1", TaskStatus.POSTPONED, TaskFeedback())),
            repository.statusChanges,
        )
    }

    @Test
    fun `cancelTask confirms CANCELLED`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.cancelTask("t1")
        advanceUntilIdle()

        assertEquals(TaskStatus.CANCELLED, repository.statusChanges.single().second)
    }

    @Test
    fun `restoreTask confirms NOT_STARTED`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.restoreTask("t1")
        advanceUntilIdle()

        assertEquals(TaskStatus.NOT_STARTED, repository.statusChanges.single().second)
    }

    @Test
    fun `completeTask with no arguments sends COMPLETED and an empty feedback`() =
        runTest(dispatcher) {
            val viewModel = TaskViewModel(repository)

            viewModel.completeTask("t1")
            advanceUntilIdle()

            assertEquals(
                listOf(Triple("t1", TaskStatus.COMPLETED, TaskFeedback())),
                repository.statusChanges,
            )
        }

    @Test
    fun `completeTask carries every supplied feedback field`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.completeTask(
            taskId = "t1",
            completedContent = "写完第三章",
            completionResult = "结论支持假设",
            actualDurationMinutes = 75,
            progressPercent = 100,
        )
        advanceUntilIdle()

        val (id, status, sentFeedback) = repository.statusChanges.single()
        assertEquals("t1", id)
        assertEquals(TaskStatus.COMPLETED, status)
        assertEquals("写完第三章", sentFeedback.completedContent)
        assertEquals("结论支持假设", sentFeedback.completionResult)
        assertEquals(75, sentFeedback.actualDurationMinutes)
        assertEquals(100, sentFeedback.progressPercent)
        assertEquals(null, sentFeedback.postponeReason)
    }

    @Test
    fun `a lifecycle failure reports INVALID_LIFECYCLE_INPUT rather than SAVE_FAILED`() =
        runTest(dispatcher) {
            repository.failWith = IllegalStateException("bad transition")
            val viewModel = TaskViewModel(repository)
            val job = collectInBackground(viewModel.uiState)
            advanceUntilIdle()

            viewModel.startTask("t1")
            advanceUntilIdle()

            // The two failure paths must not be conflated: a failed status change is not a failed
            // save, and the screen wording differs.
            assertEquals(TaskError.INVALID_LIFECYCLE_INPUT, viewModel.uiState.value.error)
            job.cancel()
        }

    @Test
    fun `a successful lifecycle change clears a previous error`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("bad transition")
        viewModel.startTask("t1")
        advanceUntilIdle()
        assertEquals(TaskError.INVALID_LIFECYCLE_INPUT, viewModel.uiState.value.error)

        repository.failWith = null
        viewModel.completeTask("t1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    // --- pass-through actions ------------------------------------------------------------------

    @Test
    fun `recordPartialCompletion forwards the task and feedback`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val sent = feedback(progressPercent = 40)

        viewModel.recordPartialCompletion("t1", sent)
        advanceUntilIdle()

        assertEquals(listOf("t1" to sent), repository.partialCompletions)
    }

    @Test
    fun `recordFeedback forwards the task and feedback`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val sent = feedback(actualDurationMinutes = 30)

        viewModel.recordFeedback("t1", sent)
        advanceUntilIdle()

        assertEquals(listOf("t1" to sent), repository.recordedFeedback)
    }

    @Test
    fun `correctExecutionLog forwards the task, the corrected log and the feedback`() =
        runTest(dispatcher) {
            val viewModel = TaskViewModel(repository)
            val sent = feedback(completedContent = "更正为两章")

            viewModel.correctExecutionLog("t1", "log-7", sent)
            advanceUntilIdle()

            assertEquals(
                listOf(Triple("t1", "log-7", sent)),
                repository.corrections,
            )
        }

    @Test
    fun `replaceTask forwards the replacement draft`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)
        val replacement = draft(displayName = "换成新的做法")

        viewModel.replaceTask("t1", replacement)
        advanceUntilIdle()

        assertEquals(listOf("t1" to replacement), repository.replacements)
    }

    @Test
    fun `a failed pass-through action reports INVALID_LIFECYCLE_INPUT`() = runTest(dispatcher) {
        repository.failWith = IllegalStateException("nope")
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.recordFeedback("t1", feedback(progressPercent = 10))
        advanceUntilIdle()

        assertEquals(TaskError.INVALID_LIFECYCLE_INPUT, viewModel.uiState.value.error)
        job.cancel()
    }

    // --- observation delegation ----------------------------------------------------------------

    @Test
    fun `observeExecutionLogs delegates the task id to the repository`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.observeExecutionLogs("t9")

        assertEquals(listOf("t9"), repository.observedLogTaskIds)
    }

    @Test
    fun `observeExecutionLogsForDate delegates the date to the repository`() = runTest(dispatcher) {
        val viewModel = TaskViewModel(repository)

        viewModel.observeExecutionLogsForDate(TEST_MONDAY)

        assertEquals(listOf(TEST_MONDAY), repository.observedLogDates)
    }

    @Test
    fun `a throwing observation stream reports SAVE_FAILED and stops loading`() = runTest(dispatcher) {
        repository.observeFlow = flow { throw IllegalStateException("read failed") }
        val viewModel = TaskViewModel(repository)

        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(TaskError.SAVE_FAILED, viewModel.uiState.value.error)
        job.cancel()
    }
}
