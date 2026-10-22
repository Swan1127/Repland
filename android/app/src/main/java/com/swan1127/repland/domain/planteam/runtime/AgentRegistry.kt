package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.planteam.CalibrationKind
import com.swan1127.repland.domain.planteam.ConflictType
import com.swan1127.repland.domain.planteam.ConflictChecker
import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.Severity
import com.swan1127.repland.domain.planteam.TaskPlanningState

class AgentSpec(
    val agentId: PlanAgentId,
    val displayName: String,
    val mission: String,
    val tools: List<ToolSpec>,
    val criteria: List<AcceptanceCriterion>,
    val memoryKeys: List<String>,
) {
    val toolIds: Set<String> = tools.map { it.id }.toSet()
    val owns: List<WorkspaceSlot> = AgentWorkspace.OWNER.filterValues { it == agentId }.keys.toList()

    fun tool(id: String): ToolSpec = tools.firstOrNull { it.id == id }
        ?: error("${displayName}($agentId) 没有声明工具 $id；请先在 AgentRegistry 里登记。")
}

object AgentRegistry {

    private val REQ_RANK = ToolSpec(
        "req.rank", "排序任务优先级", "用本地评分器给任务排序，保证团队看到的顺序和应用里一致",
        reads = "任务/类别偏好/手动顺序", writes = "工作区:需求简报",
    )
    private val REQ_STATES = ToolSpec(
        "req.states", "标注规划状态", "判定每个任务是可排、缺时长、已锁定还是已结束",
        reads = "任务/锁定分段", writes = "工作区:需求简报",
    )
    private val REQ_QUESTIONS = ToolSpec(
        "req.questions", "整理待确认与软目标", "把缺时长、逾期、必须项整理成需要你知道的几条",
        reads = "任务", writes = "工作区:需求简报",
    )
    private val REQ_AI = ToolSpec(
        "req.ai", "AI 语义理解与估时", "可选：请已配置的模型读任务语义并估计难度/时长，结果必须通过校验",
        reads = "任务 + AI 提供方", writes = "工作区:需求简报(AI 洞察)",
    )

    private val AVAIL_BLOCKS = ToolSpec(
        "avail.blocks", "读取固定占用", "把课程、休息、导入日程、锁定分段摊到目标日",
        reads = "周模板/日期例外/锁定分段/外部日程", writes = "工作区:时间地图",
    )
    private val AVAIL_SLOTS = ToolSpec(
        "avail.slots", "扫描空闲槽", "用硬约束校验器把一天切成 30 分钟槽，算出真实可排容量",
        reads = "固定占用", writes = "工作区:时间地图",
    )

    private val HABIT_PROFILE = ToolSpec(
        "habit.profile", "读取个人习惯事实", "读取缓冲时长、上午深专注偏好等画像事实",
        reads = "画像事实", writes = "无(只读)",
    )
    private val HABIT_MEMORY = ToolSpec(
        "habit.memory", "读取记忆参考", "读取复习点的稳固度与可提取率（设置里可关），只用于决定放在当天哪个时段",
        reads = "记忆参考", writes = "无(只读)",
    )
    private val HABIT_HINTS = ToolSpec(
        "habit.hints", "生成放置偏好", "为每个任务给出偏好时段与块形态，并解释理由",
        reads = "需求简报 + 画像事实 + 记忆参考", writes = "工作区:习惯偏好",
    )
    private val HABIT_AI = ToolSpec(
        "habit.ai", "AI 难度复核", "可选：按 AI 难度把高难度任务提到上午深专注窗口（不改你的时长）",
        reads = "需求简报(AI 洞察)", writes = "工作区:习惯偏好",
    )

    private val CONFLICT_OVERLAP = ToolSpec(
        "conflict.overlap", "扫描时段重叠", "逐对比较草案分段，给出精确到分钟的重叠区间",
        reads = "工作区:方案草案", writes = "工作区:冲突报告",
    )
    private val CONFLICT_CONSTRAINTS = ToolSpec(
        "conflict.constraints", "核对硬约束", "检查固定占用、日期封锁、截止日与计划开始日；新排时段的冲突会阻断草案",
        reads = "工作区:方案草案 + 周模板/日期例外", writes = "工作区:冲突报告",
    )
    private val CONFLICT_CAPACITY = ToolSpec(
        "conflict.capacity", "核对容量与待安排", "找出排不进去、缺时长、无可用窗口的任务",
        reads = "工作区:方案草案", writes = "工作区:冲突报告",
    )

