package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeDao {
    @Query("SELECT * FROM weekly_time_blocks ORDER BY dayOfWeek ASC, startMinute ASC, title ASC")
    fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlockEntity>>

    @Query("SELECT * FROM weekly_time_blocks ORDER BY dayOfWeek ASC, startMinute ASC, title ASC")
    suspend fun getAllWeeklyBlocks(): List<WeeklyTimeBlockEntity>

    @Query("SELECT * FROM date_overrides ORDER BY dateEpochDay ASC, startMinute ASC, title ASC")
    fun observeDateOverrides(): Flow<List<DateOverrideEntity>>

    @Query("SELECT * FROM date_overrides ORDER BY dateEpochDay ASC, startMinute ASC, title ASC")
    suspend fun getAllDateOverrides(): List<DateOverrideEntity>

    @Query("SELECT * FROM semester_settings WHERE id = :id LIMIT 1")
    fun observeSemesterSettings(id: String = CURRENT_SETTINGS_ID): Flow<SemesterSettingsEntity?>

    @Query("SELECT * FROM semester_settings WHERE id = :id LIMIT 1")
    suspend fun getSemesterSettings(id: String = CURRENT_SETTINGS_ID): SemesterSettingsEntity?

    @Query("SELECT * FROM weekly_time_blocks WHERE id = :id LIMIT 1")
    suspend fun getWeeklyBlockById(id: String): WeeklyTimeBlockEntity?

    @Query("SELECT * FROM date_overrides WHERE id = :id LIMIT 1")
    suspend fun getDateOverrideById(id: String): DateOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyBlock(block: WeeklyTimeBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyBlocks(blocks: List<WeeklyTimeBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDateOverride(override: DateOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemesterSettings(settings: SemesterSettingsEntity)

    @Query("DELETE FROM weekly_time_blocks WHERE id = :id")
    suspend fun deleteWeeklyBlock(id: String)

    @Query("DELETE FROM date_overrides WHERE id = :id")
    suspend fun deleteDateOverride(id: String)
}
