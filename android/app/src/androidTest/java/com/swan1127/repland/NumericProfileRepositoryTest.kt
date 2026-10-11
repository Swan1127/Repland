package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NumericProfileRepositoryTest {
    private fun verify(block: suspend (ReplandDatabase,RoomTaskRepository,RoomNumericProfileRepository) -> Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),ReplandDatabase::class.java).build()
        try { block(db,RoomTaskRepository(db),RoomNumericProfileRepository(db)) } finally { db.close() }
    }
    private suspend fun complete(tasks: RoomTaskRepository, profile: RoomNumericProfileRepository, id: String, estimate: Int=20, actual: Int=30) {
        tasks.save(TaskDraft(id=id,displayName=id,category=TaskCategory.COURSE,totalDurationMinutes=estimate))
        tasks.confirmStatus(id,TaskStatus.IN_PROGRESS)
        tasks.confirmStatus(id,TaskStatus.COMPLETED,TaskFeedback(actualDurationMinutes=actual))
        val task=tasks.observeTasks().first().single { it.id==id }
        profile.confirmActualTotal(id,actual,RoomNumericProfileRepository.taskRevision(task,tasks.observeExecutionLogs(id).first()))
    }
    @Test fun window_cannot_be_rejuvenated_by_reconfirming_an_old_completion_and_future_data_is_unknown()=verify { db,tasks,profile ->
        complete(tasks,profile,"a")
        val later=RoomNumericProfileRepository(db) { System.currentTimeMillis()+91L*86400000 }
        val task=tasks.observeTasks().first().single(); val logs=tasks.observeExecutionLogs("a").first()
        later.confirmActualTotal("a",30,RoomNumericProfileRepository.taskRevision(task,logs))
        assertNull(later.refresh().parameters.first().value)
        assertNull(profile.refresh().parameters.first().value)
    }
    @Test fun cached_future_total_becomes_eligible_when_clock_reaches_it_and_invalidates_old_description_version()=verify { db,tasks,profile ->
        complete(tasks,profile,"a")
        var now=System.currentTimeMillis()
        val current=RoomNumericProfileRepository(db) { now }
        val future=RoomNumericProfileRepository(db) { now+1000 }
        val task=tasks.observeTasks().first().single(); val logs=tasks.observeExecutionLogs("a").first()
        future.confirmActualTotal("a",30,RoomNumericProfileRepository.taskRevision(task,logs))
        val before=current.refresh(); assertNull(before.parameters.first().value)
        now+=1000
        val after=current.refresh()
        assertEquals(1.5,after.parameters.first().value!!,0.001)
        assertNotEquals(before.version,after.version)
        // A clock correction may also leave the frozen start observation in the future.
        val frozen=db.planningWorkspaceDao().get("numeric-input:original:a")!!
        val original=org.json.JSONObject(frozen.payload).put("at",now+1000)
        db.planningWorkspaceDao().put(frozen.copy(payload=original.toString()))
        val futureOriginal=current.refresh()
        assertNull("A future original observation is not current evidence",futureOriginal.parameters.first().value)
        now+=1000
        assertEquals(1.5,current.refresh().parameters.first().value!!,0.001)
    }
    @Test fun planning_preview_records_parameters_and_rejects_changed_profile_without_changing_current_plan()=verify { db,tasks,profile ->
        for(id in listOf("a","b","c")) complete(tasks,profile,id)
        tasks.save(TaskDraft(id="target",displayName="target",category=TaskCategory.COURSE,totalDurationMinutes=20))
        val plans=RoomPlanRepository(db); val time=RoomTimeRepository(db.timeDao())
        val date=LocalDate.now().plusDays(1)
        time.saveDateOverride(DateOverrideDraft(title="available",type=DateOverrideType.AVAILABLE,date=date,startMinute=600,endMinute=660))
        plans.placeTask("target",date,600,630,"focus"); val before=plans.observeCurrentPlan().first()
        val service=PlanningOperationService(PlanningReadService(tasks,time,plans,RoomCategoryPreferenceRepository(db.categoryPreferenceDao())),plans,PlanGenerator,null,profile)
        val draft=service.preview(PlanningPreviewKind.FORMULATE)
        assertEquals(profile.refresh().version,draft.numericProfileVersion)
        assertEquals(listOf("estimate:COURSE"),draft.numericParameters)
        assertEquals(draft,PlanDraftCodec.decode(PlanDraftCodec.encode(draft)))
        assertEquals(20,tasks.observeTasks().first().single { it.id=="target" }.totalDurationMinutes)
        profile.setExcluded("task:a",true)
        assertTrue(runCatching { service.confirm(draft) }.isFailure)
        assertEquals(before,plans.observeCurrentPlan().first())
        profile.setEnabled(false)
        assertNull(service.preview(PlanningPreviewKind.FORMULATE).numericProfileVersion)
    }
    @Test fun snapshot_settings_exclusion_and_sources_survive_database_reopen_and_are_in_safe_export()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="numeric-fixture-${java.util.UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,ReplandDatabase::class.java,name).build()
        try {
            val profile=RoomNumericProfileRepository(db); complete(RoomTaskRepository(db),profile,"persisted")
            profile.setExcluded("task:persisted",true); profile.setAiConsent(true)
            val expected=profile.refresh(); db.close()
            db=Room.databaseBuilder(context,ReplandDatabase::class.java,name).build()
            assertEquals(expected,RoomNumericProfileRepository(db).refresh())
            val export=com.swan1127.repland.data.export.LocalDataJsonExporter.export(RoomDataManagementRepository(db).snapshot())
            assertTrue(export.contains("numeric-input:original:persisted")); assertTrue(export.contains("numeric-snapshot"))
            assertFalse(export.contains("apiKey"))
        } finally { db.close(); context.deleteDatabase(name) } // Only this unique test fixture.
    }
    @Test fun original_is_frozen_before_start_and_later_edits_do_not_rewrite_category_or_estimate()=verify { db,tasks,profile ->
        complete(tasks,profile,"a")
        tasks.save(TaskDraft(id="a",displayName="a",category=TaskCategory.OFFICE,totalDurationMinutes=80))
        val snapshot=profile.refresh()
        assertEquals(1.5,snapshot.parameters.single { it.id=="estimate:COURSE" }.value!!,0.001)
        assertNull(snapshot.parameters.single { it.id=="estimate:OFFICE" }.value)
        assertEquals(20,org.json.JSONObject(db.planningWorkspaceDao().get("numeric-input:original:a")!!.payload).getInt("minutes"))
    }
    @Test fun changing_category_before_start_does_not_reuse_another_categorys_accepted_basis()=verify { _,tasks,profile ->
        for(id in listOf("a","b","c")) complete(tasks,profile,id)
        tasks.save(TaskDraft(id="target",displayName="target",category=TaskCategory.COURSE,totalDurationMinutes=20))
        profile.acceptDurationAdvice(profile.durationAdvice("target")!!)
        tasks.save(TaskDraft(id="target",displayName="target",category=TaskCategory.OFFICE,totalDurationMinutes=30))
        tasks.confirmStatus("target",TaskStatus.IN_PROGRESS); tasks.confirmStatus("target",TaskStatus.COMPLETED)
        val task=tasks.observeTasks().first().single { it.id=="target" }
        profile.confirmActualTotal("target",45,RoomNumericProfileRepository.taskRevision(task,tasks.observeExecutionLogs("target").first()))
        assertEquals(1.5,profile.refresh().parameters.single { it.id=="estimate:OFFICE" }.value!!,0.001)
    }
    @Test fun correction_invalidates_total_and_explicit_reconfirmation_replaces_instead_of_adding()=verify { _,tasks,profile ->
        complete(tasks,profile,"a")
        val log=tasks.observeExecutionLogs("a").first().last()
        tasks.correctExecutionLog("a",log.id,TaskFeedback(actualDurationMinutes=40))
        assertNull(profile.refresh().parameters.first().value)
        val task=tasks.observeTasks().first().single()
        profile.confirmActualTotal("a",40,RoomNumericProfileRepository.taskRevision(task,tasks.observeExecutionLogs("a").first()))
        assertEquals(2.0,profile.refresh().parameters.first().value!!,0.001)
        assertEquals(1,profile.refresh().parameters.first().count)
    }
    @Test fun repeated_adoption_uses_original_basis_and_never_changes_confirmed_plan()=verify { db,tasks,profile ->
        for(id in listOf("a","b","c")) complete(tasks,profile,id)
        tasks.save(TaskDraft(id="target",displayName="target",category=TaskCategory.COURSE,totalDurationMinutes=20))
        val plans=RoomPlanRepository(db); plans.placeTask("target",LocalDate.now().plusDays(1),600,630,"focus")
        val before=plans.observeCurrentPlan().first()
        val advice=profile.durationAdvice("target")!!
        assertEquals(30,advice.suggestedMinutes)
        profile.acceptDurationAdvice(advice)
        assertEquals(30,profile.durationAdvice("target")!!.suggestedMinutes)
        assertEquals(before,plans.observeCurrentPlan().first())
        assertTrue(runCatching { profile.acceptDurationAdvice(advice) }.isFailure)
        profile.setParameterEnabled("estimate:COURSE",false)
        assertNull(profile.durationAdvice("target"))
    }
    @Test fun legacy_started_values_cannot_be_promoted_to_historical_estimates_by_same_value_confirmation()=verify { db,tasks,profile ->
        tasks.save(TaskDraft(id="old",displayName="old",category=TaskCategory.COURSE,totalDurationMinutes=20))
        val old=db.taskDao().getById("old")!!
        db.taskDao().update(old.copy(status=TaskStatus.IN_PROGRESS.name,inputSources=""))
        tasks.confirmStatus("old",TaskStatus.COMPLETED)
        val task=tasks.observeTasks().first().single(); val logs=tasks.observeExecutionLogs("old").first()
        profile.confirmCurrentInput("old",RoomNumericProfileRepository.taskRevision(task,logs))
        val confirmed=tasks.observeTasks().first().single()
        profile.confirmActualTotal("old",30,RoomNumericProfileRepository.taskRevision(confirmed,logs))
        assertNull(profile.refresh().parameters.first().value)
        assertEquals(TaskInputSource.USER_INPUT,confirmed.inputSources.duration)
    }
    @Test fun read_or_write_failure_preserves_snapshot_and_user_data_and_retry_recovers()=verify { db,tasks,profile ->
        complete(tasks,profile,"a")
        val before=profile.refresh(); val row=db.planningWorkspaceDao().get("numeric-snapshot")!!
        profile.setExcluded("task:a",true)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_numeric BEFORE INSERT ON planning_workspace WHEN NEW.`key` = 'numeric-snapshot' BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(runCatching { profile.refresh() }.isFailure)
        assertEquals(row,db.planningWorkspaceDao().get("numeric-snapshot"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_numeric")
        assertNull(profile.refresh().parameters.first().value)
        profile.setExcluded("task:a",false)
        assertEquals(before.parameters,profile.refresh().parameters)
        db.planningWorkspaceDao().put(PlanningWorkspaceEntity("numeric-input:corrupt","{\"version\":99}"))
        val last=db.planningWorkspaceDao().get("numeric-snapshot")
        assertTrue(runCatching { profile.refresh() }.isFailure)
        assertEquals(last,db.planningWorkspaceDao().get("numeric-snapshot"))
        assertEquals(TaskStatus.COMPLETED,tasks.observeTasks().first().single().status)
    }
    @Test fun active_round_is_unknown_and_ended_paused_round_is_counted_once_without_feedback_double_counting()=verify { db,tasks,profile ->
        var now=System.currentTimeMillis()
        tasks.save(TaskDraft(id="a",displayName="a",totalDurationMinutes=30))
        val plans=RoomPlanRepository(db); plans.placeTask("a",LocalDate.now().plusDays(1),600,630,"focus")
        val sessions=RoomExecutionSessionRepository(db) { now }
        sessions.start(plans.observeCurrentPlan().first()!!.segments.single().id)
        val id=sessions.observeActive().first()!!.id
        assertNull(profile.refresh().parameters.single { it.id=="rounds" }.value)
        now+=600000; sessions.pause(id); now+=600000; sessions.resume(id); now+=600000
        sessions.finish(id,ExecutionOutcome.CONTINUE,TaskFeedback())
        val current=RoomNumericProfileRepository(db) { now }.refresh()
        assertEquals(20.0,current.parameters.single { it.id=="rounds" }.value!!,0.001)
        assertNull(current.parameters.first().value)
        val original=tasks.observeExecutionLogs("a").first().last()
        tasks.correctExecutionLog("a",original.id,TaskFeedback(completedContent="校正说明"))
        val correction=tasks.observeExecutionLogs("a").first().last()
        tasks.correctExecutionLog("a",correction.id,TaskFeedback(actualDurationMinutes=15))
        assertNull(RoomNumericProfileRepository(db) { now }.refresh().parameters.single { it.id=="rounds" }.value)
    }
}
