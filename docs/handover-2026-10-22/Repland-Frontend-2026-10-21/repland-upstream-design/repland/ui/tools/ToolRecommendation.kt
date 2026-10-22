package com.swan1127.repland.ui.tools

import androidx.annotation.StringRes
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskStatus

/**
 * The metacognition tools are the one thing no competitor ships, and until now the only way to
 * reach them was the command palette — which means a feature the app is *about* was hidden behind
 * a gesture nobody performs twice. This file decides which of them to put in front of the user on
 * the "今日" page, and nothing else: it is pure, it reads no clock and no storage, and the caller
 * supplies every fact.
 *
 * **Why the clock is a parameter.** "Recommend SQ3R when a reading block just ended" is only a
 * meaningful sentence if "just ended" has a value. Passing [ToolSituation.nowMinute] in means the
 * rule can be placed at 09:00 or at 22:30 in a test without a fake clock, and it means the
 * recommendation cannot disagree with the timeline the user is looking at, because both derive
 * from the same minute.
 *
 * **Why the priority is fixed and tested.** The strip shows at most [MAX_SUGGESTIONS] rows, so the
 * *order* is part of the specification, not an implementation detail: with a due review point and a
 * finished chapter both waiting, the review point wins, because a spaced-repetition interval that
 * slips a day cannot be recovered, while a reflection can be written tomorrow. A test pins that
 * order explicitly rather than trusting the reading order of this file.
 */

/**
 * Everything the "今日" page knows that could make one tool more useful than another right now.
 *
 * The fields are deliberately the ones the page already has in hand — no new query, no new
 * subscription. [todaySegments] must be the segments of one day only (the day being shown), which
 * is why there is no separate `date` field to disagree with them.
 */
data class ToolSituation(
    /** Minutes since midnight, 0..1439. */
    val nowMinute: Int,
    /** Every task the user owns, so the rule can join a segment to the work it was placed for. */
    val tasks: List<Task>,
    /** The planned segments of the day being shown. */
    val todaySegments: List<PlannedSegment>,
    /** Spaced-repetition points due today, already filtered by the caller. */
    val dueReviewPointCount: Int,
    /** Whether a reflection is already saved for the day being shown. */
    val hasReflection: Boolean,
    /** Tasks whose KWL "学到了" column already has text. */
    val kwlLearnedTaskIds: Set<String>,
    /** Tasks that already have a saved self-explanation. */
    val explainedTaskIds: Set<String>,
)

/**
 * The single value a reason string may refer to.
 *
 * A sealed type rather than two nullable fields, because "the reason names a task" and "the reason
 * counts things" need different printf conversions (`%1$s` and `%1$d`), and a shape that cannot be
 * expressed cannot be got wrong. The UI's `when` over this is exhaustive, so adding a case here
 * becomes a compile error there instead of a runtime `MissingFormatArgumentException`.
 */
sealed interface SuggestionReason {
    /** A task's own name, for reasons that point at one specific piece of work. */
    data class TaskName(val value: String) : SuggestionReason

    /** A count, for reasons that summarise the day. */
    data class Count(val value: Int) : SuggestionReason
}

/**
 * One row of the recommendation strip.
 *
 * [reasonRes] and [reason] travel together: the resource decides the wording and the sealed value
 * decides the substitution, and `ToolRecommendationResourceTest` reads both resource files to prove
 * the two agree. That check exists because a mismatch does not crash — `stringResource(id, arg)`
 * with a `%1$d` placeholder and a string argument renders the raw template, so the row would say
 * "今天有 %1$d 个复习点到期" to a user and nothing in the build would object.
 */
data class ToolSuggestion(
    val tool: ToolId,
    @param:StringRes val reasonRes: Int,
    val reason: SuggestionReason? = null,
)

/**
 * Two rows, not five.
 *
 * The strip sits above the timeline on the busiest screen in the app. A list that can grow to the
 * length of the tool set stops being a recommendation and becomes a second menu, which is the state
 * this feature exists to fix. Two is enough for "one thing that is time-critical, one thing that
 * closes the loop on today".
 */
const val MAX_SUGGESTIONS: Int = 2

/**
 * The hour after which a reflection becomes the situational action rather than an optional one.
 *
 * A named constant rather than a magic number, and a plain minute count rather than a `LocalTime`,
 * so the rule stays free of `java.time` clock types and the tests read as hours.
 */
const val REFLECTION_FROM_MINUTE: Int = 20 * 60

