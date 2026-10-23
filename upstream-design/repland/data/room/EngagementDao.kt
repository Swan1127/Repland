package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementDao {
    @Query("SELECT * FROM engagement_settings WHERE id = 'primary' LIMIT 1")
    fun observeSettings(): Flow<EngagementSettingsEntity?>

    @Query("SELECT * FROM engagement_settings WHERE id = 'primary' LIMIT 1")
    suspend fun getSettings(): EngagementSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: EngagementSettingsEntity)

    @Insert
    suspend fun insertEvent(event: UsageEventEntity)

    @Query("SELECT * FROM usage_events ORDER BY occurredAtEpochMillis ASC, id ASC")
    suspend fun getEvents(): List<UsageEventEntity>
}
