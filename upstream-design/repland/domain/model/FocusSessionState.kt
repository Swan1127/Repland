package com.swan1127.repland.domain.model

/** A persisted clock baseline; backgrounding never depends on delivery of timer ticks. */
data class FocusSessionState(
    val targetSeconds: Int,
    val accumulatedMillis: Long = 0,
    val runningSinceEpochMillis: Long? = null,
    val startRecorded: Boolean = false,
) {
    init { require(targetSeconds > 0 && accumulatedMillis >= 0) }

    fun elapsedMillis(nowMillis: Long): Long =
        (accumulatedMillis + (runningSinceEpochMillis?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0))
            .coerceAtMost(targetSeconds * 1_000L)

    fun pause(nowMillis: Long) = copy(accumulatedMillis = elapsedMillis(nowMillis), runningSinceEpochMillis = null)

    fun resume(nowMillis: Long) = if (runningSinceEpochMillis == null && !finished(nowMillis)) {
        copy(runningSinceEpochMillis = nowMillis)
    } else this

    fun finished(nowMillis: Long): Boolean = elapsedMillis(nowMillis) >= targetSeconds * 1_000L

    fun actualMinutes(nowMillis: Long): Int = ((elapsedMillis(nowMillis) + 59_999) / 60_000).toInt()
}