/**
 * The recommendation rules, in priority order. Each one is a sentence about the day, not about the
 * user: nothing here concludes anything about ability, and nothing here fires on a task the user
 * has not already confirmed something about.
 *
 * | # | Tool | Fires when |
 * |---|---|---|
 * | R1 | 复习点 | at least one spaced-repetition point is due today |
 * | R2 | SQ3R | a course task's block today has already ended and the task is **still open** |
 * | R3 | KWL | a course task's block ended, the task is **completed**, and "学到了" is empty |
 * | R4 | 反思 | it is past [REFLECTION_FROM_MINUTE], nothing is saved yet, and today finished ≥1 task |
 * | R5 | 自我解释 | a task finished today has no saved explanation |
 *
 * R2 and R3 are mutually exclusive by construction — they split on the task's status, which is the
 * user's own confirmation that the reading is over. That is deliberate: SQ3R is a scaffold for
 * *during* the reading, KWL is the step *after* it, and a strip that offered both at once would be
 * asking the user to choose between two tools instead of telling them what to do next.
 *
 * When no rule fires the result is empty and the caller renders nothing. An always-present strip
 * would be a permanent nag on a screen that already has a focal block, a timeline and a task rail.
 */
fun recommendTools(situation: ToolSituation): List<ToolSuggestion> {
    val tasksById = situation.tasks.associateBy(Task::id)

    // A block that is over, joined to the task it was placed for. A segment whose task is gone is
    // dropped rather than guessed at — `ScheduleTimeline.entries` drops it the same way, for the
    // same reason: a block on the timeline with no task behind it has nothing to say.
    val finishedBlocks: List<Pair<PlannedSegment, Task>> = situation.todaySegments
        .filter { it.endMinute <= situation.nowMinute }
        .mapNotNull { segment -> tasksById[segment.taskId]?.let { task -> segment to task } }

    // Both picks below break ties on the task id, so the suggestion does not depend on the order the
    // segments happen to arrive in. Two blocks ending in the same minute is normal (a parallel
    // track), and a strip that changed its mind between recompositions would look broken.
    val latestReading = finishedBlocks
        .filter { (_, task) -> task.category == TaskCategory.COURSE && task.status.isActive }
        .maxWithOrNull(compareBy({ it.first.endMinute }, { it.second.id }))

    val latestUnclosed = finishedBlocks
        .filter { (_, task) ->
            task.category == TaskCategory.COURSE &&
                task.status == TaskStatus.COMPLETED &&
                task.id !in situation.kwlLearnedTaskIds
        }
        .maxWithOrNull(compareBy({ it.first.endMinute }, { it.second.id }))

    val finishedTodayIds = finishedBlocks
        .filter { (_, task) -> task.status == TaskStatus.COMPLETED }
        .map { (_, task) -> task.id }
        .distinct()

    val latestUnexplained = finishedBlocks
        .filter { (_, task) -> task.status == TaskStatus.COMPLETED && task.id !in situation.explainedTaskIds }
        .maxWithOrNull(compareBy({ it.first.endMinute }, { it.second.id }))

    val suggestions = buildList {
        if (situation.dueReviewPointCount > 0) {
            add(
                ToolSuggestion(
                    tool = ToolId.REVIEW_POINTS,
                    reasonRes = R.string.tool_suggest_review_points,
                    reason = SuggestionReason.Count(situation.dueReviewPointCount),
                ),
            )
        }
        latestReading?.let { (_, task) ->
            add(
                ToolSuggestion(
                    tool = ToolId.SQ3R,
                    reasonRes = R.string.tool_suggest_sq3r,
                    reason = SuggestionReason.TaskName(task.displayName),
                ),
            )
        }
        latestUnclosed?.let { (_, task) ->
            add(
                ToolSuggestion(
                    tool = ToolId.KWL,
                    reasonRes = R.string.tool_suggest_kwl,
                    reason = SuggestionReason.TaskName(task.displayName),
                ),
            )
        }
        if (situation.nowMinute >= REFLECTION_FROM_MINUTE &&
            !situation.hasReflection &&
            finishedTodayIds.isNotEmpty()
        ) {
            add(
                ToolSuggestion(
                    tool = ToolId.REFLECTION,
                    reasonRes = R.string.tool_suggest_reflection,
                    reason = SuggestionReason.Count(finishedTodayIds.size),
                ),
            )
        }
        latestUnexplained?.let { (_, task) ->
            add(
                ToolSuggestion(
                    tool = ToolId.SELF_EXPLANATION,
                    reasonRes = R.string.tool_suggest_self_explanation,
                    reason = SuggestionReason.TaskName(task.displayName),
                ),
            )
        }
    }
    return suggestions.take(MAX_SUGGESTIONS)
}
