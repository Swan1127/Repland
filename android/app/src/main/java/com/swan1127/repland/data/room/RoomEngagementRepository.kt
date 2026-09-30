package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.UsageEventType
import com.swan1127.repland.domain.ports.EngagementRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEngagementRepository(private val database: ReplandDatabase) : EngagementRepository {
    private val dao = database.engagementDao()

    override fun observeMode(): Flow<EngagementMode> = dao.observeSettings().map {
        it?.mode?.let(EngagementMode::valueOf) ?: EngagementMode.GUIDED
    }

    override suspend fun setMode(mode: EngagementMode) = database.withTransaction {
        if (dao.getSettings()?.mode == mode.name) return@withTransaction
        val now = System.currentTimeMillis()
        dao.saveSettings(EngagementSettingsEntity(mode = mode.name, updatedAtEpochMillis = now))
        dao.insertEvent(UsageEventEntity(UUID.randomUUID().toString(),
            UsageEventType.MODE_CHANGED.name, now, mode.name, null))
    }

    override suspend fun recordTimelineOpened(entryId: String) {
        require(entryId.isNotBlank() && entryId.length <= 200)
        val mode = dao.getSettings()?.mode ?: EngagementMode.GUIDED.name
        dao.insertEvent(UsageEventEntity(UUID.randomUUID().toString(),
            UsageEventType.TIMELINE_OPENED.name, System.currentTimeMillis(), mode, entryId))
    }
}
