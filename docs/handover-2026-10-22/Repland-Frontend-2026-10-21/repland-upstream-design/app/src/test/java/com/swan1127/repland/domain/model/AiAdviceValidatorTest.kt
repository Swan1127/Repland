package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiAdviceValidator] is the only place where provider output stops being untrusted
 * text and becomes something the UI will render. A hole here is worse than a wrong
 * number: it lets a malformed or over-broad proposal reach the user as advice.
 *
 * Every "accepts" test below is paired with the "rejects" test for the rule it is
 * meant to protect, so a mutation that deletes a guard turns the pair red.
 */
class AiAdviceValidatorTest {

    // ---------------------------------------------------------------- request/response binding

    @Test
    fun `rejects a response that carries an unsupported contract version`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(
            contractVersion = "repland-ai-advisor/v2",
            requestId = "req-1",
            summary = "ok",
        )
        assertInvalid("Unsupported contract version", request, response)
    }

    @Test
    fun `rejects a response that belongs to a different request`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = "req-2",
            summary = "ok",
        )
        assertInvalid("Response does not belong to this request", request, response)
    }

    @Test
    fun `rejects a response type that the request never asked for`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiSortingExplanationAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = "req-1",
            explanation = "ok",
        )
        assertInvalid("Response type is not allowed for this request", request, response)
    }

    // ---------------------------------------------------------------- task understanding

    @Test
    fun `accepts an understanding advice with a summary and no note`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "先读教材第一章")
        assertValid(request, response)
    }

    @Test
    fun `rejects an understanding advice whose summary is blank`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "   ")
        assertInvalid("Advice text is empty or too long", request, response)
    }

    @Test
    fun `rejects an understanding advice whose optional note is present but blank`() {
        // A present-but-empty optional field is malformed output, not an absent field.
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(
            AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "ok", dependencyNote = "",
        )
        assertInvalid("Advice text is empty or too long", request, response)
    }

    // ---------------------------------------------------------------- difficulty and duration

    @Test
    fun `accepts difficulty and duration at the exact boundaries`() {
        val request = AiDifficultyAndDurationRequest(requestId = "req-1", task = context("current-task"))
        assertValid(request, difficultyAdvice("req-1", difficulty = 1, minutes = 1))
        assertValid(request, difficultyAdvice("req-1", difficulty = 5, minutes = 1_440))
    }

    @Test
    fun `accepts a difficulty advice that suggests no duration`() {
        val request = AiDifficultyAndDurationRequest(requestId = "req-1", task = context("current-task"))
        assertValid(request, difficultyAdvice("req-1", difficulty = 3, minutes = null))
    }

    @Test
    fun `rejects a difficulty outside one to five`() {
        val request = AiDifficultyAndDurationRequest(requestId = "req-1", task = context("current-task"))
        assertInvalid(
            "Difficulty or duration is outside the accepted range",
            request,
            difficultyAdvice("req-1", difficulty = 0, minutes = null),
        )
        assertInvalid(
            "Difficulty or duration is outside the accepted range",
            request,
            difficultyAdvice("req-1", difficulty = 6, minutes = null),
        )
    }

    @Test
    fun `rejects a suggested duration outside one to fourteen hundred forty minutes`() {
        val request = AiDifficultyAndDurationRequest(requestId = "req-1", task = context("current-task"))
        assertInvalid(
            "Difficulty or duration is outside the accepted range",
            request,
            difficultyAdvice("req-1", difficulty = 3, minutes = 0),
        )
        assertInvalid(
            "Difficulty or duration is outside the accepted range",
            request,
            difficultyAdvice("req-1", difficulty = 3, minutes = 1_441),
        )
    }

    // ---------------------------------------------------------------- task breakdown

    @Test
    fun `rejects a breakdown with no steps`() {
        val request = AiTaskBreakdownRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskBreakdownAdvice(AI_ADVISOR_CONTRACT_VERSION, "req-1", steps = emptyList(), explanation = "ok")
        assertInvalid("A task breakdown needs valid steps", request, response)
    }

    @Test
    fun `rejects a breakdown that contains one blank step`() {
        val request = AiTaskBreakdownRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskBreakdownAdvice(
            AI_ADVISOR_CONTRACT_VERSION, "req-1", steps = listOf("读第一章", "  "), explanation = "ok",
        )
        assertInvalid("A task breakdown needs valid steps", request, response)
    }

    // ---------------------------------------------------------------- plan proposal: shape

    @Test
    fun `accepts a plan proposal that stays inside the affected tasks`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
        )
        assertValid(request, response)
    }

    @Test
    fun `rejects a plan proposal that references a task the request never included`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-2", TEST_MONDAY, 480, 540)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `rejects a segment that is not aligned to the thirty minute quantum`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 485, 545)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `rejects a segment that does not end after it starts`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 540, 480)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `rejects a segment that reaches the exclusive end of the day`() {
        // startMinute 1440 is outside 0 until 1440, so the last legal start is 1410.
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 1_440, 1_440)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `rejects two proposed segments that overlap each other`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1"), context("affected-task-2")))
        val response = planAdvice(
            segments = listOf(
                AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 600),
                AiProposedSegment("affected-task-2", TEST_MONDAY, 540, 660),
            ),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `rejects more than one hundred twenty proposed segments`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        // 121 segments, each on its own day at 08:00-08:30: every one is shape-valid and
        // overlap-free, so only the size guard can reject this.
        val segments = (0..120).map { offset ->
            AiProposedSegment("affected-task-1", TEST_MONDAY.plusDays(offset.toLong()), 480, 510)
        }
        assertInvalid(
            "Plan proposal references an unrelated task or invalid segment",
            request,
            planAdvice(segments = segments),
        )
    }

    // ---------------------------------------------------------------- plan proposal: hard constraints

    @Test
    fun `rejects a segment that overlaps a hard constraint`() {
        val request = replanRequest(
            tasks = listOf(context("affected-task-1")),
            hardConstraints = listOf(AiHardConstraintContext(TEST_MONDAY, 540, 600)),
        )
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 570)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `accepts a segment that merely touches a hard constraint boundary`() {
        // Half-open intervals: 480-540 and 540-600 do not overlap.
        val request = replanRequest(
            tasks = listOf(context("affected-task-1")),
            hardConstraints = listOf(AiHardConstraintContext(TEST_MONDAY, 540, 600)),
        )
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
        )
        assertValid(request, response)
    }

    // ---------------------------------------------------------------- plan proposal: locked segments

    @Test
    fun `rejects a proposal that would overwrite a locked segment`() {
        val locked = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = true)
        val request = replanRequest(tasks = listOf(context("affected-task-1", segments = listOf(locked))))
        // 08:00-10:00 overlaps the locked 08:00-09:00 without being identical to it.
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 600)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `allows a disjoint proposal for a locked task because preservation happens locally`() {
        // `movesLockedSegment` only refuses a proposal that would COLLIDE with a locked slot.
        // Keeping the locked segment itself is the local layer's job: toSafeDraft preserves
        // every locked baseline segment and drops the task's movable ones, so a disjoint
        // proposal simply relocates the movable minutes. Pinning this stops someone from
        // "fixing" the validator into rejecting every proposal that mentions a locked task.
        val locked = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = true)
        val request = replanRequest(tasks = listOf(context("affected-task-1", segments = listOf(locked))))
        assertValid(
            request,
            planAdvice(segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 600, 660))),
        )
    }

    @Test
    fun `accepts a proposal that repeats a locked segment unchanged`() {
        // Re-emitting a locked segment verbatim is not a move; only a change is rejected.
        val locked = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = true)
        val request = replanRequest(tasks = listOf(context("affected-task-1", segments = listOf(locked))))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
        )
        assertValid(request, response)
    }

    @Test
    fun `rejects a proposal that puts another task on top of a locked segment`() {
        val locked = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = true)
        val request = replanRequest(
            tasks = listOf(context("affected-task-1", segments = listOf(locked)), context("affected-task-2")),
        )
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-2", TEST_MONDAY, 480, 540)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    // ---------------------------------------------------------------- plan proposal: REQUIRED tasks

    @Test
    fun `rejects a proposal that changes the placement of a required task`() {
        val fixed = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = false)
        val request = replanRequest(
            tasks = listOf(context("affected-task-1", priority = TaskPriority.REQUIRED, segments = listOf(fixed))),
        )
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 600, 660)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `accepts a proposal that repeats the confirmed placement of a required task`() {
        val fixed = AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = false)
        val request = replanRequest(
            tasks = listOf(context("affected-task-1", priority = TaskPriority.REQUIRED, segments = listOf(fixed))),
        )
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
        )
        assertValid(request, response)
    }

    @Test
    fun `rejects any placement for a required task that has no confirmed segment`() {
        // There is nothing to compare against, so `none { ... }` is vacuously true and the
        // proposal is refused. A required task is placed by the user, never by the advisor.
        val request = replanRequest(tasks = listOf(context("affected-task-1", priority = TaskPriority.REQUIRED)))
        val response = planAdvice(
            segments = listOf(AiProposedSegment("affected-task-1", TEST_MONDAY, 480, 540)),
        )
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    // ---------------------------------------------------------------- plan proposal: manual order

    @Test
    fun `rejects a proposal that rewrites a protected manual order`() {
        val request = replanRequest(
            tasks = listOf(context("affected-task-1"), context("affected-task-2")),
            protectedManualOrderReferences = listOf("affected-task-1", "affected-task-2"),
        )
        val response = planAdvice(ordered = listOf("affected-task-2", "affected-task-1"))
        assertInvalid("Plan proposal references an unrelated task or invalid segment", request, response)
    }

    @Test
    fun `accepts a proposal that leaves a protected manual order untouched`() {
        val request = replanRequest(
            tasks = listOf(context("affected-task-1"), context("affected-task-2")),
            protectedManualOrderReferences = listOf("affected-task-1", "affected-task-2"),
        )
        val response = planAdvice(ordered = listOf("affected-task-1", "affected-task-2"))
        assertValid(request, response)
    }

    @Test
    fun `accepts an empty order even when a manual order is protected`() {
        // `changesManualOrder` deliberately requires a non-empty replacement order. Dropping
        // the whole order is caught one layer down: toSafeDraft keeps baseline.orderedTaskIds
        // whenever the confirmed plan carries a manual order.
        val request = replanRequest(
            tasks = listOf(context("affected-task-1")),
            protectedManualOrderReferences = listOf("affected-task-1"),
        )
        assertValid(request, planAdvice(ordered = emptyList()))
    }

    @Test
    fun `rejects an order that lists a task the request never included`() {
        // NOTE: the separate `orderedTaskReferences.size > allowedReferences.size` guard is
        // unreachable — a list that is fully inside `allowedReferences` and has no duplicates
        // can never be longer than it. This test pins the guard that actually fires.
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        assertInvalid(
            "Plan proposal references an unrelated task or invalid segment",
            request,
            planAdvice(ordered = listOf("affected-task-1", "affected-task-9")),
        )
    }

    @Test
    fun `rejects an order that repeats the same task twice`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        assertInvalid(
            "Plan proposal references an unrelated task or invalid segment",
            request,
            planAdvice(ordered = listOf("affected-task-1", "affected-task-1")),
        )
    }

    @Test
    fun `rejects plan advice whose explanation is blank`() {
        val request = replanRequest(tasks = listOf(context("affected-task-1")))
        assertInvalid(
            "Advice text is empty or too long",
            request,
            planAdvice(explanation = "   "),
        )
    }

    // ---------------------------------------------------------------- daily summary

    @Test
    fun `accepts a daily summary with no next step`() {
        val request = AiDailySummaryRequest(requestId = "req-1", day = TEST_MONDAY, confirmedFeedback = emptyList())
        val response = AiDailySummaryAdvice(AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "今天完成了两段专注")
        assertValid(request, response)
    }

    @Test
    fun `rejects a daily summary whose optional next step is present but blank`() {
        val request = AiDailySummaryRequest(requestId = "req-1", day = TEST_MONDAY, confirmedFeedback = emptyList())
        val response = AiDailySummaryAdvice(
            AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "ok", suggestedNextStep = "  ",
        )
        assertInvalid("Advice text is empty or too long", request, response)
    }

    @Test
    fun `rejects advice text longer than two thousand characters`() {
        val request = AiTaskUnderstandingRequest(requestId = "req-1", task = context("current-task"))
        val response = AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, "req-1", summary = "x".repeat(2_001))
        assertInvalid("Advice text is empty or too long", request, response)
    }

    // ---------------------------------------------------------------- builders

    private fun context(
        reference: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        segments: List<AiSegmentContext> = emptyList(),
    ): AiTaskContext = AiTaskContext(
        reference = reference,
        title = "title",
        description = "description",
        category = TaskCategory.COURSE,
        initialPriority = priority,
        currentStatus = TaskStatus.NOT_STARTED,
        estimatedDays = 1,
        expectedDurationMinutes = 60,
        dueDate = null,
        recentFeedback = emptyList(),
        confirmedSegments = segments,
    )

    private fun replanRequest(
        tasks: List<AiTaskContext>,
        hardConstraints: List<AiHardConstraintContext> = emptyList(),
        protectedManualOrderReferences: List<String> = emptyList(),
    ): AiReplanRequest = AiReplanRequest(
        contractVersion = AI_ADVISOR_CONTRACT_VERSION,
        requestId = "req-1",
        affectedTasks = tasks,
        constraintSummary = emptyList(),
        hardConstraints = hardConstraints,
        protectedManualOrderReferences = protectedManualOrderReferences,
    )

    private fun planAdvice(
        segments: List<AiProposedSegment> = emptyList(),
        ordered: List<String> = emptyList(),
        explanation: String = "ok",
    ): AiPlanDraftAdvice = AiPlanDraftAdvice(
        contractVersion = AI_ADVISOR_CONTRACT_VERSION,
        requestId = "req-1",
        orderedTaskReferences = ordered,
        proposedSegments = segments,
        explanation = explanation,
    )

    private fun difficultyAdvice(
        requestId: String,
        difficulty: Int,
        minutes: Int?,
    ): AiDifficultyAndDurationAdvice = AiDifficultyAndDurationAdvice(
        contractVersion = AI_ADVISOR_CONTRACT_VERSION,
        requestId = requestId,
        difficulty = difficulty,
        suggestedDurationMinutes = minutes,
        explanation = "ok",
    )

    private fun assertValid(request: AiAdvisorRequest, response: AiAdvisorResponse) {
        val result = AiAdviceValidator.validate(request, response)
        assertTrue("expected Valid but was $result", result is AiAdviceValidation.Valid)
    }

    private fun assertInvalid(expectedReason: String, request: AiAdvisorRequest, response: AiAdvisorResponse) {
        val result = AiAdviceValidator.validate(request, response)
        assertTrue("expected Invalid but was $result", result is AiAdviceValidation.Invalid)
        assertEquals(expectedReason, (result as AiAdviceValidation.Invalid).reason)
    }
}
