package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class TimelineKind { COURSE, REST, COMMITMENT, TASK }
enum class TimelinePhase { UPCOMING, ACTIVE, OVERRUN, ENDED }

data class TimelineEntry(
    val id: String,
    val title: String,
    val kind: TimelineKind,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val taskId: String? = null,
    val taskClosed: Boolean = false,
    val trackId: String = "auto",
    val lane: Int = 0,
    /** Context carried into the detail sheet; it never changes task completion state. */
    val note: String? = null,
) {
    init {
        require(startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute)
    }
    val start: LocalDateTime get() = date.atStartOfDay().plusMinutes(startMinute.toLong())
    val end: LocalDateTime get() = date.atStartOfDay().plusMinutes(endMinute.toLong())
}

data class TimelineClock(
    val phase: TimelinePhase,
    val untilStartMinutes: Long = 0,
    val elapsedMinutes: Long = 0,
    val remainingMinutes: Long = 0,
    val overtimeMinutes: Long = 0,
)

/**
 * How much of a block's own slot has elapsed, as 0..1, for the fill drawn inside its card.
 *
 * A proportion of the block, not of the calendar. The card's height follows its content
 * rather than the grid, so this is the only fraction that can be drawn without measuring
 * the whole timeline — and being explicit about that is what keeps the visual honest.
 *
 * It is **wall time only**. It is not a claim about how much of the work is done, and
 * nothing in the app may read it as one; the user's own completion feedback is the only
 * source for that.
 */
object TimelineBlockProgress {
    fun elapsedFraction(entry: TimelineEntry, clock: TimelineClock): Float {
        val durationMinutes = (entry.endMinute - entry.startMinute).coerceAtLeast(1)
        val fraction = when (clock.phase) {
            TimelinePhase.UPCOMING -> 0.0
            TimelinePhase.ACTIVE -> clock.elapsedMinutes.toDouble() / durationMinutes
            TimelinePhase.OVERRUN, TimelinePhase.ENDED -> 1.0
        }
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }
}

object ScheduleTimeline {
    /** Schedule state is based on wall time; it never implies that work was actually performed. */
    fun clock(entry: TimelineEntry, now: LocalDateTime, userFinished: Boolean = false): TimelineClock {
        if (userFinished) return TimelineClock(TimelinePhase.ENDED)
        if (now.isBefore(entry.start)) return TimelineClock(
            TimelinePhase.UPCOMING,
            untilStartMinutes = ChronoUnit.MINUTES.between(now, entry.start).coerceAtLeast(0),
        )
        if (now.isBefore(entry.end)) return TimelineClock(
            TimelinePhase.ACTIVE,
            elapsedMinutes = ChronoUnit.MINUTES.between(entry.start, now).coerceAtLeast(0),
            remainingMinutes = ChronoUnit.MINUTES.between(now, entry.end).coerceAtLeast(0),
        )
        return TimelineClock(
            TimelinePhase.OVERRUN,
            elapsedMinutes = ChronoUnit.MINUTES.between(entry.start, now).coerceAtLeast(0),
            overtimeMinutes = ChronoUnit.MINUTES.between(entry.end, now).coerceAtLeast(0),
        )
    }

    fun entries(
        date: LocalDate,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        segments: List<PlannedSegment>,
        tasks: List<Task>,
        semesterFirstWeekMonday: LocalDate?,
    ): List<TimelineEntry> {
        val taskById = tasks.associateBy(Task::id)
        val recurring = weeklyBlocks.asSequence()
            .filter { it.kind != TimeBlockKind.AVAILABLE && it.dayOfWeek == date.dayOfWeek }
            .filter { CourseWeekPattern.appliesOn(it.weekPattern, date, semesterFirstWeekMonday) }
            .map { block ->
                TimelineEntry(
                    id = "weekly:${block.id}", title = block.title,
                    kind = when (block.kind) {
                        TimeBlockKind.COURSE -> TimelineKind.COURSE
                        TimeBlockKind.REST -> TimelineKind.REST
                        else -> TimelineKind.COMMITMENT
                    },
                    date = date, startMinute = block.startMinute, endMinute = block.endMinute,
                    trackId = block.laneKey(),
                    note = block.note,
                )
            }
        val overrides = dateOverrides.asSequence()
            .filter { it.date == date && it.type == DateOverrideType.BLOCKED }
            .map { override ->
                TimelineEntry(
                    id = "override:${override.id}", title = override.title,
                    kind = TimelineKind.COMMITMENT, date = date,
                    startMinute = override.startMinute, endMinute = override.endMinute,
                    trackId = "fixed",
                    note = override.note,
                )
            }
        val work = segments.asSequence().filter { it.date == date }.mapNotNull { segment ->
            val task = taskById[segment.taskId] ?: return@mapNotNull null
            TimelineEntry(
                id = "segment:${segment.id}", title = task.displayName,
                kind = TimelineKind.TASK, date = date,
                startMinute = segment.startMinute, endMinute = segment.endMinute,
                taskId = task.id,
                taskClosed = !task.status.isActive,
                trackId = segment.trackId,
                note = task.description.takeIf { it.isNotBlank() && it != task.displayName },
            )
        }
        val trackOrder = mutableListOf<String>()
        val laneEnds = mutableListOf<Int>()
        return (recurring + overrides + work)
            .sortedWith(compareBy(TimelineEntry::startMinute, TimelineEntry::endMinute, TimelineEntry::id))
            .map { entry ->
                val preferredLane = trackOrder.indexOf(entry.trackId).takeIf { it >= 0 }
                    ?: trackOrder.size.also { trackOrder += entry.trackId }
                while (laneEnds.size <= preferredLane) laneEnds += -1
                val lane = if (laneEnds[preferredLane] <= entry.startMinute) {
                    preferredLane
                } else {
                    laneEnds.indexOfFirst { end -> end <= entry.startMinute }.takeIf { it >= 0 }
                        ?: laneEnds.size.also { laneEnds += -1 }
                }
                laneEnds[lane] = entry.endMinute
                entry.copy(lane = lane)
            }
            .toList()
    }
}
