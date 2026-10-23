package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.UsageEvent
import com.swan1127.repland.domain.model.UsageEventType

@Entity(tableName = "engagement_settings")
data class EngagementSettingsEntity(
    @PrimaryKey val id: String = "primary",
    val mode: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "usage_events", indices = [Index("occurredAtEpochMillis")])
data class UsageEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val occurredAtEpochMillis: Long,
    val mode: String,
    val subjectId: String?,
)

fun UsageEventEntity.toDomain(): UsageEvent = UsageEvent(
    id = id,
    type = UsageEventType.valueOf(type),
    occurredAtEpochMillis = occurredAtEpochMillis,
    mode = EngagementMode.valueOf(mode),
    subjectId = subjectId,
)
