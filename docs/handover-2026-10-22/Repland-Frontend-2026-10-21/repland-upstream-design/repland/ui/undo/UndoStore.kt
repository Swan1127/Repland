package com.swan1127.repland.ui.undo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One reversible step.
 *
 * An entry stores the state *before* and *after* the operation, plus the one function that can
 * put a state back. It deliberately does not store a per-operation "inverse action": that design
 * needs a matching undo for every operation, and the pairing drifts as soon as either side gains
 * a field. Two states and one apply function cannot drift.
 */
class UndoEntry(
    /** Shown to the user, e.g. "切换课表集". Undo is never silent — the user must know what moved. */
    val label: String,
    private val applyBefore: suspend () -> Unit,
    private val applyAfter: suspend () -> Unit,
) {
    suspend fun undo() = applyBefore()

    suspend fun redo() = applyAfter()
}

/**
 * A bounded undo/redo stack.
 *
 * Depth is capped at [maxDepth] and the *oldest* entry is dropped when it overflows — losing the
 * ability to undo something from long ago is much better than letting the stack grow without
 * bound in a process that may run for days.
 *
 * In memory only, on purpose: undo is for "I just mis-tapped", which is a within-session concern.
 * Persisting it would mean persisting whole arrangements to disk and then reasoning about which
 * of them still make sense after the user edits the same rows by hand — a much larger surface for
 * a much smaller benefit. Closing the app clears the history, and the UI says so.
 */
class UndoStore(private val maxDepth: Int = 30) {
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    /**
     * Serialises [undo] and [redo].
     *
     * A restore is a delete pass followed by an insert pass, and each pass suspends on the
     * database. Two taps in quick succession — easy to do on a snackbar action, which sits under
     * the thumb — would otherwise interleave them: both would read the same starting state and
     * the second insert pass would duplicate rows the first had just written. A gate is cheaper
     * than making every call site remember to check a "busy" flag.
     */
    private val gate = Mutex()

    /** The label of the step that [undo] would reverse, or null when there is nothing to undo. */
    var undoLabel by mutableStateOf<String?>(null)
        private set

    /** The label of the step that [redo] would reapply, or null when there is nothing to redo. */
    var redoLabel by mutableStateOf<String?>(null)
        private set

    val canUndo: Boolean get() = undoLabel != null

    val canRedo: Boolean get() = redoLabel != null

    /**
     * Records a step. Call this *after* the operation has been applied, with the state captured
     * before it — the entry then knows both ends and can move in either direction.
     *
     * Recording clears the redo branch: once the user does something new, replaying a previously
     * undone step would apply a state that never followed from the current one.
     */
    fun record(entry: UndoEntry) {
        undoStack.addLast(entry)
        while (undoStack.size > maxDepth) undoStack.removeFirst()
        redoStack.clear()
        publish()
    }

    /** Convenience for the common shape: same scope, two states, one apply function. */
    fun <T> record(label: String, before: T, after: T, apply: suspend (T) -> Unit) {
        record(UndoEntry(label, { apply(before) }, { apply(after) }))
    }

    /**
     * Runs a reversible operation and records it, with capture, the operation itself and the
     * recording all under the same gate as [undo] / [redo].
     *
     * The gate matters here, not just in [undo]. "Read the state, write, read the state again" is
     * three separate suspension points, so an operation started while another is still mid-write
     * would capture a "before" that already contains the other operation's effect. Undoing it would
     * then also revert an unrelated change — the failure is invisible until the user notices that
     * one undo moved two things.
     *
     * The operation is handed in as a lambda rather than being described by an enum so that a new
     * operation needs no new entry in a table: it just has to be expressible as "do it" plus
     * "put this state back".
     */
    suspend fun <T> transaction(
        label: String,
        capture: suspend () -> T,
        apply: suspend () -> Unit,
        restore: suspend (T) -> Unit,
    ) = gate.withLock {
        val before = capture()
        apply()
        val after = capture()
        record(label, before, after, restore)
    }

    /** Reverses the newest step. Returns its label, or null when there was nothing to undo. */
    suspend fun undo(): String? = gate.withLock {
        val entry = undoStack.removeLastOrNull() ?: return null
        entry.undo()
        redoStack.addLast(entry)
        publish()
        entry.label
    }

    /** Reapplies the newest undone step. Returns its label, or null when there is nothing to redo. */
    suspend fun redo(): String? = gate.withLock {
        val entry = redoStack.removeLastOrNull() ?: return null
        entry.redo()
        undoStack.addLast(entry)
        publish()
        entry.label
    }

    suspend fun clear() = clearWith {}

    suspend fun clearWith(write: suspend () -> Unit) = gate.withLock {
        write()
        undoStack.clear()
        redoStack.clear()
        publish()
    }

    private fun publish() {
        undoLabel = undoStack.lastOrNull()?.label
        redoLabel = redoStack.lastOrNull()?.label
    }
}
