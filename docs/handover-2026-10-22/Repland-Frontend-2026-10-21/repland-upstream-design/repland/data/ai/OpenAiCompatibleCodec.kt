package com.swan1127.repland.data.ai

import com.swan1127.repland.data.json.StrictJson
// The contract surface is wide by design (six request types, six response types, a
// validator and the version constant); listing them all would be noise.
import com.swan1127.repland.domain.model.*
import java.time.LocalDate

/**
 * The pure half of the OpenAI-compatible adapter: everything decidable from a request
 * object and a response string. No IO, no Android API, no `org.json` — so it runs under a
 * plain JVM unit test, which is the only way the parsing of untrusted model output gets
 * real coverage.
 *
 * The provider is asked for **one JSON object and nothing else**. A model that answers in
 * prose, wraps the object in a Markdown fence, or adds a sentence afterwards is handled by
 * [extractJsonObject] rather than by rejecting the answer outright — that failure mode is
 * common enough to design for.
 *
 * Note that the contract's `contractVersion` and `requestId` are **not** requested from the
 * model: they are stamped back on locally from the request. A model cannot be trusted to
 * echo an identifier, and [AiAdviceValidator] rejects a response whose id does not match.
 */
object OpenAiCompatibleCodec {

    private val FENCED = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- request building

    /** The JSON body for `POST {baseUrl}/chat/completions`. */
    fun buildChatBody(model: String, request: AiAdvisorRequest): String {
        val (system, user) = promptFor(request)
        return buildChatBody(model, system, user)
    }

    /**
     * The same body, for a caller whose prompts are not one of the six contract requests —
     * the arrangement assistant is a separate port with its own shape. Kept here rather than
     * assembled by the caller so that JSON escaping has exactly one implementation.
     */
    fun buildChatBody(
        model: String,
        system: String,
        user: String,
        /**
         * The structured contract calls want near-determinism. A group-chat member is asked
         * for a line of speech, and at 0.2 every role answers in the same flat register;
         * [DebugOpenAiCompatibleAgentChat] raises this so the personas stay distinct.
         */
        temperature: Double = 0.2,
    ): String = buildString {
        append("{\"model\":").append(quote(model))
        append(",\"temperature\":").append(temperature)
        append(",\"messages\":[")
        append("{\"role\":\"system\",\"content\":").append(quote(system)).append("},")
        append("{\"role\":\"user\",\"content\":").append(quote(user)).append("}")
        append("]}")
    }

    /**
     * `response_format` is deliberately not sent: support varies by provider and an
     * unsupported field is rejected outright by some, which would break the whole call for
     * a feature we only use as a hint. The instruction lives in the system prompt instead.
     */
    private fun promptFor(request: AiAdvisorRequest): Pair<String, String> = when (request) {
        is AiTaskUnderstandingRequest -> system(
            "你是学习任务分析助手。理解用户给出的学习任务，指出它的难点和依赖关系。",
            """输出 JSON：{"summary": "一句话说明这个任务要做什么", "dependencyNote": "需要先完成的前置，没有就省略该字段"}""",
        ) to taskContext(request.task)

        is AiDifficultyAndDurationRequest -> system(
            "你是学习任务评估助手。判断任务的难度和合理的单次投入时长。",
            """输出 JSON：{"difficulty": 1到5的整数, "suggestedDurationMinutes": 建议分钟数(15到240), "explanation": "给出理由"}""",
        ) to taskContext(request.task)

        is AiTaskBreakdownRequest -> system(
            "你是学习任务拆解助手。把任务拆成可以直接开始动手的步骤。",
            """输出 JSON：{"steps": ["第一步", "第二步"], "explanation": "拆解思路"}。步骤要具体可执行，不要写"认真复习"这类空话。""",
        ) to taskContext(request.task)

        is AiSortingExplanationRequest -> system(
            "你是学习计划解释助手。用户已经用本地规则排好了优先级，请用自然语言解释这个排序为什么合理。",
            """输出 JSON：{"explanation": "解释这个排序"}""",
        ) to buildString {
            append("本地排序依据：\n")
            request.localRankingReasons.forEach { append("- ").append(it).append('\n') }
            append("\n任务信息：\n").append(taskContext(request.task))
        }

        is AiReplanRequest -> system(
            "你是学习计划重排助手。用户进度落后，请重新安排剩余任务的时间。",
            """输出 JSON：{"orderedTaskReferences": ["reference", ...], "proposedSegments": [{"taskReference": "reference", "date": "YYYY-MM-DD", "startMinute": 起分钟, "endMinute": 止分钟}], "explanation": "重排理由"}
startMinute 和 endMinute 都必须是 30 的倍数，且落在同一天内。""",
        ) to buildString {
            append("可重排的任务（只能用这些 reference）：\n")
            request.affectedTasks.forEach { append(taskContext(it)).append("\n") }
            append("\n约束：\n")
            request.constraintSummary.forEach { append("- ").append(it).append("\n") }
            if (request.hardConstraints.isNotEmpty()) {
                append("\n不可占用时段（不能与它们重叠）：\n")
                request.hardConstraints.forEach {
                    append("- ").append(it.date).append(' ')
                        .append(minuteLabel(it.startMinute)).append('-').append(minuteLabel(it.endMinute)).append("\n")
                }
            }
            if (request.protectedManualOrderReferences.isNotEmpty()) {
                append("\n用户手动排定的任务，不要改动它们的顺序：\n")
                request.protectedManualOrderReferences.forEach { append("- ").append(it).append("\n") }
            }
        }

        is AiDailySummaryRequest -> system(
            "你是学习复盘助手。根据今天的完成情况，总结一天并给出下一步建议。",
            """输出 JSON：{"summary": "今天的总结", "suggestedNextStep": "明天可以优先做的一件事"}""",
        ) to buildString {
            append("日期：").append(request.day).append('\n')
            if (request.confirmedFeedback.isEmpty()) {
                append("今天没有任何已确认的完成记录。")
            } else {
                append("已确认的完成记录：\n")
                request.confirmedFeedback.forEach { append(feedbackLine(it)).append('\n') }
            }
        }
    }

