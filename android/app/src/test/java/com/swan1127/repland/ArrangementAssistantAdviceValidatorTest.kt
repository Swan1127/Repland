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
