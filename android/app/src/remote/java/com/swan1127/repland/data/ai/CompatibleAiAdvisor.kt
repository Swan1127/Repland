package com.swan1127.repland.data.ai

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResponse
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiDailySummaryAdvice
import com.swan1127.repland.domain.model.AiDailySummaryRequest
import com.swan1127.repland.domain.model.AiDifficultyAndDurationAdvice
import com.swan1127.repland.domain.model.AiDifficultyAndDurationRequest
import com.swan1127.repland.domain.model.AiPlanDraftAdvice
import com.swan1127.repland.domain.model.AiProposedSegment
import com.swan1127.repland.domain.model.AiReplanRequest
import com.swan1127.repland.domain.model.AiSortingExplanationAdvice
import com.swan1127.repland.domain.model.AiSortingExplanationRequest
import com.swan1127.repland.domain.model.AiTaskBreakdownAdvice
import com.swan1127.repland.domain.model.AiTaskBreakdownRequest
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only Agnes transport. The main source set has no network implementation;
 * this adapter is loaded reflectively and reads a Keystore-protected key only at
 * the moment an already-confirmed advice request is made.
 */
class CompatibleAiAdvisor(
    private val providerConfigRepository: AiProviderConfigRepository,
) : AiAdvisor {
    override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult = withContext(Dispatchers.IO) {
        try {
            val revision = providerConfigRepository.observe().first()
            val provider = providerConfigRepository.readSecret()
                ?: return@withContext AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
            val content = CompatibleChatTransport().complete(provider, AI_SYSTEM_INSTRUCTION, request.toWire())
            if (providerConfigRepository.observe().first() != revision)
                return@withContext AiAdvisorResult.Failed(AiAdvisorFailureReason.CONFIGURATION_CHANGED)
            val response = request.decode(JSONObject(content.removeMarkdownFence()))
            when (val validation = com.swan1127.repland.domain.model.AiAdviceValidator.validate(request, response)) {
                is com.swan1127.repland.domain.model.AiAdviceValidation.Valid -> AiAdvisorResult.Advice(validation.response)
                is com.swan1127.repland.domain.model.AiAdviceValidation.Invalid -> AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: ProviderHttpFailure) {
            AiAdvisorResult.Failed(failure.reason)
        } catch (_: SocketTimeoutException) {
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
        } catch (_: org.json.JSONException) {
            AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } catch (_: java.time.format.DateTimeParseException) {
            AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } catch (_: Exception) {
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }
}

private const val AI_SYSTEM_INSTRUCTION = """
You are the optional reasoning service for a personal planning application. Reply with exactly one valid JSON object, no Markdown and no prose. The user payload is a typed request. Preserve requestId and contractVersion exactly. Never claim you wrote, completed, moved, deleted, or scheduled anything. Return only the fields for its kind:
- task_understanding: contractVersion, requestId, summary, dependencyNote (nullable string)
- difficulty_duration: contractVersion, requestId, difficulty (1..5), suggestedDurationMinutes (nullable integer), explanation
- task_breakdown: contractVersion, requestId, steps (array of strings), explanation
- sorting_explanation: contractVersion, requestId, explanation
- replan: contractVersion, requestId, orderedTaskReferences (array), proposedSegments (array of taskReference/date/startMinute/endMinute), explanation
- daily_summary: contractVersion, requestId, summary, suggestedNextStep (nullable string)
Do not invent task references or alter dates and hard constraints in the payload.
"""

private fun String.removeMarkdownFence(): String = trim()
    .removePrefix("```json")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()

private fun AiAdvisorRequest.toWire(): JSONObject {
    val out = JSONObject().put("requestId", requestId).put("contractVersion", contractVersion)
    when (this) {
        is AiTaskUnderstandingRequest -> out.put("kind", "task_understanding").put("task", task.toWire())
        is AiDifficultyAndDurationRequest -> out.put("kind", "difficulty_duration").put("task", task.toWire())
        is AiTaskBreakdownRequest -> out.put("kind", "task_breakdown").put("task", task.toWire())
        is AiSortingExplanationRequest -> out.put("kind", "sorting_explanation")
            .put("task", task.toWire()).put("localRankingReasons", JSONArray(localRankingReasons))
        is AiReplanRequest -> out.put("kind", "replan")
            .put("affectedTasks", JSONArray(affectedTasks.map { it.toWire() }))
            .put("constraintSummary", JSONArray(constraintSummary))
            .put("hardConstraints", JSONArray(hardConstraints.map {
                JSONObject().put("date", it.date.toString())
                    .put("startMinute", it.startMinute).put("endMinute", it.endMinute)
            }))
            .put("protectedManualOrderReferences", JSONArray(protectedManualOrderReferences))
        is AiDailySummaryRequest -> out.put("kind", "daily_summary")
            .put("day", day.toString())
            .put("confirmedFeedback", JSONArray(confirmedFeedback.map { it.toWire() }))
    }
    return out
}

private fun com.swan1127.repland.domain.model.AiTaskContext.toWire(): JSONObject = JSONObject()
    .put("reference", reference).put("title", title).put("description", description)
    .put("category", category.name).put("initialPriority", initialPriority.name)
    .put("currentStatus", currentStatus.name).put("estimatedDays", estimatedDays)
    .put("expectedDurationMinutes", expectedDurationMinutes)
    .put("dueDate", dueDate?.toString())
    .put("recentFeedback", JSONArray(recentFeedback.map { it.toWire() }))
    .put("confirmedSegments", JSONArray(confirmedSegments.map {
        JSONObject().put("date", it.date.toString())
            .put("startMinute", it.startMinute).put("endMinute", it.endMinute)
            .put("isLocked", it.isLocked)
    }))

private fun com.swan1127.repland.domain.model.AiFeedbackContext.toWire(): JSONObject = JSONObject()
    .put("actualDurationMinutes", actualDurationMinutes)
    .put("progressPercent", progressPercent)
    .put("completedContent", completedContent)
    .put("completionResult", completionResult)
    .put("postponeReason", postponeReason)

private fun AiAdvisorRequest.decode(json: JSONObject): AiAdvisorResponse {
    val version = json.getString("contractVersion")
    val id = json.getString("requestId")
    return when (this) {
        is AiTaskUnderstandingRequest -> AiTaskUnderstandingAdvice(version, id,
            json.getString("summary"), json.optString("dependencyNote").ifBlank { null })
        is AiDifficultyAndDurationRequest -> AiDifficultyAndDurationAdvice(version, id,
            json.getInt("difficulty"), json.optInt("suggestedDurationMinutes").takeIf { it > 0 },
            json.getString("explanation"))
        is AiTaskBreakdownRequest -> AiTaskBreakdownAdvice(version, id,
            json.getJSONArray("steps").toStringList(), json.getString("explanation"))
        is AiSortingExplanationRequest -> AiSortingExplanationAdvice(version, id,
            json.getString("explanation"))
        is AiReplanRequest -> AiPlanDraftAdvice(version, id,
            json.getJSONArray("orderedTaskReferences").toStringList(),
            json.getJSONArray("proposedSegments").let { array ->
                (0 until array.length()).map { index ->
                    array.getJSONObject(index).let { segment ->
                        AiProposedSegment(
                            segment.getString("taskReference"), LocalDate.parse(segment.getString("date")),
                            segment.getInt("startMinute"), segment.getInt("endMinute"),
                        )
                    }
                }
            }, json.getString("explanation"))
        is AiDailySummaryRequest -> AiDailySummaryAdvice(version, id,
            json.getString("summary"), json.optString("suggestedNextStep").ifBlank { null })
    }
}

private fun JSONArray.toStringList(): List<String> = (0 until length()).map(::getString)
