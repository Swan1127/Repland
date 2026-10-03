package com.swan1127.repland.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.swan1127.repland.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "planning_workspace")
data class PlanningWorkspaceEntity(@PrimaryKey val key: String, val payload: String)

@Dao
interface PlanningWorkspaceDao {
    @Query("SELECT * FROM planning_workspace WHERE `key` = 'active-execution-session' OR `key` LIKE 'execution-session-history:%' ORDER BY `key`")
    suspend fun getExecutionSessions(): List<PlanningWorkspaceEntity>
    @Query("SELECT * FROM planning_workspace WHERE `key` = :key")
    fun observe(key: String): Flow<PlanningWorkspaceEntity?>
    @Query("SELECT * FROM planning_workspace WHERE `key` = :key")
    suspend fun get(key: String): PlanningWorkspaceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: PlanningWorkspaceEntity)
    @Query("DELETE FROM planning_workspace WHERE `key` = :key")
    suspend fun remove(key: String)
}

/** Versioned local encoding. No provider secrets or raw model replies are stored here. */
object PlanDraftCodec {
    fun encode(draft: PlanDraft): String = JSONObject().apply {
        put("version", 1)
        put("generatedAt", draft.generatedAt.toString())
        put("revision", draft.sourceRevision)
        put("orderOnly", draft.orderOnly)
        put("manual", draft.hasManualTaskOrder)
        put("order", JSONArray(draft.orderedTaskIds))
        put("pending", JSONArray(draft.pendingTaskIds))
        put("segments", JSONArray(draft.segments.map { s -> JSONObject().apply {
            put("id", s.id); put("task", s.taskId); put("date", s.date.toString())
            put("start", s.startMinute); put("end", s.endMinute); put("locked", s.isLocked); put("track", s.trackId)
        } }))
        put("unscheduled", JSONArray(draft.unscheduledTasks.map { s -> JSONObject().apply {
            put("task", s.taskId); put("remaining", s.remainingMinutes); put("reason", s.reason.name)
        } }))
        put("assessments", JSONArray(draft.priorityAssessments.map { a -> JSONObject().apply {
            put("task", a.taskId); put("score", a.score)
            put("reasons", JSONArray(a.reasons.map { r -> JSONObject().apply { put("kind", r.kind.name); put("value", r.value) } }))
        } }))
    }.toString()

    fun decode(payload: String): PlanDraft {
        val j = JSONObject(payload)
        require(j.getInt("version") == 1)
        fun strings(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
        return PlanDraft(
            generatedAt = LocalDateTime.parse(j.getString("generatedAt")),
            segments = j.getJSONArray("segments").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let {
                PlannedSegment(it.getString("id"), it.getString("task"), LocalDate.parse(it.getString("date")),
                    it.getInt("start"), it.getInt("end"), it.getBoolean("locked"), it.getString("track"))
            } } },
            pendingTaskIds = strings("pending"), orderedTaskIds = strings("order"),
            unscheduledTasks = j.getJSONArray("unscheduled").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let {
                UnscheduledTask(it.getString("task"), it.getInt("remaining"), UnscheduledReason.valueOf(it.getString("reason")))
            } } },
            priorityAssessments = j.getJSONArray("assessments").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { assessment ->
                LocalPriorityAssessment(assessment.getString("task"), assessment.getInt("score"),
                    assessment.getJSONArray("reasons").let { reasons -> (0 until reasons.length()).map { r -> reasons.getJSONObject(r).let {
                        PriorityReason(PriorityReasonKind.valueOf(it.getString("kind")), if (it.isNull("value")) null else it.getInt("value"))
                    } } })
            } } },
            hasManualTaskOrder = j.getBoolean("manual"), sourceRevision = if (j.isNull("revision")) null else j.getString("revision"),
            orderOnly = j.optBoolean("orderOnly"),
        )
    }
}
