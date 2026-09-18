package com.swan1127.repland.data.export

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceSource
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.WeeklyTimeBlock

/**
 * Dependency-free JSON writer for the local export. The format is intentionally
 * explicit and versioned so users can inspect it without relying on a cloud service.
 */
object LocalDataJsonExporter {
    const val FORMAT = "repland-local-export/v1"

    fun export(snapshot: LocalDataSnapshot): String = jsonObject(
        "format" to jsonString(FORMAT),
        "generatedAtEpochMillis" to snapshot.generatedAtEpochMillis.toString(),
        "tasks" to jsonArray(snapshot.tasks.map(::taskJson)),
        "executionLogs" to jsonArray(snapshot.executionLogs.map(::executionLogJson)),
        "weeklyTimeBlocks" to jsonArray(snapshot.weeklyTimeBlocks.map(::weeklyBlockJson)),
        "dateOverrides" to jsonArray(snapshot.dateOverrides.map(::dateOverrideJson)),
        "timeConstraintSettings" to jsonObject(
            "semesterFirstWeekMonday" to jsonString(snapshot.timeConstraintSettings.semesterFirstWeekMonday?.toString()),
            "updatedAtEpochMillis" to snapshot.timeConstraintSettings.updatedAtEpochMillis.toString(),
        ),
        "planHistory" to jsonArray(snapshot.planHistory.map(::planJson)),
        "categoryPreferences" to jsonObject(
            *snapshot.categoryPreferences
                .toSortedMap(compareBy { it.name })
                .map { (category, weight) -> category.name to weight.toString() }
                .toTypedArray(),
        ),
        "reminderPreferences" to jsonObject(
            "isEnabled" to snapshot.reminderPreferences.isEnabled.toString(),
        ),
        "aiPreferences" to jsonObject(
            "isEnabled" to snapshot.aiPreferences.isEnabled.toString(),
            "consentedAtEpochMillis" to (snapshot.aiPreferences.consentedAtEpochMillis?.toString() ?: "null"),
        ),
        "profileEvidence" to jsonArray(snapshot.profileEvidence.map(::profileEvidenceJson)),
    )

    private fun taskJson(task: Task): String = jsonObject(
        "id" to jsonString(task.id),
        "description" to jsonString(task.description),
        "displayName" to jsonString(task.displayName),
        "category" to jsonString(task.category.name),
        "userPriority" to jsonString(task.userPriority.name),
        "estimatedDays" to task.estimatedDays.toString(),
        "totalDurationMinutes" to jsonNumber(task.totalDurationMinutes),
        "dueDate" to jsonString(task.dueDate?.toString()),
        "status" to jsonString(task.status.name),
        "completionSummary" to jsonString(task.completionSummary),
        "completionResult" to jsonString(task.completionResult),
        "actualDurationMinutes" to jsonNumber(task.actualDurationMinutes),
        "progressPercent" to jsonNumber(task.progressPercent),
        "postponeCount" to task.postponeCount.toString(),
        "createdAtEpochMillis" to task.createdAtEpochMillis.toString(),
        "updatedAtEpochMillis" to task.updatedAtEpochMillis.toString(),
    )

    private fun executionLogJson(log: TaskExecutionLog): String = jsonObject(
        "id" to jsonString(log.id),
        "taskId" to jsonString(log.taskId),
        "eventType" to jsonString(log.eventType.name),
        "confirmedStatus" to jsonString(log.confirmedStatus.name),
        "actualDurationMinutes" to jsonNumber(log.feedback.actualDurationMinutes),
        "progressPercent" to jsonNumber(log.feedback.progressPercent),
        "completedContent" to jsonString(log.feedback.completedContent),
        "completionResult" to jsonString(log.feedback.completionResult),
        "postponeReason" to jsonString(log.feedback.postponeReason),
        "correctedLogId" to jsonString(log.correctedLogId),
        "replacementTaskId" to jsonString(log.replacementTaskId),
        "createdAtEpochMillis" to log.createdAtEpochMillis.toString(),
    )

    private fun weeklyBlockJson(block: WeeklyTimeBlock): String = jsonObject(
        "id" to jsonString(block.id),
        "title" to jsonString(block.title),
        "kind" to jsonString(block.kind.name),
        "dayOfWeek" to block.dayOfWeek.value.toString(),
        "startMinute" to block.startMinute.toString(),
        "endMinute" to block.endMinute.toString(),
        "weekPattern" to jsonString(block.weekPattern),
        "createdAtEpochMillis" to block.createdAtEpochMillis.toString(),
        "updatedAtEpochMillis" to block.updatedAtEpochMillis.toString(),
    )

    private fun dateOverrideJson(override: DateOverride): String = jsonObject(
        "id" to jsonString(override.id),
        "title" to jsonString(override.title),
        "type" to jsonString(override.type.name),
        "date" to jsonString(override.date.toString()),
        "startMinute" to override.startMinute.toString(),
        "endMinute" to override.endMinute.toString(),
        "createdAtEpochMillis" to override.createdAtEpochMillis.toString(),
        "updatedAtEpochMillis" to override.updatedAtEpochMillis.toString(),
    )

    private fun planJson(plan: ConfirmedPlan): String = jsonObject(
        "id" to jsonString(plan.id),
        "createdAtEpochMillis" to plan.createdAtEpochMillis.toString(),
        "isCurrent" to plan.isCurrent.toString(),
        "segments" to jsonArray(plan.segments.map(::segmentJson)),
        "orderedTaskIds" to jsonArray(plan.orderedTaskIds.map(::jsonString)),
        "hasManualTaskOrder" to plan.hasManualTaskOrder.toString(),
    )

    private fun segmentJson(segment: PlannedSegment): String = jsonObject(
        "id" to jsonString(segment.id),
        "taskId" to jsonString(segment.taskId),
        "date" to jsonString(segment.date.toString()),
        "startMinute" to segment.startMinute.toString(),
        "endMinute" to segment.endMinute.toString(),
        "isLocked" to segment.isLocked.toString(),
    )

    private fun profileEvidenceJson(evidence: ProfileEvidence): String = jsonObject(
        "id" to jsonString(evidence.id),
        "scope" to jsonString(evidence.scope.name),
        "conclusion" to jsonString(evidence.conclusion),
        "sources" to jsonArray(evidence.sources.map(::profileSourceJson)),
        "createdAtEpochMillis" to evidence.createdAtEpochMillis.toString(),
        "updatedAtEpochMillis" to evidence.updatedAtEpochMillis.toString(),
        "isUserEdited" to evidence.isUserEdited.toString(),
    )

    private fun profileSourceJson(source: ProfileEvidenceSource): String = jsonObject(
        "executionLogId" to jsonString(source.executionLogId),
        "taskId" to jsonString(source.taskId),
        "taskDisplayName" to jsonString(source.taskDisplayName),
        "eventType" to jsonString(source.eventType.name),
        "createdAtEpochMillis" to source.createdAtEpochMillis.toString(),
    )

    private fun jsonObject(vararg fields: Pair<String, String>): String = buildString {
        append('{')
        fields.forEachIndexed { index, (name, value) ->
            if (index > 0) append(',')
            append(jsonString(name))
            append(':')
            append(value)
        }
        append('}')
    }

    private fun jsonArray(items: List<String>): String = items.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ",",
    )

    private fun jsonNumber(value: Int?): String = value?.toString() ?: "null"

    private fun jsonString(value: String?): String = value?.let { text ->
        buildString {
            append('"')
            text.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }
    } ?: "null"
}
