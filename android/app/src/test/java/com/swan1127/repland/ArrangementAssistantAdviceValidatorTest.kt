package com.swan1127.repland

import com.swan1127.repland.domain.model.ArrangementAssistantAdvice
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceValidator
import com.swan1127.repland.domain.model.ArrangementCandidate
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementAssistantAdviceValidatorTest {
    @Test fun malformed_follow_up_does_not_silently_drop_part_of_the_draft() {
        val advice = ArrangementAssistantAdvice(listOf(reference("english"), reference("english").copy(existingTaskId = null, durationMinutes = 900)), "test")
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(advice, request.copy(followUpInstruction = "调整")))
    }
    @Test fun draft_reference_is_preserved_during_follow_up() {
        val item = reference("english").copy(proposalId = "p")
        val context = request.copy(draftCandidates = listOf(item), followUpInstruction = "挪到16点")
        assertEquals("p", ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(item.copy(timeHint = ArrangementTimeHint(960))), "test"), context)?.candidates?.single()?.proposalId)
    }
    @Test fun invented_draft_reference_is_rejected() {
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(reference("english").copy(proposalId = "fake")), "test"), request))
    }
    @Test fun draft_reference_cannot_change_its_existing_task_target() {
        val context = request.copy(draftCandidates = listOf(reference("english").copy(proposalId = "p")))
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(reference("english").copy(proposalId = "p", existingTaskId = null)), "test"), context))
    }
    @Test fun duplicate_draft_reference_is_rejected() {
        val item = reference("english").copy(proposalId = "p", existingTaskId = null)
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(item, item), "test"), request.copy(draftCandidates = listOf(item))))
    }
    private val request = com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest("调整英语", java.time.LocalDate.now(), emptyList(), listOf(com.swan1127.repland.domain.model.ArrangementExistingTask("english", "英语", TaskCategory.COURSE, 60)))
    private fun reference(id: String) = ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(600), emptySet(), existingTaskId = id)
    @Test fun known_existing_reference_is_preserved() {
        val result = ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(reference("english")), "test"), request)
        assertEquals("english", result?.candidates?.single()?.existingTaskId)
    }
    @Test fun unknown_reference_rejects_the_entire_reply() {
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(reference("unknown"), reference("english")), "test"), request))
    }
    @Test fun duplicate_reference_rejects_the_entire_reply() {
        org.junit.Assert.assertNull(ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(reference("english"), reference("english")), "test"), request))
    }
    @Test fun invalid_remote_items_are_dropped_before_they_reach_a_track_preview() {
        val advice = ArrangementAssistantAdvice(
            confidenceLabel = "test",
            candidates = listOf(
                ArrangementCandidate(
                    title = "看论文",
                    category = TaskCategory.COURSE,
                    durationMinutes = 45,
                    timeHint = ArrangementTimeHint(explicitStartMinute = 14 * 60),
                    needsClarification = emptySet(),
                ),
                ArrangementCandidate(
                    title = "不应出现",
                    category = TaskCategory.COURSE,
                    durationMinutes = 900,
                    timeHint = ArrangementTimeHint(explicitStartMinute = 15 * 60),
                    needsClarification = setOf(ArrangementClarification.DURATION),
                ),
            ),
        )

        val checked = requireNotNull(ArrangementAssistantAdviceValidator.validate(advice))

        assertEquals(listOf("看论文"), checked.candidates.map(ArrangementCandidate::title))
    }
}
