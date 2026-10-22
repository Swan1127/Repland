package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.PlanningConstraintValidator
import com.swan1127.repland.domain.model.Task

object ConflictChecker {
    private const val DAY_MINUTES = 24 * 60


    fun overlaps(draft: PlanDraft, ctx: PlanTeamContext? = null): List<ConflictItem> = buildList {
        val segments = draft.segments
            .filter { ctx == null || !hasEnded(it, ctx) }
            .sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::endMinute))
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                val a = segments[i]
                val b = segments[j]
                if (a.date != b.date || a.startMinute >= b.endMinute || a.endMinute <= b.startMinute) continue
                val start = maxOf(a.startMinute, b.startMinute)
                val end = minOf(a.endMinute, b.endMinute)
                val duplicate = a.taskId == b.taskId && a.startMinute == b.startMinute && a.endMinute == b.endMinute
                val retained = ctx != null && isRetained(a, ctx) && isRetained(b, ctx)
                add(
                    ConflictItem(
                        type = if (duplicate) ConflictType.DUPLICATE_PLACEMENT else ConflictType.OVERLAP,
                        affectedTaskIds = listOf(a.taskId, b.taskId).distinct(),
                        startMinute = start,
                        endMinute = end,
                        severity = if (retained) Severity.WARN else Severity.FATAL,
                        autoFixable = false,
                        message = buildString {
                            append("${label(a.taskId, ctx)} 与 ${label(b.taskId, ctx)} 在 ${a.date} ")
                            append("${format(start)}–${format(end)} ")
                            append(if (duplicate) "重复放置。" else "重叠。")
                            if (retained) append("这一段来自已锁定或正在执行的安排，需要你决定是否保留。")
                        },
                    ),
                )
            }
        }
    }


    fun constraints(draft: PlanDraft, ctx: PlanTeamContext): List<ConflictItem> = buildList {
        val tasksById = ctx.input.tasks.associateBy(Task::id)
        val blocks = ctx.input.weeklyBlocks + ctx.calendarBlocks
        draft.segments.filter { !hasEnded(it, ctx) }.forEach { segment ->
            val task = tasksById[segment.taskId] ?: return@forEach
            val retained = isRetained(segment, ctx)
            val severity = if (retained) Severity.WARN else Severity.FATAL
            val label = label(segment.taskId, ctx)
            val range = "${segment.date} ${format(segment.startMinute)}–${format(segment.endMinute)}"

            if (segment.startMinute !in 0 until DAY_MINUTES || segment.endMinute !in 1..DAY_MINUTES ||
                segment.endMinute <= segment.startMinute
            ) {
                add(
                    ConflictItem(
                        ConflictType.WEEK_PATTERN_CLASH, listOf(segment.taskId), segment.startMinute,
                        segment.endMinute, severity, autoFixable = false,
                        message = "$label 在 $range 的起止时间无效。",
                    ),
                )
                return@forEach
            }

            val available = PlanningConstraintValidator.isAvailable(
                date = segment.date,
                startMinute = segment.startMinute,
                endMinute = segment.endMinute,
                weeklyBlocks = blocks,
                dateOverrides = ctx.input.dateOverrides,
                semesterFirstWeekMonday = ctx.input.semesterFirstWeekMonday,
            )
            if (!available) {
                add(
                    ConflictItem(
                        ConflictType.WEEK_PATTERN_CLASH, listOf(segment.taskId), segment.startMinute,
                        segment.endMinute, severity, autoFixable = false,
                        message = "$label 在 $range 落进了课程、休息或日期封锁。" +
                            if (retained) "这是既有安排，未被自动移动。" else "",
                    ),
                )
            }

            if (task.dueDate?.isBefore(segment.date) == true) {
                add(
                    ConflictItem(
                        ConflictType.AFTER_DEADLINE, listOf(segment.taskId), segment.startMinute,
                        segment.endMinute, severity, autoFixable = false,
                        message = "$label 被排到 ${segment.date}，晚于截止日 ${task.dueDate}。",
                    ),
                )
            }

            if (task.scheduledForDate?.isAfter(segment.date) == true) {
                add(
                    ConflictItem(
                        ConflictType.BEFORE_INTENDED_DAY, listOf(segment.taskId), segment.startMinute,
                        segment.endMinute, severity, autoFixable = false,
                        message = "$label 被排到 ${segment.date}，早于你设定的开始日 ${task.scheduledForDate}。",
                    ),
                )
            }
        }
    }


    fun capacity(draft: PlanDraft, ctx: PlanTeamContext? = null): List<ConflictItem> = buildList {
        if (draft.unscheduledTasks.isNotEmpty()) {
            add(
                ConflictItem(
                    type = ConflictType.CAPACITY_SHORTFALL,
                    affectedTaskIds = draft.unscheduledTasks.map { it.taskId },
                    startMinute = 0,
                    endMinute = 0,
                    severity = Severity.WARN,
                    autoFixable = false,
                    message = "有 ${draft.unscheduledTasks.size} 个任务排不进可用时间：" +
                        draft.unscheduledTasks.take(3).joinToString("、") { "${label(it.taskId, ctx)}（还差 ${it.remainingMinutes} 分钟）" } +
                        if (draft.unscheduledTasks.size > 3) " 等。" else "。",
                ),
            )
        }

        draft.pendingTaskIds.forEach { id ->
            add(
                ConflictItem(
                    type = ConflictType.UNSCHEDULABLE,
                    affectedTaskIds = listOf(id),
                    startMinute = 0,
                    endMinute = 0,
                    severity = Severity.INFO,
                    autoFixable = false,
                    message = "${label(id, ctx)} 缺时长或无可排窗口，保留为待安排。",
                ),
            )
        }
    }

    fun assemble(
        overlaps: List<ConflictItem>,
        constraints: List<ConflictItem>,
        capacity: List<ConflictItem>,
    ): ConflictReport = ConflictReport(overlaps + constraints + capacity)

    fun check(draft: PlanDraft, ctx: PlanTeamContext): ConflictReport =
        assemble(overlaps(draft, ctx), constraints(draft, ctx), capacity(draft, ctx))


    private fun isRetained(segment: PlannedSegment, ctx: PlanTeamContext): Boolean {
        if (ctx.requestKind == PlanRequestKind.FULL_REPLAN || ctx.requestKind == PlanRequestKind.PREVIEW_HABIT) {
            val sameExisting = ctx.current?.segments.orEmpty().any { existing ->
                existing.taskId == segment.taskId && existing.date == segment.date &&
                    existing.startMinute == segment.startMinute && existing.endMinute == segment.endMinute && existing.trackId == segment.trackId
            }
            if (sameExisting && (!PlanningConstraintValidator.hasExplicitAvailability(ctx.input) || ctx.todayOnly && segment.date != ctx.targetDate)) return true
            return ctx.protectedSegments().any { protected ->
                protected.taskId == segment.taskId && protected.date == segment.date &&
                    protected.startMinute == segment.startMinute && protected.endMinute == segment.endMinute &&
                    protected.trackId == segment.trackId
            }
        }
        return ctx.current?.segments.orEmpty().any { existing ->
            if (existing.id.isNotBlank() && segment.id.isNotBlank()) {
                existing.id == segment.id
            } else {
                existing.taskId == segment.taskId && existing.date == segment.date &&
                    existing.startMinute == segment.startMinute && existing.endMinute == segment.endMinute &&
                    existing.trackId == segment.trackId
            }
        }
    }

    private fun hasEnded(segment: PlannedSegment, ctx: PlanTeamContext): Boolean {
        val today = ctx.now.toLocalDate()
        return segment.date < today ||
            (segment.date == today && segment.endMinute <= ctx.now.hour * 60 + ctx.now.minute)
    }

    private fun label(taskId: String, ctx: PlanTeamContext?): String =
        ctx?.input?.tasks?.firstOrNull { it.id == taskId }?.displayName?.let { "「$it」" } ?: "任务 $taskId"

    private fun format(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
