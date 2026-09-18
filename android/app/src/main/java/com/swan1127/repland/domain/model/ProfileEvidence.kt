package com.swan1127.repland.domain.model

/**
 * A small, inspectable local conclusion drawn only from user-confirmed execution
 * feedback. It deliberately contains its source log references so a conclusion is
 * never an opaque profile assertion.
 */
data class ProfileEvidence(
    val id: String,
    val scope: ProfileEvidenceScope,
    val conclusion: String,
    val sources: List<ProfileEvidenceSource>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isUserEdited: Boolean,
)

enum class ProfileEvidenceScope {
    GENERAL,
    LEARNING,
}

data class ProfileEvidenceSource(
    val executionLogId: String,
    val taskId: String,
    val taskDisplayName: String,
    val eventType: ExecutionLogEventType,
    val createdAtEpochMillis: Long,
)

internal data class ProfileEvidenceDraft(
    val scope: ProfileEvidenceScope,
    val conclusion: String,
    val sourceLogIds: List<String>,
)

object ProfileEvidenceValidator {
    const val MAX_CONCLUSION_LENGTH = 500

    fun isValidConclusion(conclusion: String): Boolean =
        conclusion.trim().length in 1..MAX_CONCLUSION_LENGTH
}

/**
 * Generates factual evidence summaries, not predictions about a person. Corrections
 * replace their original feedback only for this derived view; both immutable logs
 * remain available in the task history.
 */
internal object ProfileEvidenceGenerator {
    fun generate(
        tasks: List<Task>,
        executionLogs: List<TaskExecutionLog>,
    ): List<ProfileEvidenceDraft> {
        val correctedLogIds = executionLogs.mapNotNull(TaskExecutionLog::correctedLogId).toSet()
        val effectiveLogs = executionLogs
            .asSequence()
            .filter { it.id !in correctedLogIds }
            .filter { it.eventType != ExecutionLogEventType.REPLACEMENT }
            .toList()
        val evidence = mutableListOf<ProfileEvidenceDraft>()

        val feedbackLogs = effectiveLogs.filter { TaskLifecycleValidator.hasFeedback(it.feedback) }
        if (feedbackLogs.isNotEmpty()) {
            val durationLogs = feedbackLogs.filter { it.feedback.actualDurationMinutes != null }
            val progressLogs = feedbackLogs.filter { it.feedback.progressPercent != null }
            val facts = buildList {
                if (durationLogs.isNotEmpty()) {
                    add("其中 ${durationLogs.size} 次实际投入共 ${durationLogs.sumOf { it.feedback.actualDurationMinutes ?: 0 }} 分钟")
                }
                if (progressLogs.isNotEmpty()) {
                    add("覆盖 ${progressLogs.map(TaskExecutionLog::taskId).distinct().size} 个任务的进度")
                }
            }
            evidence += ProfileEvidenceDraft(
                scope = ProfileEvidenceScope.GENERAL,
                conclusion = buildString {
                    append("已确认记录 ${feedbackLogs.size} 条执行反馈")
                    if (facts.isNotEmpty()) append("；${facts.joinToString("；")}")
                    append("；不据此推断效率或能力。")
                },
                sourceLogIds = feedbackLogs.map(TaskExecutionLog::id),
            )
        }

        val tasksById = tasks.associateBy(Task::id)
        val learningLogs = effectiveLogs.filter { log ->
            tasksById[log.taskId]?.category == TaskCategory.COURSE &&
                (!log.feedback.completedContent.isNullOrBlank() || !log.feedback.completionResult.isNullOrBlank())
        }
        if (learningLogs.isNotEmpty()) {
            evidence += ProfileEvidenceDraft(
                scope = ProfileEvidenceScope.LEARNING,
                conclusion = "已确认 ${learningLogs.size} 条课程学习内容或结果反馈；学习结论可逐条回溯到来源执行日志。",
                sourceLogIds = learningLogs.map(TaskExecutionLog::id),
            )
        }
        return evidence
    }
}
