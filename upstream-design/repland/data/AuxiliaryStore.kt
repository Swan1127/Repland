package com.swan1127.repland.data

import android.content.Context
import android.content.SharedPreferences
import com.swan1127.repland.domain.model.FocusSettings
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.ThemeAccent
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.ToolOutputKind
import com.swan1127.repland.domain.model.ToolOutputRecord
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/** How the app chooses between the light and dark colour schemes. */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStorage(raw: String?): AppearanceMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * A thing the user wrote down that they got stuck on, resurfaced on the classic
 * Ebbinghaus ladder. Deliberately *derived*: the text always comes from data the user
 * already produced (obstaclePlan when planning, completedContent when finishing), so
 * building a review queue costs the user zero extra input.
 */
data class ReviewPoint(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val content: String,
    val createdOn: LocalDate,
    val stage: Int,
) {
    /** 1 / 2 / 4 / 7 / 15 days after the point was captured. */
    val dueOn: LocalDate get() = createdOn.plusDays(EBBINGHAUS_DAYS[stage.coerceIn(0, EBBINGHAUS_DAYS.lastIndex)].toLong())

    val isGraduated: Boolean get() = stage >= EBBINGHAUS_DAYS.size

    fun dueWithin(days: Long, today: LocalDate): Boolean =
        !isGraduated && !dueOn.isAfter(today.plusDays(days))

    fun isDueOn(today: LocalDate): Boolean = !isGraduated && !dueOn.isAfter(today)

    companion object {
        val EBBINGHAUS_DAYS = listOf(1, 2, 4, 7, 15)

        /**
         * Wording the user already wrote, promoted to a review point.
         *
         * This is the whole trick behind the feature: nothing new is asked of the user.
         * `completionResult` is what they typed when they finished the task ("泰勒展开的
         * 余项搞不清"), which is precisely the thing worth resurfacing. The execution-log
         * feedback is scanned too, because a partial completion carries the same signal.
         */
        fun candidatesFrom(task: Task, logs: List<TaskExecutionLog>): List<String> = buildList {
            task.completionResult?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            logs.forEach { log ->
                log.feedback.completionResult?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            }
        }

        /**
         * Whether [content] should become a new point for [taskId], given what is already queued.
         *
         * Extracted from the store rather than left inline, because the store's own entry points
         * cannot be reached from a JVM test — `SharedPreferences` is an Android stub — and "the same
         * stuck-point is never queued twice" is a claim about this comparison, not about JSON.
         * Without the extraction that rule would have no test at all.
         *
         * The comparison is on trimmed text, so a sentence saved twice with different surrounding
         * whitespace is recognised as the same sentence.
         */
        fun shouldSeed(existing: List<ReviewPoint>, taskId: String, content: String): Boolean {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return false
            return existing.none { it.taskId == taskId && it.content == trimmed }
        }
    }
}

/** One day's reflection. The draft is machine-written; the stored text is user-approved. */
data class ReflectionEntry(
    val date: LocalDate,
    val text: String,
)

data class KwlEntry(
    val taskId: String,
    val known: String,
    val want: String,
    val learned: String,
)

/**
 * Storage for the derived, per-user auxiliary data introduced by the unbound features.
 *
 * These are intentionally *not* Room entities. None of them is a planning input: losing
 * them cannot corrupt a schedule, and they are all reconstructible or re-derivable. Keeping
 * them here avoids a schema migration whose only purpose would be to hold scratch notes,
 * and keeps the domain database free of presentation-level state.
 */
class AuxiliaryStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("repland-aux", Context.MODE_PRIVATE),
    )

    /** Removes notes, review points, timetable sets, focus settings and appearance together. */
    fun clearAllLocalData() {
        check(prefs.edit().clear().commit()) { "辅助数据未能全部清除。" }
    }

    // ---------------------------------------------------------------- appearance (D6)

    var appearanceMode: AppearanceMode
        get() = AppearanceMode.fromStorage(prefs.getString(KEY_APPEARANCE, null))
        set(value) = prefs.edit().putString(KEY_APPEARANCE, value.name).apply()

    /**
     * Which brand identity the app is painted in.
     *
     * Stored next to the mode rather than inside it, because the two are independent: "dark" and
     * "indigo" are answers to different questions. Read through [ThemeAccent.fromStorage] so a name
     * written by another build cannot make the app fail to open.
     */
    var themeAccent: ThemeAccent
        get() = ThemeAccent.fromStorage(prefs.getString(KEY_THEME_ACCENT, null))
        set(value) = prefs.edit().putString(KEY_THEME_ACCENT, value.name).apply()

    // ---------------------------------------------------------------- focus rounds (M12)

    /**
     * The length of one focus round. Read through [FocusSettings.fromStorage] so a value
     * written by another build cannot make the focus screen fail to open.
     */
    var focusSettings: FocusSettings
        get() = FocusSettings.fromStorage(prefs.getInt(KEY_FOCUS_MINUTES, FocusSettings.DEFAULT_FOCUS_MINUTES))
        set(value) = prefs.edit().putInt(KEY_FOCUS_MINUTES, value.focusMinutes).apply()

    // ------------------------------------------------------------ reflections (E4)

    fun reflection(date: LocalDate): String? = reflections()[date.toString()]

    fun saveReflection(date: LocalDate, text: String) {
        val all = reflections().toMutableMap()
        if (text.isBlank()) all.remove(date.toString()) else all[date.toString()] = text
        writeReflections(all)
    }

    private fun reflections(): Map<String, String> {
        val raw = prefs.getString(KEY_REFLECTIONS, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it) }
    }

    private fun writeReflections(all: Map<String, String>) {
        val obj = JSONObject()
        all.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_REFLECTIONS, obj.toString()).apply()
    }

    // -------------------------------------------------------- self-explanation (E3)

    fun selfExplanation(logKey: String): String? =
        explanations()[logKey]

    /**
     * Which keys already carry a non-blank explanation, in one parse.
     *
     * [saveSelfExplanation] drops a key when the text is blank, so this is normally just the key
     * set — but the filter is stated rather than assumed, because the store is plain JSON in
     * SharedPreferences and a restored backup can contain an empty string.
     */
    fun explainedLogKeys(): Set<String> =
        explanations().filterValues { it.isNotBlank() }.keys

    fun saveSelfExplanation(logKey: String, text: String) {
        val all = explanations().toMutableMap()
        if (text.isBlank()) all.remove(logKey) else all[logKey] = text
        val obj = JSONObject()
        all.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_EXPLANATIONS, obj.toString()).apply()
    }

    private fun explanations(): Map<String, String> {
        val raw = prefs.getString(KEY_EXPLANATIONS, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it) }
    }

    // ------------------------------------------------------------------- KWL (E1)

    fun kwl(taskId: String): KwlEntry? = kwlEntries()[taskId]

    /**
     * Every saved KWL entry, in one parse.
     *
     * The single-task lookup above re-reads and re-parses the whole JSON on every call, which is
     * harmless for one tap but quadratic on the "今日" page, where the recommendation rule asks
     * about every completed task on every recomposition. [reviewPoints] returns its whole list for
     * the same reason.
     */
    fun kwlEntries(): Map<String, KwlEntry> {
        val obj = runCatching { JSONObject(prefs.getString(KEY_KWL, "{}") ?: "{}") }.getOrNull()
            ?: return emptyMap()
        return obj.keys().asSequence().mapNotNull { key ->
            val entry = obj.optJSONObject(key) ?: return@mapNotNull null
            key to KwlEntry(
                taskId = key,
                known = entry.optString("known"),
                want = entry.optString("want"),
                learned = entry.optString("learned"),
            )
        }.toMap()
    }

    fun saveKwl(entry: KwlEntry) {
        val obj = runCatching { JSONObject(prefs.getString(KEY_KWL, "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        obj.put(
            entry.taskId,
            JSONObject()
                .put("known", entry.known)
                .put("want", entry.want)
                .put("learned", entry.learned),
        )
        prefs.edit().putString(KEY_KWL, obj.toString()).apply()
    }

    // ---------------------------------------------------------- review points (D8)

    fun reviewPoints(): List<ReviewPoint> {
        val raw = prefs.getString(KEY_REVIEW_POINTS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val created = runCatching { LocalDate.parse(o.optString("createdOn")) }.getOrNull()
                ?: return@mapNotNull null
            ReviewPoint(
                id = o.optString("id"),
                taskId = o.optString("taskId"),
                taskTitle = o.optString("taskTitle"),
                content = o.optString("content"),
                createdOn = created,
                stage = o.optInt("stage", 0),
            )
        }
    }

    /**
     * Adds points for [task] that are not already tracked, so the same stuck-point is
     * never queued twice. Returns how many were added.
     */
    fun addReviewPointsFor(
        task: Task,
        logs: List<TaskExecutionLog> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): Int {
        val existing = reviewPoints()
        val fresh = ReviewPoint.candidatesFrom(task, logs)
            .filter { candidate -> existing.none { it.taskId == task.id && it.content == candidate } }
            .map { candidate ->
                ReviewPoint(
                    id = "${task.id}-${candidate.hashCode()}",
                    taskId = task.id,
                    taskTitle = task.displayName,
                    content = candidate,
                    createdOn = today,
                    stage = 0,
                )
            }
        if (fresh.isEmpty()) return 0
        writeReviewPoints(existing + fresh)
        return fresh.size
    }

    /**
     * Seeds a review point from a sentence the user just wrote about a finished task.
     *
     * [addReviewPointsFor] can only mine text that already sits in `completionResult`, but the
     * one-tap completion path never collects that field — so on its own it would never fire.
     * This entry point closes that loop: the sentence typed into the self-explanation field
     * becomes the thing that comes back on day 1/2/4/7/15.
     */
    fun addReviewPointFromText(
        taskId: String,
        taskTitle: String,
        content: String,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val trimmed = content.trim()
        val existing = reviewPoints()
        if (!ReviewPoint.shouldSeed(existing, taskId, content)) return false
        val point = ReviewPoint(
            id = "$taskId-${trimmed.hashCode()}",
            taskId = taskId,
            taskTitle = taskTitle,
            content = trimmed,
            createdOn = today,
            stage = 0,
        )
        writeReviewPoints(existing + point)
        return true
    }

    /** "想起来了" advances the ladder; "忘了" restarts it from today. */
    fun gradeReviewPoint(id: String, remembered: Boolean, today: LocalDate = LocalDate.now()) {
        writeReviewPoints(
            reviewPoints().map { point ->
                when {
                    point.id != id -> point
                    remembered -> point.copy(stage = point.stage + 1)
                    else -> point.copy(stage = 0, createdOn = today)
                }
            },
        )
    }

    fun dueReviewPoints(today: LocalDate = LocalDate.now()): List<ReviewPoint> =
        reviewPoints().filter { it.isDueOn(today) }

    /** Retention by stage, for the summary line on the review screen. */
    fun reviewStreakSummary(today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val all = reviewPoints()
        val tracked = all.count { it.stage > 0 }
        val upcoming = all.count { it.dueWithin(7, today) }
        return tracked to upcoming
    }

    private fun writeReviewPoints(points: List<ReviewPoint>) {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("taskId", p.taskId)
                    .put("taskTitle", p.taskTitle)
                    .put("content", p.content)
                    .put("createdOn", p.createdOn.toString())
                    .put("stage", p.stage),
            )
        }
        prefs.edit().putString(KEY_REVIEW_POINTS, arr.toString()).apply()
    }

    // --------------------------------------------------------- timetable sets (D2)

    /**
     * A named snapshot of the weekly-block list.
     *
     * The design point that matters: sets are *switchable*, never concurrent. Exactly one
     * set is active at a time, so the scheduler still sees a single set of hard constraints
     * and two timetables can never silently stack up and eat each other's free time.
     */
    data class TimetableSet(val name: String, val blocksJson: String)

    fun timetableSets(): List<TimetableSet> {
        val raw = prefs.getString(KEY_TIMETABLE_SETS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TimetableSet(o.optString("name"), o.optString("blocks"))
        }
    }

    fun saveTimetableSet(name: String, blocksJson: String) {
        val sets = timetableSets().filterNot { it.name == name } + TimetableSet(name, blocksJson)
        val arr = JSONArray()
        sets.forEach { s -> arr.put(JSONObject().put("name", s.name).put("blocks", s.blocksJson)) }
        prefs.edit().putString(KEY_TIMETABLE_SETS, arr.toString()).apply()
    }

    fun deleteTimetableSet(name: String) {
        val sets = timetableSets().filterNot { it.name == name }
        val arr = JSONArray()
        sets.forEach { s -> arr.put(JSONObject().put("name", s.name).put("blocks", s.blocksJson)) }
        prefs.edit().putString(KEY_TIMETABLE_SETS, arr.toString()).apply()
    }

    /**
     * Serialises the weekly blocks so the set can be restored later.
     *
     * Only the fields the user actually authored are stored — row ids and timestamps are
     * dropped on purpose. A restored set is a *new* set of rows, not the old ones resurrected,
     * and keeping the format this narrow means a stale snapshot cannot smuggle a dead id back
     * into the database and collide with a live row.
     */
    fun encodeTimetable(blocks: List<WeeklyTimeBlock>): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(
                JSONObject()
                    .put("title", block.title)
                    .put("kind", block.kind.name)
                    .put("day", block.dayOfWeek.value)
                    .put("start", block.startMinute)
                    .put("end", block.endMinute)
                    .put("pattern", block.weekPattern)
                    .put("track", block.trackId),
            )
        }
        return array.toString()
    }

    /** Reads a snapshot written by [encodeTimetable]. Unreadable entries are skipped, not thrown. */
    fun decodeTimetable(json: String): List<WeeklyTimeBlockDraft> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val start = entry.optInt("start", -1)
            val end = entry.optInt("end", -1)
            if (start < 0 || end <= start) return@mapNotNull null
            WeeklyTimeBlockDraft(
                title = title,
                kind = runCatching { TimeBlockKind.valueOf(entry.optString("kind")) }
                    .getOrDefault(TimeBlockKind.COURSE),
                dayOfWeek = DayOfWeek.of(entry.optInt("day", 1).coerceIn(1, 7)),
                startMinute = start,
                endMinute = end,
                weekPattern = entry.optString("pattern").takeIf(String::isNotBlank),
                trackId = entry.optString("track").takeIf(String::isNotBlank) ?: "course",
            )
        }
    }

    /** Block count of a snapshot, so the list can show a size without decoding every field. */
    fun timetableBlockCount(json: String): Int =
        runCatching { JSONArray(json).length() }.getOrDefault(0)

    /**
     * True when the live timetable is, entry for entry, the same timetable as the snapshot [json].
     *
     * The active marker used to be a sticky "name of the set you last saved or switched to"
     * pointer. That kept claiming a set was in force after the user hand-edited a block or
     * imported one extra course — a claim about the present has to be checked against the
     * present, so compare content instead of remembering a name.
     *
     * Two deliberate details:
     * - **Order is ignored.** Restoring a snapshot re-inserts rows, so the live list and the
     *   stored list can legitimately differ in order while describing the same timetable.
     * - **`note` is excluded**, because [encodeTimetable] does not store it. Including it would
     *   make the marker vanish the moment a user annotates a block, for a change that is not
     *   part of the timetable's identity.
     */
    fun timetableMatches(json: String, blocks: List<WeeklyTimeBlock>): Boolean {
        val snapshot = decodeTimetable(json).map(::timetableKey).sorted()
        val live = blocks.map { block ->
            timetableKey(
                WeeklyTimeBlockDraft(
                    title = block.title,
                    kind = block.kind,
                    dayOfWeek = block.dayOfWeek,
                    startMinute = block.startMinute,
                    endMinute = block.endMinute,
                    weekPattern = block.weekPattern,
                    trackId = block.trackId,
                ),
            )
        }.sorted()
        return snapshot == live
    }

    /** Canonical form of the fields a snapshot actually round-trips, so field order cannot matter. */
    private fun timetableKey(draft: WeeklyTimeBlockDraft): String = listOf(
        draft.title.trim(),
        draft.kind.name,
        draft.dayOfWeek.value.toString(),
        draft.startMinute.toString(),
        draft.endMinute.toString(),
        draft.weekPattern?.trim()?.takeIf(String::isNotBlank).orEmpty(),
        draft.trackId.ifBlank { "course" },
    ).joinToString("\u0000")

    /** Days between two dates, used by the reflection draft to describe "today". */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    // ------------------------------------------ tool writing, for the profile page (P2 #17)

    /**
     * Every piece of tool writing that names a task, in the shape the profile evidence page joins on.
     *
     * A straight-line read: it collects and returns, and every decision about *which* of these
     * belongs to a given evidence row lives in `ProfileEvidenceOutputs`. The split is deliberate —
     * the rule is a pure function with tests, whereas this method needs `SharedPreferences` and so
     * cannot be reached from a JVM test at all.
     *
     * Reflections are absent, and that is not an oversight: a reflection is keyed by date, so it has
     * no task to attach to. Self-explanations *are* keyed by task id — the field is called `logKey`,
     * but the tool stores a task id (see the note where the explanation slots are built).
     *
     * Blank excerpts are passed through rather than dropped here: the rule drops them, and keeping
     * that in one place means "a blank note is not writing" has exactly one test.
     */
    fun toolOutputRecords(): List<ToolOutputRecord> = buildList {
        kwlEntries().values.forEach { entry ->
            add(ToolOutputRecord(ToolOutputKind.KWL, entry.taskId, entry.learned))
        }
        reviewPoints().forEach { point ->
            add(ToolOutputRecord(ToolOutputKind.REVIEW_POINT, point.taskId, point.content))
        }
        explanations().forEach { (taskId, text) ->
            add(ToolOutputRecord(ToolOutputKind.SELF_EXPLANATION, taskId, text))
        }
    }

    private companion object {
        const val KEY_APPEARANCE = "appearance-mode"
        const val KEY_THEME_ACCENT = "theme-accent"
        const val KEY_FOCUS_MINUTES = "focus-minutes"
        const val KEY_REFLECTIONS = "reflections"
        const val KEY_EXPLANATIONS = "self-explanations"
        const val KEY_KWL = "kwl"
        const val KEY_REVIEW_POINTS = "review-points"
        const val KEY_TIMETABLE_SETS = "timetable-sets"
    }
}