    private val CALIBRATE_EXECUTION = ToolSpec(
        "calibrate.execution", "读取今天的执行事实", "读取今天已确认的执行日志与正在进行的轮次，不推断效率",
        reads = "执行日志/进行中的一轮", writes = "工作区:执行校准",
    )
    private val CALIBRATE_REPORT = ToolSpec(
        "calibrate.report", "整理校准结论", "对照当前计划，说明本轮会保留什么、回收什么，以及为什么",
        reads = "执行事实 + 当前计划", writes = "工作区:执行校准",
    )

    private val COMPOSE_SCHEDULE = ToolSpec(
        "compose.schedule", "调用本地排程引擎", "把优先级与硬约束交给 PlanGenerator，生成草案分段",
        reads = "工作区:习惯偏好 + 输入", writes = "工作区:方案草案",
    )
    private val COMPOSE_LANDING = ToolSpec(
        "compose.landing", "投影到当天时间轴", "把草案投影成当天时间轴事件，得到模拟落地预览",
        reads = "工作区:方案草案", writes = "工作区:落地预览",
    )

    val REQUIREMENTS = AgentSpec(
        agentId = PlanAgentId.REQUIREMENTS,
        displayName = "需求分析师",
        mission = "读懂这次要排什么：排序、标状态、把含糊的地方挑出来",
        tools = listOf(REQ_RANK, REQ_STATES, REQ_QUESTIONS, REQ_AI),
        memoryKeys = listOf("taskCount", "schedulableCount", "needsDurationCount", "openQuestionCount"),
        criteria = listOf(
            AcceptanceCriterion(
                "req.rank-covers-all",
                "优先级排序覆盖全部任务，没有任务被静默丢掉",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val brief = c.workspace.brief) {
                    null -> Verdict.skip("本轮未运行需求分析")
                    else -> {
                        val ranked = brief.orderedTasks.map { it.taskId }.toSet()
                        if (ranked == c.taskIds) Verdict.Pass
                        else Verdict.fail(
                            "排序 ${ranked.size} 项 / 任务 ${c.taskIds.size} 项（缺 ${(c.taskIds - ranked).size}，多 ${(ranked - c.taskIds).size}）",
                        )
                    }
                }
            },
            AcceptanceCriterion(
                "req.states-cover-all",
                "每个任务都有明确的规划状态",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val brief = c.workspace.brief) {
                    null -> Verdict.skip("本轮未运行需求分析")
                    else -> {
                        val missing = c.taskIds - brief.states.keys
                        if (missing.isEmpty()) Verdict.Pass else Verdict.fail("${missing.size} 个任务没有状态")
                    }
                }
            },
            AcceptanceCriterion(
                "req.insights-bounded",
                "AI 洞察只落在已知任务上，难度与时长都在合理区间",
                CriterionLevel.REQUIRED,
            ) { c ->
                when (val brief = c.workspace.brief) {
                    null -> Verdict.skip("本轮未运行需求分析")
                    else -> {
                        if (brief.aiInsights.isEmpty()) {
                            Verdict.skip("本轮没有 AI 洞察（未配置或未通过校验）")
                        } else {
                            val unknown = brief.aiInsights.keys - c.taskIds
                            val badDifficulty = brief.aiInsights.filterValues { it.difficulty != null && it.difficulty !in 1..5 }
                            val badDuration = brief.aiInsights.filterValues {
                                it.suggestedDurationMinutes != null && it.suggestedDurationMinutes !in 5..600
                            }
                            if (unknown.isEmpty() && badDifficulty.isEmpty() && badDuration.isEmpty()) Verdict.Pass
                            else Verdict.fail(
                                "越界洞察：未知任务 ${unknown.size}，难度越界 ${badDifficulty.size}，时长越界 ${badDuration.size}",
                            )
                        }
                    }
                }
            },
        ),
    )

    val AVAILABILITY = AgentSpec(
        agentId = PlanAgentId.AVAILABILITY,
        displayName = "时间编排师",
        mission = "看清目标日到底有多少时间能用：固定占用先扣掉，剩下的才是可排容量",
        tools = listOf(AVAIL_BLOCKS, AVAIL_SLOTS),
        memoryKeys = listOf("blockedCount", "freeSlotCount", "capacityMinutes"),
        criteria = listOf(
            AcceptanceCriterion(
                "avail.slots-disjoint",
                "空闲槽互不重叠",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val map = c.workspace.availability) {
                    null -> Verdict.skip("本轮未运行时间编排")
                    else -> {
                        val sorted = map.freeSlots.sortedBy { it.startMinute }
                        val overlap = sorted.zipWithNext().count { (a, b) -> b.startMinute < a.endMinute }
                        if (overlap == 0) Verdict.Pass else Verdict.fail("$overlap 处空闲槽相互重叠")
                    }
                }
            },
            AcceptanceCriterion(
                "avail.capacity-matches",
                "容量合计等于空闲槽总和",
                CriterionLevel.REQUIRED,
            ) { c ->
                when (val map = c.workspace.availability) {
                    null -> Verdict.skip("本轮未运行时间编排")
                    else -> {
                        val sum = map.freeSlots.sumOf { it.endMinute - it.startMinute }
                        if (sum == map.capacityMinutes) Verdict.Pass
                        else Verdict.fail("声明 ${map.capacityMinutes} 分钟，实际 ${sum} 分钟")
                    }
                }
            },
            AcceptanceCriterion(
                "avail.free-not-blocked",
                "空闲槽不会落在固定占用（课程/休息/导入日程）里",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val map = c.workspace.availability) {
                    null -> Verdict.skip("本轮未运行时间编排")
                    else -> {
                        val fixed = map.blocked.filter { it.kind != com.swan1127.repland.domain.planteam.OccupiedKind.LOCKED }
                        val hits = map.freeSlots.count { slot ->
                            fixed.any { it.startMinute < slot.endMinute && it.endMinute > slot.startMinute }
                        }
                        if (hits == 0) Verdict.Pass else Verdict.fail("$hits 个空闲槽与固定占用冲突")
                    }
                }
            },
            AcceptanceCriterion(
                "avail.locked-deducted",
                "已锁定时段是否从空闲容量里扣除",
                CriterionLevel.ADVISORY,
            ) { c ->
                when (val map = c.workspace.availability) {
                    null -> Verdict.skip("本轮未运行时间编排")
                    else -> {
                        val locked = map.blocked.filter { it.kind == com.swan1127.repland.domain.planteam.OccupiedKind.LOCKED }
                        if (locked.isEmpty()) {
                            Verdict.skip("目标日没有已锁定时段")
                        } else {
                            val hits = map.freeSlots.count { slot ->
                                locked.any { it.startMinute < slot.endMinute && it.endMinute > slot.startMinute }
                            }
                            if (hits == 0) Verdict.Pass
                            else Verdict.fail("$hits 个空闲槽与已锁定时段重叠，容量估计偏高（排程引擎仍会避开这些时间）")
                        }
                    }
                }
            },
        ),
    )

    val HABIT = AgentSpec(
        agentId = PlanAgentId.HABIT,
        displayName = "习惯调适师",
        mission = "把\"什么时候做最顺手\"翻译成可执行的放置偏好，不改你的时长",
        tools = listOf(HABIT_PROFILE, HABIT_MEMORY, HABIT_HINTS, HABIT_AI),
        memoryKeys = listOf("bufferMinutes", "hintCount", "aiNoteCount", "memoryAtRisk"),
        criteria = listOf(
            AcceptanceCriterion(
                "habit.hints-known-tasks",
                "放置偏好只针对真实存在的任务",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val hints = c.workspace.hints) {
                    null -> Verdict.skip("本轮未运行习惯调适")
                    else -> {
                        val unknown = hints.perTask.keys - c.taskIds
                        if (unknown.isEmpty()) Verdict.Pass else Verdict.fail("${unknown.size} 条偏好挂在未知任务上")
                    }
                }
            },
            AcceptanceCriterion(
                "habit.hints-cover-schedulable",
                "每个可排任务都有放置偏好",
                CriterionLevel.REQUIRED,
            ) { c ->
                val brief = c.workspace.brief
                val hints = c.workspace.hints
                when {
                    hints == null -> Verdict.skip("本轮未运行习惯调适")
                    brief == null -> Verdict.skip("本轮没有需求简报，无从核对")
                    else -> {
                        val schedulable = brief.states.filterValues { it == TaskPlanningState.SCHEDULABLE }.keys
                        val missing = schedulable - hints.perTask.keys
                        if (missing.isEmpty()) Verdict.Pass else Verdict.fail("${missing.size} 个可排任务没有偏好")
                    }
                }
            },
            AcceptanceCriterion(
                "habit.buffer-sane",
                "缓冲时长在 0–120 分钟之间",
                CriterionLevel.ADVISORY,
            ) { c ->
                when (val hints = c.workspace.hints) {
                    null -> Verdict.skip("本轮未运行习惯调适")
                    else -> if (hints.bufferMinutes in 0..120) Verdict.Pass
                    else Verdict.fail("缓冲 ${hints.bufferMinutes} 分钟")
                }
            },
        ),
    )

    val CALIBRATOR = AgentSpec(
        agentId = PlanAgentId.CALIBRATOR,
        displayName = "执行校准师",
        mission = "把今天真实的执行记录带进本轮：正在做什么、哪些已完成、哪些被顺延",
        tools = listOf(CALIBRATE_EXECUTION, CALIBRATE_REPORT),
        memoryKeys = listOf("connected", "feedbackCount", "plannedSegmentsToday", "signalCount"),
        criteria = listOf(
            AcceptanceCriterion(
                "calibrate.known-tasks",
                "校准信号只涉及真实存在的任务",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val report = c.workspace.calibration) {
                    null -> Verdict.skip("本轮未运行执行校准")
                    else -> {
                        val unknown = report.signals.mapNotNull { it.taskId }.toSet() - c.taskIds
                        if (unknown.isEmpty()) Verdict.Pass
                        else Verdict.fail("${unknown.size} 个未知任务出现在校准信号里")
                    }
                }
            },
            AcceptanceCriterion(
                "calibrate.status-sets",
                "已完成、部分完成、顺延三类互不重叠，且都来自今天",
                CriterionLevel.BLOCKER,
            ) { c ->
                val pulse = c.workspace.ctx.execution
                when {
                    c.workspace.calibration == null -> Verdict.skip("本轮未运行执行校准")
                    pulse == null -> Verdict.skip("本轮没有接入执行记录")
                    else -> {
                        val sets = listOf(pulse.completedTaskIds, pulse.partialTaskIds, pulse.postponedTaskIds)
                        val overlapping = sets.flatMapIndexed { index, set ->
                            sets.drop(index + 1).flatMap { other -> set intersect other }
                        }
                        val unknown = sets.flatten().toSet() - c.taskIds
                        if (overlapping.isEmpty() && unknown.isEmpty()) Verdict.Pass
                        else Verdict.fail("状态重叠 ${overlapping.size} 个，未知任务 ${unknown.size} 个")
                    }
                }
            },
            AcceptanceCriterion(
                "calibrate.active-visible",
                "正在执行的任务在本轮校准里被明确保留",
                CriterionLevel.REQUIRED,
            ) { c ->
                val report = c.workspace.calibration
                val active = c.workspace.ctx.runningTaskId
                when {
                    report == null -> Verdict.skip("本轮未运行执行校准")
                    active == null -> Verdict.skip("今天没有正在执行的任务")
                    report.signals.any { it.kind == CalibrationKind.ACTIVE_SESSION && it.taskId == active } ->
                        Verdict.Pass
                    else -> Verdict.fail("正在执行的任务没有被校准师标记")
                }
            },
            AcceptanceCriterion(
                "calibrate.report-present",
                "校准结论有可读的说明，不是一张空表",
                CriterionLevel.REQUIRED,
            ) { c ->
                when (val report = c.workspace.calibration) {
                    null -> Verdict.skip("本轮未运行执行校准")
                    else -> if (report.notes.isNotEmpty() && report.notes.all { it.isNotBlank() }) Verdict.Pass
                    else Verdict.fail("校准结论没有说明")
                }
            },
        ),
    )

    val CONFLICT = AgentSpec(
        agentId = PlanAgentId.CONFLICT,
        displayName = "校验排重师",
        mission = "在草案落地前挑刺：哪些时段撞了、哪些违反固定安排或截止日、哪几个任务根本排不进去",
        tools = listOf(CONFLICT_OVERLAP, CONFLICT_CONSTRAINTS, CONFLICT_CAPACITY),
        memoryKeys = listOf("conflictCount", "fatalCount", "autoFixableCount"),
        criteria = listOf(
            AcceptanceCriterion(
                "conflict.ranges-valid",
                "每条重叠都给出合法的分钟区间",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val report = c.workspace.report) {
                    null -> Verdict.skip("本轮未运行校验")
                    else -> {
                        val bad = report.items.filter {
                            it.type == ConflictType.OVERLAP && it.endMinute <= it.startMinute
                        }
                        if (bad.isEmpty()) Verdict.Pass else Verdict.fail("${bad.size} 条重叠区间起点不小于终点")
                    }
                }
            },
            AcceptanceCriterion(
                "conflict.ids-known",
                "冲突只涉及草案里真实存在的任务",
                CriterionLevel.REQUIRED,
            ) { c ->
                when (val report = c.workspace.report) {
                    null -> Verdict.skip("本轮未运行校验")
                    else -> {
                        val draftIds = c.workspace.draft?.let { d ->
                            d.segments.map { it.taskId } + d.unscheduledTasks.map { it.taskId } + d.pendingTaskIds
                        }.orEmpty().toSet()
                        val unknown = report.items.flatMap { it.affectedTaskIds }.toSet() - c.taskIds - draftIds
                        if (unknown.isEmpty()) Verdict.Pass else Verdict.fail("${unknown.size} 个未知任务出现在冲突里")
                    }
                }
            },
            AcceptanceCriterion(
                "conflict.no-fatal",
                "本轮新排的时段没有硬约束冲突",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val report = c.workspace.report) {
                    null -> Verdict.skip("本轮未运行校验")
                    else -> {
                        val fatal = report.items.filter { it.severity == Severity.FATAL }
                        if (fatal.isEmpty()) Verdict.Pass else Verdict.fail(
                            "${fatal.size} 项硬冲突：${fatal.first().message}",
                        )
                    }
                }
            },
        ),
    )

    val COMPOSER = AgentSpec(
        agentId = PlanAgentId.COMPOSER,
        displayName = "方案合成师",
        mission = "把优先级、容量、习惯偏好交给本地排程引擎，产出一份能落地的草案",
        tools = listOf(COMPOSE_SCHEDULE, COMPOSE_LANDING),
        memoryKeys = listOf("segmentCount", "unscheduledCount", "landingEntryCount"),
        criteria = listOf(
            AcceptanceCriterion(
                "compose.durations-positive",
                "每个分段的结束时间晚于开始时间",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val draft = c.workspace.draft) {
                    null -> Verdict.skip("本轮未运行方案合成")
                    else -> {
                        val bad = draft.segments.count { it.endMinute <= it.startMinute }
                        if (bad == 0) Verdict.Pass else Verdict.fail("$bad 个分段时长非正")
                    }
                }
            },
            AcceptanceCriterion(
                "compose.no-self-overlap",
                "本轮新生成的时段不与其他安排重叠",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val draft = c.workspace.draft) {
                    null -> Verdict.skip("本轮未运行方案合成")
                    else -> {
                        val overlapping = ConflictChecker.overlaps(draft, c.workspace.ctx)
                            .count { it.severity == Severity.FATAL }
                        if (overlapping == 0) Verdict.Pass else Verdict.fail("$overlapping 处轨道内重叠")
                    }
                }
            },
            AcceptanceCriterion(
                "compose.ids-known",
                "草案里的每个任务 ID 都能在任务列表里找到",
                CriterionLevel.BLOCKER,
            ) { c ->
                when (val draft = c.workspace.draft) {
                    null -> Verdict.skip("本轮未运行方案合成")
                    else -> {
                        val context = c.workspace.ctx
                        val history = context.current?.segments.orEmpty().filter { segment ->
                            segment.date < context.now.toLocalDate() ||
                                segment.date == context.now.toLocalDate() && segment.endMinute <= context.now.hour * 60 + context.now.minute
                        }
                        val unknown = draft.segments.filter { candidate ->
                            candidate.taskId !in c.taskIds && history.none { original ->
                                original.taskId == candidate.taskId && original.date == candidate.date &&
                                    original.startMinute == candidate.startMinute && original.endMinute == candidate.endMinute && original.trackId == candidate.trackId
                            }
                        }.map { it.taskId }.toSet() +
                            ((draft.unscheduledTasks.map { it.taskId } + draft.pendingTaskIds).toSet() - c.taskIds)
                        if (unknown.isEmpty()) Verdict.Pass else Verdict.fail("${unknown.size} 个幽灵任务 ID")
                    }
                }
            },
        ),
    )

    val TEAM_CRITERIA = listOf(
        AcceptanceCriterion(
            "team.deliverables-present",
            "每个执行过的 agent 都真的把产物放进了工作区",
            CriterionLevel.BLOCKER,
        ) { c ->
            val slots = c.workspace.filledSlots()
            val ran = c.workspace.agentsRan()
            val expected = buildList {
                if (PlanAgentId.REQUIREMENTS in ran) add(WorkspaceSlot.BRIEF)
                if (PlanAgentId.AVAILABILITY in ran) add(WorkspaceSlot.AVAILABILITY)
                if (PlanAgentId.HABIT in ran) add(WorkspaceSlot.HINTS)
                if (PlanAgentId.CALIBRATOR in ran) add(WorkspaceSlot.CALIBRATION)
                if (PlanAgentId.CONFLICT in ran) add(WorkspaceSlot.CONFLICT_REPORT)
                if (PlanAgentId.COMPOSER in ran) add(WorkspaceSlot.DRAFT)
            }
            val missing = expected.filter { it !in slots }
            if (missing.isEmpty()) Verdict.Pass
            else Verdict.fail("${c.workspace.ctx.requestKind} 缺少产物：${missing.joinToString("、") { it.label }}")
        },
        AcceptanceCriterion(
            "team.workspace-ownership",
            "工作区每个格子都由它的归属 agent 写入",
            CriterionLevel.BLOCKER,
        ) { c ->
            val violated = c.workspace.entries().filter { c.workspace.ownerOf(it.slot) != it.agent }
            if (violated.isEmpty()) Verdict.Pass
            else Verdict.fail("${violated.size} 处越权写入：${violated.joinToString { "${it.agent}→${it.slot.label}" }}")
        },
        AcceptanceCriterion(
            "team.trace-covers-steps",
            "协作轨迹覆盖了每个执行过的 agent",
            CriterionLevel.REQUIRED,
        ) { c ->
            val trace = c.workspace.trace()
            val ran = c.workspace.agentsRan()
            if (trace.isEmpty()) Verdict.fail("轨迹为空")
            else if (ran.size <= trace.size) Verdict.Pass
            else Verdict.fail("${ran.size} 个 agent 只留下 ${trace.size} 条轨迹")
        },
    )

    fun spec(agentId: PlanAgentId): AgentSpec = when (agentId) {
        PlanAgentId.REQUIREMENTS -> REQUIREMENTS
        PlanAgentId.AVAILABILITY -> AVAILABILITY
        PlanAgentId.HABIT -> HABIT
        PlanAgentId.CALIBRATOR -> CALIBRATOR
        PlanAgentId.CONFLICT -> CONFLICT
        PlanAgentId.COMPOSER -> COMPOSER
    }

    fun all(): List<AgentSpec> = listOf(REQUIREMENTS, AVAILABILITY, HABIT, CALIBRATOR, CONFLICT, COMPOSER)
}

fun PlanAgentId.displayName(): String = AgentRegistry.spec(this).displayName
