package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.planteam.runtime.ToolCalled
import com.swan1127.repland.domain.planteam.runtime.ToolOutcome
import com.swan1127.repland.testkit.PlanningOperationFixture
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production service and local team, rather than a second planning implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningOperationTeamIntegrationTest {
    private fun day(fixture: PlanningOperationFixture) = LocalDate.now(fixture.clock)

    private fun oldDraft(fixture: PlanningOperationFixture) = PlanDraft(
        LocalDateTime.now(fixture.clock).minusHours(1), emptyList(), emptyList(), emptyList(),
        orderedTaskIds = listOf("task"), sourceRevision = "previous-source",
    )

    private fun reference(fixture: PlanningOperationFixture, reviewed: Boolean = false): MemoryReference {
        val captured = day(fixture).minusDays(10)
        val seeded = MemoryModel.seed(4, captured)
        val state = if (reviewed) MemoryModel.grade(seeded, RecallGrade.RECALLED, day(fixture)) else seeded
        return MemoryReference.of(listOf(MemoryEntry("task", "A real learning note", state)), day(fixture))
    }

    private fun assertRejected(result: Result<*>, message: String) {
        val error = result.exceptionOrNull()
        assertTrue("Expected a guarded rejection, got $error", error is IllegalArgumentException)
        assertTrue("Unexpected rejection: ${error?.message}", error?.message.orEmpty().contains(message))
    }

    @Test fun sortOnlyStoresAnOrderingPreviewWithoutReadingMemoryOrChangingTheConfirmedSchedule() = runTest {
        val fixture = PlanningOperationFixture(initialTasks = listOf(
            task("low", priority = TaskPriority.LOW), task("high", priority = TaskPriority.HIGH),
        ))
        val placement = PlannedSegment("placed", "low", day(fixture), 600, 660, trackId = "course")
        val current = confirmedPlan(segments = listOf(placement))
        fixture.plans.current.value = current
        fixture.plans.order.value = listOf("low", "high")
        fixture.beforeLogs = { error("Sort-only must not read execution feedback") }
        val service = fixture.service(memory = { error("Sort-only must not read learning memory") })

        val draft = service.preview(PlanningPreviewKind.SORT_ONLY)

        assertTrue(draft.orderOnly)
        assertTrue(draft.segments.isEmpty())
        assertTrue(draft.unscheduledTasks.isEmpty())
        assertEquals(listOf("high", "low"), draft.orderedTaskIds)
        assertEquals(current, fixture.plans.current.value)
        assertEquals(listOf("low", "high"), fixture.plans.order.value)
        assertEquals(draft, fixture.plans.draft.value)
        assertNotNull(draft.sourceRevision)
        assertEquals(0, fixture.plans.acceptCalls)
        assertTrue(service.teamPreview.value.events.isEmpty())
        assertNull(service.teamPreview.value.result)
    }

    @Test fun arrangeTodayRetainsOtherDatesAndExactLockedAndRunningPlacements() = runTest {
        val fixture = PlanningOperationFixture(initialTasks = listOf(
            task("locked"), task("running"), task("tomorrow"), task("new"),
        ))
        val today = day(fixture)
        val locked = PlannedSegment("locked-segment", "locked", today, 480, 540, true, "custom-lock")
        // The task status is NOT_STARTED: protection must come from the active execution session.
        val running = PlannedSegment("running-segment", "running", today, 540, 600, false, "custom-run")
        val tomorrow = PlannedSegment("other-date", "tomorrow", today.plusDays(1), 780, 840, false, "custom-future")
        val historical = PlannedSegment("history", "new", today.minusDays(1), 660, 720)
        val original = confirmedPlan(segments = listOf(locked, running, tomorrow, historical))
        fixture.plans.current.value = original
        fixture.sessions.active.value = ExecutionSession(
            "session", "running", "Running task", original.id, running.id, 3600, fixture.clock.millis(),
        )
        val service = fixture.service()

        val draft = service.preview(PlanningPreviewKind.ARRANGE_TODAY)

        assertEquals(listOf(historical, tomorrow).sortedBy { it.date }, draft.segments.filter { it.date != today })
        assertEquals(locked, draft.segments.single { it.id == locked.id })
        assertEquals(running, draft.segments.single { it.id == running.id })
        assertFalse(draft.segments.single { it.id == running.id }.isLocked)
        assertTrue(draft.segments.any { it.taskId == "new" && it.date == today })
        assertEquals(original, fixture.plans.current.value)
        assertEquals(0, fixture.plans.acceptCalls)
        assertEquals(draft, service.teamPreview.value.result?.draft)
    }

    @Test fun fullReplanAlsoProtectsTheActiveSessionEvenWhenItsTaskStatusHasNotChanged() = runTest {
        val fixture = PlanningOperationFixture(initialTasks = listOf(task("task"), task("new")))
        val running = PlannedSegment("running", "task", day(fixture), 600, 660, trackId = "custom")
        fixture.plans.current.value = confirmedPlan(segments = listOf(running))
        fixture.sessions.active.value = ExecutionSession("session", "task", "Task", "plan-1", "running", 3600, fixture.clock.millis())

        val draft = fixture.service().preview(PlanningPreviewKind.REPLAN_REMAINING)

        assertEquals(running, draft.segments.single { it.taskId == "task" })
        assertFalse(draft.segments.filter { it.taskId == "new" }.any {
            it.date == running.date && it.startMinute < running.endMinute && it.endMinute > running.startMinute
        })
        assertEquals(TaskStatus.NOT_STARTED, fixture.tasks.value.single { it.id == "task" }.status)
    }

    @Test fun memoryIsSkippedByDefaultAndEnablingItAddsOnlySoftPlacementAdvice() = runTest {
        val fixture = PlanningOperationFixture()
        val service = fixture.service()
        service.preview(PlanningPreviewKind.FORMULATE)
        val skipped = service.teamPreview.value.events.filterIsInstance<ToolCalled>().single { it.record.toolId == "habit.memory" }
        assertEquals(ToolOutcome.SKIPPED, skipped.record.outcome)
        assertNull(service.teamPreview.value.result?.hints?.perTask?.get("task")?.preferredWindow)

        val original = fixture.reads.snapshot()
        fixture.memory = reference(fixture)
        val draft = service.preview(PlanningPreviewKind.FORMULATE)
        val read = service.teamPreview.value.events.filterIsInstance<ToolCalled>().single { it.record.toolId == "habit.memory" }
        assertEquals(ToolOutcome.OK, read.record.outcome)
        assertNotNull(service.teamPreview.value.result?.hints?.perTask?.get("task")?.preferredWindow)
        assertEquals(original, fixture.reads.snapshot())
        assertEquals(original.input.tasks.single().totalDurationMinutes,
            draft.segments.sumOf { it.endMinute - it.startMinute })
        assertEquals(0, fixture.plans.acceptCalls)

        // Turning the reference off does not mutate the caller's saved review facts.
        val savedReference = fixture.memory
        val disabled = fixture.service(memory = { null })
        disabled.preview(PlanningPreviewKind.FORMULATE)
        assertEquals(savedReference, fixture.memory)
        assertEquals(ToolOutcome.SKIPPED, disabled.teamPreview.value.events.filterIsInstance<ToolCalled>()
            .single { it.record.toolId == "habit.memory" }.record.outcome)
    }

    @Test fun feedbackOrReferenceSwitchChangesDuringGenerationPreserveTheExistingDraftAndPlan() = runTest {
        for (disable in listOf(false, true)) {
            val fixture = PlanningOperationFixture()
            fixture.memory = reference(fixture)
            val previous = oldDraft(fixture)
            fixture.plans.draft.value = previous
            val original = confirmedPlan()
            fixture.plans.current.value = original
            val gate = CompletableDeferred<Unit>()
            fixture.beforeLogs = { gate.await() }
            val service = fixture.service(planningDispatcher = StandardTestDispatcher(testScheduler))
            val pending = async { runCatching { service.preview(PlanningPreviewKind.FORMULATE) } }
            runCurrent()
            assertTrue(service.teamPreview.value.isRunning)

            fixture.memory = if (disable) null else reference(fixture, reviewed = true)
            gate.complete(Unit)

            assertRejected(pending.await(), "记忆参考")
            assertEquals(previous, fixture.plans.draft.value)
            assertEquals(original, fixture.plans.current.value)
            assertEquals(0, fixture.plans.saveDraftCalls)
            assertEquals(0, fixture.plans.acceptCalls)
            assertFalse(service.teamPreview.value.isRunning)
        }
    }

    @Test fun sourceChangesDuringGenerationCannotOverwriteThePreviousPreview() = runTest {
        for (change in listOf("task", "time", "current")) {
            val fixture = PlanningOperationFixture()
            val previous = oldDraft(fixture)
            fixture.plans.draft.value = previous
            val gate = CompletableDeferred<Unit>()
            fixture.beforeLogs = { gate.await() }
            val service = fixture.service(planningDispatcher = StandardTestDispatcher(testScheduler))
            val pending = async { runCatching { service.preview(PlanningPreviewKind.FORMULATE) } }
            runCurrent()
            when (change) {
                "task" -> fixture.tasks.value = fixture.tasks.value.map { it.copy(totalDurationMinutes = 90) }
                "time" -> fixture.time.blocks.value = fixture.time.blocks.value.map { it.copy(endMinute = 660) }
                "current" -> fixture.plans.current.value = confirmedPlan("new-current")
            }
            gate.complete(Unit)

            assertRejected(pending.await(), "已变化")
            assertEquals("Lost draft after a $change change", previous, fixture.plans.draft.value)
            assertEquals(0, fixture.plans.saveDraftCalls)
            assertFalse(service.teamPreview.value.isRunning)
        }
    }

    @Test fun concurrentManualDraftEditIsNotReplacedByTheFinishingTeam() = runTest {
        val fixture = PlanningOperationFixture()
        val previous = oldDraft(fixture)
        fixture.plans.draft.value = previous
        val gate = CompletableDeferred<Unit>()
        fixture.beforeLogs = { gate.await() }
        val service = fixture.service(planningDispatcher = StandardTestDispatcher(testScheduler))
        val pending = async { runCatching { service.preview(PlanningPreviewKind.FORMULATE) } }
        runCurrent()
        val edited = previous.copy(orderedTaskIds = listOf("manually-edited"), hasManualTaskOrder = true)
        fixture.plans.replaceDraftIfCurrent(previous, edited)
        gate.complete(Unit)

        assertRejected(pending.await(), "草案已被修改")
        assertEquals(edited, fixture.plans.draft.value)
        assertEquals(0, fixture.plans.saveDraftCalls)
        assertEquals(0, fixture.plans.acceptCalls)
    }

    @Test fun confirmingALegacyDraftWithoutSourceRevisionStopsBeforeTheRepositoryWrite() = runTest {
        val fixture = PlanningOperationFixture()
        val legacy = oldDraft(fixture).copy(sourceRevision = null)
        fixture.plans.draft.value = legacy

        assertRejected(runCatching { fixture.service().confirm(legacy) }, "缺少来源版本")

        assertEquals(legacy, fixture.plans.draft.value)
        assertEquals(0, fixture.plans.acceptCalls)
        assertNull(fixture.plans.current.value)
    }

    @Test fun confirmingAReplacedPreviewPropagatesTheRepositoryRejectionAndKeepsTheNewDraft() = runTest {
        val fixture = PlanningOperationFixture()
        val service = fixture.service()
        val old = service.preview(PlanningPreviewKind.FORMULATE)
        val replacement = old.copy(generatedAt = old.generatedAt.plusSeconds(1))
        fixture.plans.draft.value = replacement

        val rejected = runCatching { service.confirm(old) }

        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
        assertEquals(replacement, fixture.plans.draft.value)
        assertTrue(fixture.plans.acceptedDrafts.isEmpty())
        assertNull(fixture.plans.current.value)
    }

    @Test fun repositorySourceRevisionFailureIsNotConvertedToASuccessfulConfirmation() = runTest {
        val fixture = PlanningOperationFixture()
        val service = fixture.service()
        val draft = service.preview(PlanningPreviewKind.FORMULATE)
        val current = confirmedPlan("unchanged")
        fixture.plans.current.value = current
        val rejection = IllegalArgumentException("事务检测到来源已变化")
        fixture.plans.acceptFailure = rejection

        val result = runCatching { service.confirm(draft) }

        assertSame(rejection, result.exceptionOrNull())
        assertEquals(draft, fixture.plans.draft.value)
        assertEquals(current, fixture.plans.current.value)
        assertTrue(fixture.plans.acceptedDrafts.isEmpty())
    }

    @Test fun changedReviewFeedbackRejectsConfirmationBeforeCallingTheRepository() = runTest {
        val fixture = PlanningOperationFixture()
        fixture.memory = reference(fixture)
        val service = fixture.service()
        val draft = service.preview(PlanningPreviewKind.FORMULATE)
        fixture.memory = reference(fixture, reviewed = true)

        assertRejected(runCatching { service.confirm(draft) }, "记忆参考")

        assertEquals(draft, fixture.plans.draft.value)
        assertEquals(0, fixture.plans.acceptCalls)
        assertNull(fixture.plans.current.value)
    }

    @Test fun aNewServiceCanConfirmAPersistedDraftWhenTheSameReferenceIsStillCurrent() = runTest {
        val fixture = PlanningOperationFixture()
        fixture.memory = reference(fixture)
        val draft = fixture.service().preview(PlanningPreviewKind.FORMULATE)

        fixture.service().confirm(draft)

        assertEquals(listOf(draft), fixture.plans.acceptedDrafts)
        assertNull(fixture.plans.draft.value)
        assertEquals(draft.segments, fixture.plans.current.value?.segments)
    }

    @Test fun aNewServiceStillRejectsReviewChangesToAPersistedPreview() = runTest {
        val fixture = PlanningOperationFixture()
        fixture.memory = reference(fixture)
        val draft = fixture.service().preview(PlanningPreviewKind.FORMULATE)
        fixture.memory = reference(fixture, reviewed = true)

        assertRejected(runCatching { fixture.service().confirm(draft) }, "记忆参考")

        assertEquals(draft, fixture.plans.draft.value)
        assertEquals(0, fixture.plans.acceptCalls)
    }

    @Test fun enablingReferenceAfterRestartInvalidatesAPreviewGeneratedWithReferenceDisabled() = runTest {
        val fixture = PlanningOperationFixture()
        val draft = fixture.service().preview(PlanningPreviewKind.FORMULATE)
        fixture.memory = reference(fixture)

        assertRejected(runCatching { fixture.service().confirm(draft) }, "记忆参考")

        assertEquals(draft, fixture.plans.draft.value)
        assertEquals(0, fixture.plans.acceptCalls)
    }

    @Test fun clearingTeamMemoryWhileGenerationIsSuspendedCannotResurrectItsPreview() = runTest {
        val fixture = PlanningOperationFixture()
        val gate = CompletableDeferred<Unit>()
        fixture.beforeLogs = { gate.await() }
        val service = fixture.service(planningDispatcher = StandardTestDispatcher(testScheduler))
        val pending = async { runCatching { service.preview(PlanningPreviewKind.FORMULATE) } }
        runCurrent()

        service.clearTeamMemory()
        gate.complete(Unit)

        assertTrue(pending.await().isFailure)
        assertNull(fixture.plans.draft.value)
        assertEquals(0, fixture.plans.saveDraftCalls)
        assertFalse(service.teamPreview.value.isRunning)
        assertTrue(service.teamPreview.value.events.isEmpty())
        assertNull(service.teamPreview.value.result)
        assertNull(service.teamPreview.value.draft)
    }
}
