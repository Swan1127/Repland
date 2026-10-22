package com.swan1127.repland.domain.planteam.runtime

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.model.weeklyBlock
import com.swan1127.repland.domain.planteam.CalibrationKind
import com.swan1127.repland.domain.planteam.ExecutionPulse
import com.swan1127.repland.domain.planteam.PlanAgentId
import com.swan1127.repland.domain.planteam.PlanRequestKind
import com.swan1127.repland.domain.planteam.PlanTeamContext
import com.swan1127.repland.domain.planteam.PlanningTeamCoordinator
import com.swan1127.repland.domain.planteam.RequirementsAnalyst
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlanningTeamRuntimeTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val avail = listOf(
        weeklyBlock("avail", kind = TimeBlockKind.AVAILABLE, dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 720),
    )

    private val now = LocalDateTime.of(day, LocalTime.of(7, 0))

    private fun ctx(vararg tasks: com.swan1127.repland.domain.model.Task) = PlanTeamContext(
        requestKind = PlanRequestKind.FULL_REPLAN,
        input = planInput(tasks.toList(), weeklyBlocks = avail),
        targetDate = day,
        now = now,
    )

    private fun events(ctx: PlanTeamContext, runtime: PlanningTeamRuntime = PlanningTeamRuntime()): List<TeamEvent> =
        runBlocking { runtime.run(ctx).toList() }

    @Test fun event_stream_is_ordered_and_every_step_has_real_tool_calls() {
        val events = events(ctx(task("a", 60), task("b", 30), task("c", null)))

        assertTrue("第一条必须是团队开始", events.first() is TeamStarted)
        assertTrue("最后一条必须是草案就绪", events.last() is DraftReady)
        assertTrue(events[events.size - 2] is AcceptanceFinished)

        val seqs = events.map { it.seq }
        assertEquals("序号必须严格递增且不重复", seqs.sorted().distinct(), seqs)

        for (agent in PlanAgentId.values()) {
            val indexes = events.withIndex().filter { it.value.agent == agent }.map { it.index }
            if (indexes.isEmpty()) continue
            assertTrue("$agent 就位必须是它的第一条事件", events[indexes.first()] is AgentStarted)
            assertTrue("$agent 交付必须是它的最后一条事件", events[indexes.last()] is AgentFinished)
            assertTrue(
                "$agent 必须在交付前真的调用过工具",
                indexes.drop(1).dropLast(1).any { events[it] is ToolCalled },
            )
        }
    }

    @Test fun every_tool_call_is_declared_by_its_own_agent() {
        val events = events(ctx(task("a", 60), task("b", 30)))
        val calls = events.filterIsInstance<ToolCalled>().map { it.record }
        assertTrue("工具调用不能为空", calls.isNotEmpty())
        calls.forEach { record ->
            val spec = AgentRegistry.spec(record.agent)
            assertTrue("${record.agent} 调用了未声明的工具 ${record.toolId}", record.toolId in spec.toolIds)
            assertTrue("工具 ${record.toolId} 缺少输入摘要", record.inputDigest.isNotEmpty())
            assertTrue("工具 ${record.toolId} 缺少输出摘要", record.outputDigest.isNotEmpty())
        }
    }

    @Test fun full_replan_runs_declared_tool_sets_per_agent() {
        val calls = events(ctx(task("a", 60), task("b", 30))).filterIsInstance<ToolCalled>().map { it.record }
        val byAgent = calls.groupBy { it.agent }
        assertEquals(listOf("req.rank", "req.states", "req.questions", "req.ai"), byAgent.getValue(PlanAgentId.REQUIREMENTS).map { it.toolId })
        assertEquals(listOf("calibrate.execution", "calibrate.report"), byAgent.getValue(PlanAgentId.CALIBRATOR).map { it.toolId })
        assertEquals(listOf("avail.blocks", "avail.slots"), byAgent.getValue(PlanAgentId.AVAILABILITY).map { it.toolId })
        assertEquals(
            listOf("habit.profile", "habit.memory", "habit.hints", "habit.ai"),
            byAgent.getValue(PlanAgentId.HABIT).map { it.toolId },
        )
        assertEquals(listOf("compose.schedule", "compose.landing"), byAgent.getValue(PlanAgentId.COMPOSER).map { it.toolId })
        assertEquals(
            listOf("conflict.overlap", "conflict.constraints", "conflict.capacity"),
            byAgent.getValue(PlanAgentId.CONFLICT).map { it.toolId },
        )

        val skipped = calls.filter { it.outcome == ToolOutcome.SKIPPED }
        assertEquals(listOf("req.ai", "habit.memory", "habit.ai"), skipped.map { it.toolId })
    }

    @Test fun calibrator_delivers_a_report_from_real_execution_state() {
        val context = ctx(task("a", 60), task("b", 30)).copy(
            runningTaskId = "a",
            execution = ExecutionPulse(day, completedTaskIds = setOf("b"), feedbackCount = 1),
        )
        val ready = events(context).filterIsInstance<DraftReady>().single()
        val calibration = ready.result.calibration
        assertNotNull(calibration)
        assertTrue(calibration!!.connected)
        assertTrue(calibration.signals.any { it.kind == CalibrationKind.ACTIVE_SESSION && it.taskId == "a" })
        assertTrue(calibration.signals.any { it.kind == CalibrationKind.COMPLETED && it.taskId == "b" })
        assertTrue(calibration.notes.isNotEmpty())
    }

    @Test fun draft_ready_carries_the_same_draft_as_the_coordinator() {
        val context = ctx(task("a", 60), task("b", 30))
        val events = events(context)
        val ready = events.filterIsInstance<DraftReady>().single()
        val viaCoordinator = runBlocking { PlanningTeamCoordinator().run(context) }
        assertEquals(viaCoordinator.draft.segments, ready.result.draft.segments)
        assertEquals(viaCoordinator.landing.entries, ready.result.landing.entries)
        assertEquals(viaCoordinator.trace, ready.result.trace)
        assertTrue(ready.result.draft.segments.isNotEmpty())
    }

    @Test fun missing_prerequisite_is_backfilled_and_logged() {
        val context = PlanTeamContext(
            requestKind = PlanRequestKind.PREVIEW_HABIT,
            input = planInput(listOf(task("a", 60, priority = TaskPriority.REQUIRED)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val events = events(context)
        val started = events.filterIsInstance<AgentStarted>()
        assertEquals(
            "习惯调适需要需求简报，需求分析师应被补跑到最前面",
            listOf(PlanAgentId.REQUIREMENTS, PlanAgentId.HABIT, PlanAgentId.AVAILABILITY, PlanAgentId.COMPOSER),
            started.map { it.agentId },
        )
        assertTrue("补跑必须在日志里说清楚", started.first().mission.contains("上游补跑"))
        val ready = events.filterIsInstance<DraftReady>().single()
        assertEquals(
            listOf(PlanAgentId.HABIT, PlanAgentId.AVAILABILITY, PlanAgentId.COMPOSER),
            ready.result.selectedAgents,
        )
    }

    @Test fun memory_carries_across_runs_within_the_same_runtime() {
        val runtime = PlanningTeamRuntime()
        events(ctx(task("a", 60), task("b", 30)), runtime)

        val second = events(ctx(task("a", 60), task("b", 30), task("c", 45)), runtime)
        val started = second.filterIsInstance<TeamStarted>().single()
        assertNotNull("第二次协作应带上上次的记忆", started.memoryNote)
        assertTrue(started.memoryNote!!.contains("上次"))

        val requirements = second.filterIsInstance<AgentFinished>()
            .first { it.agentId == PlanAgentId.REQUIREMENTS }
        assertTrue(
            "需求分析师应说出任务数变化：${requirements.outcomeLine}",
            requirements.outcomeLine.contains("比上次多 1 个"),
        )
        assertEquals(2, runtime.memoryBank().episodesOf(PlanAgentId.COMPOSER).size)
    }

    @Test fun memory_is_private_per_agent() {
        val bank = AgentMemoryBank()
        val requirements = bank.seed(PlanAgentId.REQUIREMENTS, "run-1")
        val availability = bank.seed(PlanAgentId.AVAILABILITY, "run-1")

        requirements.note("taskCount", 3)

        assertEquals(3, requirements.recallInt("taskCount"))
        assertNull("别的 agent 不该看到这份记忆", availability.recall("taskCount"))
    }

    @Test fun a_fresh_runtime_starts_without_memory() {
        val events = events(ctx(task("a", 60)))
        assertNull(events.filterIsInstance<TeamStarted>().single().memoryNote)
    }

    @Test fun workspace_rejects_writes_from_a_non_owner() {
        val context = ctx(task("a", 60))
        val workspace = AgentWorkspace("run-1", context)
        val brief = RequirementsAnalyst.analyze(context)
        try {
            workspace.putBrief(PlanAgentId.COMPOSER, brief, "越权写入")
            fail("合成师不该能写需求简报")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("需求分析师"))
        }
    }

    @Test fun reading_an_absent_slot_names_the_missing_producer() {
        val workspace = AgentWorkspace("run-1", ctx(task("a", 60)))
        try {
            workspace.requireDraft(PlanAgentId.CONFLICT)
            fail("没有草案时必须报错，而不是给默认值")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("方案合成师"))
        }
    }

    @Test fun composer_acceptance_detects_overlapping_segments() {
        val context = ctx(task("a", 60), task("b", 60))
        val workspace = AgentWorkspace("run-1", context)
        workspace.putDraft(
            PlanAgentId.COMPOSER,
            draftOf(
                PlannedSegment(id = "s1", taskId = "a", date = day, startMinute = 480, endMinute = 600),
                PlannedSegment(id = "s2", taskId = "b", date = day, startMinute = 540, endMinute = 660),
            ),
            "两个重叠分段",
        )
        val criterion = AgentRegistry.COMPOSER.criteria.first { it.id == "compose.no-self-overlap" }
        val result = criterion.evaluate(AcceptanceContext(workspace, AgentMemory(PlanAgentId.COMPOSER, "run-1"), false))
        assertEquals(CriterionStatus.FAIL, result.status)
        assertTrue(result.blocking)
    }

    @Test fun composer_acceptance_passes_on_disjoint_segments() {
        val context = ctx(task("a", 60), task("b", 60))
        val workspace = AgentWorkspace("run-1", context)
        workspace.putDraft(
            PlanAgentId.COMPOSER,
            draftOf(
                PlannedSegment(id = "s1", taskId = "a", date = day, startMinute = 480, endMinute = 540),
                PlannedSegment(id = "s2", taskId = "b", date = day, startMinute = 540, endMinute = 600),
            ),
            "两个不重叠分段",
        )
        val criterion = AgentRegistry.COMPOSER.criteria.first { it.id == "compose.no-self-overlap" }
        val result = criterion.evaluate(AcceptanceContext(workspace, AgentMemory(PlanAgentId.COMPOSER, "run-1"), false))
        assertEquals(CriterionStatus.PASS, result.status)
        assertFalse(result.blocking)
    }

    @Test fun composer_acceptance_rejects_unknown_task_ids() {
        val context = ctx(task("a", 60))
        val workspace = AgentWorkspace("run-1", context)
        workspace.putDraft(
            PlanAgentId.COMPOSER,
            draftOf(PlannedSegment(id = "s1", taskId = "ghost", date = day, startMinute = 480, endMinute = 540)),
            "幽灵任务",
        )
        val criterion = AgentRegistry.COMPOSER.criteria.first { it.id == "compose.ids-known" }
        val result = criterion.evaluate(AcceptanceContext(workspace, AgentMemory(PlanAgentId.COMPOSER, "run-1"), false))
        assertEquals(CriterionStatus.FAIL, result.status)
    }

    @Test fun every_agent_declares_at_least_one_blocker_criterion() {
        AgentRegistry.all().forEach { spec ->
            assertTrue(
                "${spec.displayName} 必须至少有一条 BLOCKER 标准",
                spec.criteria.any { it.level == CriterionLevel.BLOCKER },
            )
            assertTrue("${spec.displayName} 必须至少声明一个工具", spec.tools.isNotEmpty())
            assertTrue("${spec.displayName} 必须声明记忆键", spec.memoryKeys.isNotEmpty())
        }
        assertTrue("团队级验收不能为空", AgentRegistry.TEAM_CRITERIA.isNotEmpty())
    }

    private fun draftOf(vararg segments: PlannedSegment) = PlanDraft(
        generatedAt = LocalDateTime.now(),
        segments = segments.toList(),
        pendingTaskIds = emptyList(),
        unscheduledTasks = emptyList(),
    )
}
