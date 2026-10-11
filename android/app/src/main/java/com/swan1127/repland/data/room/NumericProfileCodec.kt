package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned workspace payloads, independent from provider configuration and secrets. */
internal object NumericProfileCodec {
    private fun JSONArray.objects()=(0 until length()).map(::getJSONObject)
    private fun JSONObject.number(key: String)=if(isNull(key)) null else getDouble(key).also { require(it.isFinite()) }
    fun encode(snapshot: NumericProfileSnapshot): String = numericRow().apply {
        put("revision",snapshot.version); put("rule",snapshot.rule); put("start",snapshot.windowStart); put("end",snapshot.windowEnd)
        put("updated",snapshot.updatedAt); put("enabled",snapshot.enabled); put("consent",snapshot.aiConsent)
        put("parameters",JSONArray(snapshot.parameters.map { p -> JSONObject().apply {
            put("id",p.id); put("name",p.name); put("unit",p.unit); put("value",p.value ?: JSONObject.NULL)
            put("low",p.low ?: JSONObject.NULL); put("high",p.high ?: JSONObject.NULL); put("count",p.count)
            put("sources",JSONArray(p.sources)); put("status",p.availability.name)
        } }))
        put("sources",JSONArray(snapshot.sources.map { s -> JSONObject().put("id",s.id).put("task",s.taskId).put("title",s.title)
            .put("detail",s.detail).put("included",s.included).put("excluded",s.excluded) }))
        put("weights",JSONObject(snapshot.categoryWeights.mapKeys { it.key.name }))
        put("explicitCategories",JSONArray(snapshot.explicitCategories.map { it.name }.sorted()))
    }.toString()
    fun snapshot(payload: String): NumericProfileSnapshot = numericJson(payload).let { j ->
        require(j.getString("rule")==NumericProfileCalculator.RULE)
        NumericProfileSnapshot(j.getString("revision"),j.getString("start"),j.getString("end"),j.getLong("updated"),
            j.getJSONArray("parameters").objects().map { NumericParameter(it.getString("id"),it.getString("name"),it.getString("unit"),
                it.number("value"),it.number("low"),it.number("high"),it.getInt("count"),it.getJSONArray("sources").strings(),NumericAvailability.valueOf(it.getString("status"))) },
            j.getJSONArray("sources").objects().map { NumericSource(it.getString("id"),it.getString("task"),it.getString("title"),it.getString("detail"),it.getBoolean("included"),it.getBoolean("excluded")) },
            j.getJSONObject("weights").let { w -> TaskCategory.knownEntries.associateWith { w.getInt(it.name) } },
            j.getJSONArray("explicitCategories").strings().map(TaskCategory::valueOf).toSet(),j.getBoolean("enabled"),j.getBoolean("consent"),j.getString("rule"))
    }
    fun encode(d: NumericDescription): String = numericRow().put("revision",d.profileVersion).put("at",d.generatedAt).put("origin",d.origin)
        .put("configuration",d.configurationRevision).put("access",d.accessRevision)
        .put("next",d.nextStep.name).put("claims",JSONArray(d.claims.map { JSONObject().put("id",it.parameterId).put("value",it.value) })).toString()
    fun description(payload: String): NumericDescription = numericJson(payload).let { NumericDescription(it.getString("revision"),it.getLong("at"),it.getString("origin"),
        it.getJSONArray("claims").objects().map { j -> NumericClaim(j.getString("id"),j.getDouble("value").also { value -> require(value.isFinite()) }) },NumericNextStep.valueOf(it.getString("next")),
        if(it.isNull("configuration")) null else it.getString("configuration"), if(it.isNull("access")) null else it.getString("access")) }
}
