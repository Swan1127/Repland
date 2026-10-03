package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ExecutionSessionRepository {
    fun observeActive(): Flow<ExecutionSession?>
    suspend fun start(segmentId: String)
    suspend fun pause(sessionId: String)
    suspend fun resume(sessionId: String)
    /** Ending, evidence and task status are one transaction; repeated endings are rejected. */
    suspend fun finish(sessionId: String, outcome: ExecutionOutcome, feedback: TaskFeedback = TaskFeedback())
}
