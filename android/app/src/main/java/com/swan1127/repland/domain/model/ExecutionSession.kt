package com.swan1127.repland.domain.model

/** A user-started round. Time passing never completes its task. */
data class ExecutionSession(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val planId: String,
    val segmentId: String,
    val targetSeconds: Int,
    val startedAtEpochMillis: Long,
    val accumulatedMillis: Long = 0,
    val runningSinceEpochMillis: Long? = startedAtEpochMillis,
    val endedAtEpochMillis: Long? = null,
    val outcome: ExecutionOutcome? = null,
) {
    val isPaused: Boolean get() = runningSinceEpochMillis == null && endedAtEpochMillis == null
    fun elapsedMillis(now: Long): Long = (accumulatedMillis +
        (runningSinceEpochMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)).coerceIn(0, 86_400_000)
    fun pause(now: Long) = copy(accumulatedMillis = elapsedMillis(now), runningSinceEpochMillis = null)
    fun resume(now: Long) = copy(runningSinceEpochMillis = now)
}

enum class ExecutionOutcome { CONTINUE, PARTIAL, COMPLETED, SKIPPED }
