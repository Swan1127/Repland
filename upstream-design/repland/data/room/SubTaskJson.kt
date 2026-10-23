package com.swan1127.repland.data.room

import com.swan1127.repland.data.json.StrictJson
import com.swan1127.repland.domain.model.SubTask

/**
 * Serialises a task's subtask list to and from the single `subtasks` TEXT column on the tasks table.
 *
 * Why a hand-rolled codec instead of a library: the project ships no JSON (de)serialiser, and adding
 * one risks an offline Gradle fetch. The schema is fixed and tiny, so the cost of writing the two
 * directions is lower than the cost of a new dependency — provided both directions are tested, which
 * `SubTaskTest` does (round-trips including quotes, backslashes, Chinese, and a corrupt string).
 *
 * Decode reuses [StrictJson] (the same parser the rest of the app trusts) and never throws: a blank
 * or corrupt stored string yields an empty list rather than crashing the task list on read.
 */
object SubTaskJson {
    fun encode(subtasks: List<SubTask>): String {
        if (subtasks.isEmpty()) return "[]"
        return buildString {
            append('[')
            subtasks.forEachIndexed { index, sub ->
                if (index > 0) append(',')
                append("{\"id\":").append(quote(sub.id))
                append(",\"title\":").append(quote(sub.title))
                append(",\"isDone\":").append(if (sub.isDone) "true" else "false")
                append(",\"createdAt\":").append(sub.createdAtEpochMillis)
                append('}')
            }
            append(']')
        }
    }

    fun decode(text: String?): List<SubTask> {
        if (text.isNullOrBlank()) return emptyList()
        val parsed = runCatching { StrictJson.parse(text) }.getOrNull() ?: return emptyList()
        if (parsed !is List<*>) return emptyList()
        return parsed.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            val id = item["id"] as? String ?: return@mapNotNull null
            val title = item["title"] as? String ?: return@mapNotNull null
            val isDone = when (val v = item["isDone"]) {
                is Boolean -> v
                is Double -> v != 0.0
                else -> false
            }
            val createdAt = when (val v = item["createdAt"]) {
                is Double -> v.toLong()
                is Long -> v
                else -> return@mapNotNull null
            }
            SubTask(id = id, title = title, isDone = isDone, createdAtEpochMillis = createdAt)
        }
    }

    /** JSON-string escape: backslash, quote, and every control character below 0x20. */
    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c.code < 0x20 -> append("\\u").append("%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}

class SubTaskConverters {
    @androidx.room.TypeConverter
    fun encodeSubTasks(subtasks: List<SubTask>): String = SubTaskJson.encode(subtasks)

    @androidx.room.TypeConverter
    fun decodeSubTasks(text: String?): List<SubTask> = SubTaskJson.decode(text)
}