    private fun system(role: String, schema: String): String =
        "$role\n只输出一个 JSON 对象，不要输出解释文字、Markdown 围栏或任何前后缀。\n$schema"

    private fun taskContext(task: AiTaskContext): String = buildString {
        append("reference=").append(task.reference)
        append(" 标题=").append(task.title)
        if (task.description.isNotBlank()) append(" 说明=").append(task.description)
        append(" 分类=").append(task.category.name)
        append(" 当前优先级=").append(task.initialPriority.name)
        append(" 状态=").append(task.currentStatus.name)
        append(" 预计天数=").append(task.estimatedDays)
        task.expectedDurationMinutes?.let { append(" 预计时长(分)=").append(it) }
        task.dueDate?.let { append(" 截止=").append(it) }
        task.scheduledForDate?.let { append(" 计划日期=").append(it) }
        if (task.confirmedSegments.isNotEmpty()) {
            append(" 已排定=")
            append(
                task.confirmedSegments.joinToString("; ") {
                    "${it.date} ${minuteLabel(it.startMinute)}-${minuteLabel(it.endMinute)}${if (it.isLocked) "(锁定)" else ""}"
                },
            )
        }
        if (task.recentFeedback.isNotEmpty()) {
            append(" 近期反馈=")
            append(task.recentFeedback.joinToString("; ") { feedbackLine(it) })
        }
    }

    private fun feedbackLine(feedback: AiFeedbackContext): String = buildList {
        feedback.actualDurationMinutes?.let { add("实际${it}分钟") }
        feedback.progressPercent?.let { add("完成${it}%") }
        feedback.completedContent?.takeIf(String::isNotBlank)?.let { add("已完成：$it") }
        feedback.completionResult?.takeIf(String::isNotBlank)?.let { add("结果：$it") }
        feedback.postponeReason?.takeIf(String::isNotBlank)?.let { add("拖延原因：$it") }
    }.joinToString("，").ifEmpty { "无反馈" }

    private fun minuteLabel(minute: Int): String =
        "%02d:%02d".format(minute / 60, minute % 60)

    // ---------------------------------------------------------------- response reading

    /** The assistant text inside a `chat/completions` response, or null if the shape is wrong. */
    fun parseChatContent(body: String): String? = runCatching {
        val root = StrictJson.parseObject(body)
        val choices = root["choices"] as? List<*> ?: return null
        val first = choices.firstOrNull() as? Map<*, *> ?: return null
        val message = first["message"] as? Map<*, *> ?: return null
        (message["content"] as? String)?.takeIf(String::isNotBlank)
    }.getOrNull()

