package com.swan1127.repland.domain.model

import java.time.LocalDate

/** Explicitly confirmed minimal input; an empty schedule is never assumed to be free. */
data class QuickAvailability(val date: LocalDate, val startMinute: Int, val endMinute: Int, val repeatWeekly: Boolean = false) {
    fun isValid(today: LocalDate = LocalDate.now()) = !date.isBefore(today) &&
        startMinute in 0 until 1440 && endMinute in 1..1440 && startMinute < endMinute
}
