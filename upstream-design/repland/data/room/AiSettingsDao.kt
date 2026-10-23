package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSettingsDao {
    @Query("SELECT * FROM ai_settings WHERE id = :id LIMIT 1")
    fun observe(id: String = AI_SETTINGS_ID): Flow<AiSettingsEntity?>

    @Query("SELECT * FROM ai_settings WHERE id = :id LIMIT 1")
    suspend fun get(id: String = AI_SETTINGS_ID): AiSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: AiSettingsEntity)
}
