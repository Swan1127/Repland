package com.swan1127.repland.data.ai

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import com.swan1127.repland.data.ai.OpenAiCompatibleCodec
import com.swan1127.repland.data.json.StrictJson
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
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.ports.AiProviderConfigRepository

/**
 * The arrangement assistant's optional language-refinement stage in both build types.
 *
 * It does exactly one thing: turn an utterance into the same bounded
 * [ArrangementCandidate] shape the local interpreter produces. It is **not** allowed to
 * invent a slot for something the user left undated — an item with no stated time comes
 * back with `UNSCHEDULED`, which is what makes the preview show a question instead of a
 * confident-looking guess. Everything it returns still passes through
 * [ArrangementAssistantAdviceValidator] and the local collision check.
 *
 * The transport is injected for tests; production uses the configured HTTPS provider.
 */
class OpenAiCompatibleArrangementAdvisor @JvmOverloads constructor(
    private val configRepository: AiProviderConfigRepository,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : ArrangementAssistantAdvisor {

    override suspend fun refine(
        request: ArrangementAssistantAdviceRequest,
    ): ArrangementAssistantAdviceResult {
        val secret = configRepository.readSecret()
            ?: return unavailable()
        if (secret.apiKey.isBlank()) return unavailable()

        val body = OpenAiCompatibleCodec.buildChatBody(secret.model, systemPrompt(), userPrompt(request))
        val url = "${secret.baseUrl.trimEnd('/')}/chat/completions"

        return when (val outcome = transport.post(url, secret.apiKey, body)) {
            is AiHttpOutcome.Responded -> {
                if (outcome.statusCode !in 200..299) {
                    ArrangementAssistantAdviceResult.Failed(
                        OpenAiCompatibleCodec.mapHttpStatus(outcome.statusCode),
                    )
                } else {
                    val content = OpenAiCompatibleCodec.parseChatContent(outcome.body)
                    if (content == null) {
                        ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
                    } else {
                        parseAdvice(content)
                    }
                }
            }

            AiHttpOutcome.TimedOut -> ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TIMEOUT)
            AiHttpOutcome.Unreachable -> ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
    }

    private fun parseAdvice(content: String): ArrangementAssistantAdviceResult {
        val json = OpenAiCompatibleCodec.extractJsonObject(content)
            ?: return invalid()
        val root = runCatching { StrictJson.parseObject(json) }.getOrNull()
            ?: return invalid()
        val advice = runCatching { toAdvice(root) }.getOrNull() ?: return invalid()
        // The same validator the local interpreter's output goes through: a model may
        // propose, but it cannot widen the shape the rest of the app is written against.
        val accepted = ArrangementAssistantAdviceValidator.validate(advice)
            ?: return invalid()
        return ArrangementAssistantAdviceResult.Advice(accepted)
    }

    private fun toAdvice(root: Map<String, Any?>): ArrangementAssistantAdvice {
        val raw = root["candidates"] as? List<*> ?: error("candidates missing")
        val candidates = raw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val title = (map["title"] as? String)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            // Everything below is normalised into the range the contract accepts rather than
            // left for ArrangementAssistantAdviceValidator to reject: a rejected candidate
            // disappears from the preview altogether, which is silent data loss of something
            // the user did say. Degrading to "unknown" keeps the item so the preview can ask
            // about the part that was unusable.
            val duration = (map["durationMinutes"] as? Number)?.toInt()?.takeIf { it in 5..720 }
            val startMinute = (map["explicitStartMinute"] as? Number)
                ?.toInt()
                ?.takeIf { it in 0 until 1_440 }
            // A block that would run past midnight cannot be placed on this day, so the start
            // is dropped rather than the duration clamped — clamping would invent a length.
            val explicitStart = if (startMinute != null && duration != null && startMinute + duration > 1_440) {
                null
            } else {
                startMinute
            }
            ArrangementCandidate(
                title = title,
                category = parseCategory(map["category"] as? String),
                durationMinutes = duration,
                timeHint = ArrangementTimeHint(
                    explicitStartMinute = explicitStart,
                    windowLabel = (map["windowLabel"] as? String)?.takeIf(String::isNotBlank),
                ),
                needsClarification = parseClarifications(map["needsClarification"]),
                // Only an explicitly stated time may claim USER_EXPLICIT. Anything else is
                // UNSCHEDULED on purpose: the advisor does not get to pick a slot. The
                // client's ChatDayPlanner does that against the real day.
                placementSource = if (explicitStart != null) {
                    ArrangementPlacementSource.USER_EXPLICIT
                } else {
                    ArrangementPlacementSource.UNSCHEDULED
                },
                priority = parsePriority(map["priority"] as? String),
                durationEstimated = map["durationEstimated"] as? Boolean ?: false,
            )
        }
        return ArrangementAssistantAdvice(
            candidates = candidates,
            confidenceLabel = (root["confidenceLabel"] as? String)?.takeIf(String::isNotBlank) ?: "AI 建议",
        )
    }

    /** An unknown category is a course only because that is the app's most common default. */
    private fun parseCategory(raw: String?): TaskCategory =
        TaskCategory.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
            ?: TaskCategory.COURSE

    /**
     * Null when the user expressed no urgency. Guessing MEDIUM here would make every item
     * look equally important to the planner, which is the same as having no priority at all.
     */
    private fun parsePriority(raw: String?): TaskPriority? =
        TaskPriority.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

    private fun parseClarifications(raw: Any?): Set<ArrangementClarification> {
        val list = raw as? List<*> ?: return emptySet()
        return list.mapNotNull { value ->
            val text = value as? String ?: return@mapNotNull null
            ArrangementClarification.entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
        }.toSet()
    }

    private fun invalid(): ArrangementAssistantAdviceResult =
        ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)

    private fun unavailable(): ArrangementAssistantAdviceResult =
        ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

    private fun systemPrompt(): String = buildString {
        append("你是日程安排助手。把用户这句话拆成一件件可安排的事项，抽取每件事的名称、")
        append("分类、时长、优先级和用户明确说出的时间。\n")
        append("规则：\n")
        append("1. 用户没有明确说时间的，不要替他编一个具体时间，省略 explicitStartMinute；")
        append("客户端会在真实空档里替他排。\n")
        append("2. 分类只能是 COURSE / EXTRACURRICULAR / OFFICE / LEISURE。\n")
        append("3. 用户没说时长时，按这件事的常见经验估一个 durationMinutes，并把 durationEstimated 设为 true。\n")
        append("4. priority 只能是 REQUIRED / HIGH / MEDIUM / LOW。用户说「必须/一定要/截止/来不及」用 REQUIRED 或 HIGH；")
        append("说「有空/顺便/不着急/看情况」用 LOW；没有表态就省略该字段，不要默认 MEDIUM。\n")
        append("5. needsClarification 只能包含 TIME 或 DURATION，表示这两项信息确实缺失。\n")
        append("6. explicitStartMinute 是用户明确说出的开始时间（从 0 点起的分钟数，如 14:30 = 870）。\n")
        append("7. 只输出一个 JSON 对象，不要输出解释文字、Markdown 围栏或任何前后缀。\n")
        append("输出 JSON：")
        append("""{"candidates": [{"title": "事项名称", "category": "COURSE", "durationMinutes": 60, """)
        append(""""durationEstimated": true, "priority": "HIGH", "explicitStartMinute": 870, """)
        append(""""windowLabel": "下午", "needsClarification": ["TIME"]}], """)
        append(""""confidenceLabel": "一句话说明你的把握程度"}""")
    }

    private fun userPrompt(request: ArrangementAssistantAdviceRequest): String = buildString {
        append("用户这句话：\n").append(request.utterance).append("\n\n")
        append("当天已经占用的时段（不要安排到这些时间里）：\n")
        if (request.occupiedIntervals.isEmpty()) {
            append("（没有）\n")
        } else {
            request.occupiedIntervals.forEach {
                append("- ").append(it.title).append(' ')
                    .append(label(it.startMinute)).append('-').append(label(it.endMinute))
                    .append(if (it.isHardBusy) "（不可占用）" else "（可调整）")
                    .append('\n')
            }
        }
    }

    private fun label(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
