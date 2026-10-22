package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.planteam.PlanAgentId

enum class ToolOutcome { OK, DEGRADED, SKIPPED, FAILED }

data class ToolCallRecord(
    val toolId: String,
    val agent: PlanAgentId,
    val label: String,
    val outcome: ToolOutcome,
    val inputDigest: String,
    val outputDigest: String,
    val detail: String? = null,
    val elapsedMillis: Long = 0,
)

data class ToolSpec(
    val id: String,
    val label: String,
    val purpose: String,
    val reads: String,
    val writes: String,
)
