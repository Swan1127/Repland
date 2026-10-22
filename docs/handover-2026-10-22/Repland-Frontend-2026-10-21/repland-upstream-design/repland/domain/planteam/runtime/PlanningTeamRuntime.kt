package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.planteam.Calibrator
import com.swan1127.repland.domain.planteam.ConflictChecker
import com.swan1127.repland.domain.planteam.HabitTuner
import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.PlanComposer
import com.swan1127.repland.domain.planteam.PlanTeamContext
import com.swan1127.repland.domain.planteam.PlanTeamResult
import com.swan1127.repland.domain.planteam.PlanTeamRouter
import com.swan1127.repland.domain.planteam.PlanningBrief
import com.swan1127.repland.domain.planteam.RequirementsAnalyst
import com.swan1127.repland.domain.planteam.ScheduleAvailability
import com.swan1127.repland.domain.planteam.TaskPlanningState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class PlanningTeamRuntime(
    private val memoryBank: AgentMemoryBank = AgentMemoryBank(),
    private val runIdFactory: () -> String = { "run-" + UUID.randomUUID().toString().take(8) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    
    fun run(ctx: PlanTeamContext, advisor: AiAdvisor = NoOpAiAdvisor): Flow<TeamEvent> = channelFlow {
        TeamSession(ctx, advisor, memoryBank, runIdFactory(), clock) { send(it) }.execute()
    }

    
    suspend fun execute(
        ctx: PlanTeamContext,
        advisor: AiAdvisor = NoOpAiAdvisor,
        onEvent: (TeamEvent) -> Unit = {},
    ): PlanTeamResult {
        var result: PlanTeamResult? = null
        run(ctx, advisor).collect { event ->
            onEvent(event)
            if (event is DraftReady) result = event.result
        }
        return result ?: error("规划团队没有产出草案：运行未走到验收通过。")
    }

    fun memoryBank(): AgentMemoryBank = memoryBank
}

private class TeamSession(
    private val ctx: PlanTeamContext,
    private val advisor: AiAdvisor,
    private val memoryBank: AgentMemoryBank,
    private val runId: String,
    private val clock: () -> Long,
    private val send: suspend (TeamEvent) -> Unit,
) {
    private val workspace = AgentWorkspace(runId, ctx)
    private val memories = LinkedHashMap<PlanAgentId, AgentMemory>()
    private val criteriaLog = mutableListOf<CriterionResult>()
    private var seq = 0

    private val advisorAvailable: Boolean = advisor !is NoOpAiAdvisor

    suspend fun execute() {
        val selected = PlanTeamRouter.select(ctx.requestKind, ctx)
        val order = executionOrder(selected)
        val plannedTools = order.sumOf { AgentRegistry.spec(it.agent).tools.size }

        emit { s, t ->
            TeamStarted(
                seq = s, atMillis = t, runId = runId, requestKind = ctx.requestKind,
                targetDate = ctx.targetDate, agents = selected.toList(),
                plannedToolCalls = plannedTools, memoryNote = teamMemoryNote(),
            )
        }
        workspace.appendTrace("调度：请求=${ctx.requestKind}，选中 agent=[${selected.joinToString()}]")

        var index = 0
        try {
            for (step in order) {
                index += 1
                runStep(step.agent, step.backfilled, index, order.size)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            emit { s, t ->
                TeamFailed(s, t, null, error.message ?: "未知错误（${error::class.simpleName}）")
            }
            throw error
        }

        finishWithAcceptance()
    }

    private data class Step(val agent: PlanAgentId, val backfilled: Boolean)

    
    private fun executionOrder(selected: Set<PlanAgentId>): List<Step> {
        val steps = LinkedHashMap<PlanAgentId, Step>()
        val upstream = selected.filter { it != PlanAgentId.CONFLICT && it != PlanAgentId.COMPOSER }
        for (agent in upstream) {
            for (dep in dependenciesOf(AgentRegistry.spec(agent))) {
                if (dep !in selected && dep !in steps) steps[dep] = Step(dep, backfilled = true)
            }
            steps[agent] = Step(agent, backfilled = false)
        }
        steps[PlanAgentId.COMPOSER] = Step(PlanAgentId.COMPOSER, backfilled = false)
        if (PlanAgentId.CONFLICT in selected) steps[PlanAgentId.CONFLICT] = Step(PlanAgentId.CONFLICT, false)
        return steps.values.toList()
    }

    private fun dependenciesOf(spec: AgentSpec): List<PlanAgentId> = when (spec.agentId) {
        PlanAgentId.HABIT -> listOf(PlanAgentId.REQUIREMENTS)
        else -> emptyList()
    }

    private suspend fun runStep(agent: PlanAgentId, backfilled: Boolean, index: Int, total: Int) {
        val spec = AgentRegistry.spec(agent)
        val memory = memories.getOrPut(agent) { memoryBank.seed(agent, runId) }
        workspace.markRan(agent)

        emit { s, t ->
            AgentStarted(
                seq = s, atMillis = t, agentId = agent, displayName = spec.displayName,
                index = index, total = total,
                mission = if (backfilled) "${spec.mission}（上游补跑）" else spec.mission,
                memoryNote = memoryNoteFor(memory),
            )
        }
        val startedNanos = System.nanoTime()

        val outcomeLine = when (agent) {
            PlanAgentId.REQUIREMENTS -> runRequirements(spec, memory)
            PlanAgentId.AVAILABILITY -> runAvailability(spec, memory)
            PlanAgentId.HABIT -> runHabit(spec, memory)
            PlanAgentId.CALIBRATOR -> runCalibrator(spec, memory)
            PlanAgentId.COMPOSER -> runComposer(spec, memory)
            PlanAgentId.CONFLICT -> runConflict(spec, memory)
        }

        val criteria = spec.criteria.map { it.evaluate(acceptanceContext(memory)) }
        criteriaLog += criteria
        val writes = memory.written()
        memoryBank.commit(memory, ctx.requestKind.name, outcomeLine, clock())

        emit { s, t ->
            AgentFinished(
                seq = s, atMillis = t, agentId = agent, displayName = spec.displayName,
                outcomeLine = outcomeLine, criteria = criteria, memoryWrites = writes,
                elapsedMillis = elapsedMillis(startedNanos),
            )
        }
    }

    private suspend fun runRequirements(spec: AgentSpec, memory: AgentMemory): String {
        val previousCount = memory.recallInt("taskCount")

        val ranked = invoke(spec, "req.rank", "${ctx.input.tasks.size} 个任务", { "${it.size} 项排序" }) {
            RequirementsAnalyst.rank(ctx)
        }
        val states = invoke(spec, "req.states", "${ctx.input.tasks.size} 个任务", ::describeStates) {
            RequirementsAnalyst.states(ctx)
        }
        val notes = invoke(spec, "req.questions", "${ctx.input.tasks.size} 个任务", { "${it.openQuestions.size} 条待确认 / ${it.softGoals.size} 条软目标" }) {
            RequirementsAnalyst.questions(ctx)
        }

        var brief = PlanningBrief(
            orderedTasks = ranked,
            states = states,
            openQuestions = notes.openQuestions,
            softGoals = notes.softGoals,
        )

        brief = if (!advisorAvailable) {
            skip(spec, "req.ai", "${brief.orderedTasks.size} 个任务", "未配置 AI 提供方，按本地规则继续")
            brief
        } else {
            invoke(spec, "req.ai", "${brief.orderedTasks.size} 个任务", { "AI 洞察 ${it.aiInsights.size} 条（已校验）" }) {
                RequirementsAnalyst.enrichWithAi(brief, ctx, advisor)
            }
        }

        val schedulable = states.count { it.value == TaskPlanningState.SCHEDULABLE }
        val needsDuration = states.count { it.value == TaskPlanningState.NEEDS_DURATION }
        workspace.putBrief(
            spec.agentId, brief,
            "${brief.orderedTasks.size} 个任务，可排 $schedulable，缺时长 $needsDuration",
        )
        memory.note("taskCount", brief.orderedTasks.size)
        memory.note("schedulableCount", schedulable)
        memory.note("needsDurationCount", needsDuration)
        memory.note("openQuestionCount", brief.openQuestions.size)

        val delta = when {
            previousCount == null -> ""
            previousCount == brief.orderedTasks.size -> "，与上次任务数相同"
            else -> "，比上次${if (brief.orderedTasks.size > previousCount) "多" else "少"} ${kotlin.math.abs(brief.orderedTasks.size - previousCount)} 个"
        }
        workspace.appendTrace(
            "需求分析：任务 ${brief.orderedTasks.size} 项，可排 $schedulable 项，待确认 ${brief.openQuestions.size} 项，软目标 ${brief.softGoals.size} 条$delta。",
        )
        return "${brief.orderedTasks.size} 个任务，可排 $schedulable，待确认 ${brief.openQuestions.size}$delta"
    }

    private suspend fun runAvailability(spec: AgentSpec, memory: AgentMemory): String {
        val blocked = invoke(spec, "avail.blocks", "目标日 ${ctx.targetDate}", { "${it.size} 段固定占用" }) {
            ScheduleAvailability.blocked(ctx)
        }
        val freeSlots = invoke(spec, "avail.slots", "${blocked.size} 段固定占用", { "${it.size} 个 30 分钟槽" }) {
            ScheduleAvailability.freeSlots(ctx)
        }
        val map = ScheduleAvailability.assemble(ctx, blocked, freeSlots)
        workspace.putAvailability(
            spec.agentId, map,
            "固定占用 ${blocked.size} 段，空闲 ${freeSlots.size} 槽 ≈ ${map.capacityMinutes} 分钟",
        )
        memory.note("blockedCount", blocked.size)
        memory.note("freeSlotCount", freeSlots.size)
        memory.note("capacityMinutes", map.capacityMinutes)
        workspace.appendTrace(
            "时间编排：目标日 ${map.targetDate}，固定占用 ${blocked.size}，空闲槽 ${freeSlots.size}（≈${map.capacityMinutes}分钟）。",
        )
        return "空闲 ${freeSlots.size} 个 30 分钟槽，容量 ≈ ${map.capacityMinutes} 分钟"
    }

    private suspend fun runHabit(spec: AgentSpec, memory: AgentMemory): String {
        val brief = workspace.requireBrief(spec.agentId)
        val facts = invoke(spec, "habit.profile", "画像事实", { "${it.size} 条" }) {
            (if (ctx.profileFacts.isConfigured) {
                listOf(
                    "缓冲 ${ctx.profileFacts.bufferMinutes} 分钟",
                    "上午深专注=${if (ctx.profileFacts.preferMorningForDeepWork) "是" else "否"}",
                    "专注块 ${ctx.profileFacts.focusBlockMinutes} 分钟",
                )
            } else {
                listOf("本轮未接入习惯设置，没有可用的作息偏好")
            }) + ctx.profileFacts.notes
        }
        val reference = ctx.profileFacts.memory
        if (reference == null) {
            skip(spec, "habit.memory", "记忆参考", "设置里未开启记忆参考，本轮不读记忆状态")
        } else {
            invoke(spec, "habit.memory", reference.logNote(), { "${it.atRisk.size} 条濒临遗忘" }) { reference }
        }
        val calibration = workspace.calibration
        val base = invoke(
            spec, "habit.hints",
            "${brief.orderedTasks.size} 个任务" + (calibration?.let { " / 校准 ${it.signals.size} 条" } ?: ""),
            { "${it.perTask.size} 个任务偏好 / ${it.profileNotes.size} 条备注" },
        ) {
            HabitTuner.tune(brief, ctx, calibration)
        }
        val hints = if (!advisorAvailable) {
            skip(spec, "habit.ai", "${brief.aiInsights.size} 条 AI 洞察", "未配置 AI 提供方，使用本地习惯规则")
            base
        } else {
            invoke(spec, "habit.ai", "${brief.aiInsights.size} 条 AI 洞察", { "备注 ${it.profileNotes.size} 条（含 AI 提示）" }) {
                HabitTuner.tuneWithAi(brief, ctx, advisor, calibration, base)
            }
        }
        workspace.putHints(
            spec.agentId, hints,
            "${hints.perTask.size} 个任务偏好" +
                if (ctx.profileFacts.isConfigured) "，缓冲 ${hints.bufferMinutes} 分钟" else "（未接入习惯设置）",
        )
        if (ctx.profileFacts.isConfigured) memory.note("bufferMinutes", hints.bufferMinutes)
        memory.note("hintCount", hints.perTask.size)
        memory.note("aiNoteCount", hints.profileNotes.size)
        memory.note("memoryAtRisk", reference?.atRisk?.size ?: 0)
        workspace.appendTrace(
            if (ctx.profileFacts.isConfigured) {
                "习惯调适：缓冲 ${hints.bufferMinutes} 分钟，已为 ${hints.perTask.size} 个任务生成放置偏好（依据 ${facts.size} 条画像事实）。"
            } else {
                "习惯调适：未接入习惯设置，仅按任务优先级为 ${hints.perTask.size} 个任务生成放置偏好（依据 ${facts.size} 条说明）。"
            },
        )
        return if (ctx.profileFacts.isConfigured) {
            "为 ${hints.perTask.size} 个任务给出偏好，缓冲 ${hints.bufferMinutes} 分钟"
        } else {
            "为 ${hints.perTask.size} 个任务给出偏好（未接入习惯设置）"
        }
    }

    private suspend fun runCalibrator(spec: AgentSpec, memory: AgentMemory): String {
        val pulse = ctx.execution
        val digest = if (pulse == null) {
            "未接入今天的执行记录"
        } else {
            "完成 ${pulse.completedTaskIds.size} / 部分 ${pulse.partialTaskIds.size} / " +
                "顺延 ${pulse.postponedTaskIds.size}，反馈 ${pulse.feedbackCount} 条"
        }
        invoke(spec, "calibrate.execution", "今天 ${ctx.targetDate}", { digest }) { pulse }
        val report = invoke(
            spec, "calibrate.report",
            "执行事实 + 当前计划 ${ctx.current?.segments?.size ?: 0} 段",
            { "${it.signals.size} 条信号 / ${it.notes.size} 条说明" },
        ) {
            Calibrator.report(ctx)
        }
        workspace.putCalibration(
            spec.agentId, report,
            "${report.signals.size} 条信号，${if (report.connected) "已接入执行记录" else "未接入执行记录"}",
        )
        memory.note("connected", if (report.connected) 1 else 0)
        memory.note("feedbackCount", report.feedbackCount)
        memory.note("plannedSegmentsToday", report.plannedSegmentsToday)
        memory.note("signalCount", report.signals.size)
        workspace.appendTrace(
            "执行校准：今天计划 ${report.plannedSegmentsToday} 段共 ${report.plannedMinutesToday} 分钟，" +
                "反馈 ${report.feedbackCount} 条，信号 ${report.signals.size} 条。",
        )
        return "${report.signals.size} 条信号，反馈 ${report.feedbackCount} 条" +
            if (report.connected) "" else "（未接入执行记录）"
    }

    private suspend fun runComposer(spec: AgentSpec, memory: AgentMemory): String {
        val hints = workspace.hints
        val draft = invoke(
            spec, "compose.schedule",
            "优先级 ${workspace.brief?.orderedTasks?.size ?: 0} 项 + 习惯偏好 ${hints?.perTask?.size ?: 0} 项",
            { "${it.segments.size} 个分段 / ${it.unscheduledTasks.size} 个待安排" },
        ) {
            PlanComposer.compose(ctx, hints)
        }
        workspace.putDraft(spec.agentId, draft, "${draft.segments.size} 个分段，${draft.unscheduledTasks.size} 个待安排")

        val landing = invoke(spec, "compose.landing", "${draft.segments.size} 个分段", { "${it.entries.size} 个时间轴事件" }) {
            PlanComposer.project(ctx, draft)
        }
        workspace.putLanding(spec.agentId, landing, "${landing.entries.size} 个事件落在 ${ctx.targetDate}")

        memory.note("segmentCount", draft.segments.size)
        memory.note("unscheduledCount", draft.unscheduledTasks.size)
        memory.note("landingEntryCount", landing.entries.size)
        workspace.appendTrace("方案合成：生成草案 ${draft.segments.size} 个时段，待安排 ${draft.unscheduledTasks.size} 个。")
        workspace.appendTrace("模拟落地：预览 ${landing.entries.size} 个事件已排到 ${ctx.targetDate} 的时间轴上。")
        return "${draft.segments.size} 个分段，${draft.unscheduledTasks.size} 个待安排"
    }

    private suspend fun runConflict(spec: AgentSpec, memory: AgentMemory): String {
        val draft = workspace.requireDraft(spec.agentId)
        val overlaps = invoke(spec, "conflict.overlap", "${draft.segments.size} 个分段", { "${it.size} 处重叠或重复" }) {
            ConflictChecker.overlaps(draft, ctx)
        }
        val constraints = invoke(
            spec, "conflict.constraints", "${draft.segments.size} 个分段",
            { "${it.size} 条固定安排/截止日问题" },
        ) {
            ConflictChecker.constraints(draft, ctx)
        }
        val capacity = invoke(
            spec, "conflict.capacity",
            "待安排 ${draft.unscheduledTasks.size} / 待定 ${draft.pendingTaskIds.size}",
            { "${it.size} 条容量与待安排问题" },
        ) {
            ConflictChecker.capacity(draft, ctx)
        }
        val report = ConflictChecker.assemble(overlaps, constraints, capacity)
        workspace.putReport(spec.agentId, report, "${report.items.size} 项问题，致命 ${report.fatalCount}")

        memory.note("conflictCount", report.items.size)
        memory.note("fatalCount", report.fatalCount)
        memory.note("autoFixableCount", report.autoFixableCount)
        workspace.appendTrace("校验排重：${report.items.size} 项问题（硬冲突 ${report.fatalCount}，容量/待安排 ${capacity.size}）。")
        return "${report.items.size} 项问题，硬冲突 ${report.fatalCount}，容量/待安排 ${capacity.size}"
    }

    private suspend fun finishWithAcceptance() {
        val acceptance = acceptanceContext(memories.values.lastOrNull())
        val teamResults = AgentRegistry.TEAM_CRITERIA.map { it.evaluate(acceptance) }
        val inherited = criteriaLog.filter { it.blocking }
        val all = teamResults + inherited
        val passed = all.none { it.blocking }

        emit { s, t -> AcceptanceFinished(seq = s, atMillis = t, results = all, passed = passed) }
        if (!passed) {
            val reason = all.filter { it.blocking }.joinToString("；") { it.statement }
            emit { s, t -> TeamFailed(s, t, null, "最终验收未通过：$reason") }
            error("最终验收未通过：$reason")
        }

        val draft = requireNotNull(workspace.draft) { "验收通过了却没有草案，这是运行时缺陷。" }
        val result = PlanTeamResult(
            requestKind = ctx.requestKind,
            selectedAgents = PlanTeamRouter.select(ctx.requestKind, ctx).toList(),
            brief = workspace.brief,
            availability = workspace.availability,
            hints = workspace.hints,
            calibration = workspace.calibration,
            report = workspace.report,
            draft = draft,
            landing = requireNotNull(workspace.landing) { "验收通过了却没有落地预览，这是运行时缺陷。" },
            trace = workspace.trace(),
        )
        emit { s, t -> DraftReady(seq = s, atMillis = t, result = result) }
    }

    private suspend fun <T> invoke(
        spec: AgentSpec,
        toolId: String,
        inputDigest: String,
        digest: (T) -> String,
        block: suspend () -> T,
    ): T {
        val tool = spec.tool(toolId)
        val startedNanos = System.nanoTime()
        return try {
            val output = block()
            emitTool(
                ToolCallRecord(
                    toolId = tool.id, agent = spec.agentId, label = tool.label, outcome = ToolOutcome.OK,
                    inputDigest = inputDigest, outputDigest = digest(output),
                    elapsedMillis = elapsedMillis(startedNanos),
                ),
            )
            output
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            emitTool(
                ToolCallRecord(
                    toolId = tool.id, agent = spec.agentId, label = tool.label, outcome = ToolOutcome.FAILED,
                    inputDigest = inputDigest, outputDigest = "未产出",
                    detail = error.message ?: error::class.simpleName,
                    elapsedMillis = elapsedMillis(startedNanos),
                ),
            )
            throw error
        }
    }

    
    private suspend fun skip(spec: AgentSpec, toolId: String, inputDigest: String, reason: String) {
        val tool = spec.tool(toolId)
        emitTool(
            ToolCallRecord(
                toolId = tool.id, agent = spec.agentId, label = tool.label, outcome = ToolOutcome.SKIPPED,
                inputDigest = inputDigest, outputDigest = "未执行", detail = reason,
            ),
        )
    }

    private suspend fun emitTool(record: ToolCallRecord) {
        emit { s, t -> ToolCalled(s, t, record) }
    }

    private fun memoryNoteFor(memory: AgentMemory): String? {
        val history = memoryBank.episodesOf(memory.agentId)
        val last = history.lastOrNull() ?: return null
        return "第 ${history.size + 1} 次参与；上次：${last.summary}"
    }

    private fun teamMemoryNote(): String? {
        val history = memoryBank.episodesOf(PlanAgentId.COMPOSER)
        val last = history.lastOrNull() ?: return null
        return "第 ${history.size + 1} 次协作，上次：${last.summary}"
    }

    private fun acceptanceContext(memory: AgentMemory?): AcceptanceContext =
        AcceptanceContext(
            workspace = workspace,
            memory = memory ?: AgentMemory(PlanAgentId.COMPOSER, runId),
            advisorAvailable = advisorAvailable,
        )

    private suspend fun emit(build: (Int, Long) -> TeamEvent) {
        send(build(seq, clock()))
        seq += 1
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)

    private fun describeStates(states: Map<String, TaskPlanningState>): String {
        val grouped = states.values.groupingBy { it }.eachCount()
        fun count(state: TaskPlanningState) = grouped[state] ?: 0
        return "可排 ${count(TaskPlanningState.SCHEDULABLE)} / 缺时长 ${count(TaskPlanningState.NEEDS_DURATION)} / " +
            "锁定 ${count(TaskPlanningState.LOCKED)} / 结束 ${count(TaskPlanningState.DEFERRED)}"
    }
}
