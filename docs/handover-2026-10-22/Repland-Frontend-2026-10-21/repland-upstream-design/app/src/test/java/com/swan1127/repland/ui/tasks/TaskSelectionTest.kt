package com.swan1127.repland.ui.tasks

import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of batch operations: which ids are ticked, and which of them a given action would
 * touch.
 *
 * Everything here is a plain function over data — no Context, no Compose, no coroutine — because
 * the two decisions that matter (does this row leave the selection when the filter changes; does
 * this row accept this status change) are decidable without a screen. The half that is not
 * decidable that way is the write loop in `TaskViewModel.confirmStatuses`, which is a straight-line
 * `forEach` and is covered by `TaskBatchActionTest`.
 */
class TaskSelectionTest {

    private val visible = listOf(task("t1"), task("t2"), task("t3"))

    // ------------------------------------------------------------------ the selection itself

    @Test
    fun `EMPTY holds nothing`() {
        assertEquals(0, TaskSelection.EMPTY.size)
        assertTrue(TaskSelection.EMPTY.isEmpty)
    }

    @Test
    fun `toggle adds a row that was not there`() {
        val selection = TaskSelection.EMPTY.toggle("t2")

        assertEquals(1, selection.size)
        assertTrue(selection.contains("t2"))
    }

    @Test
    fun `toggling the same row twice leaves nothing`() {
        // The single gesture that both adds and removes is the whole reason `toggle` exists; if it
        // only ever added, the user could enter the mode and never leave it by tapping.
        val selection = TaskSelection.EMPTY.toggle("t2").toggle("t2")

        assertTrue(selection.isEmpty)
    }

    @Test
    fun `toggling one row does not disturb another`() {
        val selection = TaskSelection.EMPTY.toggle("t1").toggle("t2").toggle("t1")

        assertEquals(setOf("t2"), selection.ids)
    }

    @Test
    fun `contains is false for a row that was never added`() {
        assertFalse(TaskSelection(setOf("t1")).contains("t9"))
    }

    @Test
    fun `contains is true for a row that was added`() {
        // The positive direction. Without it, `contains` returning `false` unconditionally passes the
        // test above — and every target list in this file would be empty for the same reason.
        assertTrue(TaskSelection(setOf("t1")).contains("t1"))
    }

    @Test
    fun `allOf takes every row on screen`() {
        assertEquals(setOf("t1", "t2", "t3"), TaskSelection.EMPTY.allOf(visible).ids)
    }

    @Test
    fun `allOf ignores what was selected before, because it is a select-all`() {
        // Not "add the rest": a 全选 that merged would make the button mean something different the
        // second time it was pressed, and there would be no way to say "these three and only these".
        val selection = TaskSelection(setOf("t1")).allOf(visible)

        assertEquals(setOf("t1", "t2", "t3"), selection.ids)
    }

    @Test
    fun `allOf over an empty view is empty`() {
        assertTrue(TaskSelection(setOf("t1")).allOf(emptyList()).isEmpty)
    }

    @Test
    fun `retained keeps the rows still on screen`() {
        val selection = TaskSelection(setOf("t1", "t3")).retained(visible)

        assertEquals(setOf("t1", "t3"), selection.ids)
    }

    @Test
    fun `retained drops a row that left the view`() {
        // The invariant the whole file rests on: `selection ⊆ visible`. A tick surviving a filter
        // change would let the action bar write to a row the user cannot see, and the count would be
        // reporting a set the screen is not showing.
        val selection = TaskSelection(setOf("t1", "t9")).retained(visible)

        assertEquals(setOf("t1"), selection.ids)
    }

    @Test
    fun `retained against an empty view empties the selection`() {
        assertTrue(TaskSelection(setOf("t1", "t2")).retained(emptyList()).isEmpty)
    }

    // ------------------------------------------------------------------ which rows an action touches

    @Test
    fun `targets keeps the selected rows that can take the action`() {
        val selection = TaskSelection(setOf("t1", "t3"))

        assertEquals(
            listOf("t1", "t3"),
            BatchTasks.targets(BatchTaskAction.COMPLETE, selection, visible).map { it.id },
        )
    }

    @Test
    fun `targets never reaches a row that is not selected`() {
        // Selection is a set of ids, so "which rows" is answered against the list, not against the
        // ids: a stray id in the selection must not conjure a row that is not there.
        val selection = TaskSelection(setOf("t1", "ghost"))

        assertEquals(
            listOf("t1"),
            BatchTasks.targets(BatchTaskAction.COMPLETE, selection, visible).map { it.id },
        )
    }

    @Test
    fun `an empty selection has no targets for any action`() {
        val targets = BatchTaskAction.entries.map { action ->
            action to BatchTasks.targets(action, TaskSelection.EMPTY, visible)
        }

        assertEquals(
            "an action found rows to write with nothing ticked",
            emptyList<Pair<BatchTaskAction, List<String>>>(),
            targets.filter { (_, rows) -> rows.isNotEmpty() },
        )
    }

    @Test
    fun `targets keeps the order the list is in`() {
        // The confirm dialog names rows in the order the screen shows them; a set-ordered result
        // would make the dialog's wording depend on hash order.
        val selection = TaskSelection(setOf("t3", "t1", "t2"))

        assertEquals(
            listOf("t1", "t2", "t3"),
            BatchTasks.targets(BatchTaskAction.CANCEL, selection, visible).map { it.id },
        )
    }

