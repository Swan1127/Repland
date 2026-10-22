package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.LocalPriorityAssessment
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskLifecycleValidator
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimelineEntry
import java.time.LocalDate

enum class PlanAgentId { REQUIREMENTS, AVAILABILITY, HABIT, CALIBRATOR, CONFLICT, COMPOSER }

enum class PlanRequestKind { ADD, REMOVE, FULL_REPLAN, REPAIR, ADJUST, RECOVER, PREVIEW_HABIT }

enum class TaskPlanningState { SCHEDULABLE, NEEDS_DURATION, DEFERRED, LOCKED }

enum class ConflictType {
    OVERLAP,
    WEEK_PATTERN_CLASH,
    DUPLICATE_PLACEMENT,
    CAPACITY_SHORTFALL,
    UNSCHEDULABLE,
    AFTER_DEADLINE,
    BEFORE_INTENDED_DAY,
}
enum class Severity { INFO, WARN, FATAL }
enum class BlockStyle { DEEP_FOCUS, FRAGMENTED, BATCHED }

data class TimeWindow(val startMinute: Int, val endMinute: Int)

data class AiTaskInsight(
    val summary: String? = null,
    val dependencyNote: String? = null,
    val difficulty: Int? = null,
    val suggestedDurationMinutes: Int? = null,
    val source: String = "ai",
)

data class PlanningBrief(
    val orderedTasks: List<LocalPriorityAssessment>,
    val states: Map<String, TaskPlanningState>,
    val openQuestions: List<String>,
    val softGoals: List<String>,
    val aiInsights: Map<String, AiTaskInsight> = emptyMap(),
)

data class PlanningNotes(
    val openQuestions: List<String>,
    val softGoals: List<String>,
)

enum class OccupiedKind { COURSE, REST, COMMITMENT, LOCKED }

data class OccupiedInterval(
    val title: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val kind: OccupiedKind,
    val trackId: String,
    val taskId: String? = null,
)

data class TimeSlot(val date: LocalDate, val startMinute: Int, val endMinute: Int)

data class AvailabilityMap(
    val targetDate: LocalDate,
    val blocked: List<OccupiedInterval>,
    val freeSlots: List<TimeSlot>,
    val capacityMinutes: Int,
)

data class PlacementHint(
    val preferredWindow: TimeWindow?,
    val blockStyle: BlockStyle,
    val rationale: String,
)

data class CategoryHint(
    val preferredWindow: TimeWindow?,
    val blockStyle: BlockStyle,
    val rationale: String,
)

data class PersonalizationHints(
    val perTask: Map<String, PlacementHint>,
    val perCategory: Map<TaskCategory, CategoryHint>,
    val bufferMinutes: Int,
    val profileNotes: List<String>,
)

data class ConflictItem(
    val type: ConflictType,
    val affectedTaskIds: List<String>,
    val startMinute: Int,
    val endMinute: Int,
    val severity: Severity,
    val autoFixable: Boolean,
    val message: String,
)

data class ConflictReport(
    val items: List<ConflictItem>,
    val autoFixableCount: Int = items.count { it.autoFixable },
    val fatalCount: Int = items.count { it.severity == Severity.FATAL },
)

data class ExecutionPulse(
    val asOf: LocalDate,
    val completedTaskIds: Set<String> = emptySet(),
    val partialTaskIds: Set<String> = emptySet(),
    val postponedTaskIds: Set<String> = emptySet(),
    val feedbackCount: Int = 0,
    val durationSamples: Int = 0,
    val lastEventAtMillis: Long? = null,
) {
    val isEmpty: Boolean get() = completedTaskIds.isEmpty() && partialTaskIds.isEmpty() &&
        postponedTaskIds.isEmpty() && feedbackCount == 0

    companion object {
        
        fun from(date: LocalDate, logs: List<TaskExecutionLog>): ExecutionPulse {
            val latestByTask = logs.groupBy(TaskExecutionLog::taskId).mapValues { (_, rows) ->
                rows.maxByOrNull(TaskExecutionLog::createdAtEpochMillis)
            }
            val completed = latestByTask.filterValues { it?.confirmedStatus == TaskStatus.COMPLETED }.keys
            val postponed = latestByTask.filterValues { it?.confirmedStatus == TaskStatus.POSTPONED }.keys
            val partial = latestByTask.filterValues { log ->
                log != null && log.eventType == ExecutionLogEventType.PARTIAL_COMPLETION &&
                    log.confirmedStatus != TaskStatus.COMPLETED
            }.keys
            return ExecutionPulse(
                asOf = date,
                completedTaskIds = completed,
                partialTaskIds = partial - completed,
                postponedTaskIds = postponed,
                feedbackCount = logs.count { TaskLifecycleValidator.hasFeedback(it.feedback) },
                durationSamples = logs.count { it.feedback.actualDurationMinutes != null },
                lastEventAtMillis = logs.maxOfOrNull(TaskExecutionLog::createdAtEpochMillis),
            )
        }
    }
}

enum class CalibrationKind { ACTIVE_SESSION, COMPLETED, PARTIAL, POSTPONED, DAY_RECORD }

data class CalibrationSignal(
    val kind: CalibrationKind,
    val taskId: String?,
    val message: String,
    val minutes: Int = 0,
)

data class CalibrationReport(
    val asOf: LocalDate,
    val connected: Boolean,
    val plannedSegmentsToday: Int,
    val plannedMinutesToday: Int,
    val feedbackCount: Int,
    val signals: List<CalibrationSignal>,
    val notes: List<String>,
)

data class SimulatedLanding(
    val date: LocalDate,
    val entries: List<TimelineEntry>,
)

data class PlanTeamResult(
    val requestKind: PlanRequestKind,
    val selectedAgents: List<PlanAgentId>,
    val brief: PlanningBrief?,
    val availability: AvailabilityMap?,
    val hints: PersonalizationHints?,
    val calibration: CalibrationReport?,
    val report: ConflictReport?,
    val draft: PlanDraft,
    val landing: SimulatedLanding,
    val trace: List<String>,
)
