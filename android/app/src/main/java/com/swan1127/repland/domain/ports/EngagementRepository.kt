package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.EngagementMode
import kotlinx.coroutines.flow.Flow

interface EngagementRepository {
    fun observeMode(): Flow<EngagementMode>
    suspend fun setMode(mode: EngagementMode)
    suspend fun recordTimelineOpened(entryId: String)
}