    @Test
    fun `a replaced row is not a target of any action`() {
        // The catch-all branch of TaskLifecycleValidator, and the one that reaches the user: 已完成
        // lists every non-active task, replaced ones included, so the 恢复 button there is offered
        // over a selection that can contain rows it must not touch.
        val replaced = listOf(task("t1", status = TaskStatus.REPLACED))
        val offenders = BatchTaskAction.entries.filter { action ->
            BatchTasks.targets(action, TaskSelection(setOf("t1")), replaced).isNotEmpty()
        }

        assertEquals(
            "actions that would write to a replaced task",
            emptyList<BatchTaskAction>(),
            offenders,
        )
    }

    @Test
    fun `a finished row can only be restored`() {
        val finished = listOf(task("t1", status = TaskStatus.COMPLETED))
        val selection = TaskSelection(setOf("t1"))

        assertEquals(listOf("t1"), BatchTasks.targets(BatchTaskAction.RESTORE, selection, finished).map { it.id })
        assertTrue(BatchTasks.targets(BatchTaskAction.COMPLETE, selection, finished).isEmpty())
        assertTrue(BatchTasks.targets(BatchTaskAction.CANCEL, selection, finished).isEmpty())
    }

    @Test
    fun `an active row can be completed or cancelled but not restored`() {
        val active = listOf(task("t1", status = TaskStatus.IN_PROGRESS))
        val selection = TaskSelection(setOf("t1"))

        assertEquals(listOf("t1"), BatchTasks.targets(BatchTaskAction.COMPLETE, selection, active).map { it.id })
        assertEquals(listOf("t1"), BatchTasks.targets(BatchTaskAction.CANCEL, selection, active).map { it.id })
        assertTrue(BatchTasks.targets(BatchTaskAction.RESTORE, selection, active).isEmpty())
    }

    // ------------------------------------------------------------------ which actions to offer

    @Test
    fun `an active selection offers complete and cancel, not restore`() {
        val selection = TaskSelection(setOf("t1", "t2"))

        assertEquals(
            listOf(BatchTaskAction.COMPLETE, BatchTaskAction.CANCEL),
            BatchTasks.actionsFor(selection, visible),
        )
    }

    @Test
    fun `a finished selection offers restore alone`() {
        val finished = listOf(task("t1", status = TaskStatus.COMPLETED))

        assertEquals(
            listOf(BatchTaskAction.RESTORE),
            BatchTasks.actionsFor(TaskSelection(setOf("t1")), finished),
        )
    }

    @Test
    fun `a replaced-only selection offers nothing at all`() {
        // No button rather than a disabled one: `actionsFor` answers "what can be done", and the
        // answer here is nothing, so the action bar renders empty instead of offering a 恢复 whose
        // confirm dialog would say "0 tasks".
        val replaced = listOf(task("t1", status = TaskStatus.REPLACED))

        assertEquals(
            emptyList<BatchTaskAction>(),
            BatchTasks.actionsFor(TaskSelection(setOf("t1")), replaced),
        )
    }

    @Test
    fun `a mixed selection offers an action when even one row can take it`() {
        // The count and the button list are derived from the same call, so a single eligible row is
        // enough to show the button — and the dialog then reports the rows it will skip.
        val mixed = listOf(
            task("t1", status = TaskStatus.REPLACED),
            task("t2", status = TaskStatus.CANCELLED),
        )

        assertEquals(
            listOf(BatchTaskAction.RESTORE),
            BatchTasks.actionsFor(TaskSelection(setOf("t1", "t2")), mixed),
        )
    }

    @Test
    fun `an empty selection offers no actions`() {
        assertEquals(emptyList<BatchTaskAction>(), BatchTasks.actionsFor(TaskSelection.EMPTY, visible))
    }

    @Test
    fun `actions keep the declaration order`() {
        // The bar lays its buttons out in this order, so the enum is the layout: 完成 before 取消 is
        // the same reading as the single-row action list, and a set-based implementation would
        // shuffle them per run.
        val all = listOf(
            task("t1", status = TaskStatus.NOT_STARTED),
            task("t2", status = TaskStatus.CANCELLED),
        )

        assertEquals(
            BatchTaskAction.entries.toList(),
            BatchTasks.actionsFor(TaskSelection(setOf("t1", "t2")), all),
        )
    }

    // ------------------------------------------------------------------ the actions themselves

    @Test
    fun `each action carries the status it writes`() {
        // The status is what the repository is asked to confirm, and the label is what the user
        // reads; if the two came apart the button would say one thing and do another. Asserted
        // against the literal statuses rather than against `BatchTasks`, so a wrong mapping in the
        // enum cannot be confirmed by the code under test.
        assertEquals(TaskStatus.COMPLETED, BatchTaskAction.COMPLETE.status)
        assertEquals(TaskStatus.CANCELLED, BatchTaskAction.CANCEL.status)
        assertEquals(TaskStatus.NOT_STARTED, BatchTaskAction.RESTORE.status)
    }

    @Test
    fun `every action is reachable from some selection`() {
        // Anti-vacuity for the three tests above: an enum entry no selection can ever offer is a
        // button that does not exist, and the guards around it would be checking dead code.
        val reachable = BatchTaskAction.entries.filter { action ->
            val rows = listOf(
                task("active", status = TaskStatus.IN_PROGRESS),
                task("finished", status = TaskStatus.COMPLETED),
            )
            BatchTasks.actionsFor(TaskSelection(setOf("active", "finished")), rows).contains(action)
        }

        assertEquals(BatchTaskAction.entries.toList(), reachable)
    }
}
