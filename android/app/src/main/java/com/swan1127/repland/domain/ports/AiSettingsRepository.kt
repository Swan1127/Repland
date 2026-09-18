package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.AiPreferences
import kotlinx.coroutines.flow.Flow

interface AiSettingsRepository {
    fun observe(): Flow<AiPreferences>

    /** Records explicit first-use consent and enables the optional advisor. */
    suspend fun grantConsentAndEnable()

    /** Enables only after consent has been persisted; disabling never makes a request. */
    suspend fun setEnabled(enabled: Boolean)
}
