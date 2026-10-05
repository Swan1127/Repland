package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TaskCaptureRepository
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate

object TaskCaptureCodec {
    fun encode(value: TaskCaptureDraft): String = JSONObject().apply {
        put("version", 1); put("id", value.id); put("text", value.text); put("stage", value.stage.name)
        put("due", value.dueDate?.toString()); put("scheduled", value.scheduledForDate?.toString())
        put("duration", value.duration); put("category", value.category.name); put("priority", value.priority.name)
        put("custom", value.isCustomDuration); put("customText", value.customDurationText)
    }.toString()
    fun decode(payload: String): TaskCaptureDraft = JSONObject(payload).let { j ->
        require(j.getInt("version") == 1)
        TaskCaptureDraft(j.getString("id"), j.getString("text"), TaskCaptureStage.valueOf(j.getString("stage")),
            if (j.isNull("due")) null else LocalDate.parse(j.getString("due")),
            if (j.isNull("scheduled")) null else LocalDate.parse(j.getString("scheduled")),
            j.getInt("duration"), TaskCategory.valueOf(j.getString("category")), TaskPriority.valueOf(j.getString("priority")),
            j.getBoolean("custom"), j.getString("customText"))
    }
}

class RoomTaskCaptureRepository(private val database: ReplandDatabase) : TaskCaptureRepository {
    private val workspace = database.planningWorkspaceDao()
    private val tasks = RoomTaskRepository(database)
    override fun observeDraft() = workspace.observe("task-capture").map { it?.let { TaskCaptureCodec.decode(it.payload) } }
    override suspend fun saveDraft(draft: TaskCaptureDraft) = database.withTransaction {
        // An autosave queued before commit must not resurrect a consumed draft.
        if (database.taskDao().getById(draft.id) == null) {
            workspace.put(PlanningWorkspaceEntity("task-capture", TaskCaptureCodec.encode(draft)))
        }
    }
    override suspend fun discard(id: String) = database.withTransaction {
        if (workspace.get("task-capture")?.let { TaskCaptureCodec.decode(it.payload).id } == id) workspace.remove("task-capture")
    }
    override suspend fun commit(capture: TaskCaptureDraft, task: TaskDraft) = database.withTransaction {
        require(task.id == capture.id && capture.durationIsValid &&
            task.totalDurationMinutes == capture.selectedDuration && TaskDraftValidator.isValid(task)) {
            "任务草稿无效，请检查内容与时长。"
        }
        if (database.taskDao().getById(capture.id) != null) return@withTransaction
        require(workspace.get("task-capture")?.let { TaskCaptureCodec.decode(it.payload) } == capture) { "草稿已变化，请重新确认。" }
        tasks.save(task)
        workspace.remove("task-capture")
    }
}
