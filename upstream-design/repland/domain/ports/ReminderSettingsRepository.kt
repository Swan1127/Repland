package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.ReminderPreferences
import kotlinx.coroutines.flow.Flow

interface ReminderSettingsRepository {
    fun observe(): Flow<ReminderPreferences>

    suspend fun setEnabled(enabled: Boolean)
}
