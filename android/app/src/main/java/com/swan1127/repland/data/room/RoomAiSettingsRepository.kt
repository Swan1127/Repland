package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.ports.AiSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAiSettingsRepository(
    private val dao: AiSettingsDao,
) : AiSettingsRepository {
    override fun observe(): Flow<AiPreferences> =
        dao.observe().map { settings -> settings?.toDomain() ?: AiPreferences() }

    override suspend fun grantConsentAndEnable() {
        val existing = dao.get()
        dao.save(
            AiSettingsEntity(
                isEnabled = true,
                consentedAtEpochMillis = existing?.consentedAtEpochMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        val existing = dao.get()
        require(!enabled || existing?.consentedAtEpochMillis != null) {
            "Explicit consent is required before enabling the AI advisor."
        }
        dao.save(
            AiSettingsEntity(
                isEnabled = enabled,
                consentedAtEpochMillis = existing?.consentedAtEpochMillis,
            ),
        )
    }
}
