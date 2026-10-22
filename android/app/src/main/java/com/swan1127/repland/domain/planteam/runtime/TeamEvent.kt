package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.PlanRequestKind
import com.swan1127.repland.domain.planteam.PlanTeamResult
import java.time.LocalDate

enum class LogTone {

    INFO,


    WORKING,


    DONE,


    WARN,


    ERROR,
}

sealed interface TeamEvent {
    val seq: Int
    val atMillis: Long


    val agent: PlanAgentId?

    val tone: LogTone


    val summary: String
}

data class TeamStarted(
    override val seq: Int,
    override val atMillis: Long,
    val runId: String,
    val requestKind: PlanRequestKind,
    val targetDate: LocalDate,
    val agents: List<PlanAgentId>,
    val plannedToolCalls: Int,
    val memoryNote: String?,
) : TeamEvent {
    override val agent: PlanAgentId? = null
    override val tone: LogTone = LogTone.INFO
    override val summary: String =
        "开始规划：${requestKind.label()} · $targetDate · 调度 ${agents.size} 个 agent，备好 $plannedToolCalls 个工具调用" +
            (memoryNote?.let { "（$it）" } ?: "")
}

data class AgentStarted(
    override val seq: Int,
    override val atMillis: Long,
    val agentId: PlanAgentId,
    val displayName: String,
    val index: Int,
    val total: Int,
    val mission: String,
    val memoryNote: String?,
) : TeamEvent {
    override val agent: PlanAgentId get() = agentId
    override val tone: LogTone = LogTone.WORKING
    override val summary: String =
        "$displayName 就位（$index/$total）：$mission" + (memoryNote?.let { " · $it" } ?: "")
}

data class ToolCalled(
    override val seq: Int,
    override val atMillis: Long,
    val record: ToolCallRecord,
) : TeamEvent {
    override val agent: PlanAgentId get() = record.agent
    override val tone: LogTone = when (record.outcome) {
        ToolOutcome.OK -> LogTone.DONE
        ToolOutcome.DEGRADED -> LogTone.WARN
        ToolOutcome.SKIPPED -> LogTone.INFO
        ToolOutcome.FAILED -> LogTone.ERROR
    }
    override val summary: String = buildString {
        append(record.label)
        append(" · ")
        append(
            when (record.outcome) {
                ToolOutcome.OK -> record.outputDigest
                ToolOutcome.DEGRADED -> "降级：${record.detail ?: record.outputDigest}"
                ToolOutcome.SKIPPED -> "跳过：${record.detail ?: "本轮不需要"}"
                ToolOutcome.FAILED -> "失败：${record.detail ?: record.outputDigest}"
            },
        )
    }
}

data class AgentFinished(
    override val seq: Int,
    override val atMillis: Long,
    val agentId: PlanAgentId,
    val displayName: String,
    val outcomeLine: String,
    val criteria: List<CriterionResult>,
    val memoryWrites: List<MemoryWrite>,
    val elapsedMillis: Long,
) : TeamEvent {
    override val agent: PlanAgentId get() = agentId
    val failed: List<CriterionResult> = criteria.filter { it.status == CriterionStatus.FAIL }
    val blockingFailures: List<CriterionResult> = criteria.filter { it.blocking }
    override val tone: LogTone = when {
        blockingFailures.isNotEmpty() -> LogTone.ERROR
        failed.isNotEmpty() -> LogTone.WARN
        else -> LogTone.DONE
    }
    override val summary: String = buildString {
        append("$displayName 完成：$outcomeLine")
        append("（验收 ${criteria.count { it.status == CriterionStatus.PASS }}/${criteria.size}")
        if (failed.isNotEmpty()) append("，未过 ${failed.size}")
        append("，${elapsedMillis}ms）")
    }
}

data class AcceptanceFinished(
    override val seq: Int,
    override val atMillis: Long,
    val results: List<CriterionResult>,
    val passed: Boolean,
) : TeamEvent {
    override val agent: PlanAgentId? = null
    val blockingFailures: List<CriterionResult> = results.filter { it.blocking }
    override val tone: LogTone = if (passed) LogTone.DONE else LogTone.ERROR
    override val summary: String = buildString {
        append(if (passed) "最终验收通过" else "最终验收未通过")
        append("：${results.count { it.status == CriterionStatus.PASS }}/${results.size} 条标准通过")
        if (blockingFailures.isNotEmpty()) {
            append("，阻断项：")
            append(blockingFailures.joinToString("；") { it.statement })
        }
    }
}

data class DraftReady(
    override val seq: Int,
    override val atMillis: Long,
    val result: PlanTeamResult,
) : TeamEvent {
    override val agent: PlanAgentId? = null
    override val tone: LogTone = LogTone.DONE
    override val summary: String =
        "草案就绪：${result.draft.segments.size} 个分段，${result.draft.unscheduledTasks.size} 个待安排，" +
            "模拟落地 ${result.landing.entries.size} 个事件"
}

data class TeamFailed(
    override val seq: Int,
    override val atMillis: Long,
    val agentId: PlanAgentId?,
    val message: String,
) : TeamEvent {
    override val agent: PlanAgentId? get() = agentId
    override val tone: LogTone = LogTone.ERROR
    override val summary: String = "规划中断：$message"
}

fun PlanRequestKind.label(): String = when (this) {
    PlanRequestKind.ADD -> "新增任务"
    PlanRequestKind.REMOVE -> "移除任务"
    PlanRequestKind.FULL_REPLAN -> "全量重排"
    PlanRequestKind.REPAIR -> "保留稳定安排，修复其余"
    PlanRequestKind.ADJUST -> "局部调整"
    PlanRequestKind.RECOVER -> "恢复今天"
    PlanRequestKind.PREVIEW_HABIT -> "习惯建议"
}
