package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TimeRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTimeRepository(private val timeDao: TimeDao) : TimeRepository {
    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> =
        timeDao.observeWeeklyBlocks().map { it.map(WeeklyTimeBlockEntity::toDomain) }
    override fun observeDateOverrides(): Flow<List<DateOverride>> =
        timeDao.observeDateOverrides().map { it.map(DateOverrideEntity::toDomain) }
    override fun observeTimeConstraintSettings(): Flow<TimeConstraintSettings> =
        timeDao.observeSemesterSettings().map {
            TimeConstraintSettings(it?.firstWeekMondayEpochDay?.let(LocalDate::ofEpochDay), it?.updatedAtEpochMillis ?: 0L)
        }

    private fun entity(draft: WeeklyTimeBlockDraft, now: Long): WeeklyTimeBlockEntity {
        require(TimeBlockValidator.isValid(draft)) { "名称或时间无效" }
        require(draft.id == null || draft.id.isNotBlank()) { "时间设置身份无效" }
        return WeeklyTimeBlockEntity(id = draft.id ?: UUID.randomUUID().toString(), title = draft.title.trim(),
            kind = draft.kind.name, dayOfWeek = draft.dayOfWeek.value, startMinute = draft.startMinute,
            endMinute = draft.endMinute, weekPattern = draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
            trackId = draft.trackId.ifBlank { "course" }, note = draft.note?.trim()?.takeIf(String::isNotBlank),
            createdAtEpochMillis = now, updatedAtEpochMillis = now)
    }

    override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) =
        timeDao.saveWeeklyAndAdvance(entity(draft, System.currentTimeMillis()), draft.id != null)

    override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) {
        require(drafts.all { TimeBlockValidator.isValid(it) && it.id == null }) { "批量新增不能包含无效课程或已有身份" }
        val now = System.currentTimeMillis()
        val blocks = drafts.distinctBy { listOf(it.title.trim(), it.kind, it.dayOfWeek, it.startMinute,
            it.endMinute, it.weekPattern?.trim()?.takeIf(String::isNotBlank), it.trackId.ifBlank { "course" }) }
            .map { entity(it, now) }
        timeDao.saveWeeklyBatchAndAdvance(blocks)
    }

    override suspend fun importWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>): Int {
        require(drafts.all { TimeBlockValidator.isValid(it) && it.id == null }) { "课程名称、时间或导入身份无效" }
        val now = System.currentTimeMillis()
        return timeDao.importWeeklyBlocksAndAdvance(drafts.map { entity(it, now) })
    }

    override suspend fun deleteWeeklyBlock(id: String) = timeDao.deleteWeeklyAndAdvance(id)

    override suspend fun saveDateOverride(draft: DateOverrideDraft) {
        require(TimeBlockValidator.isValid(draft)) { "名称或时间无效" }
        require(draft.id == null || draft.id.isNotBlank()) { "时间设置身份无效" }
        val now = System.currentTimeMillis()
        timeDao.saveOverrideAndAdvance(DateOverrideEntity(id = draft.id ?: UUID.randomUUID().toString(),
            title = draft.title.trim(), type = draft.type.name, dateEpochDay = draft.date.toEpochDay(),
            startMinute = draft.startMinute, endMinute = draft.endMinute,
            note = draft.note?.trim()?.takeIf(String::isNotBlank), createdAtEpochMillis = now, updatedAtEpochMillis = now),
            draft.id != null)
    }

    override suspend fun deleteDateOverride(id: String) = timeDao.deleteOverrideAndAdvance(id)
    override suspend fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        require(date == null || date.dayOfWeek == DayOfWeek.MONDAY) { "学期第一周起点应为周一" }
        timeDao.saveSemesterAndAdvance(date?.toEpochDay())
    }
}
