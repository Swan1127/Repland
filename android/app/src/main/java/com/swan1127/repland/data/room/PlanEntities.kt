package com.swan1127.repland.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlannedSegment
import java.time.LocalDate

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMillis: Long,
    val isCurrent: Boolean,
)

@Entity(
    tableName = "plan_segments",
    indices = [Index(value = ["planId"])],
)
data class PlanSegmentEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val taskId: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val isLocked: Boolean = false,
)

/** The explicit user-reviewed task order that belongs to one immutable plan version. */
@Entity(
    tableName = "plan_task_order",
    primaryKeys = ["planId", "taskId"],
    indices = [Index(value = ["planId"])],
)
data class PlanTaskOrderEntity(
    val planId: String,
    val taskId: String,
    val position: Int,
    val isManual: Boolean = false,
)

data class PlanWithSegments(
    @Embedded val plan: PlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val segments: List<PlanSegmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val taskOrder: List<PlanTaskOrderEntity>,
)

fun PlanWithSegments.toDomain(): ConfirmedPlan = ConfirmedPlan(
    id = plan.id,
    createdAtEpochMillis = plan.createdAtEpochMillis,
    isCurrent = plan.isCurrent,
    segments = segments.map { segment ->
        PlannedSegment(
            id = segment.id,
            taskId = segment.taskId,
            date = LocalDate.ofEpochDay(segment.dateEpochDay),
            startMinute = segment.startMinute,
            endMinute = segment.endMinute,
            isLocked = segment.isLocked,
        )
    }.sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute)),
    orderedTaskIds = taskOrder
        .sortedBy(PlanTaskOrderEntity::position)
        .map(PlanTaskOrderEntity::taskId)
        .ifEmpty { segments.map(PlanSegmentEntity::taskId).distinct() },
    hasManualTaskOrder = taskOrder.any(PlanTaskOrderEntity::isManual),
)
