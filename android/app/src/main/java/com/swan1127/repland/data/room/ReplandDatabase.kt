package com.swan1127.repland.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        WeeklyTimeBlockEntity::class,
        DateOverrideEntity::class,
        SemesterSettingsEntity::class,
        PlanEntity::class,
        PlanSegmentEntity::class,
        PlanTaskOrderEntity::class,
        CategoryPreferenceEntity::class,
        ExecutionLogEntity::class,
        ReminderSettingsEntity::class,
        ProfileEvidenceEntity::class,
        AiSettingsEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class ReplandDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun timeDao(): TimeDao

    abstract fun planDao(): PlanDao

    abstract fun categoryPreferenceDao(): CategoryPreferenceDao

    abstract fun executionLogDao(): ExecutionLogDao

    abstract fun reminderSettingsDao(): ReminderSettingsDao

    abstract fun profileEvidenceDao(): ProfileEvidenceDao

    abstract fun aiSettingsDao(): AiSettingsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekly_time_blocks (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS date_overrides (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekly_time_blocks ADD COLUMN weekPattern TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plans (
                        id TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        isCurrent INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_segments (
                        id TEXT NOT NULL,
                        planId TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_segments_planId ON plan_segments(planId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS semester_settings (
                        id TEXT NOT NULL,
                        firstWeekMondayEpochDay INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds immutable execution evidence without rewriting tasks, plans, or time
         * constraints already stored on-device. Existing task feedback stays intact
         * in the tasks table and new feedback is appended to execution_logs.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completionResult TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS execution_logs (
                        id TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        confirmedStatus TEXT NOT NULL,
                        actualDurationMinutes INTEGER,
                        progressPercent INTEGER,
                        completedContent TEXT,
                        completionResult TEXT,
                        postponeReason TEXT,
                        correctedLogId TEXT,
                        replacementTaskId TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_execution_logs_taskId_createdAtEpochMillis
                    ON execution_logs(taskId, createdAtEpochMillis)
                    """.trimIndent(),
                )
            }
        }

        /** Persists user-owned locks on confirmed plan segments without rewriting plans. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plan_segments ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Stores the user's reviewed task order with each plan version. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_task_order (
                        planId TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(planId, taskId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_plan_task_order_planId ON plan_task_order(planId)",
                )
            }
        }

        /** Marks whether a persisted task order was explicitly arranged by the user. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plan_task_order ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Persists local category preferences used by the deterministic fallback ranker. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_preferences (
                        category TEXT NOT NULL,
                        weight INTEGER NOT NULL,
                        PRIMARY KEY(category)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Adds a durable revision used for all user-confirmed time-constraint changes. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE semester_settings ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Adds an opt-in local reminder setting; existing users remain opted out. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_settings (
                        id TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Stores inspectable local profile evidence without touching existing user data. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profile_evidence (
                        id TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        conclusion TEXT NOT NULL,
                        sourceLogIds TEXT NOT NULL,
                        sourceFingerprint TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        isUserEdited INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_profile_evidence_sourceFingerprint
                    ON profile_evidence(sourceFingerprint)
                    """.trimIndent(),
                )
            }
        }

        /** Adds opt-in consent state only; existing users remain offline and AI-disabled. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_settings (
                        id TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 0,
                        consentedAtEpochMillis INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
