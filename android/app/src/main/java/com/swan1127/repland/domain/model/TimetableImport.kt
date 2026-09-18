package com.swan1127.repland.domain.model

import java.time.DayOfWeek

/** A course recognized from a timetable PDF before the user confirms the import. */
data class ImportedCourse(
    val id: String,
    val title: String,
    val dayOfWeek: DayOfWeek,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPattern: String?,
)

/**
 * Default class-period clock used only because common timetable PDFs name periods, not clock time.
 * Imported entries remain editable in the fixed-time screen.
 */
object ClassPeriodClock {
    private val starts = mapOf(
        1 to 8 * 60,
        2 to 8 * 60 + 50,
        3 to 10 * 60,
        4 to 10 * 60 + 50,
        5 to 14 * 60,
        6 to 14 * 60 + 50,
        7 to 16 * 60,
        8 to 16 * 60 + 50,
        9 to 19 * 60,
        10 to 19 * 60 + 50,
        11 to 21 * 60,
        12 to 21 * 60 + 50,
    )

    private val ends = mapOf(
        1 to 8 * 60 + 45,
        2 to 9 * 60 + 35,
        3 to 10 * 60 + 45,
        4 to 11 * 60 + 35,
        5 to 14 * 60 + 45,
        6 to 15 * 60 + 35,
        7 to 16 * 60 + 45,
        8 to 17 * 60 + 35,
        9 to 19 * 60 + 45,
        10 to 20 * 60 + 35,
        11 to 21 * 60 + 45,
        12 to 22 * 60 + 35,
    )

    fun toWeeklyTimeBlockDraft(course: ImportedCourse): WeeklyTimeBlockDraft? {
        val startMinute = starts[course.startPeriod] ?: return null
        val endMinute = ends[course.endPeriod] ?: return null
        if (startMinute >= endMinute) return null
        return WeeklyTimeBlockDraft(
            title = course.title,
            kind = TimeBlockKind.COURSE,
            dayOfWeek = course.dayOfWeek,
            startMinute = startMinute,
            endMinute = endMinute,
            weekPattern = course.weekPattern,
        )
    }
}
