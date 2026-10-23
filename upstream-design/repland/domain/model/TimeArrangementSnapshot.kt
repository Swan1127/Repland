package com.swan1127.repland.domain.model

/**
 * Everything the user owns about *when* things happen: the weekly pattern plus the one-day
 * exceptions. Reading, rather than writing, both tables together is the point.
 *
 * Undo needs a value that can be captured before an operation and put back afterwards. The design
 * question is what that value should cover. The tempting answer is "only the rows this operation
 * touched", with a hand-written inverse per operation — but that table has to be extended every
 * time an operation grows a field, and in this project's HTML prototype exactly that happened:
 * the restore table fell out of sync with the snapshot table and undo silently stopped restoring
 * part of the state. Nobody noticed until a test compared the two lists.
 *
 * So the unit is the whole arrangement, not the delta. It is a little more work per undo and it
 * cannot drift, because there is no second list to keep in sync.
 *
 * `id`s are carried through on purpose. Restoring must put back the *same* rows: if a restore
 * minted fresh ids, a redo followed by another undo would be addressing rows that no longer exist.
 * `createdAtEpochMillis` rides along for the same reason — a row that comes back stamped "now" is
 * not the row that was deleted, and the JSON export publishes that field.
 */
data class TimeArrangementSnapshot(
    val blocks: List<WeeklyTimeBlockDraft>,
    val overrides: List<DateOverrideDraft>,
) {
    val isEmpty: Boolean get() = blocks.isEmpty() && overrides.isEmpty()

    companion object {
        val Empty = TimeArrangementSnapshot(emptyList(), emptyList())
    }
}

/** Turns a stored block back into the draft shape a restore replays. */
fun WeeklyTimeBlock.toDraft(): WeeklyTimeBlockDraft = WeeklyTimeBlockDraft(
    id = id,
    title = title,
    kind = kind,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    weekPattern = weekPattern,
    trackId = trackId,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun DateOverride.toDraft(): DateOverrideDraft = DateOverrideDraft(
    id = id,
    title = title,
    type = type,
    date = date,
    startMinute = startMinute,
    endMinute = endMinute,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
)
