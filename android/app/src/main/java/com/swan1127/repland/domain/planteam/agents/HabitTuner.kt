package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority

object HabitTuner {

    private val AtRiskWindow = TimeWindow(480, 660)

    fun tune(
        brief: PlanningBrief,
        ctx: PlanTeamContext,
        calibration: CalibrationReport? = null,
    ): PersonalizationHints {
        val facts = ctx.profileFacts
        val memory = facts.memory

        val perTask = brief.orderedTasks.associate { assessment ->
            val task = ctx.input.tasks.firstOrNull { it.id == assessment.taskId }
            val isDeep = task?.userPriority == TaskPriority.REQUIRED || assessment.score >= 80
            val window = if (facts.isConfigured && facts.preferMorningForDeepWork && isDeep) TimeWindow(480, 720) else null
            val style = if (isDeep) BlockStyle.DEEP_FOCUS else BlockStyle.FRAGMENTED
            val rationale = buildString {
                append("得分 ${assessment.score}，建议 ${if (isDeep) "深度专注块" else "就近碎片块"}。")
                val signal = memory?.forTask(assessment.taskId)?.takeIf { it.isAtRisk }
                if (signal != null) {
                    append(
                        "记忆参考：这条「${signal.content.take(20)}」的可提取率已降到 " +
                            "${(signal.retrievability * 100).toInt()}%，建议优先当天靠前的可用时段。",
                    )
                }
            }
            assessment.taskId to PlacementHint(
                window ?: if (memory?.forTask(assessment.taskId)?.isAtRisk == true) AtRiskWindow else null,
                style,
                rationale,
            )
        }

        val perCategory = TaskCategory.knownEntries.associateWith { cat ->
            CategoryHint(
                if (facts.isConfigured && facts.preferMorningForDeepWork) TimeWindow(480, 720) else null,
                BlockStyle.DEEP_FOCUS,
                if (facts.isConfigured && facts.preferMorningForDeepWork) "类别 $cat 参考上午偏好。" else "类别 $cat 没有已确认的时段偏好。",
            )
        }

        return PersonalizationHints(
            perTask = perTask,
            perCategory = perCategory,
            bufferMinutes = facts.bufferMinutes,
            profileNotes = facts.notes +
                (if (facts.isConfigured) {
                    listOf("偏好参考缓冲 ${facts.bufferMinutes} 分钟（本轮不自动空出时段）。")
                } else {
                    listOf("本轮未接入个人习惯设置，只按任务优先级与类别偏好放置，不假设你的作息。")
                }) +
                calibration?.notes.orEmpty() +
                memory?.takeUnless { it.isEmpty }?.let { listOf(it.logNote() + "只影响放置，未改动时长与优先级。") }.orEmpty(),
        )
    }


    suspend fun tuneWithAi(
        brief: PlanningBrief,
        ctx: PlanTeamContext,
        advisor: AiAdvisor,
        calibration: CalibrationReport? = null,
        base: PersonalizationHints = tune(brief, ctx, calibration),
    ): PersonalizationHints {
        if (advisor is NoOpAiAdvisor) return base
        val extraNotes = buildList {
            brief.aiInsights.forEach { (taskId, insight) ->
                val title = ctx.input.tasks.firstOrNull { it.id == taskId }?.displayName ?: taskId
                val parts = buildList {
                    if (insight.difficulty != null) add("难度 ${insight.difficulty}")
                    if (insight.suggestedDurationMinutes != null) add("约 ${insight.suggestedDurationMinutes} 分钟")
                }
                if (parts.isNotEmpty()) add("AI 估计『$title』${parts.joinToString("，")}（仅供参考，未改动你的时长设置）。")
            }
        }
        val upgradedPerTask = base.perTask.mapValues { (taskId, hint) ->
            val diff = brief.aiInsights[taskId]?.difficulty
            if (diff != null && diff >= 4 && hint.preferredWindow == null &&
                ctx.profileFacts.isConfigured && ctx.profileFacts.preferMorningForDeepWork
            ) {
                hint.copy(
                    preferredWindow = TimeWindow(480, 720),
                    rationale = hint.rationale + "；AI 估计高难度，建议参考已确认的上午偏好。",
                )
            } else {
                hint
            }
        }
        return base.copy(perTask = upgradedPerTask, profileNotes = base.profileNotes + extraNotes)
    }
}
