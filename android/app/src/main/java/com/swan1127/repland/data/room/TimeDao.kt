package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    suspend fun saveWeeklyAndAdvance(block: WeeklyTimeBlockEntity, editing: Boolean) {
        val existing = getWeeklyBlockById(block.id)
        require(!editing || existing != null) { "原时间设置已不存在，请重新打开" }
        insertWeeklyBlock(block.copy(createdAtEpochMillis = existing?.createdAtEpochMillis ?: block.createdAtEpochMillis))
        advanceRevision()
    }

    @Transaction
    suspend fun saveWeeklyBatchAndAdvance(blocks: List<WeeklyTimeBlockEntity>) {
        if (blocks.isEmpty()) return
        insertWeeklyBlocks(blocks)
        advanceRevision()
    }

    @Transaction
    suspend fun deleteWeeklyAndAdvance(id: String) {
        require(getWeeklyBlockById(id) != null) { "原时间设置已不存在，请重新打开" }
        deleteWeeklyBlock(id)
        advanceRevision()
    }

    @Transaction
    suspend fun saveOverrideAndAdvance(override: DateOverrideEntity, editing: Boolean) {
        val existing = getDateOverrideById(override.id)
        require(!editing || existing != null) { "原时间设置已不存在，请重新打开" }
        insertDateOverride(override.copy(createdAtEpochMillis = existing?.createdAtEpochMillis ?: override.createdAtEpochMillis))
        advanceRevision()
    }

    @Transaction
    suspend fun deleteOverrideAndAdvance(id: String) {
        require(getDateOverrideById(id) != null) { "原时间设置已不存在，请重新打开" }
        deleteDateOverride(id)
        advanceRevision()
    }

    @Transaction
    suspend fun saveSemesterAndAdvance(firstMondayEpochDay: Long?) {
        val current = getSemesterSettings()
        insertSemesterSettings(SemesterSettingsEntity(firstWeekMondayEpochDay = firstMondayEpochDay,
            updatedAtEpochMillis = maxOf(System.currentTimeMillis(), (current?.updatedAtEpochMillis ?: 0L) + 1)))
    }

    suspend fun advanceRevision() {
        val current = getSemesterSettings()
        insertSemesterSettings(SemesterSettingsEntity(firstWeekMondayEpochDay = current?.firstWeekMondayEpochDay,
            updatedAtEpochMillis = maxOf(System.currentTimeMillis(), (current?.updatedAtEpochMillis ?: 0L) + 1)))
    }

    @Transaction
    suspend fun importWeeklyBlocksAndAdvance(blocks: List<WeeklyTimeBlockEntity>): Int {
        fun key(block: WeeklyTimeBlockEntity) = listOf(block.title.trim(), block.kind, block.dayOfWeek,
            block.startMinute, block.endMinute, block.weekPattern?.trim()?.takeIf(String::isNotBlank), block.trackId)
        val existingKeys = getAllWeeklyBlocks().map(::key).toSet()
        val additions = blocks.distinctBy(::key).filter { key(it) !in existingKeys }
        if (additions.isEmpty()) return 0
        insertWeeklyBlocks(additions)
        advanceRevision()
        return additions.size
    }
}
