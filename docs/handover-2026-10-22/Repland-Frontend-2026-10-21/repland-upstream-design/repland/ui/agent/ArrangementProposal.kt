package com.swan1127.repland.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.swan1127.repland.domain.model.ArrangementCandidate
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.ChatDayPlanner
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.ui.theme.replandAccent
import java.util.UUID

/**
 * The preview both entry points share.
 *
 * Placement used to live here as an ad-hoc "find a free five-minute step" loop that only
 * ran for items the user had already timed, so an untimed item stayed `时间待定` forever and
 * the app never actually planned anything. It now delegates to [ChatDayPlanner], which
 * places every item that has a duration, in priority order, into the real day.
 */
data class AgentTaskProposal(
    val id: String,
    val text: String,
    val category: TaskCategory,
    val durationMinutes: Int?,
    val timeHint: ArrangementTimeHint,
    val needsClarification: Set<ArrangementClarification>,
    val placementSource: ArrangementPlacementSource,
    val trackId: String = "focus",
    /** Null means the sentence implied nothing about urgency. */
    val priority: TaskPriority? = null,
    /** The length is a typical-value assumption, not something the user said. */
    val durationEstimated: Boolean = false,
)

fun parseProposal(
    candidates: List<ArrangementCandidate>,
    occupiedEntries: List<TimelineEntry>,
    dayStartMinute: Int = ChatDayPlanner.DEFAULT_DAY_START_MINUTE,
    dayEndMinute: Int = ChatDayPlanner.DEFAULT_DAY_END_MINUTE,
): List<AgentTaskProposal> {
    // A candidate the engine already placed is used verbatim. Re-planning it here would
    // relabel a time the app chose as a time the user stated.
    val pendingIndices = candidates.indices.filter { index ->
        val candidate = candidates[index]
        !(candidate.placementSource == ArrangementPlacementSource.AI_SUGGESTED &&
            candidate.timeHint.explicitStartMinute != null)
    }
    val plan = ChatDayPlanner.plan(
        candidates = pendingIndices.map { candidates[it] },
        occupied = occupiedEntries,
        dayStartMinute = dayStartMinute,
        dayEndMinute = dayEndMinute,
    )

    return candidates.mapIndexed { index, candidate ->
        val position = pendingIndices.indexOf(index)
        val placement = if (position >= 0) plan.placementFor(position) else null
        val start = placement?.startMinute ?: candidate.timeHint.explicitStartMinute
        val duration = candidate.durationMinutes
        val source = when {
            placement != null && placement.fromUser -> ArrangementPlacementSource.USER_EXPLICIT
            placement != null -> ArrangementPlacementSource.AI_SUGGESTED
            candidate.placementSource == ArrangementPlacementSource.AI_SUGGESTED && start != null ->
                ArrangementPlacementSource.AI_SUGGESTED
            start != null -> ArrangementPlacementSource.USER_EXPLICIT
            else -> ArrangementPlacementSource.UNSCHEDULED
        }
        val missing = candidate.needsClarification.toMutableSet().apply {
            if (start == null) add(ArrangementClarification.TIME) else remove(ArrangementClarification.TIME)
            if (duration == null) add(ArrangementClarification.DURATION) else remove(ArrangementClarification.DURATION)
        }
        AgentTaskProposal(
            id = UUID.randomUUID().toString(),
            text = candidate.title,
            category = candidate.category,
            durationMinutes = duration,
            timeHint = candidate.timeHint.copy(explicitStartMinute = start),
            needsClarification = missing,
            placementSource = source,
            trackId = placement?.trackId ?: candidate.preferredTrackId ?: "focus",
            priority = candidate.priority,
            durationEstimated = candidate.durationEstimated,
        )
    }
}

@Composable
fun previewColor(category: TaskCategory): Color = replandAccent(previewAccentIndex(category)).surface

@Composable
fun previewInk(category: TaskCategory): Color = replandAccent(previewAccentIndex(category)).ink

private fun previewAccentIndex(category: TaskCategory): Int = when (category) {
    TaskCategory.COURSE -> 0
    TaskCategory.EXTRACURRICULAR -> 1
    TaskCategory.OFFICE -> 3
    TaskCategory.LEISURE -> 2
}

fun formatMinute(minute: Int): String = String.format("%02d:%02d", minute / 60, minute % 60)
