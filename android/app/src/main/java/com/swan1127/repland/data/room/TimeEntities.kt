package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "weekly_time_blocks")
data class WeeklyTimeBlockEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val weekPattern: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "date_overrides")
data class DateOverrideEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "semester_settings")
data class SemesterSettingsEntity(
    @PrimaryKey val id: String = CURRENT_SETTINGS_ID,
    val firstWeekMondayEpochDay: Long?,
    /** Revision of all time constraints, including changes that delete an individual row. */
    val updatedAtEpochMillis: Long,
)

const val CURRENT_SETTINGS_ID = "current"

fun WeeklyTimeBlockEntity.toDomain(): WeeklyTimeBlock = WeeklyTimeBlock(
    id = id,
    title = title,
    kind = TimeBlockKind.valueOf(kind),
    dayOfWeek = DayOfWeek.of(dayOfWeek),
    startMinute = startMinute,
    endMinute = endMinute,
    weekPattern = weekPattern,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun DateOverrideEntity.toDomain(): DateOverride = DateOverride(
    id = id,
    title = title,
    type = DateOverrideType.valueOf(type),
    date = LocalDate.ofEpochDay(dateEpochDay),
    startMinute = startMinute,
    endMinute = endMinute,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
