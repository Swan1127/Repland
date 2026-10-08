package com.swan1127.repland.domain.model

/**
 * The local, inspectable first stage of the arrangement assistant.
 *
 * It intentionally does not choose a free slot.  Language can express a hard
 * time, a soft time window, or no time at all; converting the latter into a
 * fabricated 09:00/13:00/19:00 plan would make the preview look decisive while
 * hiding the exact information the person still needs to decide.
 */
enum class ArrangementIntent {
    ARRANGE_TODAY,
    CAPTURE_TASKS,
    REPLAN,
    REVIEW,
}

enum class ArrangementClarification {
    TIME,
    DURATION,
}

/**
 * Makes a suggested position auditable in the draft. A model may propose a
 * free slot, but only the person can turn that proposal into a saved plan.
 */
enum class ArrangementPlacementSource {
    /** The utterance explicitly stated a time. */
    USER_EXPLICIT,
    /** The advisor picked a currently free slot around read-only commitments. */
    AI_SUGGESTED,
    /** There is not enough information to position the item yet. */
    UNSCHEDULED,
}

data class ArrangementTimeHint(
    val explicitStartMinute: Int? = null,
    val windowLabel: String? = null,
)

data class ArrangementCandidate(
    val title: String,
    val category: TaskCategory,
    val durationMinutes: Int?,
    val timeHint: ArrangementTimeHint,
    /** Details the user has not supplied. They are questions, never defaults. */
    val needsClarification: Set<ArrangementClarification>,
    val placementSource: ArrangementPlacementSource = ArrangementPlacementSource.UNSCHEDULED,
    val preferredTrackId: String? = null,
    val existingTaskId: String? = null,
    val proposalId: String? = null,
)

data class ArrangementInterpretation(
    val intent: ArrangementIntent,
    val candidates: List<ArrangementCandidate>,
    val confidenceLabel: String,
) {
    val needsClarification: Boolean get() = candidates.any { it.needsClarification.isNotEmpty() }
}

/**
 * Deliberately small deterministic parser used while the app is local-only.
 * A future model may improve extraction, but it must return this same bounded
 * shape and never bypass the local constraint/confirmation pipeline.
 */
object ArrangementAssistantInterpreter {
    private val separators = Regex("[\\n，。；;、]+")
    private val conjunctions = Regex("\\s*(?:和|以及|及|并且|同时|还有|再|然后)\\s*")
    private val actionWords = listOf(
        "复习", "写", "完成", "整理", "处理", "准备", "做", "学习", "练", "背", "读", "看", "听",
        "提交", "回复", "参加", "跑", "买", "联系", "规划", "study", "write", "review", "prepare",
    )
    private val durationPattern = Regex("(\\d{1,3})\\s*(分钟|分|小时|h|min)", RegexOption.IGNORE_CASE)
    private val hourRangePattern = Regex("(?:下午|晚上|早上|上午|中午)?\\s*(\\d{1,2})\\s*(?:点|:|：)\\s*(?:(\\d{1,2})\\s*分?)?\\s*(?:到|至|-)\\s*(\\d{1,2})\\s*(?:点|:|：)\\s*(?:(\\d{1,2})\\s*分?)?")
    private val singleTimePattern = Regex("(早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})\\s*(?:点|:|：)\\s*(?:(\\d{1,2})\\s*分?)?")

    fun interpret(text: String): ArrangementInterpretation {
        val intent = intentFor(text)
        val candidates = text
            .split(separators)
            .map(String::trim)
            .filter(String::isNotBlank)
            .flatMap(::splitIndependentActions)
            .map(::candidateFor)
            .take(8)
        return ArrangementInterpretation(
            intent = intent,
            candidates = candidates,
            confidenceLabel = if (candidates.isEmpty()) "没有识别到可以安排的事项" else "已提取 ${candidates.size} 件事项",
        )
    }

