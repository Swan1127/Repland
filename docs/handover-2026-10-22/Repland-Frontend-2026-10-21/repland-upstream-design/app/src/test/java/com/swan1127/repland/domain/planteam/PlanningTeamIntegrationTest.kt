package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.MemoryReference
import com.swan1127.repland.domain.model.MemorySignal
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.planInput
import com.swan1127.repland.domain.model.task
import com.swan1127.repland.domain.model.weeklyBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlanningTeamIntegrationTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val now = LocalDateTime.of(day, LocalTime.of(7, 0))
    private val availability = listOf(
        weeklyBlock("available", kind = TimeBlockKind.AVAILABLE, dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 720),
    )

    private fun context(tasks: List<Task>, kind: PlanRequestKind = PlanRequestKind.FULL_REPLAN): PlanTeamContext =
        PlanTeamContext(
            requestKind = kind,
            input = planInput(tasks, weeklyBlocks = availability),
            targetDate = day,
            profileFacts = PlanTeamProfileFacts(isConfigured = false, preferMorningForDeepWork = false),
            now = now,
        )

    private fun run(context: PlanTeamContext): PlanTeamResult = runBlocking {
        PlanningTeamCoordinator().run(context)
    }

    @Test fun everyInProgressTaskKeepsItsOwnExistingPlacement() {
        val tasks = listOf(
            task("first", 60).copy(status = TaskStatus.IN_PROGRESS),
            task("second", 60).copy(status = TaskStatus.IN_PROGRESS),
        )
        val placements = listOf(
            PlannedSegment("existing:first", "first", day, 480, 540),
            PlannedSegment("existing:second", "second", day, 600, 660),
        )
        val context = context(tasks).copy(current = confirmedPlan(segments = placements))
        val result = run(context)

        assertTrue(result.draft.segments.containsAll(placements))
        assertEquals(120, result.availability!!.capacityMinutes)
        assertEquals(setOf("first", "second"), result.calibration!!.signals
            .filter { it.kind == CalibrationKind.ACTIVE_SESSION }.mapNotNull { it.taskId }.toSet())
        assertTrue(result.calibration!!.signals.filter { it.kind == CalibrationKind.ACTIVE_SESSION }
            .all { "不据此推断计时会话" in it.message })
    }

    @Test fun preexistingLockedOverlapIsReportedWithoutBlockingTheDraft() {
        val placements = listOf(
            PlannedSegment("lock:first", "first", day, 480, 540, isLocked = true),
            PlannedSegment("lock:second", "second", day, 510, 570, isLocked = true),
        )
        val context = context(listOf(task("first", 60), task("second", 60)))
            .copy(current = confirmedPlan(segments = placements))
        val result = run(context)

        assertTrue(result.draft.segments.containsAll(placements))
        assertTrue(result.report!!.items.any { it.type == ConflictType.OVERLAP && it.severity == Severity.WARN })
        assertEquals(0, result.report!!.fatalCount)
    }

    @Test fun newlyGeneratedOverlapWithALockRemainsFatal() {
        val locked = PlannedSegment("lock:first", "first", day, 480, 540, isLocked = true)
        val newPlacement = PlannedSegment("draft:second", "second", day, 510, 570)
        val context = context(listOf(task("first", 60), task("second", 60)))
            .copy(current = confirmedPlan(segments = listOf(locked)))
        val draft = PlanDraft(now, listOf(locked, newPlacement), emptyList(), emptyList())

        assertEquals(Severity.FATAL, ConflictChecker.overlaps(draft, context).single().severity)
    }

    @Test fun historicalOverlapDoesNotBlockAUsableFutureDraft() {
        val history = listOf(
            PlannedSegment("history:first", "first", day.minusDays(1), 480, 540),
            PlannedSegment("history:second", "second", day.minusDays(1), 510, 570),
        )
        val result = run(context(listOf(task("first", 60), task("second", 60)))
            .copy(current = confirmedPlan(segments = history)))

        assertTrue(result.draft.segments.containsAll(history))
        assertEquals(120, result.draft.segments.filter { it.date == day }.sumOf { it.endMinute - it.startMinute })
        assertEquals(0, result.report!!.fatalCount)
    }

    @Test fun memoryReferenceChangesRealPlacementButNotDurationOrPriority() {
        val earlyAvailability = listOf(
            weeklyBlock("early", dayOfWeek = day.dayOfWeek, startMinute = 0, endMinute = 720),
        )
        val base = context(listOf(task("review", 60))).let { context ->
            context.copy(
                now = day.atStartOfDay(),
                input = context.input.copy(weeklyBlocks = earlyAvailability),
            )
        }
        val reference = MemoryReference(day, listOf(MemorySignal("review", "概念复习", 2.0, 0.3, day.minusDays(1), 1.0)))
        val plain = run(base)
        val withMemory = run(base.copy(profileFacts = base.profileFacts.copy(memory = reference)))

        assertEquals(0, plain.draft.segments.first().startMinute)
        assertEquals(480, withMemory.draft.segments.first().startMinute)
        assertEquals(plain.draft.priorityAssessments, withMemory.draft.priorityAssessments)
        assertEquals(plain.draft.orderedTaskIds, withMemory.draft.orderedTaskIds)
        assertEquals(60, withMemory.draft.segments.sumOf { it.endMinute - it.startMinute })
        assertTrue(withMemory.hints!!.profileNotes.any { "记忆参考" in it })
        assertFalse(withMemory.hints!!.profileNotes.any { "偏好参考缓冲" in it })
    }

    @Test fun unavailableMemoryWindowFallsBackWithoutLosingWork() {
        val context = context(listOf(task("review", 60))).let { context ->
            context.copy(input = context.input.copy(weeklyBlocks = listOf(
                weeklyBlock("late", dayOfWeek = day.dayOfWeek, startMinute = 900, endMinute = 1020),
            )))
        }
        val reference = MemoryReference(day, listOf(MemorySignal("review", "概念", 2.0, 0.3, day, 1.0)))
        val result = run(context.copy(profileFacts = context.profileFacts.copy(memory = reference)))

        assertEquals(900, result.draft.segments.first().startMinute)
        assertEquals(60, result.draft.segments.sumOf { it.endMinute - it.startMinute })
        assertTrue(result.draft.unscheduledTasks.isEmpty())
    }

    @Test fun remainingGenerationHonorsTheTaskIntendedDay() {
        val context = context(listOf(task("future", 60).copy(scheduledForDate = day.plusDays(1))))
        val result = run(context.copy(input = context.input.copy(weeklyBlocks = availability +
            weeklyBlock("tomorrow", dayOfWeek = day.plusDays(1).dayOfWeek, startMinute = 480, endMinute = 720))))

        assertEquals(day.plusDays(1), result.draft.segments.first().date)
        assertEquals(0, result.report!!.fatalCount)
    }

    @Test fun calendarCommitmentsReachCompositionAndPreview() {
        val commitment = weeklyBlock("meeting", kind = TimeBlockKind.COURSE,
            dayOfWeek = day.dayOfWeek, startMinute = 540, endMinute = 600)
        val result = run(context(listOf(task("work", 90))).copy(calendarBlocks = listOf(commitment)))

        assertTrue(result.draft.segments.none { it.startMinute < 600 && it.endMinute > 540 })
        assertEquals(90, result.draft.segments.sumOf { it.endMinute - it.startMinute })
        assertEquals(0, result.report!!.fatalCount)
        assertTrue(result.availability!!.blocked.any { it.title == commitment.title })
    }

    @Test fun teamCanUseExplicitLateNightAvailabilityWithoutChangingLegacyDefaults() {
        val context = context(listOf(task("late", 30))).let { context ->
            context.copy(
                now = day.atTime(22, 0),
                input = context.input.copy(weeklyBlocks = listOf(
                    weeklyBlock("night", dayOfWeek = day.dayOfWeek, startMinute = 1380, endMinute = 1410),
                )),
            )
        }
        val result = run(context)

        assertEquals(1380, result.draft.segments.single().startMinute)
        assertEquals(30, result.availability!!.capacityMinutes)
    }

    @Test fun advisoryEnrichmentPropagatesCancellation() = runBlocking {
        val advisor = object : AiAdvisor {
            override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult =
                throw CancellationException("cancel requested")
        }
        try {
            PlanningTeamCoordinator().run(context(listOf(task("work", 60))), advisor)
            fail("Cancellation must not become a successful local fallback")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel requested", cancelled.message)
        }
    }

    @Test fun unwiredHabitsCannotBeReintroducedByAiDifficulty() = runBlocking {
        val context = context(listOf(task("work", 60))).copy(
            profileFacts = PlanTeamProfileFacts(isConfigured = false, preferMorningForDeepWork = true),
        )
        val brief = RequirementsAnalyst.analyze(context).copy(aiInsights = mapOf("work" to AiTaskInsight(difficulty = 5)))
        val advisor = object : AiAdvisor {
            override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult =
                error("Habit tuning must only consume validated prior insights")
        }
        val hints = HabitTuner.tuneWithAi(brief, context, advisor)

        assertNull(hints.perTask.getValue("work").preferredWindow)
        assertTrue(hints.perCategory.values.none { "默认上午" in it.rationale })
    }

    @Test fun unknownDurationDoesNotStealCapacityFromKnownWorkDuringRepair() {
        val context = context(listOf(task("unknown", null), task("known", 30)), PlanRequestKind.REPAIR)
        val result = run(context.copy(
            input = context.input.copy(weeklyBlocks = listOf(
                weeklyBlock("only", dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 510),
            )),
            current = confirmedPlan(segments = emptyList()),
        ))

        assertEquals("known", result.draft.segments.single().taskId)
        assertTrue("unknown" in result.draft.pendingTaskIds)
        assertTrue(result.draft.unscheduledTasks.none { it.taskId == "known" })
    }

    @Test fun unknownDurationKeepsTheUsersExistingPlacementWithoutTrimmingIt() {
        val placement = PlannedSegment("existing:unknown", "unknown", day, 480, 600)
        val result = run(context(listOf(task("unknown", null)), PlanRequestKind.REPAIR)
            .copy(current = confirmedPlan(segments = listOf(placement))))

        assertEquals(listOf(placement), result.draft.segments)
        assertFalse("unknown" in result.draft.pendingTaskIds)
    }

    @Test fun aPartialQuantumLockBlocksEveryOverlappingCandidateSlot() {
        val locked = PlannedSegment("lock", "locked", day, 490, 520, isLocked = true)
        val context = context(listOf(task("locked", 30), task("new", 30)))
        val result = run(context.copy(
            input = context.input.copy(weeklyBlocks = listOf(
                weeklyBlock("only", dayOfWeek = day.dayOfWeek, startMinute = 480, endMinute = 570),
            )),
            current = confirmedPlan(segments = listOf(locked)),
        ))

        assertEquals(540, result.draft.segments.single { it.taskId == "new" }.startMinute)
        assertEquals(0, result.report!!.fatalCount)
    }

    @Test fun missingAvailabilityDoesNotTurnAnExistingManualPlacementIntoAFatalNewDecision() {
        val placement = PlannedSegment("existing:manual", "work", day, 480, 540)
        val context = context(listOf(task("work", 60)))
        val result = run(context.copy(
            input = context.input.copy(weeklyBlocks = emptyList()),
            current = confirmedPlan(segments = listOf(placement)),
        ))

        assertEquals(listOf(placement), result.draft.segments)
        assertEquals(0, result.availability!!.capacityMinutes)
        assertEquals(0, result.report!!.fatalCount)
        assertTrue(result.report!!.items.any { it.severity == Severity.WARN })
    }

    @Test fun elapsedPartOfAnInProgressPlacementDoesNotReduceUnconfirmedRemainingWork() {
        val existing = PlannedSegment("existing:ongoing", "work", day, 480, 540)
        val result = run(context(listOf(task("work", 60, status = TaskStatus.IN_PROGRESS)))
            .copy(now = day.atTime(8, 30), current = confirmedPlan(segments = listOf(existing))))

        assertTrue(result.draft.segments.contains(existing))
        assertTrue(result.draft.segments.any { it.taskId == "work" && it.startMinute == 540 && it.endMinute == 570 })
        assertEquals(60, result.draft.segments.filter { it.date == day }.sumOf { segment ->
            (segment.endMinute - maxOf(segment.startMinute, 510)).coerceAtLeast(0)
        })
    }

    @Test fun overlappingProtectedPlacementsCountFutureCapacityOnlyOncePerTask() {
        val protected = listOf(
            PlannedSegment("lock:first", "work", day, 480, 540, isLocked = true),
            PlannedSegment("lock:second", "work", day, 510, 570, isLocked = true),
        )
        val result = run(context(listOf(task("work", 120)))
            .copy(current = confirmedPlan(segments = protected)))

        assertTrue(result.draft.segments.containsAll(protected))
        assertTrue(result.draft.segments.any { it.startMinute == 570 && it.endMinute == 600 })
        assertEquals(0, result.report!!.fatalCount)
    }
}
