package com.swan1127.repland.domain.model

/**
 * Places the chat's items onto real clock times.
 *
 * The model reads "下午三点复习高数，另外写实验报告" well and knows nothing about the class
 * that already occupies 15:00. So it is not asked for a slot at all: it reports a duration,
 * an optional explicit time and a priority, and this object solves the packing against the
 * day that actually exists.
 *
 * Three behaviours are deliberate, and each exists because the obvious alternative is wrong:
 *
 *  - **Priority decides who gets the room.** Items are placed in priority order, so a
 *    REQUIRED task claims the morning and a LOW one lands wherever is left. If priority did
 *    not change the output, the user's setting would be decoration.
 *  - **The day gets filled.** An item without an explicit time is packed after the cursor
 *    with a small gap, so "把今天排满" produces a schedule rather than a list of TODOs.
 *  - **Parallel only on purpose.** Placing every item at its own earliest free slot would
 *    stack the whole list on 08:00 across parallel lanes. Sequential work therefore shares
 *    one cursor; a second lane is used only when the user pinned two things to the same
 *    time. That is the one case where "同一时间做多件事" is true.
 */
object ChatDayPlanner {

    const val DEFAULT_DAY_START_MINUTE = 8 * 60
    const val DEFAULT_DAY_END_MINUTE = 22 * 60

    private const val GAP_MINUTES = 5
    private const val SEARCH_STEP_MINUTES = 5
    private const val MAX_PARALLEL_TRACKS = 4
    private const val DEFAULT_TRACK = "focus"

