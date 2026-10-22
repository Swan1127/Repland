package com.swan1127.repland.domain.planteam

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.PlanningConstraintValidator
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.LocalDate
import java.time.LocalDateTime

data class PlanTeamProfileFacts(
    
    val isConfigured: Boolean = true,
    val bufferMinutes: Int = 30,
    val preferMorningForDeepWork: Boolean = true,
    val focusBlockMinutes: Int = 90,
    val notes: List<String> = emptyList(),
    val memory: com.swan1127.repland.domain.model.MemoryReference? = null,
)

data class PlanTeamContext(
    val requestKind: PlanRequestKind,
    val input: PlanGenerationInput,
    val current: ConfirmedPlan? = null,
    val targetDate: LocalDate,
    val calendarBlocks: List<WeeklyTimeBlock> = emptyList(),
    val profileFacts: PlanTeamProfileFacts = PlanTeamProfileFacts(),
    
    val now: LocalDateTime = LocalDateTime.now(),
    
    val runningTaskId: String? = null,
    
    val execution: ExecutionPulse? = null,
) {
    fun protectedSegments(): List<PlannedSegment> {
        val tasksById = input.tasks.associateBy { it.id }
        val today = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val hasAvailability = PlanningConstraintValidator.hasExplicitAvailability(input)
        val currentCommitments = current?.segments.orEmpty().filter { segment ->
            val task = tasksById[segment.taskId]
            task?.status?.isActive == true && (
                !hasAvailability || segment.isLocked || task.userPriority == TaskPriority.REQUIRED ||
                    task.status == TaskStatus.IN_PROGRESS || task.id == runningTaskId ||
                    segment.date == today && segment.startMinute <= minute && segment.endMinute > minute
                )
        }
        return (input.lockedSegments + currentCommitments).filter { segment ->
            tasksById[segment.taskId]?.status?.isActive == true &&
                (segment.date > today || segment.date == today && segment.endMinute > minute)
        }.distinctBy { segment ->
            listOf(segment.taskId, segment.date, segment.startMinute, segment.endMinute, segment.trackId)
        }
    }
}
