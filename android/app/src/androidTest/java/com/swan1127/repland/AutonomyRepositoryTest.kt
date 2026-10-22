package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Real Room transactions: one utterance, immutable evidence, durable and guarded whole-operation undo. */
@RunWith(AndroidJUnit4::class)
class AutonomyRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun memory() = Room.inMemoryDatabaseBuilder(context, ReplandDatabase::class.java).build()
    private val day get() = LocalDate.now().plusDays(1)
    private suspend fun availability(db: ReplandDatabase) {
        RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "学习时间", type = DateOverrideType.AVAILABLE,
            date = day, startMinute = 480, endMinute = 1440))
    }
    private fun candidates(text: String) = ArrangementAssistantInterpreter.interpret(text).candidates
    private suspend fun capture(repo: RoomAutonomyRepository, text: String, id: String = UUID.randomUUID().toString()) =
        repo.capture(candidates(text), day, true, id, originalText = text)
    private fun command(name: String, change: AutonomousTaskChange) = AutonomousStatusCommand(
        AutonomousTaskTarget.Named(name), change, "$name 已明确更新")

    @Test fun capture_estimates_unknown_duration_without_inventing_actual_work_and_undo_survives_reopen() = runBlocking {
        val name = "autonomy-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "明天复习物理")
            val task = db.taskDao().getAll().single().toDomain()
            assertEquals("复习物理", task.displayName)
            assertEquals(30, task.totalDurationMinutes)
            assertEquals(TaskInputSource.PLANNING_ESTIMATE, task.inputSources.duration)
            assertEquals(TaskInputSource.USER_INPUT, task.inputSources.scheduledDate)
            assertNull(task.actualDurationMinutes)
            assertTrue(db.executionLogDao().getAll().isEmpty())
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.any { it.taskId == task.id })
            db.close()
            db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
            val reopened = RoomAutonomyRepository(db)
            assertTrue(reopened.observe().first().canUndo)
            reopened.undo()
            assertTrue(db.taskDao().getAll().isEmpty())
            assertNull(db.planDao().getCurrentPlanWithSegments())
            assertFalse(reopened.observe().first().canUndo)
            assertNull(reopened.reconcile())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun explicit_duration_is_user_input_but_remote_guess_remains_estimate() = runBlocking {
        val db = memory()
        try {
            val repo = RoomAutonomyRepository(db)
            capture(repo, "明天复习物理45分钟")
            assertEquals(TaskInputSource.USER_INPUT, db.taskDao().getAll().single().toDomain().inputSources.duration)
            repo.capture(candidates("整理笔记60分钟"), day, false, "remote", originalText = "整理笔记")
            val guessed = db.taskDao().getAll().single { it.displayName == "整理笔记" }.toDomain()
            assertEquals(TaskInputSource.PLANNING_ESTIMATE, guessed.inputSources.duration)
        } finally { db.close() }
    }

    @Test fun repeated_request_does_not_create_duplicate_tasks_or_plan_versions() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            val first = capture(repo, "复习物理30分钟", "same-request")
            val planCount = db.planDao().getAllPlansWithSegments().size
            assertEquals(first, capture(repo, "复习物理30分钟", "same-request"))
            assertEquals(1, db.taskDao().getAll().size)
            assertEquals(planCount, db.planDao().getAllPlansWithSegments().size)
            assertNull(repo.reconcile())
        } finally { db.close() }
    }

    @Test fun completion_updates_task_and_future_plan_together_and_undo_appends_correction() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理60分钟")
            val original = db.taskDao().getAll().single()
            val beforePlan = db.planDao().getCurrentPlanWithSegments()!!.toDomain()
            repo.updateStatus(listOf(command(original.displayName, AutonomousTaskChange.Complete)), "done")
            assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById(original.id)!!.status)
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.none { it.taskId == original.id })
            val originalLog = db.executionLogDao().getAll().single()
            assertNull(originalLog.actualDurationMinutes)
            repo.undo()
            assertEquals(original, db.taskDao().getById(original.id))
            val logs = db.executionLogDao().getAll()
            assertEquals(2, logs.size)
            assertEquals(originalLog, logs.first())
            assertEquals(ExecutionLogEventType.CORRECTION.name, logs.last().eventType)
            assertEquals(originalLog.id, logs.last().correctedLogId)
            assertNull(logs.last().actualDurationMinutes)
            val restored = db.planDao().getCurrentPlanWithSegments()!!.toDomain()
            assertEquals(beforePlan.segments.map { it.taskId to it.startMinute }, restored.segments.map { it.taskId to it.startMinute })
        } finally { db.close() }
    }

    @Test fun partial_progress_is_an_explicit_fact_and_never_fabricates_actual_minutes() = runBlocking {
        val db = memory()
        try {
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理60分钟")
            val task = db.taskDao().getAll().single()
            repo.updateStatus(listOf(command(task.displayName, AutonomousTaskChange.Progress(50))), "half")
            val actual = db.taskDao().getById(task.id)!!
            assertEquals(TaskStatus.IN_PROGRESS.name, actual.status)
            assertEquals(50, actual.progressPercent)
            assertNull(actual.actualDurationMinutes)
            assertNull(db.executionLogDao().getAll().single().actualDurationMinutes)
        } finally { db.close() }
    }

    @Test fun undo_cannot_restore_a_placement_into_a_new_hard_time_block() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理60分钟")
            val original = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(
                title = "临时会议", type = DateOverrideType.BLOCKED, date = day,
                startMinute = original.startMinute, endMinute = original.endMinute,
            ))
            repo.reconcile()
            val repaired = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            assertTrue(repaired.endMinute <= original.startMinute || repaired.startMinute >= original.endMinute)
            assertUndoBlockedWithoutWrites(db, repo, "冲突")
        } finally { db.close() }
    }

    @Test fun undo_cannot_restore_a_placement_outside_updated_availability() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理60分钟")
            val original = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            val available = db.timeDao().getAllDateOverrides().single()
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(
                id = available.id, title = "调整后的学习时间", type = DateOverrideType.AVAILABLE,
                date = day, startMinute = original.endMinute, endMinute = 1440,
            ))
            repo.reconcile()
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.single().startMinute >= original.endMinute)
            assertUndoBlockedWithoutWrites(db, repo, "可用时间")
        } finally { db.close() }
    }

    @Test fun rejected_undo_rolls_back_task_restoration_and_appended_corrections_together() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理60分钟")
            val task = db.taskDao().getAll().single()
            val original = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(
                title = "临时会议", type = DateOverrideType.BLOCKED, date = day,
                startMinute = original.startMinute, endMinute = original.endMinute,
            ))
            // A command can reach the repository before the debounced automatic repair.
            repo.updateStatus(listOf(command(task.displayName, AutonomousTaskChange.Complete)), "done-before-repair")
            assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById(task.id)!!.status)
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.isEmpty())
            assertEquals(1, db.executionLogDao().getAll().size)
            assertUndoBlockedWithoutWrites(db, repo, "冲突")
            assertEquals(TaskStatus.COMPLETED.name, db.taskDao().getById(task.id)!!.status)
            assertEquals(ExecutionLogEventType.STATUS_CHANGE.name, db.executionLogDao().getAll().single().eventType)
        } finally { db.close() }
    }

    private suspend fun assertUndoBlockedWithoutWrites(db: ReplandDatabase, repo: RoomAutonomyRepository, reason: String) {
        suspend fun persistedState(): List<Any?> = listOf(
            db.taskDao().getAll(), db.planDao().getAllPlansWithSegments(), db.executionLogDao().getAll(),
            db.timeDao().getAllWeeklyBlocks(), db.timeDao().getAllDateOverrides(), db.timeDao().getSemesterSettings(),
            db.planningWorkspaceDao().getAutonomyExport(),
            listOf("order", "order-undo", "draft").map { db.planningWorkspaceDao().get(it) },
        )
        val before = persistedState()
        val delegation = repo.observe().first()
        assertTrue(delegation.canUndo)
        repeat(2) {
            val failure = runCatching { repo.undo() }.exceptionOrNull()
            assertNotNull("Unsafe undo must fail explicitly", failure)
            assertTrue(failure!!.message.orEmpty().contains("本次未撤销"))
            assertTrue(failure.message.orEmpty().contains(reason))
            assertEquals("Rejected undo must leave all task, plan, time and journal data unchanged", before, persistedState())
            assertEquals(delegation, repo.observe().first())
        }
        assertNull(repo.reconcile())
        assertEquals(before, persistedState())
    }

    @Test fun ambiguous_status_batch_rolls_back_all_tasks_and_logs() = runBlocking {
        val db = memory()
        try {
            val repo = RoomAutonomyRepository(db)
            val tasks = RoomTaskRepository(db)
            tasks.save(TaskDraft(id = "a", displayName = "物理")); tasks.save(TaskDraft(id = "b", displayName = "笔记"))
            tasks.save(TaskDraft(id = "c", displayName = "笔记"))
            val before = db.taskDao().getAll()
            assertTrue(runCatching { repo.updateStatus(listOf(command("物理", AutonomousTaskChange.Complete),
                command("笔记", AutonomousTaskChange.Complete)), "batch") }.isFailure)
            assertEquals(before, db.taskDao().getAll())
            assertTrue(db.executionLogDao().getAll().isEmpty())
            assertFalse(repo.observe().first().canUndo)
        } finally { db.close() }
    }

    @Test fun stale_undo_preserves_later_manual_task_edits() = runBlocking {
        val db = memory()
        try {
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理")
            val task = db.taskDao().getAll().single()
            RoomTaskRepository(db).updateExisting(TaskDraft(id = task.id, displayName = "物理新名称", totalDurationMinutes = 90))
            assertTrue(runCatching { repo.undo() }.isFailure)
            assertEquals("物理新名称", db.taskDao().getById(task.id)!!.displayName)
            assertEquals(90, db.taskDao().getById(task.id)!!.totalDurationMinutes)
        } finally { db.close() }
    }

    @Test fun paused_automatic_repair_is_read_only_but_explicit_arrange_works() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            repo.setEnabled(false)
            capture(repo, "复习物理30分钟")
            assertNull(db.planDao().getCurrentPlanWithSegments())
            assertNull(repo.reconcile())
            assertNull(db.planDao().getCurrentPlanWithSegments())
            repo.arrange(day, "arrange")
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.isNotEmpty())
            assertFalse(repo.observe().first().enabled)
        } finally { db.close() }
    }

    @Test fun fixed_conflict_keeps_task_update_and_preserves_plan_instead_of_partial_commit() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val tasks = RoomTaskRepository(db); val plans = RoomPlanRepository(db)
            tasks.save(TaskDraft(id = "fixed", displayName = "固定任务", userPriority = TaskPriority.REQUIRED, totalDurationMinutes = 30))
            plans.placeTask("fixed", day, 600, 630, "focus")
            val originalPlan = db.planDao().getCurrentPlanWithSegments()!!.toDomain()
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "新增课程", type = DateOverrideType.BLOCKED,
                date = day, startMinute = 600, endMinute = 630))
            val repo = RoomAutonomyRepository(db)
            val receipt = capture(repo, "整理笔记30分钟")
            assertEquals(2, db.taskDao().getAll().size)
            assertTrue(receipt.contains("冲突"))
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.toDomain().segments.any {
                it.taskId == "fixed" && it.startMinute == originalPlan.segments.single().startMinute
            })
        } finally { db.close() }
    }

    @Test fun an_existing_preview_is_preserved_while_authorized_capture_is_arranged() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val draft = PlanDraft(LocalDateTime.now(), emptyList(), emptyList(), emptyList(), sourceRevision = "old-input")
            RoomPlanRepository(db).saveDraft(draft)
            capture(RoomAutonomyRepository(db), "复习物理30分钟")
            assertEquals(draft, RoomPlanRepository(db).observeDraft().first())
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.isNotEmpty())
        } finally { db.close() }
    }

    @Test fun tonight_never_silently_moves_to_the_next_morning() = runBlocking {
        val db = memory()
        try {
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "仅上午可用", type = DateOverrideType.AVAILABLE,
                date = day, startMinute = 480, endMinute = 720))
            val repo = RoomAutonomyRepository(db)
            val receipt = capture(repo, "今晚复习物理30分钟")
            assertTrue(db.planDao().getCurrentPlanWithSegments()?.segments.orEmpty().isEmpty())
            assertTrue(receipt.contains("未能按指定时刻或时段安排"))
            assertEquals(1, db.taskDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun database_failure_in_a_status_batch_rolls_back_tasks_logs_and_receipt() = runBlocking {
        val db = memory()
        try {
            val tasks = RoomTaskRepository(db); val repo = RoomAutonomyRepository(db)
            tasks.save(TaskDraft(id = "a", displayName = "物理")); tasks.save(TaskDraft(id = "b", displayName = "笔记"))
            val before = db.taskDao().getAll()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_autonomy_second BEFORE UPDATE ON tasks WHEN NEW.id = 'b' BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
            assertTrue(runCatching { repo.updateStatus(listOf(command("物理", AutonomousTaskChange.Complete),
                command("笔记", AutonomousTaskChange.Complete)), "fail-batch") }.isFailure)
            assertEquals(before, db.taskDao().getAll())
            assertTrue(db.executionLogDao().getAll().isEmpty())
            assertFalse(repo.observe().first().canUndo)
            assertNull(repo.observe().first().summary)
        } finally { db.close() }
    }

    @Test fun paused_completion_removes_only_closed_future_work_without_scheduling_other_tasks() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理30分钟")
            repo.setEnabled(false)
            capture(repo, "整理笔记30分钟")
            val notes = db.taskDao().getAll().single { it.displayName == "整理笔记" }
            repo.updateStatus(listOf(command("复习物理", AutonomousTaskChange.Complete)), "paused-done")
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.isEmpty())
            assertEquals(TaskStatus.NOT_STARTED.name, db.taskDao().getById(notes.id)!!.status)
            assertFalse(repo.observe().first().enabled)
        } finally { db.close() }
    }

    @Test fun active_focus_requires_ending_the_session_before_natural_language_status_changes() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理30分钟")
            val slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single().id
            val sessions = RoomExecutionSessionRepository(db)
            sessions.start(slot)
            val taskBefore = db.taskDao().getAll().single()
            val logsBefore = db.executionLogDao().getAll()
            assertTrue(runCatching { repo.updateStatus(listOf(command("复习物理", AutonomousTaskChange.Complete)), "running-done") }.isFailure)
            assertEquals(taskBefore, db.taskDao().getAll().single())
            assertEquals(logsBefore, db.executionLogDao().getAll())
            assertNotNull(sessions.observeActive().first())
            repo.updateStatus(listOf(command("复习物理", AutonomousTaskChange.Progress(50))), "running-half")
            assertEquals(50, db.taskDao().getAll().single().progressPercent)
            assertNull(db.taskDao().getAll().single().actualDurationMinutes)
            assertNotNull(sessions.observeActive().first())
        } finally { db.close() }
    }

    @Test fun explicit_arrange_tomorrow_does_not_fill_today_and_tonight_respects_its_window() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            repo.setEnabled(false)
            RoomTaskRepository(db).save(TaskDraft(id = "free", displayName = "复习物理", totalDurationMinutes = 30))
            repo.arrange(day, "tonight", startMinute = 1080, endMinute = 1440)
            val placed = db.planDao().getCurrentPlanWithSegments()!!.toDomain().segments.single()
            assertEquals(day, placed.date)
            assertTrue(placed.startMinute >= 1080)
            assertTrue(placed.endMinute <= 1440)
        } finally { db.close() }
    }
    @Test fun later_manual_move_and_date_edit_override_the_original_capture_window() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "明天16点复习物理30分钟")
            val plans = RoomPlanRepository(db)
            val slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            plans.movePlacement(slot.id, 1020, 1050, "focus")
            repo.reconcile()
            assertEquals(1020, db.planDao().getCurrentPlanWithSegments()!!.segments.single().startMinute)
            val nextDay = day.plusDays(1)
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "后天可用", type = DateOverrideType.AVAILABLE,
                date = nextDay, startMinute = 900, endMinute = 1080))
            val task = db.taskDao().getAll().single()
            RoomTaskRepository(db).updateExisting(TaskDraft(id = task.id, displayName = task.displayName,
                totalDurationMinutes = 30, scheduledForDate = nextDay))
            repo.reconcile()
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.all { it.dateEpochDay == nextDay.toEpochDay() })
            assertEquals(1, db.planDao().getCurrentPlanWithSegments()!!.segments.size)
        } finally { db.close() }
    }

    @Test fun manual_removal_stays_unplaced_until_an_explicit_arrangement_request() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "复习物理30分钟")
            RoomPlanRepository(db).removePlacement(db.planDao().getCurrentPlanWithSegments()!!.segments.single().id)
            repo.reconcile()
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.isEmpty())
            assertTrue(db.taskDao().getAll().single().toDomain().status.isActive)
            repo.arrange(day, "explicit-after-removal")
            assertEquals(1, db.planDao().getCurrentPlanWithSegments()!!.segments.size)
        } finally { db.close() }
    }

    @Test fun provider_paraphrase_cannot_change_explicit_duration_start_or_end_of_window() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            val words = "明天16点到17点复习物理45分钟"
            val local = candidates(words).single()
            repo.capture(listOf(local.copy(title = "物理复习", durationMinutes = 60, timeHint = ArrangementTimeHint(720))),
                day, true, "remote-facts", originalText = words)
            val task = db.taskDao().getAll().single().toDomain()
            assertEquals(45, task.totalDurationMinutes)
            assertEquals(TaskInputSource.USER_INPUT, task.inputSources.duration)
            val slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            assertEquals(960, slot.startMinute)
            assertEquals(1020, slot.endMinute)
        } finally { db.close() }
    }

    @Test fun adjacent_available_blocks_can_support_a_merged_segment() = runBlocking {
        val db = memory()
        try {
            val time = RoomTimeRepository(db.timeDao())
            for (start in listOf(840, 870)) time.saveDateOverride(DateOverrideDraft(title = "空闲", type = DateOverrideType.AVAILABLE,
                date = day, startMinute = start, endMinute = start + 30))
            capture(RoomAutonomyRepository(db), "复习物理60分钟")
            val slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            assertEquals(840, slot.startMinute)
            assertEquals(900, slot.endMinute)
        } finally { db.close() }
    }

    @Test fun placing_an_ordinary_task_before_its_old_intended_day_preserves_the_manual_choice() = runBlocking {
        val db = memory()
        try {
            availability(db)
            RoomTaskRepository(db).save(TaskDraft(id = "manual-earlier", displayName = "复习物理",
                totalDurationMinutes = 30, scheduledForDate = day.plusDays(1)))
            val plans = RoomPlanRepository(db)
            plans.placeTask("manual-earlier", day, 600, 630, "focus")
            val repo = RoomAutonomyRepository(db)
            repo.reconcile()
            val task = db.taskDao().getById("manual-earlier")!!.toDomain()
            assertEquals(day, task.scheduledForDate)
            assertEquals(TaskInputSource.USER_INPUT, task.inputSources.scheduledDate)
            val slot = db.planDao().getCurrentPlanWithSegments()!!.segments.single()
            assertEquals(day.toEpochDay(), slot.dateEpochDay)
            assertEquals(600, slot.startMinute)
            assertEquals(630, slot.endMinute)
        } finally { db.close() }
    }

    @Test fun arranging_one_day_preserves_manual_deferrals_for_other_dates_after_later_changes() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val nextDay = day.plusDays(1)
            RoomTimeRepository(db.timeDao()).saveDateOverride(DateOverrideDraft(title = "后天可用",
                type = DateOverrideType.AVAILABLE, date = nextDay, startMinute = 480, endMinute = 720))
            val repo = RoomAutonomyRepository(db)
            capture(repo, "明天复习物理30分钟")
            repo.capture(candidates("后天整理笔记30分钟"), nextDay, true, "capture-other-day",
                originalText = "后天整理笔记30分钟")
            val physics = db.taskDao().getAll().single { it.displayName == "复习物理" }.id
            val notes = db.taskDao().getAll().single { it.displayName == "整理笔记" }.id
            val plans = RoomPlanRepository(db)
            for (taskId in listOf(physics, notes)) {
                plans.removePlacement(db.planDao().getCurrentPlanWithSegments()!!.segments.single { it.taskId == taskId }.id)
            }
            repo.reconcile()
            repo.arrange(day, "arrange-only-one-day")
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.any { it.taskId == physics })
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.none { it.taskId == notes })
            RoomTaskRepository(db).save(TaskDraft(id = "later-input", displayName = "后来新增的任务",
                totalDurationMinutes = 30, scheduledForDate = day))
            repo.reconcile()
            assertTrue(db.planDao().getCurrentPlanWithSegments()!!.segments.none { it.taskId == notes })
            val held = org.json.JSONArray(db.planningWorkspaceDao().get(RoomAutonomyRepository.HELD)!!.payload)
            assertEquals(listOf(notes), (0 until held.length()).map(held::getString))
        } finally { db.close() }
    }

    @Test fun explicit_record_only_stays_unscheduled_until_the_user_requests_arrangement() = runBlocking {
        val db = memory()
        try {
            availability(db)
            val repo = RoomAutonomyRepository(db)
            capture(repo, "只记着：复习物理30分钟")
            val task = db.taskDao().getAll().single()
            assertEquals("复习物理", task.displayName)
            assertTrue(db.planDao().getCurrentPlanWithSegments()?.segments.orEmpty().isEmpty())
            repo.reconcile()
            assertTrue(db.planDao().getCurrentPlanWithSegments()?.segments.orEmpty().isEmpty())
            repo.arrange(day, "arrange-recorded-item")
            assertEquals(task.id, db.planDao().getCurrentPlanWithSegments()!!.segments.single().taskId)
        } finally { db.close() }
    }

}
