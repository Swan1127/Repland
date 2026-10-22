package com.swan1127.repland.domain.model

import java.time.LocalDate

data class LearningMemorySnapshot(
    val version: Int = 1,
    val referenceEnabled: Boolean,
    val gain: Double,
    val points: List<LearningMemoryPointSnapshot>,
)

data class LearningMemoryPointSnapshot(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val content: String,
    val createdOn: LocalDate,
    val stage: Int,
    val memory: MemoryState? = null,
)
