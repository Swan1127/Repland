package com.swan1127.repland.domain.model

/**
 * Which metacognition tool produced a piece of the user's own writing.
 *
 * The set is closed on purpose. A new tool that writes text about a task has to add a case here,
 * and the label lookup in the UI has no `else` branch — so an unlabelled kind is a compile error
 * rather than a row that renders as the empty string.
 *
 * Note what is *not* here: reflections. A reflection is keyed by date, not by task, so it cannot be
 * attached to an evidence row drawn from a task without inventing a relationship that does not
 * exist. The other three tools all name a task, and that is the whole matching rule.
 */
enum class ToolOutputKind {
    KWL,
    REVIEW_POINT,
    SELF_EXPLANATION,
}

/**
 * One thing the user wrote in a tool, as read out of the auxiliary store.
 *
 * Carries no display name: a name belongs to the *task*, and every caller that needs one already
 * has the tasks (or an evidence source) in hand. Keeping it out means the store can produce these
 * without joining against anything, which is what lets the mapping stay a straight line.
 *
 * The excerpt is the user's own sentence. It is never rewritten, only trimmed — the profile page's
 * promise is that a conclusion traces back to something the user actually wrote.
 */
data class ToolOutputRecord(
    val kind: ToolOutputKind,
    val taskId: String,
    val excerpt: String,
)

/** A [ToolOutputRecord] resolved against a task name, ready to render or export. */
data class ProfileEvidenceToolOutput(
    val kind: ToolOutputKind,
    val taskId: String,
    val taskDisplayName: String,
    val excerpt: String,
)

/**
 * The tool writing that relates to one evidence row, and how much of it there was.
 *
 * [total] is carried next to [shown] instead of being recomputed by the caller, so the "还有 N 条"
 * line can never disagree with the list printed above it. A UI that derives the count from a
 * *different* query than the one that produced the list is one refactor away from lying.
 */
data class RelatedToolOutputs(
    val shown: List<ProfileEvidenceToolOutput>,
    val total: Int,
) {
    val hiddenCount: Int get() = (total - shown.size).coerceAtLeast(0)

    val isEmpty: Boolean get() = shown.isEmpty()

    companion object {
        val EMPTY = RelatedToolOutputs(shown = emptyList(), total = 0)
    }
}

/**
 * Which of the user's tool writing belongs to an evidence row.
 *
 * ## Why this is derived and never stored
 *
 * An evidence row is a durable, fingerprinted, user-editable database row; tool outputs are
 * deliberately *not* durable (they live in `SharedPreferences`, and the store's own KDoc says they
 * are reconstructible and must never be a planning input). Persisting a reference to them inside
 * the durable row would fail in three separate ways:
 *
 * 1. A restored backup or a cleared preference file leaves the row pointing at writing that no
 *    longer exists — the "traceable back to your own words" promise would break silently.
 * 2. `ProfileEvidenceGenerator`'s conclusions are deduplicated by a fingerprint that includes the
 *    source ids. Feeding tool outputs into that fingerprint would make an existing install mint a
 *    *second* row beside the one the user already edited, because the two fingerprints differ.
 * 3. It would need a schema migration whose only job is to hold a reference that is cheaper to
 *    recompute than to keep valid.
 *
 * So the link is computed on read, from data both sides already have, and nothing about the
 * database changes.
 *
 * ## Why tool outputs are not [ProfileEvidenceSource]s
 *
 * A source is a confirmed execution log, and the conclusion *counts* its sources ("已确认记录 N 条
 * 执行反馈"). A tool output is not counted by anything. Putting the two in one list would make the
 * page imply that the count includes writing it does not include — so they stay apart, and the UI
 * labels the tool writing as not counted.
 */
object ProfileEvidenceOutputs {

    /** How many related outputs one evidence card lists before collapsing the rest into a count. */
    const val MAX_SHOWN = 5

    /**
     * The tool writing that belongs to [sources]' tasks.
     *
     * The match is on task id and nothing else. That is deliberately loose in one direction: a
     * general evidence row aggregates every task with confirmed feedback, so on a long-running
     * install it will list the user's tool writing across all of them. That is the same shape the
     * log sources already have (the learning row's logs are a subset of the general row's), so the
     * card is honest about what it is showing rather than inventing a tighter rule the data cannot
     * support.
     *
     * Ordering is by kind, then task name, then excerpt, so the list is stable across reads and a
     * test can state exactly what it expects. Blank excerpts are dropped — a KWL row saved with an
     * empty "学到了" is not writing, and listing it would pad the count with nothing.
     */
    fun related(
        sources: List<ProfileEvidenceSource>,
        records: List<ToolOutputRecord>,
        limit: Int = MAX_SHOWN,
    ): RelatedToolOutputs {
        require(limit >= 1) { "a card shows at least one related output, got $limit" }
        if (sources.isEmpty()) return RelatedToolOutputs.EMPTY

        val nameByTask = sources.associate { it.taskId to it.taskDisplayName }
        val matching = records
            .asSequence()
            .filter { it.excerpt.isNotBlank() }
            .filter { it.taskId in nameByTask }
            .distinctBy { Triple(it.kind, it.taskId, it.excerpt.trim()) }
            .map { record ->
                ProfileEvidenceToolOutput(
                    kind = record.kind,
                    taskId = record.taskId,
                    taskDisplayName = nameByTask.getValue(record.taskId),
                    excerpt = record.excerpt.trim(),
                )
            }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.taskDisplayName }, { it.excerpt }))
            .toList()

        return RelatedToolOutputs(shown = matching.take(limit), total = matching.size)
    }

    /**
     * Resolves [records] against every row's sources.
     *
     * Only [ProfileEvidence.relatedToolOutputs] changes; the conclusion, its scope, its user-edited
     * flag and its log sources are passed through untouched, which is what keeps this a
     * presentation-time decoration rather than a second opinion about the user.
     */
    fun attach(
        evidence: List<ProfileEvidence>,
        records: List<ToolOutputRecord>,
        limit: Int = MAX_SHOWN,
    ): List<ProfileEvidence> = evidence.map { row ->
        row.copy(relatedToolOutputs = related(row.sources, records, limit))
    }
}
