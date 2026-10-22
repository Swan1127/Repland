package com.swan1127.repland.domain.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanningAgentWorkflow] is the only component that can decide to call a provider, and
 * [PlanningAgent.toSafeDraft] is the only component that can turn a provider proposal into
 * a draft the user might confirm. Both are pure logic, so both are tested here without any
 * Android dependency.
 *
 * The single most important property is negative: with no explicit consent, nothing leaves
 * the device. That is asserted by counting provider calls, not by inspecting state alone.
 */
class PlanningAgentTest {

    // ---------------------------------------------------------------- consent gating

    @Test
    fun `without explicit consent no request is ever sent`() = runBlocking {
        val advisor = FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED) }
        val workflow = workflow(advisor)

        workflow.begin(work(), noConsent)
        assertTrue(workflow.state.value is PlanningAgentState.AwaitingConsent)

        workflow.confirmPreview(noConsent)
        assertEquals(0, advisor.calls)
        assertTrue(workflow.state.value is PlanningAgentState.AwaitingConsent)
    }

    @Test
    fun `resuming after consent moves on to the request preview`() {
        val workflow = workflow(FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.DISABLED) })
        workflow.begin(work(), noConsent)
        assertTrue(workflow.state.value is PlanningAgentState.AwaitingConsent)

        workflow.resumeAfterConsent(consentingAccess)
        assertTrue(workflow.state.value is PlanningAgentState.PreviewingRequest)
    }

    @Test
    fun `a disabled agent falls back without sending a request`() = runBlocking {
        val advisor = FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.DISABLED) }
        val workflow = workflow(advisor)
        workflow.begin(work(), disabled)

        val state = workflow.state.value
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(AiAdvisorFailureReason.DISABLED, (state as PlanningAgentState.FailedFallback).reason)
        assertEquals(0, advisor.calls)
    }

    @Test
    fun `a consented request is sent exactly once`() = runBlocking {
        val advisor = FakeAdvisor { request ->
            AiAdvisorResult.Advice(AiTaskUnderstandingAdvice(request.contractVersion, request.requestId, summary = "ok"))
        }
        val workflow = workflow(advisor)
        workflow.begin(work(), consentingAccess)
        assertTrue(workflow.state.value is PlanningAgentState.PreviewingRequest)

        workflow.confirmPreview(consentingAccess)
        assertEquals(1, advisor.calls)
        assertTrue(workflow.state.value is PlanningAgentState.ShowingAdvice)
    }

    // ---------------------------------------------------------------- result handling

    @Test
    fun `an advice for a different request is rejected as invalid`() = runBlocking {
        val advisor = FakeAdvisor { request ->
            AiAdvisorResult.Advice(
                AiTaskUnderstandingAdvice(request.contractVersion, "another-request", summary = "ok"),
            )
        }
        val workflow = workflow(advisor)
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)

        val state = workflow.state.value
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(AiAdvisorFailureReason.INVALID_RESPONSE, (state as PlanningAgentState.FailedFallback).reason)
    }

    @Test
    fun `an unavailable provider falls back to the local explanation`() = runBlocking {
        val advisor = FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED) }
        val workflow = workflow(advisor)
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)

        val state = workflow.state.value
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(
            AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED,
            (state as PlanningAgentState.FailedFallback).reason,
        )
        assertTrue(state.fallback.explanation.isNotBlank())
    }

    @Test
    fun `a provider that throws becomes a transport failure, not a crash`() = runBlocking {
        val advisor = FakeAdvisor { throw IllegalStateException("socket closed") }
        val workflow = workflow(advisor)
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)

        val state = workflow.state.value
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(AiAdvisorFailureReason.TRANSPORT_FAILURE, (state as PlanningAgentState.FailedFallback).reason)
    }

    // ---------------------------------------------------------------- non-network actions

    @Test
    fun `dismissing never calls the provider`() = runBlocking {
        val advisor = FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.DISABLED) }
        val workflow = workflow(advisor)
        workflow.begin(work(), consentingAccess)

        workflow.dismiss()
        assertEquals(0, advisor.calls)
        assertTrue(workflow.state.value is PlanningAgentState.UserDismissed)
    }

    @Test
    fun `disabling mid-preview ends in a local fallback`() {
        val workflow = workflow(FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.DISABLED) })
        workflow.begin(work(), consentingAccess)
        workflow.disable()

        val state = workflow.state.value
        assertTrue(state is PlanningAgentState.FailedFallback)
        assertEquals(AiAdvisorFailureReason.DISABLED, (state as PlanningAgentState.FailedFallback).reason)
    }

    @Test
    fun `accepting is an acknowledgement that performs no write`() = runBlocking {
        val workflow = workflow(
            FakeAdvisor { request ->
                AiAdvisorResult.Advice(AiTaskUnderstandingAdvice(request.contractVersion, request.requestId, summary = "ok"))
            },
        )
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)
        workflow.markAccepted()
        assertTrue(workflow.state.value is PlanningAgentState.UserAccepted)
    }

    // ---------------------------------------------------------------- audit trail

    @Test
    fun `the audit trail records the outcome of a request`() = runBlocking {
        val advisor = FakeAdvisor { request ->
            AiAdvisorResult.Advice(AiTaskUnderstandingAdvice(request.contractVersion, request.requestId, summary = "ok"))
        }
        val sink = RecordingAuditSink()
        val workflow = workflow(advisor, sink)
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)

        assertEquals(listOf("advice"), sink.events.map(PlanningAgentAuditEvent::resultCode))
        assertEquals(AI_ADVISOR_CONTRACT_VERSION, sink.events.single().contractVersion)
        assertEquals(PlanningAgentRequestType.TASK_UNDERSTANDING, sink.events.single().requestType)
    }

    @Test
    fun `a rejected response is audited with its local validation reason`() = runBlocking {
        val advisor = FakeAdvisor { request ->
            AiAdvisorResult.Advice(AiTaskUnderstandingAdvice(request.contractVersion, "another-request", summary = "ok"))
        }
        val sink = RecordingAuditSink()
        val workflow = workflow(advisor, sink)
        workflow.begin(work(), consentingAccess)
        workflow.confirmPreview(consentingAccess)

        val event = sink.events.single()
        assertEquals("fallback:INVALID_RESPONSE", event.resultCode)
        assertEquals("Response does not belong to this request", event.localValidation)
    }

    // ---------------------------------------------------------------- toSafeDraft

    @Test
    fun `a proposal that keeps the local minutes becomes a draft`() {
        val (agent, prepared) = replanPrepared(
            affected = listOf(task("a")),
            baseline = planDraft(segments = listOf(segment("base", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        val result = agent.validate(
            prepared,
            planAdvice(prepared, segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540))),
        )
        assertTrue(result is AgentValidationResult.Draft)
        val draft = (result as AgentValidationResult.Draft).draft
        assertEquals(1, draft.segments.size)
        assertEquals("a", draft.segments.single().taskId)
        assertEquals(480, draft.segments.single().startMinute)
    }

    @Test
    fun `a proposal that would change the task's total minutes is rejected`() {
        // Baseline gives the task 60 minutes; the proposal asks for 120.
        val (agent, prepared) = replanPrepared(
            affected = listOf(task("a")),
            baseline = planDraft(segments = listOf(segment("base", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        val result = agent.validate(
            prepared,
            planAdvice(prepared, segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 600))),
        )
        assertTrue(result is AgentValidationResult.Invalid)
    }

    @Test
    fun `no proposal for a required task ever becomes a draft`() {
        // End-to-end property rather than a unit test of one guard: a REQUIRED task's
        // placement belongs to the user, so nothing the advisor proposes for it may reach the
        // draft stage. Three layers enforce that, and any of them may be the one that fires:
        //   1. the request carries the confirmed placement as a hard constraint
        //      (PlanningAgent.relevantHardConstraints), so an overlapping proposal is refused;
        //   2. `changesRequiredPlacement` refuses a proposal that differs from it;
        //   3. toSafeDraft's own REQUIRED guard — unreachable while 1 and 2 hold, kept as
        //      defence in depth. Disabling guard 3 leaves this suite green; that is expected,
        //      not a coverage gap, and was confirmed by a surviving mutant.
        // The arrangement below makes the confirmed placement and the local baseline disagree
        // (a legal state: the user's placement and the generator's output can differ), so the
        // property is exercised where the two sources are not identical.
        val required = task("a", priority = TaskPriority.REQUIRED)
        val currentPlan = ConfirmedPlan(
            id = "plan-1",
            createdAtEpochMillis = 1_000L,
            isCurrent = true,
            segments = listOf(segment("confirmed", taskId = "a", startMinute = 480, endMinute = 540)),
        )
        val (agent, prepared) = replanPrepared(
            affected = listOf(required),
            baseline = planDraft(segments = listOf(segment("base", taskId = "a", startMinute = 600, endMinute = 660))),
            currentPlan = currentPlan,
            tasks = listOf(required),
        )
        val advice = planAdvice(prepared, segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)))
        assertTrue(agent.validate(prepared, advice) is AgentValidationResult.Invalid)
    }

    @Test
    fun `a proposal outside the stated availability never becomes a draft`() {
        // Availability is declared for Monday only. The contract validator has no notion of
        // availability, so only the local draft layer can refuse a Tuesday slot.
        val (agent, prepared) = replanPrepared(
            affected = listOf(task("a")),
            baseline = planDraft(segments = listOf(segment("base", taskId = "a", startMinute = 480, endMinute = 540))),
        )
        val result = agent.validate(
            prepared,
            planAdvice(
                prepared,
                segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY.plusDays(1), 480, 540)),
            ),
        )
        assertTrue(result is AgentValidationResult.Invalid)
    }

    @Test
    fun `a proposal that collides with a preserved locked segment is rejected`() {
        val lockedElsewhere = segment("lock", taskId = "b", startMinute = 480, endMinute = 540, isLocked = true)
        val (agent, prepared) = replanPrepared(
            affected = listOf(task("a")),
            baseline = planDraft(
                segments = listOf(
                    segment("base", taskId = "a", startMinute = 600, endMinute = 660),
                    lockedElsewhere,
                ),
            ),
            tasks = listOf(task("a"), task("b")),
        )
        // Same minutes for "a" (60), so only the collision can reject it.
        val result = agent.validate(
            prepared,
            planAdvice(prepared, segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540))),
        )
        assertTrue(result is AgentValidationResult.Invalid)
    }

    @Test
    fun `a confirmed manual order survives a proposal that reorders the draft`() {
        val manualPlan = ConfirmedPlan(
            id = "plan-1",
            createdAtEpochMillis = 1_000L,
            isCurrent = true,
            segments = emptyList(),
            orderedTaskIds = listOf("b", "a"),
            hasManualTaskOrder = true,
        )
        val (agent, prepared) = replanPrepared(
            affected = listOf(task("a"), task("b")),
            baseline = planDraft(
                segments = listOf(segment("base", taskId = "a", startMinute = 480, endMinute = 540)),
                orderedTaskIds = listOf("b", "a"),
            ),
            currentPlan = manualPlan,
            tasks = listOf(task("a"), task("b")),
        )
        // The response must echo the protected order (aliases: a -> 1, b -> 2) or the
        // validator refuses it before the draft layer is reached.
        val result = agent.validate(
            prepared,
            planAdvice(
                prepared,
                segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
                ordered = listOf("affected-task-2", "affected-task-1"),
            ),
        )
        assertTrue(result is AgentValidationResult.Draft)
        assertEquals(listOf("b", "a"), (result as AgentValidationResult.Draft).draft.orderedTaskIds)
    }

    // ---------------------------------------------------------------- builders

    private fun work(): PlanningAgentWork.TaskUnderstanding =
        PlanningAgentWork.TaskUnderstanding(task("t1"), emptyList(), null)

    private val consentingAccess = PlanningAgentAccess(isEnabled = true, hasExplicitConsent = true)
    private val noConsent = PlanningAgentAccess(isEnabled = true, hasExplicitConsent = false)
    private val disabled = PlanningAgentAccess(isEnabled = false, hasExplicitConsent = true)

    private fun workflow(
        advisor: AiAdvisor,
        sink: PlanningAgentAuditSink = NoOpPlanningAgentAuditSink,
    ): PlanningAgentWorkflow = PlanningAgentWorkflow(
        PlanningAgent(advisor, FakeGenerator(planDraft())),
        sink,
    )

    /**
     * Builds an agent and a prepared replan request whose local plan input has explicit
     * availability, which [PlanningConstraintValidator.supportsProposal] requires.
     */
    private fun replanPrepared(
        affected: List<Task>,
        baseline: PlanDraft,
        currentPlan: ConfirmedPlan? = null,
        tasks: List<Task> = affected,
    ): Pair<PlanningAgent, PreparedPlanningAgentWork> {
        val agent = PlanningAgent(
            FakeAdvisor { AiAdvisorResult.Unavailable(AiAdvisorFailureReason.DISABLED) },
            FakeGenerator(baseline),
        )
        val input = planInput(
            tasks = tasks,
            weeklyBlocks = listOf(
                weeklyBlock(id = "free", kind = TimeBlockKind.AVAILABLE, startMinute = 0, endMinute = 1_440),
            ),
        )
        val prepared = agent.prepare(
            PlanningAgentWork.Replan(
                affectedTasks = affected,
                executionLogs = emptyList(),
                currentPlan = currentPlan,
                localPlanInput = input,
                constraintSummary = emptyList(),
            ),
        )
        return agent to prepared
    }

    private fun planAdvice(
        prepared: PreparedPlanningAgentWork,
        segments: List<AiProposedSegment> = emptyList(),
        ordered: List<String> = emptyList(),
        explanation: String = "ok",
    ): AiPlanDraftAdvice = AiPlanDraftAdvice(
        contractVersion = prepared.request.contractVersion,
        requestId = prepared.request.requestId,
        orderedTaskReferences = ordered,
        proposedSegments = segments,
        explanation = explanation,
    )

    private class FakeAdvisor(
        private val handler: (AiAdvisorRequest) -> AiAdvisorResult,
    ) : AiAdvisor {
        var calls = 0
            private set

        override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
            calls += 1
            return handler(request)
        }
    }

    private class FakeGenerator(private val draft: PlanDraft) : PlanDraftGenerator {
        override fun generate(input: PlanGenerationInput): PlanDraft = draft
    }

    private class RecordingAuditSink : PlanningAgentAuditSink {
        val events = mutableListOf<PlanningAgentAuditEvent>()
        override fun record(event: PlanningAgentAuditEvent) {
            events += event
        }
    }
}