    /** The model ids in a `GET /models` response, in the order the provider listed them. */
    fun parseModelIds(body: String): List<String> = runCatching {
        val root = StrictJson.parseObject(body)
        (root["data"] as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
            .filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    /**
     * Turns raw assistant text into a validated contract response.
     *
     * Every failure path collapses to `Failed(INVALID_RESPONSE)` on purpose: the caller has
     * one recovery move (fall back to the local rule-based path), and distinguishing "the
     * JSON was malformed" from "the JSON was well-formed but out of range" would not change
     * it.
     */
    fun parseAdvice(request: AiAdvisorRequest, content: String): AiAdvisorResult {
        val json = extractJsonObject(content) ?: return invalid()
        val obj = runCatching { StrictJson.parseObject(json) }.getOrNull() ?: return invalid()
        val response = runCatching { buildResponse(request, obj) }.getOrNull() ?: return invalid()
        return when (val validation = AiAdviceValidator.validate(request, response)) {
            is AiAdviceValidation.Valid -> AiAdvisorResult.Advice(validation.response)
            is AiAdviceValidation.Invalid -> invalid()
        }
    }

    private fun invalid(): AiAdvisorResult =
        AiAdvisorResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)

    /**
     * Pulls the first complete JSON object out of [raw].
     *
     * Scans with a brace depth counter that skips braces inside string literals, so a
     * message containing `{` in prose cannot truncate the object early. A Markdown fence, if
     * present, is unwrapped first because that is how models most often decorate the answer.
     */
    fun extractJsonObject(raw: String): String? {
        val fenced = FENCED.find(raw)
        val candidate = (fenced?.groupValues?.get(1) ?: raw).trim()
        val start = candidate.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until candidate.length) {
            val c = candidate[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return candidate.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun buildResponse(request: AiAdvisorRequest, obj: Map<String, Any?>): AiAdvisorResponse {
        val version = request.contractVersion
        val id = request.requestId
        return when (request) {
            is AiTaskUnderstandingRequest -> AiTaskUnderstandingAdvice(
                contractVersion = version,
                requestId = id,
                summary = requireText(obj, "summary"),
                dependencyNote = optionalText(obj, "dependencyNote"),
            )

            is AiDifficultyAndDurationRequest -> AiDifficultyAndDurationAdvice(
                contractVersion = version,
                requestId = id,
                difficulty = requireInt(obj, "difficulty"),
                suggestedDurationMinutes = optionalInt(obj, "suggestedDurationMinutes"),
                explanation = requireText(obj, "explanation"),
            )

            is AiTaskBreakdownRequest -> AiTaskBreakdownAdvice(
                contractVersion = version,
                requestId = id,
                steps = requireStringList(obj, "steps"),
                explanation = requireText(obj, "explanation"),
            )

            is AiSortingExplanationRequest -> AiSortingExplanationAdvice(
                contractVersion = version,
                requestId = id,
                explanation = requireText(obj, "explanation"),
            )

            is AiReplanRequest -> AiPlanDraftAdvice(
                contractVersion = version,
                requestId = id,
                orderedTaskReferences = optionalStringList(obj, "orderedTaskReferences"),
                proposedSegments = requireSegments(obj, "proposedSegments"),
                explanation = requireText(obj, "explanation"),
            )

            is AiDailySummaryRequest -> AiDailySummaryAdvice(
                contractVersion = version,
                requestId = id,
                summary = requireText(obj, "summary"),
                suggestedNextStep = optionalText(obj, "suggestedNextStep"),
            )
        }
    }

    private fun requireSegments(obj: Map<String, Any?>, key: String): List<AiProposedSegment> {
        val raw = obj[key] as? List<*> ?: error("missing $key")
        return raw.map { item ->
            val map = item as? Map<*, *> ?: error("segment is not an object")
            AiProposedSegment(
                taskReference = map["taskReference"] as? String ?: error("segment has no taskReference"),
                date = LocalDate.parse(map["date"] as? String ?: error("segment has no date")),
                startMinute = (map["startMinute"] as? Number)?.toInt() ?: error("segment has no startMinute"),
                endMinute = (map["endMinute"] as? Number)?.toInt() ?: error("segment has no endMinute"),
            )
        }
    }

    private fun requireText(obj: Map<String, Any?>, key: String): String =
        (obj[key] as? String)?.takeIf(String::isNotBlank) ?: error("missing $key")

    private fun optionalText(obj: Map<String, Any?>, key: String): String? =
        (obj[key] as? String)?.takeIf(String::isNotBlank)

    private fun requireInt(obj: Map<String, Any?>, key: String): Int =
        (obj[key] as? Number)?.toInt() ?: error("missing $key")

    private fun optionalInt(obj: Map<String, Any?>, key: String): Int? =
        (obj[key] as? Number)?.toInt()

    private fun requireStringList(obj: Map<String, Any?>, key: String): List<String> {
        val list = requireStringListOrNull(obj, key) ?: error("missing $key")
        require(list.isNotEmpty()) { "$key is empty" }
        return list
    }

    private fun optionalStringList(obj: Map<String, Any?>, key: String): List<String> =
        requireStringListOrNull(obj, key).orEmpty()

    private fun requireStringListOrNull(obj: Map<String, Any?>, key: String): List<String>? =
        (obj[key] as? List<*>)?.mapNotNull { it as? String }?.filter(String::isNotBlank)

    // ---------------------------------------------------------------- failure mapping

    /**
     * `4xx` other than auth and rate limiting is reported as `REMOTE_FAILURE` rather than a
     * local error: the provider rejected the call, and the user's actionable move is the
     * same either way.
     */
    fun mapHttpStatus(statusCode: Int): AiAdvisorFailureReason = when {
        statusCode == 401 || statusCode == 403 -> AiAdvisorFailureReason.AUTHENTICATION_FAILURE
        statusCode == 429 -> AiAdvisorFailureReason.RATE_LIMITED
        else -> AiAdvisorFailureReason.REMOTE_FAILURE
    }

    // ---------------------------------------------------------------- JSON writing

    /** Escapes a string for embedding in JSON. Control characters become `\uXXXX`. */
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c.code < 0x20) {
                    append("\\u%04x".format(c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }
}
