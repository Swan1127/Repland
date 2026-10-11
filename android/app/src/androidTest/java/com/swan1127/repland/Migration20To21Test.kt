package com.swan1127.repland

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class Migration20To21Test {
    @Test fun legacy_defaults_and_related_user_data_survive_upgrade_without_becoming_confirmed_evidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "legacy20-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        try {
            val tasks = RoomTaskRepository(db)
            tasks.save(TaskDraft("old", "旧任务", "旧说明", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 45, null))
            tasks.recordFeedback("old", TaskFeedback(actualDurationMinutes = 15, completedContent = "旧反馈"))
            val plans = RoomPlanRepository(db)
            plans.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment(taskId = "old",
                date = LocalDate.now().plusDays(1), startMinute = 600, endMinute = 630)), emptyList(), emptyList(), listOf("old")))
            val originalPlan = plans.observeCurrentPlan().first()
            val originalLogs = tasks.observeExecutionLogs("old").first()
            val time=RoomTimeRepository(db.timeDao())
            time.saveWeeklyBlock(WeeklyTimeBlockDraft(title="保留课程",kind=TimeBlockKind.COURSE,
                dayOfWeek=java.time.DayOfWeek.MONDAY,startMinute=1080,endMinute=1140))
            time.saveDateOverride(DateOverrideDraft(title="保留固定事项",type=DateOverrideType.BLOCKED,
                date=LocalDate.now().plusDays(3),startMinute=1200,endMinute=1260))
            time.saveSemesterFirstWeekMonday(LocalDate.of(2026,9,28))
            val originalCourses=time.observeWeeklyBlocks().first()
            val originalOverrides=time.observeDateOverrides().first()
            val originalTimeSettings=time.observeTimeConstraintSettings().first()
            RoomReminderSettingsRepository(db.reminderSettingsDao()).setEnabled(true)
            RoomAiSettingsRepository(db.aiSettingsDao()).grantConsentAndEnable()
            val originalReminders=db.reminderSettingsDao().get()
            val originalAi=db.aiSettingsDao().get()
            val originalWorkspace=PlanningWorkspaceEntity("migration-retained-draft","{\"version\":1,\"text\":\"保留草案\"}")
            db.planningWorkspaceDao().put(originalWorkspace)
            db.openHelper.writableDatabase.execSQL("INSERT INTO category_preferences (category, weight) VALUES ('COURSE', 70)")
            db.close()
            // Construct the actual v20 task shape in a dedicated fixture file.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.beginTransaction()
                try {
                    old.execSQL("""CREATE TABLE legacy_tasks (
                        id TEXT NOT NULL PRIMARY KEY, description TEXT NOT NULL, displayName TEXT NOT NULL,
                        category TEXT NOT NULL, userPriority TEXT NOT NULL, estimatedDays INTEGER NOT NULL,
                        totalDurationMinutes INTEGER, dueDateEpochDay INTEGER, status TEXT NOT NULL,
                        completionSummary TEXT, actualDurationMinutes INTEGER, progressPercent INTEGER,
                        postponeCount INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL, completionResult TEXT, scheduledForEpochDay INTEGER)""")
                    old.execSQL("""INSERT INTO legacy_tasks SELECT id, description, displayName, category, userPriority,
                        estimatedDays, totalDurationMinutes, dueDateEpochDay, status, completionSummary,
                        actualDurationMinutes, progressPercent, postponeCount, createdAtEpochMillis,
                        updatedAtEpochMillis, completionResult, scheduledForEpochDay FROM tasks""")
                    old.execSQL("DROP TABLE tasks")
                    old.execSQL("ALTER TABLE legacy_tasks RENAME TO tasks")
                    old.version = 20
                    old.setTransactionSuccessful()
                } finally { old.endTransaction() }
            }
            db = Room.databaseBuilder(context, ReplandDatabase::class.java, name)
                .addMigrations(ReplandDatabase.MIGRATION_20_21).build()
            val migrated = RoomTaskRepository(db).observeTasks().first().single()
            assertEquals("旧任务", migrated.displayName)
            assertEquals(TaskCategory.COURSE, migrated.category)
            assertEquals(TaskPriority.MEDIUM, migrated.userPriority)
            assertEquals(1, migrated.estimatedDays)
            assertEquals(45, migrated.totalDurationMinutes)
            assertEquals(TaskInputSources.legacy, migrated.inputSources)
            assertEquals(originalPlan, RoomPlanRepository(db).observeCurrentPlan().first())
            assertEquals(originalLogs, RoomTaskRepository(db).observeExecutionLogs("old").first())
            val migratedTime=RoomTimeRepository(db.timeDao())
            assertEquals(originalCourses,migratedTime.observeWeeklyBlocks().first())
            assertEquals(originalOverrides,migratedTime.observeDateOverrides().first())
            assertEquals(originalTimeSettings,migratedTime.observeTimeConstraintSettings().first())
            assertEquals(originalReminders,db.reminderSettingsDao().get())
            assertEquals(originalAi,db.aiSettingsDao().get())
            assertEquals(originalWorkspace,db.planningWorkspaceDao().get(originalWorkspace.key))
            assertEquals(70, RoomCategoryPreferenceRepository(db.categoryPreferenceDao()).observe().first()[TaskCategory.COURSE])
            val profile=RoomNumericProfileRepository(db).refresh()
            assertTrue(profile.parameters.all { it.value==null && it.count==0 })
            assertTrue(TaskCategory.COURSE in profile.explicitCategories)
            assertTrue(profile.sources.none { it.included })
            RoomTaskRepository(db).updateExisting(TaskDraft("old", "改标题", "旧说明", migrated.category,
                migrated.userPriority, migrated.estimatedDays, migrated.totalDurationMinutes, null))
            assertEquals(TaskInputSources.legacy, RoomTaskRepository(db).observeTasks().first().single().inputSources)
            val capture = TaskCaptureDraft(text = "新未知事项")
            RoomTaskRepository(db).save(capture.toTaskDraft())
            assertNull(RoomTaskRepository(db).observeTasks().first().single { it.id == capture.id }.estimatedDays)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
