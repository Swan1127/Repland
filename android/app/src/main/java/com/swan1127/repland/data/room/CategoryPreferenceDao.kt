package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryPreferenceDao {
    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    fun observeAll(): Flow<List<CategoryPreferenceEntity>>

    @Query("SELECT * FROM category_preferences ORDER BY category ASC")
    suspend fun getAll(): List<CategoryPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(preferences: List<CategoryPreferenceEntity>)
}
