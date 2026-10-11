package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.NumericProfileRepository
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal fun numericHash(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
internal fun numericJson(payload: String): JSONObject = JSONObject(payload).also { require(it.getInt("version")==1) { "画像记录版本不支持，请保留数据并检查升级。" } }
internal fun numericRow() = JSONObject().put("version",1)
internal fun JSONArray.strings() = (0 until length()).map(::getString)

/** Called within the same transaction as the FIRST explicit start. Never reconstructs old history. */
internal suspend fun freezeNumericOriginal(database: ReplandDatabase, task: TaskEntity, now: Long) {
    val workspace=database.planningWorkspaceDao(); val key="numeric-input:original:${task.id}"
    if(workspace.get(key)!=null) return
    val sources=TaskInputSources.decode(task.inputSources)
    val trusted=setOf(TaskInputSource.USER_INPUT,TaskInputSource.ACCEPTED_SUGGESTION)
    val logs=database.executionLogDao().getForTask(task.id)
    val beforeFirstStart=task.status==TaskStatus.NOT_STARTED.name && logs.none {
        it.confirmedStatus in setOf(TaskStatus.IN_PROGRESS.name,TaskStatus.COMPLETED.name) || it.actualDurationMinutes!=null || it.eventType==ExecutionLogEventType.PARTIAL_COMPLETION.name }
    val basis=workspace.get("numeric-input:basis:${task.id}")?.let { numericJson(it.payload) }
    val original=if(basis!=null && basis.optInt("applied")==task.totalDurationMinutes && basis.optString("category")==task.category) basis.getInt("original") else task.totalDurationMinutes
    val row=numericRow().put("task",task.id).put("at",now)
        .put("minutes",if(beforeFirstStart && sources.duration in trusted) original else JSONObject.NULL)
        .put("category",if(beforeFirstStart && sources.category in trusted) task.category else TaskCategory.UNSPECIFIED.name)
        .put("source",if(beforeFirstStart) "EXPLICIT_START" else "HISTORY_UNVERIFIED")
    workspace.put(PlanningWorkspaceEntity(key,row.toString()))
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomNumericProfileRepository(private val db: ReplandDatabase,
    private val configuration: com.swan1127.repland.domain.ports.AiProviderConfigRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis) : NumericProfileRepository {
    private val workspace=db.planningWorkspaceDao()
    override fun observe(): Flow<NumericProfileSnapshot> = combine(db.taskDao().observeAll(),db.executionLogDao().observeAll(),
        workspace.observeNumericInputs(),workspace.observeEndedSessions(),db.categoryPreferenceDao().observeAll()) { _,_,_,_,_ -> Unit }
        .mapLatest { refresh() }.distinctUntilChanged()
    override fun observeDescription() = workspace.observe("numeric-description").map { it?.let { NumericProfileCodec.description(it.payload) } }
    private suspend fun settings() = workspace.get("numeric-input:settings")?.let { numericJson(it.payload) } ?: numericRow()
    private suspend fun changeSettings(action: (JSONObject) -> Unit) = db.withTransaction {
        val json=settings(); action(json); workspace.put(PlanningWorkspaceEntity("numeric-input:settings",json.toString()))
    }
    override suspend fun setEnabled(enabled: Boolean) { changeSettings { it.put("enabled",enabled) } }
    override suspend fun setAiConsent(consented: Boolean) { changeSettings { it.put("aiConsent",consented) } }
    override suspend fun setParameterEnabled(id: String, enabled: Boolean) {
        require(id=="rounds" || id in (listOf("ALL")+TaskCategory.knownEntries.map { it.name }).map { "estimate:$it" })
        changeSettings { val set=(it.optJSONArray("disabled")?.strings().orEmpty()).toMutableSet()
            if(enabled) set.remove(id) else set.add(id); it.put("disabled",JSONArray(set.sorted())) }
    }
    override suspend fun setExcluded(sourceId: String, excluded: Boolean) = changeSettings {
        require(sourceId.startsWith("task:") || sourceId.startsWith("session:"))
        val set=it.optJSONArray("excluded")?.strings().orEmpty().toMutableSet()
        if(excluded) set.add(sourceId) else set.remove(sourceId); it.put("excluded",JSONArray(set.sorted()))
    }
    private suspend fun taskAndLogs(id: String): Pair<Task,List<TaskExecutionLog>> =
        requireNotNull(db.taskDao().getById(id)) { "任务不存在，请刷新。" }.toDomain() to db.executionLogDao().getForTask(id).map { it.toDomain() }
    companion object {
        fun taskRevision(task: Task, logs: List<TaskExecutionLog>): String = numericHash(task.toString()+logs.sortedBy { it.id }.toString())
        private fun evidenceRevision(task: Task, logs: List<TaskExecutionLog>): String = numericHash(task.status.name+logs.sortedBy { it.id }.toString())
    }
    override suspend fun confirmCurrentInput(taskId: String, expectedRevision: String) = db.withTransaction {
        val (task,logs)=taskAndLogs(taskId)
        require(taskRevision(task,logs)==expectedRevision) { "任务或执行记录已变化，请重新查看后确认。" }
        val entity=db.taskDao().getById(taskId)!!
        db.taskDao().update(entity.copy(inputSources=task.inputSources.copy(
            category=if(task.category!=TaskCategory.UNSPECIFIED) TaskInputSource.USER_INPUT else TaskInputSource.UNKNOWN,
            duration=if(task.totalDurationMinutes!=null) TaskInputSource.USER_INPUT else TaskInputSource.UNKNOWN).encode(),updatedAtEpochMillis=clock()))
        // Intentionally does not modify the immutable before-start observation.
    }
    override suspend fun confirmActualTotal(taskId: String, minutes: Int, expectedRevision: String) = db.withTransaction {
        require(minutes in 1..525600) { "累计实际投入需为 1–525600 的整数分钟。" }
        val (task,logs)=taskAndLogs(taskId)
        require(task.status==TaskStatus.COMPLETED && logs.any { it.confirmedStatus==TaskStatus.COMPLETED }) { "请先明确完成任务，再确认累计总投入。" }
        require(taskRevision(task,logs)==expectedRevision) { "任务或执行记录已变化，请重新查看后确认。" }
        workspace.put(PlanningWorkspaceEntity("numeric-input:total:$taskId",numericRow().put("task",taskId).put("minutes",minutes)
            .put("at",clock()).put("logs",JSONArray(logs.map { it.id }.sorted())).put("evidence",evidenceRevision(task,logs)).put("source","USER_BACKFILL_TOTAL").toString()))
    }
    override suspend fun clearActualTotal(taskId: String) { workspace.remove("numeric-input:total:$taskId") }
    override suspend fun refresh(): NumericProfileSnapshot = db.withTransaction {
        val now=clock(); val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val start=today.minusDays(NumericProfileCalculator.WINDOW_DAYS-1)
        fun within(millis: Long): Boolean { val day=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(); return day in start..today && millis<=now }
        val tasks=db.taskDao().getAll().map { it.toDomain() }; val taskMap=tasks.associateBy { it.id }
        val logs=db.executionLogDao().getAll().map { it.toDomain() }; val inputs=workspace.getNumericInputs()
        val rows=inputs.associate { it.key to numericJson(it.payload) }; val settings=rows["numeric-input:settings"] ?: numericRow()
        val excluded=settings.optJSONArray("excluded")?.strings().orEmpty().toSet()
        val enabled=settings.optBoolean("enabled",true); val disabled=settings.optJSONArray("disabled")?.strings().orEmpty().toSet()
        val sessionRows=workspace.getExecutionSessions().filter { it.key.startsWith("execution-session-history:") }
        val sessions=sessionRows.map { ExecutionSessionCodec.decode(it.payload) }
        val preferences=db.categoryPreferenceDao().getAll()
        // Future evidence can become eligible later on the SAME date without a database write.
        // Bind that boundary to the content version so cached unknowns and descriptions expire.
        val temporalEligibility=logs.map { it.createdAtEpochMillis<=now }+
            rows.values.map { it.optLong("at",Long.MIN_VALUE)<=now }+
            sessions.map { (it.endedAtEpochMillis ?: Long.MAX_VALUE)<=now }
        val version=numericHash(NumericProfileCalculator.RULE+today+ZoneId.systemDefault()+tasks.sortedBy { it.id }+logs+inputs+sessionRows+preferences+temporalEligibility)
        val previous=workspace.get("numeric-snapshot")?.let { NumericProfileCodec.snapshot(it.payload) }
        if(previous?.version==version) return@withTransaction previous
        val sources=mutableListOf<NumericSource>()
        val samples=tasks.map { task ->
            val original=rows["numeric-input:original:${task.id}"]
            val total=rows["numeric-input:total:${task.id}"]
            val originalMinutes=original?.takeUnless { it.isNull("minutes") }?.getInt("minutes")
            val category=original?.getString("category")?.let(TaskCategory::valueOf) ?: TaskCategory.UNSPECIFIED
            val completedAt=logs.filter { it.taskId==task.id && it.confirmedStatus==TaskStatus.COMPLETED && it.eventType==ExecutionLogEventType.STATUS_CHANGE }.maxOfOrNull { it.createdAtEpochMillis }
            val valid=task.status==TaskStatus.COMPLETED && original!=null && originalMinutes!=null && originalMinutes>0 && original.getLong("at")<=now && total!=null && total.getInt("minutes")>0 &&
                total.getString("evidence")==evidenceRevision(task,logs.filter { it.taskId==task.id }) && within(total.getLong("at")) && completedAt?.let(::within)==true
            val actual=total?.getInt("minutes")
            val reason=when { originalMinutes==null -> "开始前原估计未知，当前值不能补写历史"
                task.status!=TaskStatus.COMPLETED -> "尚未明确完成"
                total==null -> "累计总投入未明确确认，不将会话与反馈相加"
                !valid -> "记录变化或不在窗口内，请重新核对累计总投入"
                else -> "开始前原估计 $originalMinutes 分钟（观察 ${original!!.getLong("at")}）；累计总投入 $actual 分钟（用户补录 ${total!!.getLong("at")}）；冻结类别 ${category.name}；执行引用 ${total.optJSONArray("logs") ?: "见任务执行记录"}" }
            sources+=NumericSource("task:${task.id}",task.id,task.displayName,reason,valid && "task:${task.id}" !in excluded,"task:${task.id}" in excluded)
            NumericEstimateSample(task.id,category,originalMinutes,actual,valid)
        }
        val byLogId=logs.associateBy { it.id }
        val corrected=mutableSetOf<String>()
        logs.filter { it.eventType==ExecutionLogEventType.CORRECTION && it.feedback.actualDurationMinutes!=null }.forEach { correction ->
            var ancestor=correction.correctedLogId
            val visited=mutableSetOf<String>()
            while(ancestor!=null && visited.add(ancestor)) { corrected.add(ancestor); ancestor=byLogId[ancestor]?.correctedLogId }
        }
        val safeSessions=sessions.filter { s ->
            val linkage=rows["numeric-input:session:${s.id}"]?.optJSONArray("logs")?.strings()
            val hasCorrection=if(linkage==null) logs.any { it.taskId==s.taskId && it.id in corrected } else linkage.any { it in corrected }
            val valid=s.endedAtEpochMillis?.let(::within)==true && s.accumulatedMillis in 1..86_400_000 && s.outcome!=null && s.runningSinceEpochMillis==null && !hasCorrection && taskMap.containsKey(s.taskId)
            sources+=NumericSource("session:${s.id}",s.taskId,s.taskTitle,
                if(hasCorrection) "关联时长已更正，原会话暂不纳入；手填值不冒充单轮"
                else "主动开始并结束；有效运行 ${"%.2f".format(java.util.Locale.ROOT,s.accumulatedMillis/60000.0)} 分钟，暂停不计",valid && "session:${s.id}" !in excluded,"session:${s.id}" in excluded)
            valid
        }
        val allDisabled=if(enabled) disabled else (listOf("ALL")+TaskCategory.knownEntries.map { it.name }).map { "estimate:$it" }.toSet()
        val parameters=NumericProfileCalculator.estimates(samples,excluded.filter { it.startsWith("task:") }.map { it.removePrefix("task:") }.toSet(),allDisabled)+
            NumericProfileCalculator.rounds(safeSessions,excluded.filter { it.startsWith("session:") }.map { it.removePrefix("session:") }.toSet(),!enabled || "rounds" in disabled)
        val snapshot=NumericProfileSnapshot(version,start.toString(),today.toString(),now,parameters,sources.sortedWith(compareBy<NumericSource> { !it.id.startsWith("task:") }.thenBy { it.id }),
            CategoryPreferences.normalized(preferences.associate { it.toDomainPair() }),preferences.map { TaskCategory.valueOf(it.category) }.toSet(),enabled,settings.optBoolean("aiConsent"))
        workspace.put(PlanningWorkspaceEntity("numeric-snapshot",NumericProfileCodec.encode(snapshot)))
        snapshot
    }
    override suspend fun durationAdvice(taskId: String): NumericDurationAdvice? = db.withTransaction {
        val snapshot=refresh(); if(!snapshot.enabled) return@withTransaction null
        val (task,logs)=taskAndLogs(taskId)
        if(!task.status.isActive || task.category==TaskCategory.UNSPECIFIED || task.inputSources.category !in setOf(TaskInputSource.USER_INPUT,TaskInputSource.ACCEPTED_SUGGESTION) ||
            task.inputSources.duration !in setOf(TaskInputSource.USER_INPUT,TaskInputSource.ACCEPTED_SUGGESTION)) return@withTransaction null
        val parameter=snapshot.parameters.single { it.id=="estimate:${task.category.name}" }
        val basis=workspace.get("numeric-input:basis:$taskId")?.let { numericJson(it.payload) }
        val original=if(basis!=null && basis.optInt("applied")==task.totalDurationMinutes && basis.optString("category")==task.category.name) basis.getInt("original") else task.totalDurationMinutes
        val suggested=NumericProfileCalculator.suggestMinutes(original,parameter) ?: return@withTransaction null
        NumericDurationAdvice(taskId,taskRevision(task,logs),snapshot.version,parameter.id,original!!,suggested,parameter.value!!,parameter.sources)
    }
    override suspend fun acceptDurationAdvice(advice: NumericDurationAdvice) = db.withTransaction {
        require(durationAdvice(advice.taskId)==advice) { "任务、来源或画像已变化，请重新查看建议。" }
        val task=db.taskDao().getById(advice.taskId)!!
        db.taskDao().update(task.copy(totalDurationMinutes=advice.suggestedMinutes,inputSources=TaskInputSources.decode(task.inputSources).copy(duration=TaskInputSource.ACCEPTED_SUGGESTION).encode(),updatedAtEpochMillis=clock()))
        workspace.put(PlanningWorkspaceEntity("numeric-input:basis:${task.id}",numericRow().put("original",advice.originalMinutes).put("applied",advice.suggestedMinutes)
            .put("category",task.category).put("profile",advice.profileVersion).put("parameter",advice.parameterId).put("sources",JSONArray(advice.sources)).toString()))
    }
    override suspend fun saveDescription(description: NumericDescription) = db.withTransaction {
        val snapshot=refresh()
        require(snapshot.version==description.profileVersion && snapshot.enabled && snapshot.aiConsent) { "画像或授权已变化，描述未保存。" }
        val access=db.aiSettingsDao().get()?.toDomain() ?: AiPreferences()
        require(access.isEnabled && access.hasExplicitConsent && description.accessRevision==numericHash(access.toString())) { "AI 总开关或授权已变化，描述未保存。" }
        if(description.origin!="LOCAL") require(configuration!=null && description.configurationRevision==numericHash(configuration.observe().first().toString())) { "AI 配置已变化，描述未保存。" }
        val request=NumericNarrationRequest("stored",snapshot.version,snapshot.rule,snapshot.windowStart,snapshot.windowEnd,snapshot.parameters.filter { it.value!=null && it.availability!=NumericAvailability.DISABLED })
        require(NumericNarrationValidator.valid(request,NumericNarration("stored",description.profileVersion,description.claims,description.nextStep)))
        workspace.put(PlanningWorkspaceEntity("numeric-description",NumericProfileCodec.encode(description)))
    }
}
