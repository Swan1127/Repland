package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.AiPreferences

@Entity(tableName = "ai_settings")
data class AiSettingsEntity(
    @PrimaryKey val id: String = AI_SETTINGS_ID,
    val isEnabled: Boolean = false,
    val consentedAtEpochMillis: Long? = null,
)

const val AI_SETTINGS_ID = "bounded-ai-advisor"

fun AiSettingsEntity.toDomain(): AiPreferences = AiPreferences(
    isEnabled = isEnabled,
    consentedAtEpochMillis = consentedAtEpochMillis,
)
