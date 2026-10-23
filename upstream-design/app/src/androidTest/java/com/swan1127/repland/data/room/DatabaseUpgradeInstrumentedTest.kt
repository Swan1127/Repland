package com.swan1127.repland.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-disk historical SQLite fixtures, upgraded by Room's migration engine and schema validator.
 * No current Room database is created and relabelled with an old user_version. The independent v1
 * tasks DDL is the unchanged base proven by MIGRATION_1_2 through MIGRATION_20_21: only v5, v18,
 * v19 and v20 subsequently add task columns. v20 is constructed by replaying the historical SQL
 * from this base, not by opening v21 or removing its fields. Archived schema JSON is unavailable
 * (exportSchema=false), so these are reconstructed schema fixtures, not historical user databases.
 * Instrumentation runs under the target UID, so each file uses targetContext writable storage,
 * has a UUID name, and is deleted in finally. The application's actual repland.db is never opened.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 10, 23)

    @Test
    fun versionOneUpgradesThroughEveryMigrationWithoutLosingItsTaskOrInventingFeedback() = runBlocking {
        withHistoricalDatabase(1) { database ->
            val snapshot = RoomDataManagementRepository(database).snapshot()
            assertEquals(1, snapshot.tasks.size)
            val task = snapshot.tasks.single()
            assertEquals("legacy-task", task.id)
            assertEquals("旧版课程复习", task.displayName)
            assertEquals("旧版任务内容：复习数据结构", task.description)
            assertEquals(TaskStatus.IN_PROGRESS, task.status)
            assertEquals(90, task.totalDurationMinutes)
            assertEquals(date, task.dueDate)
            assertEquals("已完成树结构", task.completionSummary)
            assertEquals(45, task.actualDurationMinutes)
            assertEquals(50, task.progressPercent)
            assertEquals(2, task.postponeCount)
            assertEquals(1_000L, task.createdAtEpochMillis)
            assertEquals(2_000L, task.updatedAtEpochMillis)
            assertNull(task.completionResult)
            assertNull(task.scheduledForDate)
            assertNull(task.repeatRule)
            assertTrue(task.subtasks.isEmpty())
            // Old task snapshots are preserved without fabricating execution evidence or plans.
            assertTrue(snapshot.executionLogs.isEmpty())
            assertTrue(snapshot.planHistory.isEmpty())
            assertTrue(snapshot.weeklyTimeBlocks.isEmpty())
            assertTrue(snapshot.dateOverrides.isEmpty())
            assertTrue(snapshot.profileEvidence.isEmpty())
            assertFalse(snapshot.aiPreferences.isEnabled)
            assertFalse(snapshot.reminderPreferences.isEnabled)

            // The migrated schema must remain writable through the real repository, not just queryable.
            RoomTaskRepository(database).recordFeedback("legacy-task", TaskFeedback(
                actualDurationMinutes = 60, completedContent = "升级后保存的新反馈",
            ))
            val after = RoomDataManagementRepository(database).snapshot()
            assertEquals(1, after.tasks.size)
            assertEquals(1, after.executionLogs.size)
            assertEquals("legacy-task", after.executionLogs.single().taskId)
            assertEquals(ExecutionLogEventType.FEEDBACK, after.executionLogs.single().eventType)
            assertEquals(60, after.tasks.single().actualDurationMinutes)
            assertEquals(21, database.openHelper.writableDatabase.version)
        }
    }

    @Test
    fun versionTwentyKeepsSubtasksAndEveryExistingRelationshipWhenRepeatRuleIsAdded() = runBlocking {
        withHistoricalDatabase(20) { database ->
            val snapshot = RoomDataManagementRepository(database).snapshot()
            val task = snapshot.tasks.single()
            assertEquals("legacy-task", task.id)
            assertEquals("旧版课程复习", task.displayName)
            assertEquals(TaskStatus.IN_PROGRESS, task.status)
            assertEquals(date.minusDays(1), task.scheduledForDate)
            assertEquals(date, task.dueDate)
            assertEquals("旧版结果保留", task.completionResult)
            assertEquals(legacyChecklist, task.subtasks)
            assertNull("An old task must not start repeating on upgrade", task.repeatRule)

            val course = snapshot.weeklyTimeBlocks.single()
            assertEquals("legacy-course", course.id)
            assertEquals("数据结构与算法", course.title)
            assertEquals("1-12周(单)", course.weekPattern)
            assertEquals("course", course.trackId)
            assertEquals("带教材", course.note)
            assertEquals(480, course.startMinute)
            assertEquals(575, course.endMinute)
            val override = snapshot.dateOverrides.single()
            assertEquals("legacy-override", override.id)
            assertEquals("旧版固定事项", override.title)
            assertEquals("带实验报告", override.note)
            assertEquals(date, override.date)
            assertEquals(LocalDate.of(2026, 8, 31), snapshot.timeConstraintSettings.semesterFirstWeekMonday)
            assertEquals(4_321L, snapshot.timeConstraintSettings.updatedAtEpochMillis)

            val plan = snapshot.planHistory.single()
            assertEquals("legacy-plan", plan.id)
            assertTrue(plan.isCurrent)
            assertEquals(3_000L, plan.createdAtEpochMillis)
            assertEquals(listOf("legacy-task"), plan.orderedTaskIds)
            assertTrue(plan.hasManualTaskOrder)
            val segment = plan.segments.single()
            assertEquals("legacy-segment", segment.id)
            assertEquals(task.id, segment.taskId)
            assertEquals(date, segment.date)
            assertEquals(780, segment.startMinute)
            assertEquals(870, segment.endMinute)
            assertTrue(segment.isLocked)
            assertEquals("focus", segment.trackId)

            val log = snapshot.executionLogs.single()
            assertEquals("legacy-log", log.id)
            assertEquals(task.id, log.taskId)
            assertEquals(ExecutionLogEventType.PARTIAL_COMPLETION, log.eventType)
            assertEquals(TaskStatus.IN_PROGRESS, log.confirmedStatus)
            assertEquals(45, log.feedback.actualDurationMinutes)
            assertEquals(50, log.feedback.progressPercent)
            assertEquals("已完成树结构", log.feedback.completedContent)
            assertEquals(4_000L, log.createdAtEpochMillis)
            val evidence = snapshot.profileEvidence.single()
            assertEquals("legacy-evidence", evidence.id)
            assertEquals("旧版可追溯结论", evidence.conclusion)
            assertTrue(evidence.isUserEdited)
            assertEquals(log.id, evidence.sources.single().executionLogId)
            assertEquals(task.id, evidence.sources.single().taskId)
            assertEquals(task.displayName, evidence.sources.single().taskDisplayName)
            assertTrue(snapshot.reminderPreferences.isEnabled)
            assertFalse(snapshot.aiPreferences.isEnabled)
            assertEquals(EngagementMode.CO_PLANNER, snapshot.engagementMode)
            assertEquals("legacy-usage", snapshot.usageEvents.single().id)
            assertEquals("legacy-task", snapshot.usageEvents.single().subjectId)
            assertEquals(70, database.categoryPreferenceDao().getAll().single().weight)
            assertEquals(21, database.openHelper.writableDatabase.version)
        }
    }

    private suspend fun withHistoricalDatabase(version: Int, verify: suspend (ReplandDatabase) -> Unit) {
        val name = "repland-migration-verification-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        assertFalse(file.exists())
        assertTrue(requireNotNull(file.parentFile).let { it.isDirectory || it.mkdirs() })
        var room: ReplandDatabase? = null
        try {
            val creator = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersionOne(db)
                            migrations.filter { it.endVersion <= version }.forEach { it.migrate(db) }
                            if (version == 20) seedVersionTwenty(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            error("The isolated fixture must be newly created: $oldVersion -> $newVersion")
                        }
                    }).build(),
            )
            try {
                val old = creator.writableDatabase
                assertEquals(version, old.version)
                val oldColumns = columns(old, "tasks")
                assertFalse("The historical fixture cannot already contain the v21 column", "repeatRule" in oldColumns)
                assertEquals(version >= 20, "subtasks" in oldColumns)
                if (version == 1) {
                    assertFalse("completionResult" in oldColumns)
                    assertFalse("scheduledForEpochDay" in oldColumns)
                }
            } finally {
                creator.close()
            }
            assertTrue("The test must exercise a real existing SQLite file", file.isFile && file.length() > 0)
            val upgraded = Room.databaseBuilder(context, ReplandDatabase::class.java, name)
                .addMigrations(*migrations)
                .build()
            room = upgraded
            // Opens the file through Room: its actual migration selection and final schema validation
            // must succeed before the domain-level preservation assertions can run.
            upgraded.openHelper.writableDatabase
            verify(upgraded)
        } finally {
            room?.close()
            context.deleteDatabase(name)
            assertFalse("Only the UUID fixture should be removed after acceptance", file.exists())
        }
    }

    private fun createVersionOne(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE tasks (
                id TEXT NOT NULL PRIMARY KEY,
                description TEXT NOT NULL,
                displayName TEXT NOT NULL,
                category TEXT NOT NULL,
                userPriority TEXT NOT NULL,
                estimatedDays INTEGER NOT NULL,
                totalDurationMinutes INTEGER,
                dueDateEpochDay INTEGER,
                status TEXT NOT NULL,
                completionSummary TEXT,
                actualDurationMinutes INTEGER,
                progressPercent INTEGER,
                postponeCount INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO tasks VALUES (
                'legacy-task', '旧版任务内容：复习数据结构', '旧版课程复习', 'COURSE', 'HIGH', 2,
                90, ?, 'IN_PROGRESS', '已完成树结构', 45, 50, 2, 1000, 2000
            )
        """.trimIndent(), arrayOf(date.toEpochDay()))
    }

    private fun seedVersionTwenty(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE tasks SET completionResult = ?, scheduledForEpochDay = ?, subtasks = ? WHERE id = 'legacy-task'",
            arrayOf<Any>("旧版结果保留", date.minusDays(1).toEpochDay(), SubTaskJson.encode(legacyChecklist)),
        )
        db.execSQL("""
            INSERT INTO weekly_time_blocks
            (id,title,kind,dayOfWeek,startMinute,endMinute,createdAtEpochMillis,updatedAtEpochMillis,weekPattern,trackId,note)
            VALUES ('legacy-course','数据结构与算法','COURSE',1,480,575,1000,2000,'1-12周(单)','course','带教材')
        """.trimIndent())
        db.execSQL("""
            INSERT INTO date_overrides
            (id,title,type,dateEpochDay,startMinute,endMinute,createdAtEpochMillis,updatedAtEpochMillis,note)
            VALUES ('legacy-override','旧版固定事项','BLOCKED',?,600,660,1000,2000,'带实验报告')
        """.trimIndent(), arrayOf(date.toEpochDay()))
        db.execSQL("INSERT INTO semester_settings VALUES ('current', ?, 4321)", arrayOf(LocalDate.of(2026, 8, 31).toEpochDay()))
        db.execSQL("INSERT INTO plans VALUES ('legacy-plan',3000,1)")
        db.execSQL("""
            INSERT INTO plan_segments (id,planId,taskId,dateEpochDay,startMinute,endMinute,isLocked,trackId)
            VALUES ('legacy-segment','legacy-plan','legacy-task',?,780,870,1,'focus')
        """.trimIndent(), arrayOf(date.toEpochDay()))
        db.execSQL("INSERT INTO plan_task_order VALUES ('legacy-plan','legacy-task',0,1)")
        db.execSQL("INSERT INTO category_preferences VALUES ('COURSE',70)")
        db.execSQL("""
            INSERT INTO execution_logs VALUES
            ('legacy-log','legacy-task','PARTIAL_COMPLETION','IN_PROGRESS',45,50,'已完成树结构','旧版结果保留',NULL,NULL,NULL,4000)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO profile_evidence VALUES
            ('legacy-evidence','LEARNING','旧版可追溯结论','legacy-log','legacy-fingerprint',4001,4002,1)
        """.trimIndent())
        db.execSQL("INSERT INTO reminder_settings VALUES ('local-reminders',1)")
        db.execSQL("INSERT INTO ai_settings VALUES ('bounded-ai-advisor',0,NULL)")
        db.execSQL("INSERT INTO engagement_settings VALUES ('primary','CO_PLANNER',5000)")
        db.execSQL("INSERT INTO usage_events VALUES ('legacy-usage','TIMELINE_OPENED',5001,'GUIDED','legacy-task')")
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> = buildSet {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
    }

    private val legacyChecklist = listOf(
        SubTask("legacy-sub-1", "已勾选：树结构", true, 101),
        SubTask("legacy-sub-2", "待完成：图结构", false, 102),
    )

    private val migrations: Array<Migration> = arrayOf(
        ReplandDatabase.MIGRATION_1_2, ReplandDatabase.MIGRATION_2_3,
        ReplandDatabase.MIGRATION_3_4, ReplandDatabase.MIGRATION_4_5,
        ReplandDatabase.MIGRATION_5_6, ReplandDatabase.MIGRATION_6_7,
        ReplandDatabase.MIGRATION_7_8, ReplandDatabase.MIGRATION_8_9,
        ReplandDatabase.MIGRATION_9_10, ReplandDatabase.MIGRATION_10_11,
        ReplandDatabase.MIGRATION_11_12, ReplandDatabase.MIGRATION_12_13,
        ReplandDatabase.MIGRATION_13_14, ReplandDatabase.MIGRATION_14_15,
        ReplandDatabase.MIGRATION_15_16, ReplandDatabase.MIGRATION_16_17,
        ReplandDatabase.MIGRATION_17_18, ReplandDatabase.MIGRATION_18_19,
        ReplandDatabase.MIGRATION_19_20, ReplandDatabase.MIGRATION_20_21,
    )
}
