package com.swan1127.repland.domain.model

import java.time.LocalDate

/** Missing input and legacy defaults never become confirmed evidence merely by being read. */
enum class TaskInputSource { UNKNOWN, USER_INPUT, ACCEPTED_SUGGESTION, LEGACY_UNVERIFIED }

data class TaskInputSources(
    val category: TaskInputSource,
    val priority: TaskInputSource,
    val days: TaskInputSource,
    val duration: TaskInputSource,
    val dueDate: TaskInputSource,
    val scheduledDate: TaskInputSource,
) {
    fun encode(): String = listOf(category, priority, days, duration, dueDate, scheduledDate).joinToString("|") { it.name }

    companion object {
        val legacy = TaskInputSources(
            TaskInputSource.LEGACY_UNVERIFIED, TaskInputSource.LEGACY_UNVERIFIED,
            TaskInputSource.LEGACY_UNVERIFIED, TaskInputSource.LEGACY_UNVERIFIED,
            TaskInputSource.LEGACY_UNVERIFIED, TaskInputSource.LEGACY_UNVERIFIED,
        )

        fun decode(value: String): TaskInputSources {
            if (value.isEmpty()) return legacy
            val fields = value.split("|").map(TaskInputSource::valueOf)
            require(fields.size == 6) { "Invalid task input provenance." }
            return TaskInputSources(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5])
        }

        fun forInput(category: TaskCategory, priority: TaskPriority, days: Int?, duration: Int?,
                     dueDate: LocalDate?, scheduledDate: LocalDate?,
                     source: TaskInputSource = TaskInputSource.USER_INPUT): TaskInputSources {
            fun known(present: Boolean) = if (present) source else TaskInputSource.UNKNOWN
            return TaskInputSources(known(category != TaskCategory.UNSPECIFIED), known(priority != TaskPriority.UNSPECIFIED),
                known(days != null), known(duration != null), known(dueDate != null), known(scheduledDate != null))
        }
    }
}
