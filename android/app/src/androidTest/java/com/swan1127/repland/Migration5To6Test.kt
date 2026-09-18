package com.swan1127.repland

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.ReplandDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    @Test
    fun migration_adds_execution_log_storage_without_losing_existing_task_plan_or_constraint_rows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "migration-5-6-test.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion5Schema(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        var migratedDatabase: ReplandDatabase? = null
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO tasks VALUES ('task-1', '', '旧任务', 'COURSE', 'HIGH', 1, NULL, NULL, 'NOT_STARTED', NULL, NULL, NULL, 0, 1, 1)")
            db.execSQL("INSERT INTO plans VALUES ('plan-1', 1, 1)")
            db.execSQL("INSERT INTO plan_segments VALUES ('segment-1', 'plan-1', 'task-1', 20400, 540, 600)")
            db.execSQL("INSERT INTO weekly_time_blocks VALUES ('block-1', '课程', 'COURSE', 1, 540, 600, 1, 1, NULL)")
            db.execSQL("INSERT INTO semester_settings VALUES ('current', 20400)")
            helper.close()

            // Open the v5 file through Room itself so it validates and executes the
            // registered production migration rather than only testing SQL fragments.
            migratedDatabase = Room.databaseBuilder(context, ReplandDatabase::class.java, databaseName)
                .addMigrations(
                    ReplandDatabase.MIGRATION_5_6,
                    ReplandDatabase.MIGRATION_6_7,
                    ReplandDatabase.MIGRATION_7_8,
                    ReplandDatabase.MIGRATION_8_9,
                    ReplandDatabase.MIGRATION_9_10,
                    ReplandDatabase.MIGRATION_10_11,
                    ReplandDatabase.MIGRATION_11_12,
                    ReplandDatabase.MIGRATION_12_13,
                    ReplandDatabase.MIGRATION_13_14,
                )
                .allowMainThreadQueries()
                .build()
            val migrated = migratedDatabase.openHelper.writableDatabase

            migrated.query("SELECT displayName, completionResult FROM tasks WHERE id = 'task-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            migrated.query("SELECT COUNT(*) FROM plans").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM plan_segments").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT isLocked FROM plan_segments WHERE id = 'segment-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM weekly_time_blocks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query(
                "SELECT firstWeekMondayEpochDay, updatedAtEpochMillis FROM semester_settings WHERE id = 'current'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(20400, cursor.getLong(0))
                assertEquals(0, cursor.getLong(1))
            }
            migrated.execSQL("INSERT INTO plan_task_order (planId, taskId, position) VALUES ('plan-1', 'task-1', 0)")
            migrated.query("SELECT COUNT(*) FROM plan_task_order WHERE planId = 'plan-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.execSQL("INSERT INTO category_preferences VALUES ('COURSE', 35)")
            migrated.query("SELECT weight FROM category_preferences WHERE category = 'COURSE'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(35, cursor.getInt(0))
            }
            migrated.query("SELECT isEnabled FROM reminder_settings WHERE id = 'local-reminders'").use { cursor ->
                assertTrue(cursor.moveToFirst().not())
            }
            migrated.query("SELECT COUNT(*) FROM profile_evidence").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT isEnabled, consentedAtEpochMillis FROM ai_settings WHERE id = 'bounded-ai-advisor'").use { cursor ->
                assertTrue(cursor.moveToFirst().not())
            }
            migrated.execSQL("INSERT INTO execution_logs VALUES ('log-1', 'task-1', 'FEEDBACK', 'NOT_STARTED', 30, 20, '笔记', '理解', NULL, NULL, NULL, 2)")
            migrated.query("SELECT COUNT(*) FROM execution_logs WHERE taskId = 'task-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.execSQL(
                "INSERT INTO profile_evidence VALUES ('evidence-1', 'GENERAL', '已确认投入', 'log-1', 'fingerprint-1', 3, 3, 0)",
            )
            migrated.query("SELECT conclusion FROM profile_evidence WHERE id = 'evidence-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("已确认投入", cursor.getString(0))
            }
            migrated.execSQL("INSERT INTO ai_settings VALUES ('bounded-ai-advisor', 0, NULL)")
            migrated.query("SELECT isEnabled FROM ai_settings WHERE id = 'bounded-ai-advisor'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migratedDatabase?.close()
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion5Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, description TEXT NOT NULL, displayName TEXT NOT NULL, category TEXT NOT NULL, userPriority TEXT NOT NULL, estimatedDays INTEGER NOT NULL, totalDurationMinutes INTEGER, dueDateEpochDay INTEGER, status TEXT NOT NULL, completionSummary TEXT, actualDurationMinutes INTEGER, progressPercent INTEGER, postponeCount INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE plans (id TEXT NOT NULL PRIMARY KEY, createdAtEpochMillis INTEGER NOT NULL, isCurrent INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE plan_segments (id TEXT NOT NULL PRIMARY KEY, planId TEXT NOT NULL, taskId TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX index_plan_segments_planId ON plan_segments(planId)")
        db.execSQL("CREATE TABLE weekly_time_blocks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, kind TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, weekPattern TEXT)")
        db.execSQL("CREATE TABLE date_overrides (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE semester_settings (id TEXT NOT NULL PRIMARY KEY, firstWeekMondayEpochDay INTEGER)")
    }
}
