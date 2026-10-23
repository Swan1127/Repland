package com.swan1127.repland.domain.model

/** A presentation preference, not a fixed diagnosis of the user. */
enum class EngagementMode {
    CO_PLANNER,
    GUIDED,
    EXECUTOR,
}

enum class UsageEventType { MODE_CHANGED, TIMELINE_OPENED }

data class UsageEvent(
    val id: String,
    val type: UsageEventType,
    val occurredAtEpochMillis: Long,
    val mode: EngagementMode,
    val subjectId: String? = null,
)
