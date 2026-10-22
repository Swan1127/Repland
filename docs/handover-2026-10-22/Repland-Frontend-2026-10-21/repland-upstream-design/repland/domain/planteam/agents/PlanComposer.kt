package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AutonomousPlanRepair
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PlacementWindow
import com.swan1127.repland.domain.model.ScheduleTimeline
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

object PlanComposer {
    fun compose(ctx: PlanTeamContext, hints: PersonalizationHints?): PlanDraft {
        val windows = hints?.perTask?.mapNotNull { (taskId, hint) ->
            hint.preferredWindow?.let { taskId to PlacementWindow(it.startMinute, it.endMinute) }
        }?.toMap().orEmpty()
        val input = ctx.input.copy(
            weeklyBlocks = ctx.input.weeklyBlocks + ctx.calendarBlocks,
            lockedSegments = ctx.protectedSegments(),
            placementWindows = ctx.input.placementWindows + windows,
        )
        return when (ctx.requestKind) {
            PlanRequestKind.FULL_REPLAN, PlanRequestKind.PREVIEW_HABIT ->
                PlanGenerator.generateRemaining(input, ctx.current, clockAt(ctx.now))

            PlanRequestKind.ADD, PlanRequestKind.REMOVE, PlanRequestKind.ADJUST, PlanRequestKind.REPAIR ->
                repairOrRebuild(ctx, input, todayOnly = false)

            PlanRequestKind.RECOVER -> repairOrRebuild(ctx, input, todayOnly = true)
        }
    }

    private fun repairOrRebuild(ctx: PlanTeamContext, input: PlanGenerationInput, todayOnly: Boolean): PlanDraft {
        val current = ctx.current ?: return PlanGenerator.generateRemaining(
            input, null, clockAt(ctx.now), todayOnly = todayOnly,
        )
        val unknown = ctx.input.tasks
            .filter { it.status.isActive && it.totalDurationMinutes == null }
            .mapTo(mutableSetOf()) { it.id }
        val repaired = AutonomousPlanRepair.repair(
            input = input,
            current = current,
            now = ctx.now,
            runningTaskId = ctx.runningTaskId,
            targetDate = if (todayOnly) ctx.targetDate else null,
            excludedFromNewTaskIds = unknown,
        )
        if (unknown.isEmpty()) return repaired
        val segments = repaired.segments
        val unplaced = unknown - segments.mapTo(mutableSetOf()) { it.taskId }
        return repaired.copy(
            segments = segments,
            pendingTaskIds = (repaired.pendingTaskIds + unplaced).distinct(),
            unscheduledTasks = repaired.unscheduledTasks.filterNot { it.taskId in unknown },
        )
    }

    fun project(ctx: PlanTeamContext, draft: PlanDraft): SimulatedLanding = SimulatedLanding(
        ctx.targetDate,
        ScheduleTimeline.entries(
            ctx.targetDate,
            ctx.input.weeklyBlocks + ctx.calendarBlocks,
            ctx.input.dateOverrides,
            draft.segments,
            ctx.input.tasks,
            ctx.input.semesterFirstWeekMonday,
        ),
    )

    private fun clockAt(now: LocalDateTime): Clock =
        Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
}
