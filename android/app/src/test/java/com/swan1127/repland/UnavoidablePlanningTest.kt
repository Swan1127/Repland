package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UnavoidablePlanningTest {
    private val day = LocalDate.of(2026, 9, 14)
    private val clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC)
    private fun task(id: String, priority: TaskPriority, duration: Int = 30) = Task(
        id, "", id, TaskCategory.UNSPECIFIED, priority, null, duration, day,
        TaskStatus.NOT_STARTED, null, null, null, 0, 1, 1)
    private fun input(tasks: List<Task>) = PlanGenerationInput(tasks,
        listOf(WeeklyTimeBlock("available", "可用", TimeBlockKind.AVAILABLE, day.dayOfWeek,
            480, 630, null, 1, 1)), emptyList(), null)

    @Test fun remaining_and_today_keep_confirmed_unavoidable_interval_without_converting_it_to_a_lock() {
        val fixed = task("required", TaskPriority.REQUIRED, 45)
        val ordinary = task("other", TaskPriority.HIGH)
        val segment = PlannedSegment("original", fixed.id, day, 555, 600, false, "custom")
        val current = ConfirmedPlan("current", 1, true, listOf(segment))
        for (todayOnly in listOf(false, true)) {
            val draft = PlanGenerator.generateRemaining(input(listOf(fixed, ordinary)), current, clock, todayOnly)
            assertEquals(segment, draft.segments.single { it.taskId == fixed.id })
            assertFalse(draft.segments.filter { it.taskId != fixed.id }.any {
                it.date == segment.date && it.startMinute < segment.endMinute && it.endMinute > segment.startMinute })
        }
    }

    @Test fun direct_generation_reserves_unavoidable_confirmed_segments_even_when_their_lock_flag_is_false() {
        val fixed = task("required", TaskPriority.REQUIRED, 45)
        val segment = PlannedSegment("original", fixed.id, day, 555, 600, false)
        val draft = PlanGenerator.generate(listOf(fixed), input(listOf(fixed)).weeklyBlocks,
            emptyList(), lockedSegments = listOf(segment), clock = clock, horizonDays = 1)
        assertEquals(listOf(segment), draft.segments)
    }

    @Test fun an_unplaced_unavoidable_task_is_considered_before_a_higher_soft_score() {
        val fixed = task("required", TaskPriority.REQUIRED).copy(dueDate = null)
        val overdue = task("high", TaskPriority.HIGH).copy(dueDate = day.minusDays(1), postponeCount = 10)
        assertEquals(fixed.id, LocalPriorityRanker.rank(listOf(overdue, fixed), today = day).first().taskId)
        assertEquals(listOf(overdue.id, fixed.id), LocalPriorityRanker.rank(listOf(overdue, fixed),
            today = day, manualTaskOrder = listOf(overdue.id, fixed.id)).map { it.taskId })
    }

    @Test fun missing_availability_never_invents_a_time_for_an_unplaced_unavoidable_task() {
        val draft = PlanGenerator.generate(listOf(task("required", TaskPriority.REQUIRED)),
            emptyList(), emptyList(), clock = clock)
        assertTrue(draft.segments.isEmpty())
        assertEquals(listOf("required"), draft.pendingTaskIds)
    }

    @Test fun completed_unavoidable_work_is_not_reintroduced_by_remaining_planning() {
        val fixed = task("required", TaskPriority.REQUIRED).copy(status = TaskStatus.COMPLETED)
        val current = ConfirmedPlan("current", 1, true,
            listOf(PlannedSegment("original", fixed.id, day, 555, 600)))
        assertTrue(PlanGenerator.generateRemaining(input(listOf(fixed)), current, clock).segments.isEmpty())
    }

    @Test fun disabled_unconfigured_and_timeout_fallbacks_keep_the_same_confirmed_unavoidable_position() = runBlocking {
        val future = LocalDate.now().plusDays(1)
        val fixed = task("required", TaskPriority.REQUIRED).copy(dueDate = future)
        val segment = PlannedSegment("original", fixed.id, future, 600, 630, false)
        val current = ConfirmedPlan("current", 1, true, listOf(segment))
        val localInput = PlanGenerationInput(listOf(fixed),
            listOf(WeeklyTimeBlock("available", "可用", TimeBlockKind.AVAILABLE, future.dayOfWeek,
                480, 660, null, 1, 1)), emptyList(), null)
        val work = PlanningAgentWork.Replan(listOf(fixed), emptyList(), current, localInput, emptyList())
        for (reason in listOf(AiAdvisorFailureReason.DISABLED, AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED, AiAdvisorFailureReason.TIMEOUT)) {
            var calls = 0
            val advisor = object : AiAdvisor {
                override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
                    calls++
                    return AiAdvisorResult.Failed(reason)
                }
            }
            val workflow = PlanningAgentWorkflow(PlanningAgent(advisor, PlanGenerator))
            val access = PlanningAgentAccess(reason != AiAdvisorFailureReason.DISABLED, true)
            workflow.begin(work, access)
            if (access.isEnabled) workflow.confirmPreview(access)
            val result = workflow.state.value as PlanningAgentState.FailedFallback
            assertEquals(segment, (result.fallback as LocalPlanningFallback.Draft).draft.segments.single())
            assertEquals(if (access.isEnabled) 1 else 0, calls)
        }
    }
}