    private fun candidateFor(line: String): ArrangementCandidate {
        val range = hourRangePattern.find(line)
        val explicitTime = singleTimePattern.find(line)
        val duration = durationPattern.find(line)?.let(::durationFrom)
            ?: range?.let { durationFromRange(it, prefixFor(line)) }
        val timeHint = when {
            range != null -> ArrangementTimeHint(
                explicitStartMinute = minute(range.groupValues[1], range.groupValues[2], prefixFor(line)),
                windowLabel = range.value.trim(),
            )

            explicitTime != null -> ArrangementTimeHint(
                explicitStartMinute = minute(explicitTime.groupValues[2], explicitTime.groupValues[3], explicitTime.groupValues[1]),
                windowLabel = explicitTime.value.trim(),
            )

            else -> ArrangementTimeHint(windowLabel = windowFor(line))
        }
        val missing = buildSet {
            if (timeHint.explicitStartMinute == null && timeHint.windowLabel == null) add(ArrangementClarification.TIME)
            if (duration == null) add(ArrangementClarification.DURATION)
        }
        return ArrangementCandidate(
            title = cleanTitle(line),
            category = categoryForAssistant(line),
            durationMinutes = duration,
            timeHint = timeHint,
            needsClarification = missing,
            placementSource = if (timeHint.explicitStartMinute != null) {
                ArrangementPlacementSource.USER_EXPLICIT
            } else {
                ArrangementPlacementSource.UNSCHEDULED
            },
        )
    }

    /**
     * A conjunction is only a split point when both sides independently carry
     * an action. This keeps "数学和英语复习" together while turning
     * "复习英语和写报告" into two real planning candidates.
     */
    private fun splitIndependentActions(line: String): List<String> {
        val matches = conjunctions.findAll(line).toList()
        if (matches.isEmpty()) return listOf(line)
        val pieces = mutableListOf<String>()
        var cursor = 0
        matches.forEach { match ->
            val left = line.substring(cursor, match.range.first).trim()
            val rightRemainder = line.substring(match.range.last + 1).trim()
            if (left.hasActionWord() && rightRemainder.hasActionWord()) {
                if (left.isNotBlank()) pieces += left
                cursor = match.range.last + 1
            }
        }
        val tail = line.substring(cursor).trim()
        if (tail.isNotBlank()) pieces += tail
        return pieces.ifEmpty { listOf(line) }
    }

    private fun String.hasActionWord(): Boolean = actionWords.any { word ->
        contains(word, ignoreCase = word.firstOrNull()?.isLetter() == true)
    }

    private fun intentFor(text: String): ArrangementIntent = when {
        listOf("复盘", "回顾", "总结").any(text::contains) -> ArrangementIntent.REVIEW
        listOf("改期", "重排", "推迟", "腾出").any(text::contains) -> ArrangementIntent.REPLAN
        listOf("今天", "下午", "晚上", "早上", "上午", "中午", "安排").any(text::contains) -> ArrangementIntent.ARRANGE_TODAY
        else -> ArrangementIntent.CAPTURE_TASKS
    }

    private fun durationFrom(match: MatchResult): Int? {
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return if (match.groupValues[2].lowercase() in setOf("小时", "h")) value * 60 else value
    }

    private fun durationFromRange(match: MatchResult, prefix: String): Int? {
        val start = minute(match.groupValues[1], match.groupValues[2], prefix) ?: return null
        val end = minute(match.groupValues[3], match.groupValues[4], prefix) ?: return null
        return (end - start).takeIf { it in 1..720 }
    }

    private fun minute(hourText: String, minuteText: String, prefix: String): Int? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: 0
        if (minute !in 0..59 || hour !in 0..23) return null
        if (prefix in setOf("下午", "晚上") && hour in 1..11) hour += 12
        if (prefix == "中午" && hour in 1..10) hour += 12
        return hour * 60 + minute
    }

    private fun prefixFor(text: String): String = listOf("早上", "上午", "中午", "下午", "晚上")
        .firstOrNull(text::contains)
        .orEmpty()

    private fun windowFor(text: String): String? = when {
        text.contains("早上") || text.contains("上午") -> "上午可安排"
        text.contains("中午") -> "中午可安排"
        text.contains("下午") -> "下午可安排"
        text.contains("晚上") -> "晚上可安排"
        else -> null
    }

    private fun cleanTitle(line: String): String = line
        .replace(durationPattern, "")
        .replace(hourRangePattern, "")
        .replace(singleTimePattern, "")
        .replace(Regex("^(?:今天|帮我安排(?:一下)?|我(?:还)?(?:想|要)|需要|请)"), "")
        .trim(' ', '，', '。', '：', ':')
        .ifBlank { line }

    private fun categoryForAssistant(text: String): TaskCategory = when {
        listOf("课", "复习", "作业", "考试", "背", "论文", "实验").any(text::contains) -> TaskCategory.COURSE
        listOf("会议", "工作", "报告", "邮件", "客户").any(text::contains) -> TaskCategory.OFFICE
        listOf("跑步", "运动", "吃", "家", "休息", "医生").any(text::contains) -> TaskCategory.LEISURE
        else -> TaskCategory.UNSPECIFIED
    }
}
