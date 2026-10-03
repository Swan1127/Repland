package com.swan1127.repland.data.ai

import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementAssistantAdvice
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceValidator
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.ArrangementCandidate
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only OpenAI-compatible refinement for an explicit arrangement request. */
class CompatibleArrangementAdvisor(
    private val providerConfigRepository: AiProviderConfigRepository,
) : ArrangementAssistantAdvisor {
    override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult = withContext(Dispatchers.IO) {
        try {
            val revision = providerConfigRepository.observe().first()
            val provider = providerConfigRepository.readSecret()
                ?: return@withContext ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
            val content = CompatibleChatTransport().complete(provider, ARRANGEMENT_SYSTEM_PROMPT, request.toWire())
            if (providerConfigRepository.observe().first() != revision)
                return@withContext ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.CONFIGURATION_CHANGED)
            val advice = decodeArrangementAdvice(content)
            ArrangementAssistantAdviceValidator.validate(advice, request)?.let { ArrangementAssistantAdviceResult.Advice(it) }
                ?: ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: ProviderHttpFailure) {
            ArrangementAssistantAdviceResult.Failed(failure.reason)
        } catch (_: SocketTimeoutException) {
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        } catch (_: org.json.JSONException) {
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } catch (_: java.time.format.DateTimeParseException) {
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } catch (_: Exception) {
            ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }
}

private fun Int.toFailureReason(): AiAdvisorFailureReason = when (this) {
    401, 403 -> AiAdvisorFailureReason.AUTHENTICATION_FAILURE
    429 -> AiAdvisorFailureReason.RATE_LIMITED
    in 500..599 -> AiAdvisorFailureReason.REMOTE_FAILURE
    else -> AiAdvisorFailureReason.TRANSPORT_FAILURE
}

private fun ArrangementAssistantAdviceRequest.toWire(): JSONObject = JSONObject()
    .put("utterance", utterance)
    .put("date", date.toString())
    .put("existingTasks", JSONArray(existingTasks.map { JSONObject().put("id", it.id).put("title", it.title).put("category", it.category.name).put("durationMinutes", it.durationMinutes) }))
    .put("draftCandidates", JSONArray(draftCandidates.map { JSONObject().put("title", it.title).put("existingTaskId", it.existingTaskId).put("durationMinutes", it.durationMinutes).put("startMinute", it.timeHint.explicitStartMinute).put("preferredTrackId", it.preferredTrackId) }))
    .put("occupiedIntervals", JSONArray(occupiedIntervals.map {
        JSONObject().put("title", it.title).put("startMinute", it.startMinute)
            .put("endMinute", it.endMinute).put("trackId", it.trackId)
            .put("isHardBusy", it.isHardBusy)
            .put("taskId", it.taskId)
    }))

internal fun decodeArrangementAdvice(rawResponse: String): ArrangementAssistantAdvice = parseAdvice(rawResponse.responseJson())

private fun parseAdvice(json: JSONObject): ArrangementAssistantAdvice {
    val plan = listOf(json, json.optJSONObject("plan"), json.optJSONObject("data"), json.optJSONObject("result"))
        .filterNotNull()
        .firstOrNull { it.optJSONArray("candidates") != null || it.optJSONArray("tasks") != null || it.optJSONArray("items") != null }
        ?: throw org.json.JSONException("Missing planning candidates")
    val candidates = plan.optJSONArray("candidates") ?: plan.optJSONArray("tasks") ?: plan.getJSONArray("items")
    return ArrangementAssistantAdvice(
        confidenceLabel = (plan.firstText("confidenceLabel", "planSummary", "summary", "explanation")
            ?: "已生成可编辑建议").take(60),
        candidates = candidates.let { items ->
            (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val category = item.taskCategory()
            val start = item.minute("startMinute") ?: item.minute("start") ?: item.minute("startTime")
            val duration = item.minute("durationMinutes") ?: item.minute("estimatedMinutes") ?: item.minute("duration")
            val clarification = buildSet {
                (item.optJSONArray("needsClarification") ?: item.optJSONArray("missing") ?: item.optJSONArray("questions"))?.let { fields ->
                    (0 until fields.length()).forEach { field ->
                        runCatching { ArrangementClarification.valueOf(fields.getString(field)) }.getOrNull()?.let(::add)
                    }
                }
                if (start == null) add(ArrangementClarification.TIME)
                if (duration == null) add(ArrangementClarification.DURATION)
            }
            ArrangementCandidate(
                title = item.firstText("title", "name", "task", "taskTitle")?.take(120)
                    ?: throw org.json.JSONException("Missing candidate title"),
                category = category,
                durationMinutes = duration,
                timeHint = ArrangementTimeHint(start, item.firstText("windowLabel", "timeWindow", "window")),
                needsClarification = clarification,
                placementSource = runCatching {
                    ArrangementPlacementSource.valueOf(item.firstText("placementSource", "source") ?: "")
                }.getOrDefault(if (start != null && duration != null) ArrangementPlacementSource.AI_SUGGESTED else ArrangementPlacementSource.UNSCHEDULED),
                preferredTrackId = item.firstText("preferredTrackId", "trackId", "track"),
                existingTaskId = item.firstText("existingTaskId"),
            )
        }
        },
    )
}

