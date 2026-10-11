package com.swan1127.repland.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.components.EditorSheet
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal fun numericDisplayNumber(value: Double?): String = when {
    value==null -> "未知"
    value>0 && value<0.01 -> "<0.01"
    else -> "%.2f".format(Locale.ROOT,value).trimEnd('0').trimEnd('.')
}
private fun number(value: Double?)=numericDisplayNumber(value)
private fun category(category: TaskCategory)=when(category) { TaskCategory.COURSE->"课程"; TaskCategory.EXTRACURRICULAR->"课外"; TaskCategory.OFFICE->"办公"; TaskCategory.LEISURE->"休闲"; TaskCategory.UNSPECIFIED->"未分类" }
private fun name(p: NumericParameter)=if(p.id.startsWith("estimate:")) "估时修正 · "+(p.id.removePrefix("estimate:").let { if(it=="ALL") "全局摘要" else category(TaskCategory.valueOf(it)) }) else p.name
private fun status(p: NumericParameter)=when(p.availability) { NumericAvailability.UNKNOWN->"未知 / 待积累"; NumericAvailability.REFERENCE->"仅供参考（不足3样本）"; NumericAvailability.USABLE->"可用于建议"; NumericAvailability.DISABLED->"已停用" }

@Composable
fun NumericProfileSection(vm: NumericProfileViewModel, onOpenTask: (String) -> Unit = {}) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var sourceParameter by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceLimit by rememberSaveable { mutableIntStateOf(20) }
    OutlinedButton(onClick={ open=true; vm.retry() },modifier=Modifier.fillMaxWidth().testTag("numeric-profile-open")) { Text("个人规划画像 · 数值与来源") }
    if(!open) return
    val snapshot=state.snapshot; val enabled=state.trusted && !state.busy
    EditorSheet(onDismissRequest={ vm.cancelDescription(); open=false },saving=state.busy,
        title={ Text("个人规划画像") },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).testTag("numeric-profile-scroll"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("仅整理规划记录。样本不足保持未知；不评价人格、能力、自律或健康。",style=MaterialTheme.typography.bodySmall)
            state.readError?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("numeric-read-error")); TextButton(vm::retry,modifier=Modifier.testTag("numeric-retry")) { Text("重新读取") } }
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("numeric-action-error")) }
            state.receipt?.let { Text(it,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("numeric-receipt")) }
            if(snapshot==null) Text("正在读取本地记录…") else {
                Text("${snapshot.windowStart} — ${snapshot.windowEnd} · 90天窗口\n更新 ${Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()}\n规则 ${snapshot.rule} · 画像 ${snapshot.version.take(12)}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("numeric-version"))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("使用画像建议"); Switch(snapshot.enabled,vm::setEnabled,enabled=enabled,modifier=Modifier.testTag("numeric-enabled")) }
                snapshot.parameters.forEach { p ->
                    Card(Modifier.fillMaxWidth().testTag("numeric-parameter-${p.id}")) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(name(p),style=MaterialTheme.typography.titleSmall)
                        Text("${number(p.value)} ${p.unit} · ${p.count} 个有效样本 · ${status(p)}",modifier=Modifier.testTag("numeric-value-${p.id}"))
                        if(p.id=="rounds") Text("中位数；常见范围 Q1–Q3 ${number(p.low)}–${number(p.high)} 分钟。实际长度不代表偏好或能力。",style=MaterialTheme.typography.bodySmall)
                        if(p.id=="estimate:ALL") Text("全局摘要不能替代类别样本。",style=MaterialTheme.typography.bodySmall)
                        Row { TextButton({ sourceParameter=if(sourceParameter==p.id) null else p.id; sourceLimit=20 },modifier=Modifier.testTag("numeric-sources-${p.id}")) { Text("查看来源") }
                            TextButton({ vm.parameter(p.id,p.availability==NumericAvailability.DISABLED) },enabled=enabled && snapshot.enabled,modifier=Modifier.testTag("numeric-toggle-${p.id}")) { Text(if(p.availability==NumericAvailability.DISABLED) "启用" else "停用") } }
                    } }
                }
                Text("明确类别偏好 · 复用现有权重",style=MaterialTheme.typography.titleSmall)
                snapshot.categoryWeights.forEach { (c,w) -> Text("${category(c)} $w% · ${if(c in snapshot.explicitCategories) "用户设置" else "默认值（不是用户选择）"}") }
                Text("重排 / 中断、明确时段偏好、安排方式：未知 / 待补充可靠证据；不从拖动或延期推断。",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("来源与任务",style=MaterialTheme.typography.titleMedium)
                val filtered=sourceParameter?.let { selected -> snapshot.parameters.find { it.id==selected }?.sources?.toSet()?.let { ids -> snapshot.sources.filter { it.id in ids } } } ?: snapshot.sources
                if(sourceParameter!=null) TextButton({ sourceParameter=null }) { Text("查看全部来源（含未纳入）") }
                filtered.take(sourceLimit).forEach { source ->
                    val task=state.tasks.find { it.id==source.taskId }
                    Card(Modifier.fillMaxWidth().testTag("numeric-source-${source.id}")) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(source.title); Text(source.detail,style=MaterialTheme.typography.bodySmall)
                        Text("${if(source.excluded) "用户排除" else if(source.included) "已纳入" else "未纳入"} · ${source.id}",style=MaterialTheme.typography.labelSmall)
                        TextButton({ vm.exclude(source.id,!source.excluded) },enabled=enabled,modifier=Modifier.testTag("numeric-exclude-${source.id}")) { Text(if(source.excluded) "恢复纳入资格" else "排除此样本") }
                        if(source.id.startsWith("task:") && task!=null) {
                            TextButton({ vm.openTask(task.id) },enabled=enabled,modifier=Modifier.testTag("numeric-edit-${task.id}")) { Text("确认输入来源 / 补录累计投入") }
                            if(task.status.isActive) TextButton({ vm.inspectAdvice(task.id) },enabled=enabled,modifier=Modifier.testTag("numeric-advice-${task.id}")) { Text("查看估时建议") }
                            TextButton({ open=false; onOpenTask(task.id) },enabled=enabled) { Text("打开任务与执行记录") }
                        }
                    } }
                }
                if(filtered.size>sourceLimit) TextButton({ sourceLimit+=20 }) { Text("显示更多来源") }
                HorizontalDivider(); Text("现状描述",style=MaterialTheme.typography.titleMedium)
                Text("本地事实摘要：${snapshot.parameters.count { it.value!=null }} 项参数有记录，其余未知。文案不成为画像证据。",modifier=Modifier.testTag("numeric-local-summary"))
                state.description?.let { d ->
                    val stale=state.descriptionExpired
                    Text(if(stale) "旧描述已过期，请刷新；不作为当前规划依据。" else if(d.origin=="LOCAL") "本地事实摘要（非 AI）" else "${d.origin} · 基于当前指定快照",modifier=Modifier.testTag("numeric-description-status"))
                    Text("生成 ${Instant.ofEpochMilli(d.generatedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()} · 画像 ${d.profileVersion.take(12)}",style=MaterialTheme.typography.bodySmall)
                    if(!stale) {
                        d.claims.forEach { claim -> snapshot.parameters.find { it.id==claim.parameterId }?.let { p -> Text("${name(p)}：${number(p.value)} ${p.unit}，${p.count} 个样本（${snapshot.windowStart}—${snapshot.windowEnd}）。") } }
                        Text(when(d.nextStep) { NumericNextStep.NONE->"以上仅陈述记录事实。"; NumericNextStep.REVIEW_ESTIMATE->"可选建议：下次可核对同类任务的预计时长，采纳前先查看来源。"; NumericNextStep.CONSIDER_SPLITTING->"可选建议：可参考实际执行段长度拆分任务，不自动改变日程。" })
                    }
                }
                Row { Checkbox(snapshot.aiConsent,vm::setAiConsent,enabled=enabled,modifier=Modifier.testTag("numeric-ai-consent")); Text("个性化发送授权：主动生成时仅发送以上有效数值、样本量、窗口与匿名参数 ID；不发送任务正文或原始日志。",style=MaterialTheme.typography.bodySmall) }
                if(!vm.remoteSupported) Text("此构建不支持远程描述，将使用本地事实摘要。",style=MaterialTheme.typography.bodySmall)
                Button(vm::generateDescription,enabled=enabled && !state.generating,modifier=Modifier.testTag("numeric-generate")) { Text(if(state.generating) "正在生成…" else "主动生成 / 刷新描述") }
                if(state.generating) TextButton(vm::cancelDescription,modifier=Modifier.testTag("numeric-cancel-generation")) { Text("取消生成，保留原内容") }
            }
        } },confirmButton={ TextButton({ vm.cancelDescription(); open=false },enabled=!state.busy,modifier=Modifier.testTag("numeric-close")) { Text("关闭") } })
    state.target?.let { target ->
        var minutes by rememberSaveable(target.task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest=vm::closeTask,title={ Text("核对 ${target.task.displayName}") },
            text={ Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("当前类别：${category(target.task.category)}；预计 ${target.task.totalDurationMinutes?.toString() ?: "未知"} 分钟。确认只标记当前输入来源，不补写开始前历史，也不改日程。")
                TextButton(vm::confirmInput,enabled=enabled,modifier=Modifier.testTag("numeric-confirm-input")) { Text("我明确确认以上当前输入") }
                if(target.task.status==TaskStatus.COMPLETED) {
                    Text("补录整个任务的累计实际总投入，包含所有轮次；不会再加会话或其他反馈分钟。记录来源为用户补录，更正会替换本项。")
                    OutlinedTextField(minutes,{ minutes=it },label={ Text("累计实际总投入（分钟）") },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=!state.busy,modifier=Modifier.testTag("numeric-total-input"))
                    TextButton({ vm.clearActual(target.task.id) },enabled=enabled,modifier=Modifier.testTag("numeric-clear-total")) { Text("移除本项累计投入确认，保留执行日志") }
                }
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("numeric-total-error")) }
            } },confirmButton={ if(target.task.status==TaskStatus.COMPLETED) TextButton({ vm.confirmActual(minutes) },enabled=enabled,modifier=Modifier.testTag("numeric-confirm-total")) { Text("确认补录累计投入") } },
            dismissButton={ TextButton(vm::closeTask,enabled=!state.busy,modifier=Modifier.testTag("numeric-cancel-total")) { Text("取消") } })
    }
    state.advice?.let { advice ->
        AlertDialog(onDismissRequest=vm::cancelAdvice,title={ Text("估时建议 · 待确认") },
            text={ Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("未修正基准 ${advice.originalMinutes} 分钟 × ${number(advice.factor)} = ${advice.suggestedMinutes} 分钟。仅采纳后改此任务预计，不改变当前计划。重复采纳使用同一基准。")
                Text("画像 ${advice.profileVersion.take(12)} · ${advice.parameterId}\n来源 ${advice.sources.joinToString()}",style=MaterialTheme.typography.bodySmall)
                snapshot?.parameters?.find { it.id=="rounds" && it.availability==NumericAvailability.USABLE }?.let { Text("可选拆分参考：实际执行段中位 ${number(it.value)} 分钟；并非用户偏好，不自动拆分。") }
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            } },confirmButton={ TextButton(vm::acceptAdvice,enabled=enabled,modifier=Modifier.testTag("numeric-accept-advice")) { Text("采纳预计时长") } },
            dismissButton={ TextButton(vm::cancelAdvice,enabled=!state.busy,modifier=Modifier.testTag("numeric-cancel-advice")) { Text("取消，保留原预计") } })
    }
}
