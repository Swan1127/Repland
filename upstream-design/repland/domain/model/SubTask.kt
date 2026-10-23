package com.swan1127.repland.domain.model

import java.util.UUID

/**
 * One checklist item belonging to a [Task].
 *
 * A subtask is deliberately a flat, self-contained value: an id, a cleaned title, a done flag and a
 * creation timestamp. It carries nothing Android-specific, so the rules that act on a task's
 * subtasks (add / remove / toggle / aggregate) live in plain JVM code and are driven directly by
 * `SubTaskTest` and the mutation harness — no emulator, no clock, no `Context`.
 */
data class SubTask(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val createdAtEpochMillis: Long,
)

/**
 * The title a user types can be messy in the same silent ways a nickname can: stray newlines, a wall
 * of internal whitespace, leading/trailing spaces, or a string long enough to break the row it sits
 * in. None of those produce an error — they just produce a wrong string — so the cleaning is a rule
 * worth isolating and testing rather than burying in a Composable.
 */
object SubTaskValidator {
    const val MAX_TITLE_LENGTH = 80

    fun cleanTitle(raw: String): String {
        // Each physical line is trimmed and joined with a single space, so a multi-line paste can
        // never survive as embedded newlines; then any run of whitespace collapses to one space.
        val collapsed = raw.lineSequence()
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (collapsed.length <= MAX_TITLE_LENGTH) collapsed else collapsed.take(MAX_TITLE_LENGTH)
    }

    fun isValidTitle(raw: String): Boolean = cleanTitle(raw).isNotEmpty()
}

/**
 * The derived completion state of a task's subtasks, computed — never stored.
 *
 * A subtask checklist is independent of the task's own [TaskStatus] lifecycle: ticking the last box
 * does not auto-complete the parent (that would fight the user-confirmed status machine in
 * [com.swan1127.repland.domain.model.TaskLifecycleValidator]). The numbers below are a display aid,
 * shown as a chip on the library row and a progress bar on the detail screen.
 */
data class SubTaskProgress(
    val done: Int,
    val total: Int,
    val percent: Int,
    val allDone: Boolean,
    val hasSubtasks: Boolean,
)

/**
 * Pure operations on a task's subtasks. Every function returns a *new* [Task] (the list is
 * immutable), so the UI simply collects the next emission from the repository flow.
 *
 * `add` takes an [idProvider] rather than generating an id itself: in production that is
 * `UUID::randomUUID`, but the tests pass a deterministic counter so two subtasks never collide and
 * a round-trip assertion is stable.
 */
object SubTasks {
    fun add(
        task: Task,
        title: String,
        nowMillis: Long,
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ): Task {
        val cleaned = SubTaskValidator.cleanTitle(title)
        require(cleaned.isNotEmpty()) { "a subtask needs a non-blank title" }
        val sub = SubTask(
            id = idProvider(),
            title = cleaned,
            isDone = false,
            createdAtEpochMillis = nowMillis,
        )
        return task.copy(subtasks = task.subtasks + sub)
    }

    fun remove(task: Task, subTaskId: String): Task =
        task.copy(subtasks = task.subtasks.filterNot { it.id == subTaskId })

    fun toggle(task: Task, subTaskId: String, isDone: Boolean): Task =
        task.copy(
            subtasks = task.subtasks.map { sub ->
                if (sub.id == subTaskId) sub.copy(isDone = isDone) else sub
            },
        )

    fun progress(task: Task): SubTaskProgress = progress(task.subtasks)

    fun progress(subtasks: List<SubTask>): SubTaskProgress {
        val total = subtasks.size
        val done = subtasks.count { it.isDone }
        val percent = if (total == 0) 0 else done * 100 / total
        return SubTaskProgress(
            done = done,
            total = total,
            percent = percent,
            allDone = total > 0 && done == total,
            hasSubtasks = total > 0,
        )
    }
}
