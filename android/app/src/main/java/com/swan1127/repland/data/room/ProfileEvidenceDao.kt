package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileEvidenceDao {
    @Query("SELECT * FROM profile_evidence ORDER BY updatedAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<ProfileEvidenceEntity>>

    @Query("SELECT * FROM profile_evidence ORDER BY updatedAtEpochMillis DESC, id DESC")
    suspend fun getAll(): List<ProfileEvidenceEntity>

    @Query("SELECT * FROM profile_evidence WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEvidenceEntity?

    /** A matching fingerprint is existing evidence, never a reason to replace a user edit. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(evidence: ProfileEvidenceEntity): Long

    @Update
    suspend fun update(evidence: ProfileEvidenceEntity)

    @Query("DELETE FROM profile_evidence WHERE id = :id")
    suspend fun delete(id: String)
}
