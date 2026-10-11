package com.swan1127.repland.domain.model

/** Shared guard for newly placed work. Existing user-confirmed parallel work is not rewritten. */
object PlacementValidator {
    fun requireValid(
        candidate: PlannedSegment,
        existing: List<PlannedSegment>,
        weeklyBlocks: List<WeeklyTimeBlock>,
        overrides: List<DateOverride>,
        semesterFirstWeekMonday: java.time.LocalDate?,
        unavoidableTaskIds: Set<String> = emptySet(),
        allowOrdinaryOverlap: Boolean = false,
    ) {
        require(candidate.startMinute in 0 until 1440 && candidate.endMinute in 1..1440 &&
            candidate.startMinute < candidate.endMinute && candidate.trackId.isNotBlank()) {
            "请输入有效的起止时间和轨道。"
        }
        fun overlaps(start: Int, end: Int) = start < candidate.endMinute && end > candidate.startMinute
        require(existing.none {
            it !== candidate && (candidate.id.isBlank() || it.id != candidate.id) &&
                (it.isLocked || it.taskId in unavoidableTaskIds) && it.date == candidate.date && overlaps(it.startMinute, it.endMinute)
        }) { "这个时间有锁定或不可避免安排，请先解除保护或调整时间。" }
        require(weeklyBlocks.none {
            it.kind != TimeBlockKind.AVAILABLE && it.dayOfWeek == candidate.date.dayOfWeek &&
                CourseWeekPattern.appliesOn(it.weekPattern, candidate.date, semesterFirstWeekMonday) &&
                overlaps(it.startMinute, it.endMinute)
        } && overrides.none {
            it.date == candidate.date && it.type == DateOverrideType.BLOCKED && overlaps(it.startMinute, it.endMinute)
        }) { "这个时间与课程、固定事项或休息冲突，请调整时间。" }
        require(allowOrdinaryOverlap || existing.none {
            it !== candidate && (candidate.id.isBlank() || it.id != candidate.id) && it.date == candidate.date &&
                overlaps(it.startMinute, it.endMinute)
        }) { "这个时间已有任务；普通任务重叠须由用户单独确认。" }
    }
}
