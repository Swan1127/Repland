package com.swan1127.repland.data

import android.content.Context
import com.swan1127.repland.data.json.StrictJson
import com.swan1127.repland.domain.model.*
import java.time.LocalDate

/** Local conversation recovery. Date checking prevents yesterday's draft being applied today. */
class GroupChatHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(date: LocalDate): StoredGroupChatSession? =
        GroupChatHistoryCodec.decode(preferences.getString("session", null))?.takeIf { it.date == date }

    fun save(session: StoredGroupChatSession) {
        preferences.edit().putString("session", GroupChatHistoryCodec.encode(session)).apply()
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear conversation history." }
    }

    companion object {
        const val PREFERENCES = "repland_group_chat_history"
    }
}

/** Pure codec so corrupt records, escaping and storage limits run through JVM tests. */
object GroupChatHistoryCodec {
    const val MAX_MESSAGES = 60
    const val MAX_CANDIDATES = 20
    private const val MAX_TEXT = 2_000
    private const val MAX_RECORD = 300_000

    fun encode(session: StoredGroupChatSession): String = buildString {
        append("{\"date\":").append(quote(session.date.toString()))
        append(",\"messages\":[")
        session.messages.takeLast(MAX_MESSAGES).forEachIndexed { index, message ->
            if (index > 0) append(',')
            append("{\"role\":").append(quote(message.role.name))
            append(",\"text\":").append(quote(message.text.take(MAX_TEXT)))
            append(",\"speakerId\":").append(message.speakerId?.take(100)?.let(::quote) ?: "null")
            append('}')
        }
        append("],\"candidates\":[")
        session.pendingCandidates.take(MAX_CANDIDATES).forEachIndexed { index, candidate ->
            if (index > 0) append(',')
            append("{\"title\":").append(quote(candidate.title.take(MAX_TEXT)))
            append(",\"category\":").append(quote(candidate.category.name))
            append(",\"duration\":").append(candidate.durationMinutes ?: "null")
            append(",\"start\":").append(candidate.timeHint.explicitStartMinute ?: "null")
            append(",\"window\":").append(candidate.timeHint.windowLabel?.take(100)?.let(::quote) ?: "null")
            append(",\"source\":").append(quote(candidate.placementSource.name))
            append(",\"track\":").append(candidate.preferredTrackId?.take(100)?.let(::quote) ?: "null")
            append(",\"priority\":").append(candidate.priority?.name?.let(::quote) ?: "null")
            append(",\"estimated\":").append(candidate.durationEstimated)
            append(",\"missing\":[")
            append(candidate.needsClarification.joinToString(",") { quote(it.name) })
            append("]}")
        }
        append("]}")
    }

    fun decode(raw: String?): StoredGroupChatSession? {
        if (raw.isNullOrBlank() || raw.length > MAX_RECORD) return null
        return runCatching {
            val root = StrictJson.parseObject(raw)
            val date = LocalDate.parse(root["date"] as String)
            val messages = (root["messages"] as List<*>).takeLast(MAX_MESSAGES).map { value ->
                val item = value as Map<*, *>
                GroupChatMessage(
                    role = GroupChatMessageRole.valueOf(item["role"] as String),
                    text = (item["text"] as String).take(MAX_TEXT),
                    speakerId = (item["speakerId"] as? String)?.take(100),
                )
            }
            val candidates = (root["candidates"] as? List<*>).orEmpty().take(MAX_CANDIDATES).map { value ->
                val item = value as Map<*, *>
                val duration = (item["duration"] as? Number)?.toInt()?.also { require(it in 1..1440) }
                val start = (item["start"] as? Number)?.toInt()?.also { require(it in 0 until 1440) }
                if (duration != null && start != null) require(start + duration <= 1440)
                ArrangementCandidate(
                    title = (item["title"] as String).take(MAX_TEXT).also { require(it.isNotBlank()) },
                    category = TaskCategory.valueOf(item["category"] as String),
                    durationMinutes = duration,
                    timeHint = ArrangementTimeHint(start, item["window"] as? String),
                    needsClarification = (item["missing"] as List<*>).map { ArrangementClarification.valueOf(it as String) }.toSet(),
                    placementSource = ArrangementPlacementSource.valueOf(item["source"] as String),
                    preferredTrackId = item["track"] as? String,
                    priority = (item["priority"] as? String)?.let(TaskPriority::valueOf),
                    durationEstimated = item["estimated"] as? Boolean ?: false,
                )
            }
            StoredGroupChatSession(date, messages, candidates)
        }.getOrNull()
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.code < 0x20 -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }
}
