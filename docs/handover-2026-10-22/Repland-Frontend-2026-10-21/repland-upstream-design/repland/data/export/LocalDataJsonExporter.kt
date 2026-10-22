package com.swan1127.repland.data.export

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.LearningMemoryPointSnapshot
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.MemoryState
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceSource
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.ToolOutputRecord
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
        // Its own array, not a field on each evidence row: the row is stored and this is not, and
        // the file would otherwise assert a link the database does not hold. A reader joins on
        // `taskId`, which is also why no task name is written here — it is already in `tasks`.
        "toolOutputs" to jsonArray(snapshot.toolOutputs.map(::toolOutputJson)),
        "engagementMode" to jsonString(snapshot.engagementMode.name),
        "usageEvents" to jsonArray(snapshot.usageEvents.map { event ->
            jsonObject(
                "id" to jsonString(event.id),
                "type" to jsonString(event.type.name),
                "occurredAtEpochMillis" to event.occurredAtEpochMillis.toString(),
                "mode" to jsonString(event.mode.name),
                "subjectId" to jsonString(event.subjectId),
            )
        }),
        *snapshot.learningMemory?.let { arrayOf("learningMemory" to learningMemoryJson(it)) }.orEmpty(),
    )

    private fun learningMemoryJson(snapshot: LearningMemorySnapshot): String = jsonObject(
        "version" to snapshot.version.toString(),
        "referenceEnabled" to snapshot.referenceEnabled.toString(),
        "gain" to jsonFiniteNumber(snapshot.gain),
        "points" to jsonArray(snapshot.points.map(::learningMemoryPointJson)),
    )

    private fun learningMemoryPointJson(point: LearningMemoryPointSnapshot): String = jsonObject(
        "id" to jsonString(point.id),
        "taskId" to jsonString(point.taskId),
        "taskTitle" to jsonString(point.taskTitle),
        "content" to jsonString(point.content),
        "createdOn" to jsonString(point.createdOn.toString()),
        "stage" to point.stage.toString(),
        "memory" to (point.memory?.let(::memoryStateJson) ?: "null"),
    )

    private fun memoryStateJson(state: MemoryState): String = jsonObject(
        "capturedOn" to jsonString(state.capturedOn.toString()),
        "lastEventOn" to jsonString(state.lastEventOn?.toString()),
        "stabilityDays" to jsonFiniteNumber(state.stabilityDays),
        "difficulty" to jsonFiniteNumber(state.difficulty),
        "difficultySeed" to jsonFiniteNumber(state.difficultySeed),
        "importance" to state.importance.toString(),
        "reviews" to state.reviews.toString(),
        "lapses" to state.lapses.toString(),
        "exposures" to state.exposures.toString(),
    )

    private fun jsonFiniteNumber(value: Double): String {
        require(value.isFinite()) { "Learning memory contains a non-finite number." }
        return value.toString()
    }

    private fun taskJson(task: Task): String = jsonObject(
        "id" to jsonString(task.id),
        "description" to jsonString(task.description),
        "displayName" to jsonString(task.displayName),
        "category" to jsonString(task.category.name),
        "userPriority" to jsonString(task.userPriority.name),
        "estimatedDays" to task.estimatedDays.toString(),
        "totalDurationMinutes" to jsonNumber(task.totalDurationMinutes),
        "scheduledForDate" to jsonString(task.scheduledForDate?.toString()),
        "dueDate" to jsonString(task.dueDate?.toString()),
        // The rule, not the next occurrence: the occurrence is already in the two fields above,
        // so exporting it again would be the same fact stated twice — and the two could drift.
        "repeatRule" to jsonString(task.repeatRule?.name),
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
        "trackId" to jsonString(block.trackId),
        "note" to jsonString(block.note),
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
        "note" to jsonString(override.note),
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
        "trackId" to jsonString(segment.trackId),
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

    private fun toolOutputJson(output: ToolOutputRecord): String = jsonObject(
        "kind" to jsonString(output.kind.name),
        "taskId" to jsonString(output.taskId),
        "excerpt" to jsonString(output.excerpt),
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
