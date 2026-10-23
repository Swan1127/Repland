package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.ports.ReminderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReminderSettingsRepository(
    private val dao: ReminderSettingsDao,
) : ReminderSettingsRepository {
    override fun observe(): Flow<ReminderPreferences> =
        dao.observe().map { settings -> settings?.toDomain() ?: ReminderPreferences() }

    override suspend fun setEnabled(enabled: Boolean) {
        dao.save(ReminderSettingsEntity(isEnabled = enabled))
    }
}
