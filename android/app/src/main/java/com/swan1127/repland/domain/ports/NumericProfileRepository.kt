package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.Flow

interface NumericProfileRepository {
    fun observe(): Flow<NumericProfileSnapshot>
    fun observeDescription(): Flow<NumericDescription?>
    suspend fun refresh(): NumericProfileSnapshot
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setParameterEnabled(id: String, enabled: Boolean)
    suspend fun setExcluded(sourceId: String, excluded: Boolean)
    suspend fun setAiConsent(consented: Boolean)
    suspend fun confirmCurrentInput(taskId: String, expectedRevision: String)
    suspend fun confirmActualTotal(taskId: String, minutes: Int, expectedRevision: String)
    suspend fun clearActualTotal(taskId: String)
    suspend fun durationAdvice(taskId: String): NumericDurationAdvice?
    suspend fun acceptDurationAdvice(advice: NumericDurationAdvice)
    suspend fun saveDescription(description: NumericDescription)
}
