package com.swan1127.repland.domain.model

import java.time.LocalDate

/** One confirmed placement of a task, using the same identity as its task draft. */
data class TaskPlacement(
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String = "focus",
)
