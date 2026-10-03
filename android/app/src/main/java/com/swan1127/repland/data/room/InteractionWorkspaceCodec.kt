package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object InteractionWorkspaceCodec {
    fun encodeTracks(tracks: List<RhythmTrack>): String = JSONArray(tracks.map {
        JSONObject().put("id", it.id).put("name", it.name)
    }).toString()

    fun decodeTracks(payload: String): List<RhythmTrack> = JSONArray(payload).let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { row -> RhythmTrack(row.getString("id"), row.getString("name")) } }
    }

    fun encodeAssistant(value: AssistantWorkspace): String = JSONObject().apply {
        put("version", 1); put("date", value.date.toString()); put("prompt", value.prompt)
        put("intent", value.intent?.name); put("selectedIntent", value.selectedIntent?.name)
        put("revision", value.sourceRevision)
        put("proposals", JSONArray(value.proposals.map { p -> JSONObject().apply {
            put("id", p.id); put("text", p.text); put("category", p.category.name); put("duration", p.durationMinutes)
            put("start", p.timeHint.explicitStartMinute); put("window", p.timeHint.windowLabel)
            put("needs", JSONArray(p.needsClarification.map { it.name }))
            put("source", p.placementSource.name); put("track", p.trackId)
            put("existingTaskId", p.existingTaskId)
        } }))
    }.toString()

    fun decodeAssistant(payload: String): AssistantWorkspace = JSONObject(payload).let { j ->
        require(j.getInt("version") == 1)
        fun intent(key: String) = if (j.isNull(key)) null else ArrangementIntent.valueOf(j.getString(key))
        AssistantWorkspace(LocalDate.parse(j.getString("date")), j.getString("prompt"),
            j.getJSONArray("proposals").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { p ->
                AssistantTaskProposal(p.getString("id"), p.getString("text"), TaskCategory.valueOf(p.getString("category")),
                    if (p.isNull("duration")) null else p.getInt("duration"),
                    ArrangementTimeHint(if (p.isNull("start")) null else p.getInt("start"), if (p.isNull("window")) null else p.getString("window")),
                    p.getJSONArray("needs").let { needs -> (0 until needs.length()).map { ArrangementClarification.valueOf(needs.getString(it)) }.toSet() },
                    ArrangementPlacementSource.valueOf(p.getString("source")), p.getString("track"), if (p.isNull("existingTaskId")) null else p.getString("existingTaskId"))
            } } }, intent("intent"), intent("selectedIntent"), if (j.isNull("revision")) null else j.getString("revision"))
    }
}
