package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class ArrangementAvailabilityTest {
    private val day = LocalDate.of(2026, 10, 3)
    private val now = day.atTime(10, 0)
    private val empty = PlanGenerationInput(emptyList(), emptyList(), emptyList(), null)
    private fun override(id: String, type: DateOverrideType, start: Int, end: Int) =
        DateOverride(id, id, type, day, start, end, 0, 0)

    @Test fun absence_of_occupancy_is_not_all_day_availability() {
        assertTrue(ArrangementAvailability.forDay(empty, day, now).isEmpty())
    }
    @Test fun explicit_availability_excludes_past_minutes_and_global_blockers() {
        val input = empty.copy(dateOverrides = listOf(override("free", DateOverrideType.AVAILABLE, 540, 780),
            override("busy", DateOverrideType.BLOCKED, 660, 690)))
        assertEquals(listOf(ArrangementAvailableInterval(601, 660), ArrangementAvailableInterval(690, 780)),
            ArrangementAvailability.forDay(input, day, now))
        assertTrue(ArrangementAvailability.forDay(input, day, now.plusDays(1)).isEmpty())
    }
    private val suggestion = ArrangementCandidate("英语", TaskCategory.COURSE, 30, ArrangementTimeHint(660),
        emptySet(), ArrangementPlacementSource.AI_SUGGESTED)
    private val request = ArrangementAssistantAdviceRequest("复习英语", day, emptyList())
    private fun validate(request: ArrangementAssistantAdviceRequest, candidate: ArrangementCandidate = suggestion) =
        ArrangementAssistantAdviceValidator.validate(ArrangementAssistantAdvice(listOf(candidate), "test"), request)!!.candidates.single()
    @Test fun ai_time_without_declared_availability_becomes_an_unplaced_question() {
        val checked = validate(request)
        assertNull(checked.timeHint.explicitStartMinute)
        assertEquals(ArrangementPlacementSource.UNSCHEDULED, checked.placementSource)
        assertTrue(ArrangementClarification.TIME in checked.needsClarification)
        assertEquals(30, checked.durationMinutes)
    }
    @Test fun ai_cannot_cross_the_edge_of_an_available_interval() {
        assertNull(validate(request.copy(availableIntervals = listOf(ArrangementAvailableInterval(660, 680)))).timeHint.explicitStartMinute)
        assertEquals(660, validate(request.copy(availableIntervals = listOf(ArrangementAvailableInterval(660, 690)))).timeHint.explicitStartMinute)
    }
    @Test fun explicit_user_time_is_not_confused_with_automatic_scheduling() {
        assertEquals(660, validate(request.copy(utterance = "11:00复习英语30分钟"), suggestion.copy(placementSource = ArrangementPlacementSource.USER_EXPLICIT)).timeHint.explicitStartMinute)
    }
    @Test fun model_cannot_label_an_invented_time_as_a_user_fact() {
        assertNull(validate(request, suggestion.copy(placementSource = ArrangementPlacementSource.USER_EXPLICIT)).timeHint.explicitStartMinute)
    }
}
