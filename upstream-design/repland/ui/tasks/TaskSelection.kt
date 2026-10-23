package com.swan1127.repland.ui.tasks

import androidx.annotation.StringRes
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskLifecycleValidator
import com.swan1127.repland.domain.model.TaskStatus

/**
 * The rows the user has ticked in the task list.
 *
 * A set of **ids** rather than a set of [Task]s, because the repository re-emits the list on every
 * write: holding the objects would pin stale copies, and the row the user ticked would stop being
 * the row the database has.
 *
 * **The one invariant, and where it comes from.** A selection is only ever built out of rows that
 * are on screen: [allOf] takes the visible list, [toggle] adds a row the user just tapped, and
 * [retained] drops anything that has left the view. So `selection ⊆ visible` holds *by
 * construction*, which is what makes "batch-act on something you cannot see" impossible rather than
 * merely unlikely — and it is why the count on the action bar can be trusted.
 */
@JvmInline
value class TaskSelection(val ids: Set<String>) {
    val size: Int get() = ids.size
    val isEmpty: Boolean get() = ids.isEmpty()

    fun contains(id: String): Boolean = id in ids

    /** Ticking a row that is already ticked unticks it — the only way one row leaves the selection. */
    fun toggle(id: String): TaskSelection =
        TaskSelection(if (id in ids) ids - id else ids + id)

    /**
     * Every row currently on screen — deliberately not "every task", because the list is filtered.
     *
     * Ignores the current ids rather than adding to them: the button that calls this says "全选",
     * and with [retained] already guaranteeing `selection ⊆ visible` the two are the same set, so
     * the simpler reading is the honest one.
     */
    fun allOf(visible: List<Task>): TaskSelection =
        TaskSelection(visible.mapTo(mutableSetOf(), Task::id))

    /**
     * The part of this selection that is still on screen.
     *
     * Applied against whatever the current filter shows, so switching filters shrinks the selection
     * to what the user can still see — possibly to nothing. An empty selection does not end
     * selection mode: the mode is what the screen is *doing*, the selection is what it *holds*, and
     * conflating them would make "entered the mode, have not ticked anything yet" unrepresentable.
     * The action bar therefore stays up with its buttons disabled, which is the honest reading of
     * "nothing to act on".
     */
    fun retained(visible: List<Task>): TaskSelection {
        val visibleIds = visible.mapTo(mutableSetOf(), Task::id)
        return TaskSelection(ids intersect visibleIds)
    }

    companion object {
        val EMPTY: TaskSelection = TaskSelection(emptySet())
    }
}

/**
 * A lifecycle change the user can apply to a whole selection.
 *
 * [status] is the confirmation to write and [labelRes] is its button text. They travel together
 * because the eligibility rule and the wording have to describe the same thing.
 */
enum class BatchTaskAction(
    val status: TaskStatus,
    @param:StringRes val labelRes: Int,
) {
    COMPLETE(TaskStatus.COMPLETED, R.string.batch_action_complete),
    CANCEL(TaskStatus.CANCELLED, R.string.batch_action_cancel),
    RESTORE(TaskStatus.NOT_STARTED, R.string.batch_action_restore),
}

/**
 * Which rows a batch action would touch, and which actions are worth offering.
 *
 * **Eligibility is not invented here.** [TaskLifecycleValidator.isValidStatusConfirmation] already
 * states which status changes the app accepts, and `RoomTaskRepository.confirmStatus` *enforces*
 * it with a `require`. Asking the same question rather than re-deriving it is what makes a batch
 * write incapable of being rejected: a second hand-written rule would eventually disagree with the
 * one the repository checks, and the failure would be a half-applied batch whose remaining writes
 * throw and are swallowed into an error flag — the user sees some rows change and no explanation.
 *
 * The rule that matters most falls straight out of that reuse: `REPLACED → anything` is `false`.
 * A task that has been superseded cannot be restored, so the 已完成 view — the one place a REPLACED
 * row is still listed, since it shows every non-active task — offers a 恢复 whose target count is
 * genuinely smaller than the selection. That gap is the reason [targets] and the selection count are
 * two different numbers, and the reason the confirm dialog reports both.
 *
 * The list is returned in the caller's order — the order the user is looking at — so a confirm
 * dialog can name the rows the way the screen does.
 */
object BatchTasks {
    fun targets(
        action: BatchTaskAction,
        selection: TaskSelection,
        visible: List<Task>,
    ): List<Task> = visible.filter { task ->
        selection.contains(task.id) &&
            TaskLifecycleValidator.isValidStatusConfirmation(task.status, action.status)
    }

    /**
     * Only the actions that would change something.
     *
     * Offering 完成 over a selection of finished tasks would be a button whose confirm dialog says
     * "0 tasks" — so the set is derived from the selection rather than hard-coded, and an action
     * disappears exactly when it has nothing to do. Which actions appear is therefore a property of
     * the *statuses* on screen: the active views offer 完成 / 取消, the finished view offers 恢复.
     */
    fun actionsFor(selection: TaskSelection, visible: List<Task>): List<BatchTaskAction> =
        BatchTaskAction.entries.filter { action -> targets(action, selection, visible).isNotEmpty() }
}
