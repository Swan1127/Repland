package com.swan1127.repland.ui.schedule

import androidx.compose.runtime.Composable
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.ui.theme.ReplandAccent
import com.swan1127.repland.ui.theme.replandAccent

@Composable
internal fun scheduleAccent(kind: TimelineKind): ReplandAccent = replandAccent(
    when (kind) {
        TimelineKind.COURSE -> 0
        TimelineKind.TASK -> 1
        TimelineKind.REST -> 2
        TimelineKind.COMMITMENT -> 3
    },
)

internal fun scheduledClockMinutes(entries: List<TimelineEntry>): Int =
    entries.groupBy(TimelineEntry::date).values.sumOf { dayEntries ->
        var occupiedMinutes = 0
        var coveredUntil = 0
        dayEntries.sortedBy(TimelineEntry::startMinute).forEach { entry ->
            occupiedMinutes += (entry.endMinute - maxOf(entry.startMinute, coveredUntil)).coerceAtLeast(0)
            coveredUntil = maxOf(coveredUntil, entry.endMinute)
        }
        occupiedMinutes
    }

internal fun scheduledDurationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
}
