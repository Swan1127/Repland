package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import com.swan1127.repland.data.room.RoomDataManagementRepository
import com.swan1127.repland.data.room.RoomProfileEvidenceRepository
import com.swan1127.repland.data.room.RoomReminderSettingsRepository
import com.swan1127.repland.data.room.RoomAiSettingsRepository
import com.swan1127.repland.data.room.RoomTaskRepository
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEvidenceAndDataManagementRepositoryTest {
    private lateinit var database: ReplandDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var profileRepository: RoomProfileEvidenceRepository
    private lateinit var dataManagementRepository: RoomDataManagementRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReplandDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskRepository = RoomTaskRepository(database)
        profileRepository = RoomProfileEvidenceRepository(database)
        dataManagementRepository = RoomDataManagementRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun generated_evidence_is_traceable_and_user_correction_or_deletion_never_changes_logs() = runBlocking {
        val taskId = createTask()
        taskRepository.recordFeedback(
            taskId,
            TaskFeedback(actualDurationMinutes = 45, progressPercent = 50, completedContent = "完成练习", completionResult = "理解了重点"),
        )
        val log = taskRepository.observeExecutionLogs(taskId).first().single()

        profileRepository.generateFromConfirmedFeedback()
        val generated = profileRepository.observe().first()
        assertEquals(2, generated.size)
        assertTrue(generated.all { evidence -> evidence.sources.single().executionLogId == log.id })
        assertTrue(generated.all { evidence -> evidence.sources.single().taskId == taskId })

        val edited = generated.first()
        profileRepository.updateConclusion(edited.id, "我修正的可核对结论")
        assertEquals("我修正的可核对结论", profileRepository.observe().first().first { it.id == edited.id }.conclusion)
        assertEquals(1, taskRepository.observeExecutionLogs(taskId).first().size)

        // Rebuilding the same source set must not overwrite a user-owned correction.
        profileRepository.generateFromConfirmedFeedback()
        assertEquals(2, profileRepository.observe().first().size)
        assertEquals("我修正的可核对结论", profileRepository.observe().first().first { it.id == edited.id }.conclusion)

        profileRepository.delete(edited.id)
        assertEquals(1, profileRepository.observe().first().size)
        assertEquals(1, taskRepository.observeExecutionLogs(taskId).first().size)
    }

    @Test
    fun export_snapshot_is_complete_and_explicit_clear_removes_local_rows_only() = runBlocking {
        val taskId = createTask()
        taskRepository.recordFeedback(taskId, TaskFeedback(actualDurationMinutes = 30, progressPercent = 20))
        profileRepository.generateFromConfirmedFeedback()
        RoomReminderSettingsRepository(database.reminderSettingsDao()).setEnabled(true)
        RoomAiSettingsRepository(database.aiSettingsDao()).grantConsentAndEnable()

        val snapshot = dataManagementRepository.snapshot()
        assertEquals(listOf(taskId), snapshot.tasks.map { it.id })
        assertEquals(1, snapshot.executionLogs.size)
        assertFalse(snapshot.profileEvidence.isEmpty())
        assertTrue(snapshot.reminderPreferences.isEnabled)
        assertTrue(snapshot.aiPreferences.isEnabled)
        assertTrue(snapshot.aiPreferences.hasExplicitConsent)

        dataManagementRepository.clearAllLocalData()

        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.executionLogDao().getAll().isEmpty())
        assertTrue(database.profileEvidenceDao().getAll().isEmpty())
        assertFalse(RoomReminderSettingsRepository(database.reminderSettingsDao()).observe().first().isEnabled)
        assertFalse(RoomAiSettingsRepository(database.aiSettingsDao()).observe().first().isEnabled)
        assertFalse(RoomAiSettingsRepository(database.aiSettingsDao()).observe().first().hasExplicitConsent)
    }

    private suspend fun createTask(): String {
        taskRepository.save(
            TaskDraft(
                displayName = "课程复习",
                description = "复习课程",
                category = TaskCategory.COURSE,
                userPriority = TaskPriority.HIGH,
                estimatedDays = 1,
                totalDurationMinutes = 60,
                dueDate = LocalDate.now().plusDays(1),
            ),
        )
        return database.taskDao().getAll().single().id
    }
}
