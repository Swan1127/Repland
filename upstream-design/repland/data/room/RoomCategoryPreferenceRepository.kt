package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.ports.CategoryPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryPreferenceRepository(
    private val dao: CategoryPreferenceDao,
) : CategoryPreferenceRepository {
    override fun observe(): Flow<Map<TaskCategory, Int>> = dao.observeAll().map { entities ->
        CategoryPreferences.normalized(entities.associate(CategoryPreferenceEntity::toDomainPair))
    }

    override suspend fun save(weights: Map<TaskCategory, Int>) {
        val normalized = CategoryPreferences.normalized(weights)
        require(CategoryPreferences.isValid(normalized)) { "Category preferences are invalid." }
        dao.upsertAll(
            normalized.map { (category, weight) ->
                CategoryPreferenceEntity(category = category.name, weight = weight)
            },
        )
    }
}
