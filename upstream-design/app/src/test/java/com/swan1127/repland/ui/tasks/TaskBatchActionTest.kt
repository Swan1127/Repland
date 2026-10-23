package com.swan1127.repland.ui.tasks

import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.testkit.FakeTaskRepository
import com.swan1127.repland.testkit.ViewModelTestBase
import com.swan1127.repland.testkit.collectInBackground
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of batch operations that is not a pure function: the loop that writes.
 *
 * `BatchTasks.targets` decides *which* rows, and `TaskSelectionTest` pins that. What is left here is
 * the mapping from that list to repository calls, plus the one thing a pure function cannot express
 * — that the list is read at the moment of the write rather than taken from the screen.
 *
 * `FakeTaskRepository` records calls and does not enforce `TaskLifecycleValidator`; the real
 * `RoomTaskRepository` does, with a `require`. So these tests assert *which* calls are made, and the
 * reason the rule matters — a rejected write aborts the loop half-applied — is argued in
 * `TaskViewModel.confirmStatuses`, not demonstrated by the fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskBatchActionTest : ViewModelTestBase() {

    private val repository = FakeTaskRepository()

    private fun seed(vararg tasks: com.swan1127.repland.domain.model.Task) {
        repository.tasks.value = tasks.toList()
    }

    // ------------------------------------------------------------------ what gets written

    @Test
    fun `every selected row that can take the action is written once`() = runTest(dispatcher) {
        seed(task("t1"), task("t2"), task("t3"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1", "t3")))
        advanceUntilIdle()

        // The status is asserted here, not just the ids: the other test that pins the status uses
        // CANCEL, so a loop that hard-coded one status would still satisfy it for that one action.
        assertEquals(
            listOf(
                Triple("t1", TaskStatus.COMPLETED, TaskFeedback()),
                Triple("t3", TaskStatus.COMPLETED, TaskFeedback()),
            ),
            repository.statusChanges,
        )
        job.cancel()
    }

    @Test
    fun `the batch writes the action's status and an empty feedback`() = runTest(dispatcher) {
        // Batch feedback is deliberately empty: a postpone reason or a completion note is per-task
        // evidence, and there is no one text that is true of three rows at once. The single-row
        // flows keep their editors.
        seed(task("t1"), task("t2"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.CANCEL, TaskSelection(setOf("t1", "t2")))
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple("t1", TaskStatus.CANCELLED, TaskFeedback()),
                Triple("t2", TaskStatus.CANCELLED, TaskFeedback()),
            ),
            repository.statusChanges,
        )
        job.cancel()
    }

    @Test
    fun `a restore batch writes NOT_STARTED, not the completion status`() = runTest(dispatcher) {
        // The third action, and the reason it needs its own test: with only COMPLETE and CANCEL
        // pinned, a loop that hard-coded COMPLETED would still satisfy the COMPLETE case, and a
        // CANCEL whose status had been swapped would still satisfy nothing — RESTORE is the action
        // whose status is furthest from the other two, so it is the one that can tell them apart.
        seed(task("t1", status = TaskStatus.CANCELLED), task("t2", status = TaskStatus.COMPLETED))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.RESTORE, TaskSelection(setOf("t1", "t2")))
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple("t1", TaskStatus.NOT_STARTED, TaskFeedback()),
                Triple("t2", TaskStatus.NOT_STARTED, TaskFeedback()),
            ),
            repository.statusChanges,
        )
        job.cancel()
    }

    @Test
    fun `a row the rule forbids is never written`() = runTest(dispatcher) {
        // The case the whole design exists for: 已完成 lists replaced tasks, so a 恢复 over that view
        // can be asked to write to one. It must not be asked of the repository at all, because the
        // real repository answers with a `require` failure and the loop would stop there.
        seed(task("t1", status = TaskStatus.REPLACED), task("t2", status = TaskStatus.CANCELLED))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.RESTORE, TaskSelection(setOf("t1", "t2")))
        advanceUntilIdle()

        assertEquals(listOf("t2"), repository.statusChanges.map { it.first })
        job.cancel()
    }

    @Test
    fun `nothing outside the selection is written`() = runTest(dispatcher) {
        seed(task("t1"), task("t2"), task("t3"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t2")))
        advanceUntilIdle()

        assertEquals(listOf("t2"), repository.statusChanges.map { it.first })
        job.cancel()
    }

    @Test
    fun `an empty selection writes nothing`() = runTest(dispatcher) {
        seed(task("t1"), task("t2"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection.EMPTY)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.statusChanges)
        job.cancel()
    }

    @Test
    fun `an id that is not in the list writes nothing`() = runTest(dispatcher) {
        // A stale selection — the row was deleted between the tick and the confirm — must be a
        // no-op rather than a write against an id the repository no longer knows.
        seed(task("t1"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("gone")))
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), repository.statusChanges)
        job.cancel()
    }

    @Test
    fun `the writes follow the order of the list`() = runTest(dispatcher) {
        seed(task("t3"), task("t1"), task("t2"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1", "t2", "t3")))
        advanceUntilIdle()

        assertEquals(listOf("t3", "t1", "t2"), repository.statusChanges.map { it.first })
        job.cancel()
    }

    // ------------------------------------------------------------------ the list is read now, not then

    @Test
    fun `the eligibility is decided against the list as it is at the write`() = runTest(dispatcher) {
        // The race the re-derivation exists for, made observable. The selection was made while t1
        // was active; by the time the batch is confirmed the list says t1 has been replaced. A
        // version that trusted the ids it was handed would write to t1 and rely on the repository to
        // refuse; this one simply does not include it.
        seed(task("t1"), task("t2"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()
        val selection = TaskSelection(setOf("t1", "t2"))

        seed(task("t1", status = TaskStatus.REPLACED), task("t2"))
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, selection)
        advanceUntilIdle()

        assertEquals(listOf("t2"), repository.statusChanges.map { it.first })
        job.cancel()
    }

    @Test
    fun `a ghost id beside a real one is dropped rather than invented`() = runTest(dispatcher) {
        seed(task("t1"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1", "t2")))
        advanceUntilIdle()

        assertEquals(listOf("t1"), repository.statusChanges.map { it.first })
        job.cancel()
    }

    // ------------------------------------------------------------------ failure reporting

    @Test
    fun `a failed batch reports INVALID_LIFECYCLE_INPUT`() = runTest(dispatcher) {
        seed(task("t1"))
        repository.failWith = IllegalStateException("bad transition")
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1")))
        advanceUntilIdle()

        assertEquals(TaskError.INVALID_LIFECYCLE_INPUT, viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `a successful batch clears a previous error`() = runTest(dispatcher) {
        seed(task("t1"))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        repository.failWith = IllegalStateException("bad transition")
        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1")))
        advanceUntilIdle()
        assertEquals(TaskError.INVALID_LIFECYCLE_INPUT, viewModel.uiState.value.error)

        repository.failWith = null
        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1")))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `a batch that writes nothing leaves no error`() = runTest(dispatcher) {
        // An empty batch is not a failure. It is the normal result of a selection the rule rejects
        // entirely, and reporting it would put an error banner over a screen where nothing happened.
        seed(task("t1", status = TaskStatus.REPLACED))
        val viewModel = TaskViewModel(repository)
        val job = collectInBackground(viewModel.uiState)
        advanceUntilIdle()

        viewModel.confirmStatuses(BatchTaskAction.COMPLETE, TaskSelection(setOf("t1")))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(repository.statusChanges.isEmpty())
        job.cancel()
    }
}
