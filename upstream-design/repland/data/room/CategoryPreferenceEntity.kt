package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.TaskCategory

@Entity(tableName = "category_preferences")
data class CategoryPreferenceEntity(
    @PrimaryKey val category: String,
    val weight: Int,
)

fun CategoryPreferenceEntity.toDomainPair(): Pair<TaskCategory, Int> =
    TaskCategory.valueOf(category) to weight
