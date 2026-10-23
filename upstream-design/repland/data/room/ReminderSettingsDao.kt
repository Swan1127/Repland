package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderSettingsDao {
    @Query("SELECT * FROM reminder_settings WHERE id = :id LIMIT 1")
    fun observe(id: String = REMINDER_SETTINGS_ID): Flow<ReminderSettingsEntity?>

    @Query("SELECT * FROM reminder_settings WHERE id = :id LIMIT 1")
    suspend fun get(id: String = REMINDER_SETTINGS_ID): ReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: ReminderSettingsEntity)
}
