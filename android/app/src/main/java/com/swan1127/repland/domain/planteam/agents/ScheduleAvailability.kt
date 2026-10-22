package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.PlanningConstraintValidator
import com.swan1127.repland.domain.model.TimeBlockKind

object ScheduleAvailability {


    fun blocked(ctx: PlanTeamContext): List<OccupiedInterval> {
        val date = ctx.targetDate
        val blocks = ctx.input.weeklyBlocks + ctx.calendarBlocks
        val monday = ctx.input.semesterFirstWeekMonday

        return buildList {
            blocks
                .filter { it.kind != TimeBlockKind.AVAILABLE && it.dayOfWeek == date.dayOfWeek &&
                    CourseWeekPattern.appliesOn(it.weekPattern, date, monday) }
                .forEach { b ->
                    add(
                        OccupiedInterval(
                            title = b.title,
                            date = date,
                            startMinute = b.startMinute,
                            endMinute = b.endMinute,
                            kind = when (b.kind) {
                                TimeBlockKind.COURSE -> OccupiedKind.COURSE
                                TimeBlockKind.REST -> OccupiedKind.REST
                                else -> OccupiedKind.COMMITMENT
                            },
                            trackId = b.trackId,
                        ),
                    )
                }
            ctx.input.dateOverrides
                .filter { it.date == date && it.type == DateOverrideType.BLOCKED }
                .forEach { o ->
                    add(OccupiedInterval(o.title, date, o.startMinute, o.endMinute, OccupiedKind.COMMITMENT, trackId = "fixed"))
                }
            ctx.protectedSegments()
                .filter { it.date == date }
                .forEach { s ->
                    add(OccupiedInterval(if (s.isLocked) "已锁定" else "已保护安排", date, s.startMinute, s.endMinute, OccupiedKind.LOCKED, trackId = s.trackId, taskId = s.taskId))
                }
        }
    }


    fun freeSlots(ctx: PlanTeamContext): List<TimeSlot> {
        val date = ctx.targetDate
        val blocks = ctx.input.weeklyBlocks + ctx.calendarBlocks
        val monday = ctx.input.semesterFirstWeekMonday
        val lockedToday = ctx.protectedSegments().filter { it.date == date }
        val nowMinute = when {
            date.isAfter(ctx.now.toLocalDate()) -> 0
            date.isBefore(ctx.now.toLocalDate()) -> 24 * 60
            else -> ctx.now.hour * 60 + ctx.now.minute
        }
        return buildList {
            var minute = 0
            while (minute + 30 <= 1440) {
                val end = minute + 30
                val hardFree = PlanningConstraintValidator.isAvailable(
                    date, minute, end, blocks, ctx.input.dateOverrides, monday,
                )
                val committed = lockedToday.any { it.startMinute < end && it.endMinute > minute }
                if (hardFree && !committed && minute >= nowMinute) add(TimeSlot(date, minute, end))
                minute += 30
            }
        }
    }

    fun assemble(ctx: PlanTeamContext, blocked: List<OccupiedInterval>, freeSlots: List<TimeSlot>): AvailabilityMap =
        AvailabilityMap(
            targetDate = ctx.targetDate,
            blocked = blocked,
            freeSlots = freeSlots,
            capacityMinutes = freeSlots.sumOf { it.endMinute - it.startMinute },
        )

    fun map(ctx: PlanTeamContext): AvailabilityMap =
        assemble(ctx, blocked(ctx), freeSlots(ctx))
}
