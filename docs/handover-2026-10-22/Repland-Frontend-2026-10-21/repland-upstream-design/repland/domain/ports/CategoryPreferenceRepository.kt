package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.TaskCategory
import kotlinx.coroutines.flow.Flow

interface CategoryPreferenceRepository {
    fun observe(): Flow<Map<TaskCategory, Int>>

    suspend fun save(weights: Map<TaskCategory, Int>)
}
