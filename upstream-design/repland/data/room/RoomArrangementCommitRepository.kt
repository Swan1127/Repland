package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskPlacement
import com.swan1127.repland.domain.ports.ArrangementCommitRepository

/** Tasks and their placements become visible together, or the complete batch is rolled back. */
class RoomArrangementCommitRepository internal constructor(
    private val taskDao: TaskDao,
    private val planDao: PlanDao,
    private val timeDao: TimeDao,
    private val transaction: RoomWriteTransaction,
) : ArrangementCommitRepository {
    constructor(database: ReplandDatabase) : this(
        database.taskDao(),
        database.planDao(),
        database.timeDao(),
        RoomWriteTransaction { block -> database.withTransaction { block() } },
    )

    override suspend fun commit(drafts: List<TaskDraft>, placements: List<TaskPlacement>) {
        require(drafts.isNotEmpty()) { "An arrangement needs at least one task." }
        require(drafts.all(TaskDraftValidator::isValid)) { "The arrangement contains an invalid task." }
        require(drafts.all { it.id == null || it.id.isNotBlank() }) { "A task identity cannot be blank." }
        val explicitIds = drafts.mapNotNull(TaskDraft::id)
        require(explicitIds.distinct().size == explicitIds.size) { "Task identities must be unique." }
        placements.forEach(::validatePlacement)
        require(placements.all { it.taskId in explicitIds }) { "Every placement must belong to a task in this batch." }

        transaction.run {
            CurrentPlanConstraintGuard(planDao, timeDao).validateAdditions(placements)
            val now = System.currentTimeMillis()
            drafts.forEach { draft ->
                val existing = draft.id?.let { taskDao.getById(it) }
                taskDao.insert(draft.toTaskEntity(existing, now))
            }
            appendPlacements(planDao, placements)
        }
    }

}
