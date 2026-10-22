package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AI_ADVISOR_CONTRACT_VERSION
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiDifficultyAndDurationAdvice
import com.swan1127.repland.domain.model.AiDifficultyAndDurationRequest
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.model.weeklyBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTeamCoordinatorTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val now = LocalDateTime.of(day, LocalTime.of(7, 0))
    private val avail = listOf(
        weeklyBlock("avail", kind = TimeBlockKind.AVAILABLE, dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 720),
    )

    @Test fun add_selects_requirements_calibrator_availability_conflict_composer() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.ADD,
            input = planInput(listOf(task("a", 60), task("b", 30)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(
                PlanAgentId.REQUIREMENTS, PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
            ),
            result.selectedAgents,
        )
        assertNotNull(result.brief)
        assertNotNull(result.availability)
        assertNotNull("增量规划也要知道今天已经发生了什么", result.calibration)
        assertNull(result.hints)
        assertNotNull(result.report)
        assertTrue(result.draft.segments.isNotEmpty())
        assertEquals(day, result.landing.date)
        assertTrue(result.trace.isNotEmpty())
    }

    @Test fun remove_selects_calibrator_availability_conflict_composer() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REMOVE,
            input = planInput(listOf(task("a", 60)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER),
            result.selectedAgents,
        )
        assertNull(result.brief)
    }

    @Test fun full_replan_selects_all_six_and_reports_unschedulable() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.FULL_REPLAN,
            input = planInput(
                listOf(task("a", 60), task("b", 30), task("c", null)),
                weeklyBlocks = avail,
            ),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(6, result.selectedAgents.size)
        assertNotNull(result.hints)
        assertNotNull(result.calibration)
        assertTrue(result.report!!.items.any { it.type == ConflictType.UNSCHEDULABLE })
    }

    @Test fun repair_selects_the_same_six_as_a_full_replan() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", 60), task("b", 30)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(
                PlanAgentId.REQUIREMENTS, PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY,
                PlanAgentId.HABIT, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER,
            ),
            result.selectedAgents,
        )
    }

    @Test fun adjust_selects_calibrator_conflict_composer() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.ADJUST,
            input = planInput(listOf(task("a", 60)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(PlanAgentId.CALIBRATOR, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER),
            result.selectedAgents,
        )
    }

    @Test fun recover_selects_calibrator_availability_conflict_composer() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.RECOVER,
            input = planInput(listOf(task("a", 60)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(PlanAgentId.CALIBRATOR, PlanAgentId.AVAILABILITY, PlanAgentId.CONFLICT, PlanAgentId.COMPOSER),
            result.selectedAgents,
        )
        assertNull(result.hints)
    }

    @Test fun preview_habit_runs_habit_availablity_composer() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.PREVIEW_HABIT,
            input = planInput(listOf(task("a", 60, priority = TaskPriority.REQUIRED)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertEquals(
            listOf(PlanAgentId.HABIT, PlanAgentId.AVAILABILITY, PlanAgentId.COMPOSER),
            result.selectedAgents,
        )
        assertNotNull(result.hints)
        val hint = result.hints!!.perTask["a"]
        assertEquals(BlockStyle.DEEP_FOCUS, hint?.blockStyle)
        assertEquals(TimeWindow(480, 720), hint?.preferredWindow)
    }

    @Test fun unwired_profile_defaults_are_not_reported_as_user_habits() {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.PREVIEW_HABIT,
            input = planInput(listOf(task("a", 60, priority = TaskPriority.REQUIRED)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
            profileFacts = PlanTeamProfileFacts(isConfigured = false),
        )
        val result = runBlocking { PlanningTeamCoordinator().run(ctx) }
        assertNull("没有接入作息设置时不能假装用户偏爱上午", result.hints!!.perTask["a"]?.preferredWindow)
        assertTrue(result.hints!!.profileNotes.any { "未接入" in it })
        assertTrue(result.hints!!.profileNotes.none { "缓冲 30" in it })
    }

    @Test fun repair_keeps_stable_placements_instead_of_rebuilding() {
        val stableB = PlannedSegment(id = "plan:b:1", taskId = "b", date = day, startMinute = 480, endMinute = 510)
        val stableA = PlannedSegment(id = "plan:a:1", taskId = "a", date = day, startMinute = 540, endMinute = 600)
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", 60), task("b", 30)), weeklyBlocks = avail),
            current = confirmedPlan(segments = listOf(stableB, stableA)),
            targetDate = day,
            now = now,
        )
        val draft = runBlocking { PlanningTeamCoordinator().run(ctx) }.draft
        assertTrue(
            "已稳定的安排必须原样保留，而不是被重新排一遍",
            draft.segments.any { it.id == "plan:b:1" && it.startMinute == 480 && it.endMinute == 510 },
        )
        assertTrue(
            draft.segments.any { it.id == "plan:a:1" && it.startMinute == 540 && it.endMinute == 600 },
        )
    }

    @Test fun add_keeps_existing_placement_and_finds_room_for_the_new_task() {
        val stableB = PlannedSegment(id = "plan:b:1", taskId = "b", date = day, startMinute = 480, endMinute = 510)
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.ADD,
            input = planInput(listOf(task("a", 60), task("b", 30)), weeklyBlocks = avail),
            current = confirmedPlan(segments = listOf(stableB)),
            targetDate = day,
            now = now,
        )
        val draft = runBlocking { PlanningTeamCoordinator().run(ctx) }.draft
        assertTrue("新增任务不能挤走已有安排", draft.segments.any { it.id == "plan:b:1" })
        assertTrue("新任务必须得到时段", draft.segments.any { it.taskId == "a" })
    }

    @Test fun recover_limits_new_work_to_today_and_keeps_future_untouched() {
        val tomorrow = day.plusDays(1)
        val blocks = avail + listOf(
            weeklyBlock(
                "avail-tomorrow", kind = TimeBlockKind.AVAILABLE,
                dayOfWeek = tomorrow.dayOfWeek, startMinute = 480, endMinute = 720,
            ),
        )
        val todaySegment = PlannedSegment(id = "plan:a:1", taskId = "a", date = day, startMinute = 540, endMinute = 600)
        val futureSegment = PlannedSegment(id = "plan:b:1", taskId = "b", date = tomorrow, startMinute = 540, endMinute = 570)
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.RECOVER,
            input = planInput(listOf(task("a", 60), task("b", 30), task("c", 60)), weeklyBlocks = blocks),
            current = confirmedPlan(segments = listOf(todaySegment, futureSegment)),
            targetDate = day,
            now = now,
        )
        val draft = runBlocking { PlanningTeamCoordinator().run(ctx) }.draft
        assertTrue("今天已有的安排要保留", draft.segments.any { it.id == "plan:a:1" })
        assertTrue("未来安排不在今天的操作范围内", draft.segments.any { it.id == "plan:b:1" })
        assertTrue(
            "只排今天时不能把新任务排到明天",
            draft.segments.none { it.taskId == "c" && it.date != day },
        )
        assertTrue("今天还有空位时，新任务应该排进今天", draft.segments.any { it.taskId == "c" && it.date == day })
    }

    @Test fun tasks_without_an_estimate_stay_pending_in_a_repair() {
        val stableB = PlannedSegment(id = "plan:b:1", taskId = "b", date = day, startMinute = 480, endMinute = 510)
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", null), task("b", 30)), weeklyBlocks = avail),
            current = confirmedPlan(segments = listOf(stableB)),
            targetDate = day,
            now = now,
        )
        val draft = runBlocking { PlanningTeamCoordinator().run(ctx) }.draft
        assertTrue("缺时长的任务不能被凭空安排", draft.segments.none { it.taskId == "a" })
        assertTrue(draft.pendingTaskIds.contains("a"))
    }

    @Test fun verifier_blocks_new_clashes_but_only_warns_on_retained_ones() {
        val course = weeklyBlock(
            "course", kind = TimeBlockKind.COURSE, dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 600,
        )
        val clash = PlannedSegment(id = "s1", taskId = "a", date = day, startMinute = 540, endMinute = 600)
        val base = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", 60)), weeklyBlocks = avail + course),
            targetDate = day,
            now = now,
        )
        val fresh = ConflictChecker.constraints(draftOf(clash), base)
        assertTrue("本轮新排进课程时段的冲突必须阻断", fresh.any { it.severity == Severity.FATAL })

        val retained = base.copy(current = confirmedPlan(segments = listOf(clash)))
        val existing = ConflictChecker.constraints(draftOf(clash), retained)
        assertTrue(existing.isNotEmpty())
        assertTrue("用户已有的安排只能报警，不能反过来阻断预览", existing.all { it.severity == Severity.WARN })
    }

    @Test fun elapsed_segments_are_history_not_new_conflicts() {
        val yesterday = day.minusDays(1)
        val history = PlannedSegment(
            id = "history:a", taskId = "a", date = yesterday, startMinute = 480, endMinute = 600,
        )
        val tuesdayCourse = weeklyBlock(
            "course-yesterday", kind = TimeBlockKind.COURSE,
            dayOfWeek = yesterday.dayOfWeek, startMinute = 480, endMinute = 600,
        )
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", 60)), weeklyBlocks = avail + tuesdayCourse),
            targetDate = day,
            now = now,
        )
        assertTrue(
            "昨天的安排无法修改，不能反过来阻断今天的预览",
            ConflictChecker.constraints(draftOf(history), ctx).isEmpty(),
        )
    }

    @Test fun calibrator_reports_todays_execution_without_scoring_it() {
        val todaySegment = PlannedSegment(id = "plan:a:1", taskId = "a", date = day, startMinute = 540, endMinute = 600)
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.REPAIR,
            input = planInput(listOf(task("a", 60), task("b", 30), task("c", 30)), weeklyBlocks = avail),
            current = confirmedPlan(segments = listOf(todaySegment)),
            targetDate = day,
            now = now,
            runningTaskId = "a",
            execution = ExecutionPulse(
                asOf = day,
                completedTaskIds = setOf("c"),
                partialTaskIds = setOf("b"),
                postponedTaskIds = emptySet(),
                feedbackCount = 2,
            ),
        )
        val report = runBlocking { PlanningTeamCoordinator().run(ctx) }.calibration
        assertNotNull(report)
        assertTrue(report!!.connected)
        assertEquals(1, report.plannedSegmentsToday)
        assertEquals(60, report.plannedMinutesToday)
        assertTrue(report.signals.any { it.kind == CalibrationKind.ACTIVE_SESSION && it.taskId == "a" })
        assertTrue(report.signals.any { it.kind == CalibrationKind.COMPLETED && it.taskId == "c" })
        assertTrue(report.signals.any { it.kind == CalibrationKind.PARTIAL && it.taskId == "b" })
        assertTrue("校准师不能给用户打效率分", report.notes.none { "效率" in it || "落后" in it })
        assertTrue(report.notes.isNotEmpty())
    }

    @Test fun noop_advisor_is_identical_to_phase_a() = runBlocking {
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.FULL_REPLAN,
            input = planInput(
                listOf(task("a", 60), task("b", 30), task("c", null)),
                weeklyBlocks = avail,
            ),
            targetDate = day,
            now = now,
        )
        val ai = PlanningTeamCoordinator().run(ctx, NoOpAiAdvisor)
        val base = PlanningTeamCoordinator().run(ctx)
        assertEquals(base.selectedAgents, ai.selectedAgents)
        assertEquals(base.draft.segments, ai.draft.segments)
        assertEquals(base.landing.entries, ai.landing.entries)
        assertTrue(ai.brief!!.aiInsights.isEmpty())
    }

    @Test fun valid_ai_enriches_brief_and_hints_without_rescheduling() = runBlocking {
        val fake = FakeAiAdvisor(
            onUnderstanding = { req ->
                AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, req.requestId, "需提前准备", null)
            },
            onDifficulty = { req ->
                AiDifficultyAndDurationAdvice(AI_ADVISOR_CONTRACT_VERSION, req.requestId, 4, 90, "中等偏难")
            },
        )
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.FULL_REPLAN,
            input = planInput(
                listOf(task("a", 60), task("b", 30), task("c", null)),
                weeklyBlocks = avail,
            ),
            targetDate = day,
            now = now,
        )
        val ai = PlanningTeamCoordinator().run(ctx, fake)
        assertTrue(ai.brief!!.aiInsights.size >= 2)
        assertTrue(ai.brief!!.aiInsights.containsKey("a"))
        assertTrue(ai.hints!!.profileNotes.any { "AI 估计" in it })
        assertEquals(TimeWindow(480, 720), ai.hints!!.perTask["a"]?.preferredWindow)
        val base = PlanningTeamCoordinator().run(ctx)
        assertEquals(base.draft.segments, ai.draft.segments)
    }

    @Test fun mismatched_request_id_is_rejected_gracefully() = runBlocking {
        val fake = FakeAiAdvisor(
            onUnderstanding = {
                AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, "wrong-request-id", "注入内容", null)
            },
        )
        val ctx = PlanTeamContext(
            requestKind = PlanRequestKind.FULL_REPLAN,
            input = planInput(listOf(task("a", 60), task("b", 30)), weeklyBlocks = avail),
            targetDate = day,
            now = now,
        )
        val ai = PlanningTeamCoordinator().run(ctx, fake)
        assertTrue(ai.brief!!.aiInsights.isEmpty())
    }

    private fun draftOf(vararg segments: PlannedSegment) = PlanDraft(
        generatedAt = now,
        segments = segments.toList(),
        pendingTaskIds = emptyList(),
        unscheduledTasks = emptyList(),
    )
}

private class FakeAiAdvisor(
    private val onUnderstanding: (AiTaskUnderstandingRequest) -> AiTaskUnderstandingAdvice? = { null },
    private val onDifficulty: (AiDifficultyAndDurationRequest) -> AiDifficultyAndDurationAdvice? = { null },
) : AiAdvisor {
    override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult = when (request) {
        is AiTaskUnderstandingRequest -> onUnderstanding(request)
            ?.let { AiAdvisorResult.Advice(it) }
            ?: AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

        is AiDifficultyAndDurationRequest -> onDifficulty(request)
            ?.let { AiAdvisorResult.Advice(it) }
            ?: AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

        else -> AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
    }
}
