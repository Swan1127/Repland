package com.swan1127.repland.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY createdAtEpochMillis ASC, id ASC")
    fun observeAll(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs ORDER BY createdAtEpochMillis ASC, id ASC")
    suspend fun getAll(): List<ExecutionLogEntity>

    @Query(
        """
        SELECT * FROM execution_logs
        WHERE taskId = :taskId
        ORDER BY createdAtEpochMillis ASC, id ASC
        """,
    )
    fun observeForTask(taskId: String): Flow<List<ExecutionLogEntity>>

    @Query(
        """
        SELECT * FROM execution_logs
        WHERE createdAtEpochMillis >= :startEpochMillis
            AND createdAtEpochMillis < :endEpochMillis
        ORDER BY createdAtEpochMillis ASC, id ASC
        """,
    )
    fun observeBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExecutionLogEntity?

    @Query("SELECT MAX(createdAtEpochMillis) FROM execution_logs WHERE taskId = :taskId")
    suspend fun latestCreatedAtForTask(taskId: String): Long?

    /** ABORT makes an accidental duplicate ID visible instead of replacing historical evidence. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: ExecutionLogEntity)
}