    /** Courses, rest and commitments occupy the person, not a lane. */
    private val HARD_BUSY = setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT)

    data class Placement(
        val index: Int,
        val startMinute: Int,
        val endMinute: Int,
        val trackId: String,
        /** True when the user's own sentence pinned this time, false when we chose it. */
        val fromUser: Boolean,
    )

    enum class UnplacedReason {
        /** No duration, so there is nothing to place. The card must ask for one. */
        MISSING_DURATION,
        /** The day has no room left. The card must say so instead of guessing. */
        NO_ROOM,
    }

    data class Unplaced(val index: Int, val reason: UnplacedReason)

    data class Result(
        val placements: List<Placement>,
        val unplaced: List<Unplaced>,
    ) {
        fun placementFor(index: Int): Placement? = placements.firstOrNull { it.index == index }
    }

    fun plan(
        candidates: List<ArrangementCandidate>,
        occupied: List<TimelineEntry>,
        dayStartMinute: Int = DEFAULT_DAY_START_MINUTE,
        dayEndMinute: Int = DEFAULT_DAY_END_MINUTE,
    ): Result {
        val hardBusy = occupied.filter { it.kind in HARD_BUSY }
        // A task already on the timeline only blocks its own lane; two different lanes are
        // exactly how the app draws simultaneous work.
        val occupiedByTrack = occupied
            .filter { it.kind == TimelineKind.TASK }
            .groupBy(TimelineEntry::trackId)

        val placements = mutableListOf<Placement>()
        val unplaced = mutableListOf<Unplaced>()
        var cursor = dayStartMinute

        // Pinned items first: they consume room before anything is allowed to claim it.
        val order = candidates.withIndex().sortedWith(
            compareBy(
                { if (it.value.timeHint.explicitStartMinute != null) 0 else 1 },
                { -(it.value.priority ?: TaskPriority.MEDIUM).score },
                { -(it.value.durationMinutes ?: 0) },
                { it.index },
            ),
        )

        order.forEach { (index, candidate) ->
            val duration = candidate.durationMinutes
            if (duration == null || duration <= 0) {
                unplaced += Unplaced(index, UnplacedReason.MISSING_DURATION)
                return@forEach
            }
            val pinned = candidate.timeHint.explicitStartMinute
            if (pinned != null) {
                val latest = (dayEndMinute - duration).coerceAtLeast(dayStartMinute)
                val start = pinned.coerceIn(dayStartMinute, latest)
                val placement = placePinned(
                    index = index,
                    start = start,
                    duration = duration,
                    preferredTrack = candidate.preferredTrackId,
                    hardBusy = hardBusy,
                    occupiedByTrack = occupiedByTrack,
                    placements = placements,
                    dayEndMinute = dayEndMinute,
                )
                if (placement == null) {
                    unplaced += Unplaced(index, UnplacedReason.NO_ROOM)
                } else {
                    placements += placement
                    cursor = maxOf(cursor, placement.endMinute + GAP_MINUTES)
                }
            } else {
                val placement = placeAfterCursor(
                    index = index,
                    cursor = cursor,
                    duration = duration,
                    preferredTrack = candidate.preferredTrackId,
                    hardBusy = hardBusy,
                    occupiedByTrack = occupiedByTrack,
                    placements = placements,
                    dayEndMinute = dayEndMinute,
                )
                if (placement == null) {
                    unplaced += Unplaced(index, UnplacedReason.NO_ROOM)
                } else {
                    placements += placement
                    cursor = placement.endMinute + GAP_MINUTES
                }
            }
        }

        return Result(
            placements = placements.sortedBy(Placement::startMinute),
            unplaced = unplaced.sortedBy(Unplaced::index),
        )
    }

    /**
     * A time the user stated is kept even if a class is there. Moving it silently would make
     * the card disagree with the sentence above it, and dropping it would delete a real
     * intention. The overlap stays visible on the timeline and the conflict role reports it;
     * the user is the one who decides.
     */
    private fun placePinned(
        index: Int,
        start: Int,
        duration: Int,
        preferredTrack: String?,
        hardBusy: List<TimelineEntry>,
        occupiedByTrack: Map<String, List<TimelineEntry>>,
        placements: List<Placement>,
        dayEndMinute: Int,
    ): Placement? {
        val end = start + duration
        if (end > dayEndMinute) return null
        val track = laneFor(
            start = start,
            end = end,
            preferredTrack = preferredTrack,
            hardBusy = hardBusy,
            occupiedByTrack = occupiedByTrack,
            placements = placements,
            allowHardBusy = true,
        ) ?: return null
        return Placement(index, start, end, track, fromUser = true)
    }

    private fun placeAfterCursor(
        index: Int,
        cursor: Int,
        duration: Int,
        preferredTrack: String?,
        hardBusy: List<TimelineEntry>,
        occupiedByTrack: Map<String, List<TimelineEntry>>,
        placements: List<Placement>,
        dayEndMinute: Int,
    ): Placement? {
        val latestStart = dayEndMinute - duration
        if (latestStart < cursor) return null
        var start = cursor
        while (start <= latestStart) {
            val track = laneFor(start, start + duration, preferredTrack, hardBusy, occupiedByTrack, placements)
            if (track != null) return Placement(index, start, start + duration, track, fromUser = false)
            start += SEARCH_STEP_MINUTES
        }
        return null
    }

    private fun laneFor(
        start: Int,
        end: Int,
        preferredTrack: String?,
        hardBusy: List<TimelineEntry>,
        occupiedByTrack: Map<String, List<TimelineEntry>>,
        placements: List<Placement>,
        allowHardBusy: Boolean = false,
    ): String? {
        if (!allowHardBusy && hardBusy.any { start < it.endMinute && end > it.startMinute }) return null
        val lanes = buildList {
            preferredTrack?.takeIf(String::isNotBlank)?.let(::add)
            add(DEFAULT_TRACK)
            (2..MAX_PARALLEL_TRACKS).forEach { add("parallel-$it") }
        }.distinct()
        return lanes.firstOrNull { lane ->
            occupiedByTrack[lane].orEmpty().none { start < it.endMinute && end > it.startMinute } &&
                placements.none { it.trackId == lane && start < it.endMinute && end > it.startMinute }
        }
    }
}
