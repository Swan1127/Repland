package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceDraft
import com.swan1127.repland.domain.model.ProfileEvidenceGenerator
import com.swan1127.repland.domain.model.ProfileEvidenceSource
import com.swan1127.repland.domain.model.ProfileEvidenceValidator
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.ports.ProfileEvidenceRepository
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomProfileEvidenceRepository(
    private val database: ReplandDatabase,
) : ProfileEvidenceRepository {
    private val profileEvidenceDao = database.profileEvidenceDao()
    private val executionLogDao = database.executionLogDao()
    private val taskDao = database.taskDao()

    override fun observe(): Flow<List<ProfileEvidence>> = combine(
        profileEvidenceDao.observeAll(),
        executionLogDao.observeAll(),
        taskDao.observeAll(),
    ) { evidence, logs, tasks ->
        evidence.toDomainEvidence(logs.map(ExecutionLogEntity::toDomain), tasks.map(TaskEntity::toDomain))
    }

    override suspend fun generateFromConfirmedFeedback() = database.withTransaction {
        val tasks = taskDao.getAll().map(TaskEntity::toDomain)
        val logs = executionLogDao.getAll().map(ExecutionLogEntity::toDomain)
        val now = System.currentTimeMillis()
        ProfileEvidenceGenerator.generate(tasks, logs).forEachIndexed { index, draft ->
            profileEvidenceDao.insertIgnore(
                ProfileEvidenceEntity(
                    id = UUID.randomUUID().toString(),
                    scope = draft.scope.name,
                    conclusion = draft.conclusion,
                    sourceLogIds = draft.sourceLogIds.joinToString("|"),
                    sourceFingerprint = draft.fingerprint(),
                    createdAtEpochMillis = now + index,
                    updatedAtEpochMillis = now + index,
                    isUserEdited = false,
                ),
            )
        }
    }

    override suspend fun updateConclusion(id: String, conclusion: String) {
        require(ProfileEvidenceValidator.isValidConclusion(conclusion)) {
            "A profile conclusion must be between 1 and ${ProfileEvidenceValidator.MAX_CONCLUSION_LENGTH} characters."
        }
        val existing = requireNotNull(profileEvidenceDao.getById(id)) { "Profile evidence does not exist." }
        profileEvidenceDao.update(
            existing.copy(
                conclusion = conclusion.trim(),
                updatedAtEpochMillis = maxOf(System.currentTimeMillis(), existing.updatedAtEpochMillis + 1),
                isUserEdited = true,
            ),
        )
    }

    override suspend fun delete(id: String) {
        profileEvidenceDao.delete(id)
    }
}

internal fun List<ProfileEvidenceEntity>.toDomainEvidence(
    logs: List<TaskExecutionLog>,
    tasks: List<Task>,
): List<ProfileEvidence> {
    val logsById = logs.associateBy(TaskExecutionLog::id)
    val tasksById = tasks.associateBy(Task::id)
    return map { evidence ->
        evidence.toDomain(
            sources = evidence.sourceLogIdList().mapNotNull { logId ->
                val log = logsById[logId] ?: return@mapNotNull null
                val task = tasksById[log.taskId]
                ProfileEvidenceSource(
                    executionLogId = log.id,
                    taskId = log.taskId,
                    taskDisplayName = task?.displayName ?: "未知任务",
                    eventType = log.eventType,
                    createdAtEpochMillis = log.createdAtEpochMillis,
                )
            },
        )
    }
}

private fun ProfileEvidenceDraft.fingerprint(): String {
    val canonical = buildString {
        append(scope.name)
        append(':')
        append(conclusion)
        append(':')
        append(sourceLogIds.sorted().joinToString(","))
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
