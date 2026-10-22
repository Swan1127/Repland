package com.swan1127.repland.data

import android.content.Context
import com.swan1127.repland.domain.model.LearningMemoryPointSnapshot
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.model.MemoryEntry
import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.MemoryReference
import com.swan1127.repland.domain.model.MemoryState
import com.swan1127.repland.domain.model.MemoryStateCodec
import com.swan1127.repland.domain.model.RecallGrade
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskPriority
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ReviewPoint(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val content: String,
    val createdOn: LocalDate,
    val stage: Int,
    val memory: MemoryState? = null,
) {
    val dueOn: LocalDate get() = memory?.let { MemoryModel.dueOn(it) }
        ?: createdOn.plusDays(EBBINGHAUS_DAYS[stage.coerceIn(0, EBBINGHAUS_DAYS.lastIndex)].toLong())

    val isGraduated: Boolean get() = memory == null && stage >= EBBINGHAUS_DAYS.size

    fun memoryState(): MemoryState = memory
        ?: MemoryModel.fromLadderStage(createdOn, stage, importance = 3)

    fun graded(grade: RecallGrade, today: LocalDate, gain: Double = 1.0): ReviewPoint =
        if (isGraduated) this else copy(memory = MemoryModel.grade(memoryState(), grade, today, gain))

    fun dueWithin(days: Long, today: LocalDate): Boolean =
        !isGraduated && !dueOn.isAfter(today.plusDays(days))

    fun isDueOn(today: LocalDate): Boolean = !isGraduated && !dueOn.isAfter(today)

    companion object {
        val EBBINGHAUS_DAYS = listOf(1, 2, 4, 7, 15)

        fun candidatesFrom(task: Task, logs: List<TaskExecutionLog>): List<String> = buildList {
            task.completionResult?.takeIf(String::isNotBlank)?.trim()?.let(::add)
            logs.filter { it.taskId == task.id }.forEach { log ->
                log.feedback.completionResult?.takeIf(String::isNotBlank)?.trim()?.let(::add)
                log.feedback.completedContent?.takeIf(String::isNotBlank)?.trim()?.let(::add)
            }
        }.distinct()

        fun shouldSeed(existing: List<ReviewPoint>, taskId: String, content: String): Boolean {
            val trimmed = content.trim()
            return trimmed.isNotEmpty() && existing.none { it.taskId == taskId && it.content.trim() == trimmed }
        }
    }
}

class AuxiliaryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("repland-aux", Context.MODE_PRIVATE)
    private val memoryPrefs = context.applicationContext.getSharedPreferences("repland-memory-v1", Context.MODE_PRIVATE)

    var memoryReferenceEnabled: Boolean
        get() = synchronized(MEMORY_LOCK) {
            runCatching { memoryPrefs.getBoolean(KEY_REFERENCE_ENABLED, false) }.getOrDefault(false)
        }
        set(value) = synchronized(MEMORY_LOCK) {
            memoryPrefs.edit().putBoolean(KEY_REFERENCE_ENABLED, value).apply()
        }

    val memoryGain: Double
        get() = synchronized(MEMORY_LOCK) {
            MemoryStateCodec.gain(
                runCatching {
                    (prefs.getString(KEY_MEMORY_GAIN, null) ?: memoryPrefs.getString(KEY_GAIN, null))
                        ?.toDoubleOrNull()
                }.getOrNull() ?: 1.0,
            )
        }

    fun reviewPoints(): List<ReviewPoint> = synchronized(MEMORY_LOCK) {
        val raw = runCatching { prefs.getString(KEY_REVIEW_POINTS, null) }.getOrNull() ?: return@synchronized emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return@synchronized emptyList()
        (0 until array.length()).mapNotNull { index ->
            val stored = array.optJSONObject(index) ?: return@mapNotNull null
            val createdOn = runCatching { LocalDate.parse(stored.optString("createdOn")) }.getOrNull()
                ?: return@mapNotNull null
            val id = stored.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val taskId = stored.optString("taskId").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val content = stored.optString("content").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            ReviewPoint(
                id = id,
                taskId = taskId,
                taskTitle = stored.optString("taskTitle"),
                content = content,
                createdOn = createdOn,
                stage = stored.optInt("stage", 0),
                memory = stored.optJSONObject("memory")?.let { state ->
                    MemoryStateCodec.decode(
                        state.keys().asSequence().associateWith { key ->
                            if (state.isNull(key)) null else state.optString(key)
                        },
                    )
                },
            )
        }.distinctBy(ReviewPoint::id)
    }

    fun addReviewPointsFor(
        task: Task,
        logs: List<TaskExecutionLog> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): Int = synchronized(MEMORY_LOCK) {
        val existing = reviewPoints()
        val fresh = ReviewPoint.candidatesFrom(task, logs)
            .filter { ReviewPoint.shouldSeed(existing, task.id, it) }
            .map { content -> newPoint(task.id, task.displayName, content, today, task.userPriority) }
        if (fresh.isNotEmpty()) writeReviewPoints(existing + fresh)
        fresh.size
    }

    fun addReviewPointFromText(
        taskId: String,
        taskTitle: String,
        content: String,
        today: LocalDate = LocalDate.now(),
        priority: TaskPriority = TaskPriority.UNSPECIFIED,
    ): Boolean = synchronized(MEMORY_LOCK) {
        val existing = reviewPoints()
        if (taskId.isBlank() || !ReviewPoint.shouldSeed(existing, taskId, content)) return@synchronized false
        writeReviewPoints(existing + newPoint(taskId, taskTitle, content.trim(), today, priority))
        true
    }

    fun gradeReviewPoint(id: String, remembered: Boolean, today: LocalDate = LocalDate.now()): Boolean =
        gradeReviewPoint(id, if (remembered) RecallGrade.RECALLED else RecallGrade.FORGOT, today)

    /** Call on an IO dispatcher: an acknowledged answer has reached persistent storage. */
    fun gradeReviewPoint(
        id: String,
        grade: RecallGrade,
        today: LocalDate = LocalDate.now(),
    ): Boolean = synchronized(MEMORY_LOCK) {
        val points = reviewPoints()
        val point = points.firstOrNull { it.id == id && !it.isGraduated } ?: return@synchronized false
        val state = point.memoryState()
        val gain = memoryGain
        val updated = point.graded(grade, today, gain)
        val nextGain = MemoryModel.observeGain(MemoryModel.retrievabilityAt(state, today), grade, gain)
        val previousPoints = prefs.getString(KEY_REVIEW_POINTS, null)
        val previousGain = prefs.getString(KEY_MEMORY_GAIN, null)
        // Review state and its derived gain must never get separated by a process restart.
        val saved = runCatching {
            prefs.edit()
                .putString(KEY_REVIEW_POINTS, reviewPointsJson(points.map { if (it.id == id) updated else it }))
                .putString(KEY_MEMORY_GAIN, nextGain.toString())
                .commit()
        }.getOrDefault(false)
        if (!saved) {
            // SharedPreferences changes its in-memory map even when commit fails. Restore that map
            // before allowing a retry so one answer cannot be applied twice after a disk failure.
            prefs.edit()
                .putString(KEY_REVIEW_POINTS, previousPoints)
                .putString(KEY_MEMORY_GAIN, previousGain)
                .apply()
        }
        saved
    }

    fun dueReviewPoints(today: LocalDate = LocalDate.now()): List<ReviewPoint> =
        reviewPoints().filter { it.isDueOn(today) }
            .sortedByDescending { MemoryModel.reviewPressure(it.memoryState(), today) }

    fun reviewStreakSummary(today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val all = reviewPoints()
        val reviewed = all.count { it.memory?.reviews?.let { reviews -> reviews > 0 } ?: (it.stage > 0) }
        return reviewed to all.count { it.dueWithin(7, today) }
    }

    fun memoryReference(today: LocalDate = LocalDate.now()): MemoryReference? = synchronized(MEMORY_LOCK) {
        if (!memoryReferenceEnabled) null else MemoryReference.of(
            reviewPoints().filterNot(ReviewPoint::isGraduated).map { point ->
                MemoryEntry(point.taskId, point.content, point.memoryState())
            },
            today,
        )
    }

    fun learningMemorySnapshot(): LearningMemorySnapshot = synchronized(MEMORY_LOCK) {
        LearningMemorySnapshot(
            referenceEnabled = memoryReferenceEnabled,
            gain = memoryGain,
            points = reviewPoints().map { point ->
                LearningMemoryPointSnapshot(
                    point.id, point.taskId, point.taskTitle, point.content, point.createdOn, point.stage, point.memory,
                )
            },
        )
    }

    /** Shares the grading lock across UI/container store instances. Call on an IO dispatcher. */
    fun clearLearningMemory(): Unit = synchronized(MEMORY_LOCK) {
        check(prefs.edit().remove(KEY_REVIEW_POINTS).remove(KEY_MEMORY_GAIN).commit()) {
            "Learning memory could not be cleared."
        }
        check(memoryPrefs.edit().clear().commit()) { "Learning memory settings could not be cleared." }
    }

    private fun newPoint(
        taskId: String,
        taskTitle: String,
        content: String,
        today: LocalDate,
        priority: TaskPriority,
    ): ReviewPoint = ReviewPoint(
        id = UUID.randomUUID().toString(),
        taskId = taskId,
        taskTitle = taskTitle,
        content = content,
        createdOn = today,
        stage = 0,
        memory = MemoryModel.seed(MemoryModel.importanceOf(priority), today),
    )

    private fun writeReviewPoints(points: List<ReviewPoint>) {
        prefs.edit().putString(KEY_REVIEW_POINTS, reviewPointsJson(points)).apply()
    }

    private fun reviewPointsJson(points: List<ReviewPoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            val memory = point.memory?.let { state ->
                JSONObject().apply {
                    MemoryStateCodec.encode(state).forEach { (key, value) -> put(key, value) }
                }
            }
            array.put(
                JSONObject()
                    .put("id", point.id)
                    .put("taskId", point.taskId)
                    .put("taskTitle", point.taskTitle)
                    .put("content", point.content)
                    .put("createdOn", point.createdOn.toString())
                    .put("stage", point.stage)
                    .put("memory", memory),
            )
        }
        return array.toString()
    }

    private companion object {
        const val KEY_REVIEW_POINTS = "review-points"
        const val KEY_REFERENCE_ENABLED = "reference-enabled"
        const val KEY_GAIN = "gain"
        const val KEY_MEMORY_GAIN = "memory-gain-v1"
        val MEMORY_LOCK = Any()
    }
}
