package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AiAdviceValidation
import com.swan1127.repland.domain.model.AiAdviceValidator
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiDifficultyAndDurationAdvice
import com.swan1127.repland.domain.model.AiRequestFactory
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.LocalPriorityAssessment
import com.swan1127.repland.domain.model.LocalPriorityRanker
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import kotlinx.coroutines.CancellationException

object RequirementsAnalyst {

    fun rank(ctx: PlanTeamContext): List<LocalPriorityAssessment> =
        LocalPriorityRanker.rank(
            tasks = ctx.input.tasks,
            categoryPreferences = ctx.input.categoryPreferences,
            today = ctx.targetDate,
            manualTaskOrder = ctx.input.manualTaskOrder,
        )


    fun states(ctx: PlanTeamContext): Map<String, TaskPlanningState> =
        ctx.input.tasks.associate { it.id to stateOf(it, ctx) }


    fun questions(ctx: PlanTeamContext): PlanningNotes {
        val tasks = ctx.input.tasks.filter { it.status.isActive }
        val today = ctx.targetDate
        val openQuestions = buildList {
            val noDurationWithDue = tasks.filter { it.totalDurationMinutes == null && it.dueDate != null }
            if (noDurationWithDue.isNotEmpty()) {
                add("有 ${noDurationWithDue.size} 个任务有截止但缺时长，需你确认估算或排为待安排。")
            }
        }
        val softGoals = buildList {
            val required = tasks.count { it.userPriority == TaskPriority.REQUIRED }
            if (required > 0) add("含 $required 个必须项，优先保底。")
            val overdue = tasks.count { t ->
                val d = t.dueDate
                d != null && d.isBefore(today)
            }
            if (overdue > 0) add("$overdue 个任务已逾期，建议优先处理或延期。")
        }
        return PlanningNotes(openQuestions, softGoals)
    }

    fun analyze(ctx: PlanTeamContext): PlanningBrief {
        val notes = questions(ctx)
        return PlanningBrief(
            orderedTasks = rank(ctx),
            states = states(ctx),
            openQuestions = notes.openQuestions,
            softGoals = notes.softGoals,
        )
    }


    suspend fun enrichWithAi(base: PlanningBrief, ctx: PlanTeamContext, advisor: AiAdvisor): PlanningBrief {
        if (advisor is NoOpAiAdvisor) return base
        val insights = base.orderedTasks.mapNotNull { assessment ->
            val task = ctx.input.tasks.firstOrNull { it.id == assessment.taskId } ?: return@mapNotNull null
            val uReq = AiRequestFactory.taskUnderstanding(task, emptyList(), ctx.current)
            val uAdvice = (requestSafely(advisor, uReq) as? AiAdvisorResult.Advice)
                ?.takeIf { AiAdviceValidator.validate(uReq, it.response) is AiAdviceValidation.Valid }
                ?.response as? AiTaskUnderstandingAdvice
            val dReq = AiRequestFactory.difficultyAndDuration(task, emptyList(), ctx.current)
            val dAdvice = (requestSafely(advisor, dReq) as? AiAdvisorResult.Advice)
                ?.takeIf { AiAdviceValidator.validate(dReq, it.response) is AiAdviceValidation.Valid }
                ?.response as? AiDifficultyAndDurationAdvice
            if (uAdvice == null && dAdvice == null) null
            else assessment.taskId to AiTaskInsight(
                summary = uAdvice?.summary,
                dependencyNote = uAdvice?.dependencyNote,
                difficulty = dAdvice?.difficulty,
                suggestedDurationMinutes = dAdvice?.suggestedDurationMinutes,
            )
        }.toMap()
        return base.copy(aiInsights = insights)
    }

    suspend fun analyzeWithAi(ctx: PlanTeamContext, advisor: AiAdvisor): PlanningBrief =
        enrichWithAi(analyze(ctx), ctx, advisor)

    private suspend fun requestSafely(
        advisor: AiAdvisor,
        request: com.swan1127.repland.domain.model.AiAdvisorRequest,
    ): AiAdvisorResult? = try {
        advisor.request(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun stateOf(task: Task, ctx: PlanTeamContext): TaskPlanningState {
        if (task.status == TaskStatus.COMPLETED ||
            task.status == TaskStatus.CANCELLED ||
            task.status == TaskStatus.REPLACED
        ) {
            return TaskPlanningState.DEFERRED
        }
        val locked = ctx.input.lockedSegments.any { it.taskId == task.id && it.isLocked } ||
            task.userPriority == TaskPriority.REQUIRED
        if (locked) return TaskPlanningState.LOCKED
        if (task.totalDurationMinutes == null) return TaskPlanningState.NEEDS_DURATION
        return TaskPlanningState.SCHEDULABLE
    }
}
