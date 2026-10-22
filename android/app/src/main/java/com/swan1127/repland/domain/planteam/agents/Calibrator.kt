package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskStatus

object Calibrator {
    fun report(ctx: PlanTeamContext): CalibrationReport {
        val pulse = ctx.execution
        val tasks = ctx.input.tasks.associateBy(Task::id)
        fun label(id: String): String =
            tasks[id]?.displayName?.takeIf { it.isNotBlank() }?.let { "「$it」" } ?: "任务 $id"

        val plannedToday = ctx.current?.segments.orEmpty().filter { it.date == ctx.targetDate }
        val plannedMinutes = plannedToday.sumOf { it.endMinute - it.startMinute }

        val signals = buildList {
            (ctx.input.tasks.filter { it.status == TaskStatus.IN_PROGRESS }.map(Task::id) +
                listOfNotNull(ctx.runningTaskId)).distinct().forEach { id ->
                add(
                    CalibrationSignal(
                        CalibrationKind.ACTIVE_SESSION, id,
                        if (id == ctx.runningTaskId) "${label(id)} 有正在进行的专注会话；本轮保护它已有的安排。"
                        else "${label(id)} 已标记为进行中；本轮保护它已有的安排，不据此推断计时会话。",
                    ),
                )
            }
            pulse?.completedTaskIds?.sorted()?.forEach { id ->
                add(
                    CalibrationSignal(
                        CalibrationKind.COMPLETED, id,
                        "${label(id)} 今天已完成；草案里它尚未结束的未来时段会被回收。",
                    ),
                )
            }
            pulse?.partialTaskIds?.sorted()?.forEach { id ->
                add(
                    CalibrationSignal(
                        CalibrationKind.PARTIAL, id,
                        "${label(id)} 今天有部分完成记录；剩余工作仍按任务进度计算。",
                    ),
                )
            }
            pulse?.postponedTaskIds?.sorted()?.forEach { id ->
                add(
                    CalibrationSignal(
                        CalibrationKind.POSTPONED, id,
                        "${label(id)} 今天被顺延；剩余工作继续留在未来安排里。",
                    ),
                )
            }
            if (pulse != null) {
                add(
                    CalibrationSignal(
                        CalibrationKind.DAY_RECORD, null,
                        "今天计划 ${plannedToday.size} 段共 $plannedMinutes 分钟；已确认 ${pulse.feedbackCount} 条执行反馈。",
                        minutes = plannedMinutes,
                    ),
                )
            }
        }

        val notes = buildList {
            when {
                pulse == null -> add("本轮没有接入今天的执行记录；校准师不据此下结论。")
                pulse.isEmpty -> add("今天还没有执行记录，按现有计划与画像继续。")
                else -> add(
                    "今天已有 ${pulse.feedbackCount} 条执行反馈；只用于解释本轮保留与回收，" +
                        "不改任务时长和优先级。",
                )
            }
            if (ctx.runningTaskId != null || ctx.input.tasks.any { it.status == TaskStatus.IN_PROGRESS }) {
                add(if (ctx.runningTaskId != null) "已读取当前专注会话，其已有安排保持受保护。"
                    else "已标记为进行中的任务，其已有安排保持受保护；未发现当前专注会话。")
            }
            add("已结束的时段只作为历史，不会抵扣剩余工作量；剩余量仍由任务进度决定。")
            if (plannedToday.isNotEmpty()) {
                add("当前计划今天有 ${plannedToday.size} 段，共 $plannedMinutes 分钟。")
            }
        }

        return CalibrationReport(
            asOf = ctx.targetDate,
            connected = pulse != null,
            plannedSegmentsToday = plannedToday.size,
            plannedMinutesToday = plannedMinutes,
            feedbackCount = pulse?.feedbackCount ?: 0,
            signals = signals,
            notes = notes,
        )
    }
}
