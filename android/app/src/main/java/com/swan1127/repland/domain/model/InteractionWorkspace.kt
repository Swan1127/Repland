package com.swan1127.repland.domain.model

import java.time.LocalDate

data class RhythmTrack(val id: String, val name: String)

object RhythmTracks {
    val defaults = listOf(RhythmTrack("course", "课程"), RhythmTrack("focus", "专注"), RhythmTrack("rest", "休息"))

    data class Projection(val tracks: List<RhythmTrack>, val entries: List<TimelineEntry>)

    /** IDs, not occupied-lane indices, own the user-facing names. */
    fun project(entries: List<TimelineEntry>, definitions: List<RhythmTrack>): Projection {
        val tracks = definitions.toMutableList()
        entries.forEach { entry ->
            if (tracks.none { it.id == entry.trackId }) tracks += RhythmTrack(entry.trackId, when (entry.kind) {
                TimelineKind.COURSE -> "课程"
                TimelineKind.REST -> "休息"
                TimelineKind.COMMITMENT -> "固定事项"
                TimelineKind.TASK -> if (entry.trackId == "focus") "专注" else "并行任务"
            })
        }
        val ends = MutableList(tracks.size) { -1 }
        val projected = entries.sortedWith(compareBy(TimelineEntry::startMinute, TimelineEntry::endMinute, TimelineEntry::id)).map { entry ->
            val preferred = tracks.indexOfFirst { it.id == entry.trackId }
            val lane = if (ends[preferred] <= entry.startMinute) preferred else {
                // Preserve legacy parallel placements visually without renaming their stored IDs.
                tracks += RhythmTrack(entry.trackId, "${tracks[preferred].name} · 并行")
                ends += -1
                tracks.lastIndex
            }
            ends[lane] = entry.endMinute
            entry.copy(lane = lane)
        }
        return Projection(tracks, projected)
    }
}

data class AssistantTaskProposal(
    val id: String,
    val text: String,
    val category: TaskCategory,
    val durationMinutes: Int?,
    val timeHint: ArrangementTimeHint,
    val needsClarification: Set<ArrangementClarification>,
    val placementSource: ArrangementPlacementSource,
    val trackId: String = "focus",
    val existingTaskId: String? = null,
)

/** Local editable data only: never credentials, raw responses, or a running request. */
data class AssistantWorkspace(
    val date: LocalDate,
    val prompt: String = "",
    val proposals: List<AssistantTaskProposal> = emptyList(),
    val intent: ArrangementIntent? = null,
    val selectedIntent: ArrangementIntent? = null,
    val sourceRevision: String? = null,
)
