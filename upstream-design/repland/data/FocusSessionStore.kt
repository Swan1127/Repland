package com.swan1127.repland.data

import android.content.Context
import com.swan1127.repland.domain.model.FocusSessionState
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import java.time.LocalDate
import org.json.JSONObject

data class StoredFocusSession(val entry: TimelineEntry, val clock: FocusSessionState)

/** One resumable working session. A completed/discarded round is removed explicitly. */
class FocusSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("repland_focus_session", Context.MODE_PRIVATE)

    fun load(): StoredFocusSession? = runCatching {
        val raw = preferences.getString("session", null) ?: return null
        val json = JSONObject(raw)
        val entry = TimelineEntry(
            id = json.getString("id"), title = json.getString("title"),
            kind = TimelineKind.valueOf(json.getString("kind")), date = LocalDate.parse(json.getString("date")),
            startMinute = json.getInt("start"), endMinute = json.getInt("end"),
            taskId = json.optString("taskId").takeIf(String::isNotBlank), trackId = json.getString("track"),
        )
        StoredFocusSession(entry, FocusSessionState(
            targetSeconds = json.getInt("target"), accumulatedMillis = json.getLong("accumulated"),
            runningSinceEpochMillis = json.optLong("runningSince").takeIf { it > 0 },
            startRecorded = json.optBoolean("startRecorded"),
        ))
    }.getOrNull()

    fun save(session: StoredFocusSession) {
        val (entry, clock) = session
        val json = JSONObject().put("id", entry.id).put("title", entry.title).put("kind", entry.kind.name)
            .put("date", entry.date.toString()).put("start", entry.startMinute).put("end", entry.endMinute)
            .put("taskId", entry.taskId.orEmpty()).put("track", entry.trackId)
            .put("target", clock.targetSeconds).put("accumulated", clock.accumulatedMillis)
            .put("runningSince", clock.runningSinceEpochMillis ?: 0).put("startRecorded", clock.startRecorded)
        preferences.edit().putString("session", json.toString()).apply()
    }

    fun clear() { check(preferences.edit().clear().commit()) { "Could not clear focus session." } }
}
