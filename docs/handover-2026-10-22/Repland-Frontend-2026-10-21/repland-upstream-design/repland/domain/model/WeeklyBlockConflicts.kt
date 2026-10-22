package com.swan1127.repland.domain.model

import java.time.DayOfWeek

/**
 * Two weekly rows that claim the same minutes on the same day in the same lane.
 *
 * [startMinute] and [endMinute] are the *intersection*, not either row's own span: the banner needs
 * to say which minutes are contested, and recomputing that at the call site would be a second copy
 * of the rule that decided there was a conflict at all.
 */
data class WeeklyBlockConflict(
    val first: WeeklyTimeBlock,
    val second: WeeklyTimeBlock,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
)

/**
 * Finds the weekly rows that overlap in time, on the same day, in the same lane.
 *
 * **Why the app needs this at all.** The timeline resolves a collision by drawing the two rows side
 * by side, in different lanes — which is exactly right for two courses that genuinely run in
 * parallel, and exactly wrong for a double-booking made by mistake: the picture looks deliberate
 * either way, so the user never learns they double-booked themselves. The renderer cannot tell the
 * two apart. The lane is the only thing that can, so this rule reads the lane the way the renderer
 * does ([laneKey]) and reports the rows that share one.
 *
 * The rule, in full:
 *
 * 1. same [DayOfWeek];
 * 2. same lane — see [laneKey], which is the renderer's own mapping rather than the raw `trackId`;
 * 3. the minute ranges overlap by at least one minute. Rows that merely touch (08:00–09:00 next to
 *    09:00–10:00) do **not** conflict: back-to-back classes are the normal shape of a day;
 * 4. the week notations can both be active in some week — see [CourseWeekPattern.weeksMayOverlap].
 *
 * Rule 4 is what keeps the common Chinese timetable case quiet. A class on odd weeks and one on
 * even weeks at the same hour are not a clash, and reporting them would teach the user to ignore
 * the banner — the one failure mode a warning cannot afford.
 *
 * Every kind participates. An AVAILABLE row is a template the planner reads rather than an event
 * the timeline draws, but it is still a claim on the week, and two claims on the same minute in the
 * same lane are worth saying out loud.
 *
 * The result is ordered, so the banner does not reshuffle between recompositions: by day, then by
 * the contested start, then by the two row ids. Both rows of a pair are also ordered canonically
 * (earlier start first, ties broken by id), so a pair has one representation and not two.
 */
object WeeklyBlockConflicts {
    fun find(blocks: List<WeeklyTimeBlock>): List<WeeklyBlockConflict> {
        val ordered = blocks.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinute }, { it.id }))
        val found = buildList {
            for (i in ordered.indices) {
                val first = ordered[i]
                for (j in i + 1 until ordered.size) {
                    val second = ordered[j]
                    if (second.dayOfWeek != first.dayOfWeek) continue
                    if (first.laneKey() != second.laneKey()) continue
                    val start = maxOf(first.startMinute, second.startMinute)
                    val end = minOf(first.endMinute, second.endMinute)
                    if (start >= end) continue
                    if (!CourseWeekPattern.weeksMayOverlap(first.weekPattern, second.weekPattern)) continue
                    add(WeeklyBlockConflict(first, second, first.dayOfWeek, start, end))
                }
            }
        }
        return found.sortedWith(
            compareBy(
                { it.dayOfWeek },
                { it.startMinute },
                { it.endMinute },
                { it.first.id },
                { it.second.id },
            ),
        )
    }
}
