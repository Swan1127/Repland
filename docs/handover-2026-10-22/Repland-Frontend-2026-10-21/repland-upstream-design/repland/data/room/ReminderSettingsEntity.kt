package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.ReminderPreferences

@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey val id: String = REMINDER_SETTINGS_ID,
    val isEnabled: Boolean = false,
)

const val REMINDER_SETTINGS_ID = "local-reminders"

fun ReminderSettingsEntity.toDomain(): ReminderPreferences =
    ReminderPreferences(isEnabled = isEnabled)
