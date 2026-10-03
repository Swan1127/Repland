package com.swan1127.repland.domain.model

import java.time.LocalDateTime

/** Read-only schedule projection. A time slot is never evidence of actual execution. */
object TodayFocus {
    fun next(entries: List<TimelineEntry>, now: LocalDateTime): TimelineEntry? = entries
        .filter { !it.taskClosed && it.end.isAfter(now) }
        .minWithOrNull(compareBy(TimelineEntry::start, TimelineEntry::end, TimelineEntry::id))

    fun startSegment(entry: TimelineEntry?): String? = entry
        ?.takeIf { it.kind == TimelineKind.TASK && !it.taskClosed && it.taskId != null && it.id.startsWith("segment:") }
        ?.id?.removePrefix("segment:")?.takeIf(String::isNotBlank)

    fun pending(entries: List<TimelineEntry>, now: LocalDateTime, executingTaskId: String?): List<TimelineEntry> = entries
        .filter { it.kind == TimelineKind.TASK && !it.taskClosed && it.taskId != null && it.taskId != executingTaskId && !it.end.isAfter(now) }
        .sortedWith(compareBy(TimelineEntry::end, TimelineEntry::id))
        .distinctBy(TimelineEntry::taskId)
}