/**
 * Providers occasionally wrap valid JSON in a sentence or a Markdown fence.
 * This extracts the first balanced JSON object; the domain validator still
 * rejects malformed or unsafe planning fields afterwards.
 */
private fun String.responseJson(): JSONObject {
    val body = trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = body.indexOf('{')
    if (start < 0) throw org.json.JSONException("No JSON object in response")
    var depth = 0
    var quoted = false
    var escaped = false
    for (index in start until body.length) {
        when (body[index]) {
            '\\' -> if (quoted) escaped = !escaped
            '"' -> if (!escaped) quoted = !quoted else escaped = false
            '{' -> if (!quoted) depth += 1
            '}' -> if (!quoted) {
                depth -= 1
                if (depth == 0) return JSONObject(body.substring(start, index + 1))
            }
            else -> escaped = false
        }
    }
    throw org.json.JSONException("Unclosed JSON object in response")
}

private fun JSONObject.completionText(): String {
    val message = getJSONArray("choices").getJSONObject(0).getJSONObject("message")
    return when (val content = message.opt("content")) {
        is String -> content
        is JSONArray -> (0 until content.length()).joinToString(separator = "") { index ->
            content.getJSONObject(index).firstText("text", "content").orEmpty()
        }
        else -> throw org.json.JSONException("Missing completion text")
    }
}

private fun JSONObject.firstText(vararg names: String): String? = names
    .asSequence()
    .mapNotNull { name -> opt(name) as? String }
    .map(String::trim)
    .firstOrNull { it.isNotBlank() && it != "null" }

private fun JSONObject.minute(name: String): Int? {
    val raw = opt(name)
    when (raw) {
        is Number -> return raw.toInt().takeIf { it in 0 until 1_440 || name.contains("duration", ignoreCase = true) && it in 5..720 }
        is String -> raw.toIntOrNull()?.let { value ->
            return value.takeIf { it in 0 until 1_440 || name.contains("duration", ignoreCase = true) && it in 5..720 }
        }
    }
    val clock = (raw as? String)?.trim()?.let { Regex("(\\d{1,2})[:：](\\d{2})").matchEntire(it) } ?: return null
    val hour = clock.groupValues[1].toIntOrNull() ?: return null
    val minute = clock.groupValues[2].toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

private fun JSONObject.taskCategory(): TaskCategory = firstText("category", "type")?.uppercase()?.let { value ->
    runCatching { TaskCategory.valueOf(value) }.getOrNull()
} ?: when {
    firstText("category", "type")?.contains("学习") == true -> TaskCategory.COURSE
    firstText("category", "type")?.contains("工作") == true -> TaskCategory.OFFICE
    firstText("category", "type")?.contains("生活") == true -> TaskCategory.LEISURE
    else -> TaskCategory.EXTRACURRICULAR
}

private const val ARRANGEMENT_SYSTEM_PROMPT = """
You are Repland's Planning Master: a careful Chinese/English personal-planning assistant. Turn the utterance into separate, actionable candidates using meaning rather than keyword or punctuation matching. For example, “我想复习英语和写报告” is two candidates: “复习英语” and “写报告”.

Return exactly one JSON object and no Markdown:
{"confidenceLabel":"short Chinese phrase","candidates":[{"title":"string","existingTaskId":"exact existingTasks ID or null","category":"COURSE|EXTRACURRICULAR|OFFICE|LEISURE","startMinute":number or null,"windowLabel":string or null,"durationMinutes":number or null,"needsClarification":["TIME","DURATION"],"placementSource":"AI_SUGGESTED|UNSCHEDULED|USER_EXPLICIT","preferredTrackId":"focus|parallel-2|null"}]}.

Planning rules:
0. existingTasks are read-only facts. To reschedule an existing task, return its exact existingTaskId; never invent IDs or silently duplicate it as a new task. For genuinely new work use existingTaskId=null. Do not edit task status, priority or total duration. draftCandidates describe the current editable proposal; refinement can replace that proposal, not create another database copy. Existing task output title must identify the referenced task; durationMinutes is only the proposed placement length.
1. Extract every independent action. Keep one action intact when a conjunction only joins subjects, such as “数学和英语复习”.
2. Existing occupiedIntervals are read-only. Never change or return them. Treat every interval with isHardBusy=true as a global blocker: do not schedule across it on any track.
3. Plan conservatively: preserve user-specified time as USER_EXPLICIT. Otherwise propose one concrete currently free time between 08:00 and 22:00 and a realistic 25–120 minute focus block, with a small transition buffer around hard busy intervals. Mark it AI_SUGGESTED. Use a parallel track only for genuinely compatible simultaneous work; never use one to evade a hard busy interval.
4. For study/review work, prefer a focused retrieval/review block and do not create a marathon block merely to fill time. For report/writing work, protect a continuous focus block and avoid scattering it across tiny gaps. Keep context switches low unless the person explicitly asks for parallel work.
5. If there is no safe gap or the action is too ambiguous, leave startMinute and durationMinutes null and mark UNSCHEDULED with the relevant clarification fields.
6. Suggestions are editable drafts only: do not claim completion, do not write actions, do not make promises. At most 8 candidates.
"""
