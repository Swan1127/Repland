package com.swan1127.repland.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.*
import java.time.LocalDate

@Composable
fun QuickAvailabilityDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (QuickAvailability) -> Unit,
    canSave: Boolean = true, readNotice: (@Composable () -> Unit)? = null) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var weekly by rememberSaveable { mutableStateOf(false) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val startMinute = TimeBlockValidator.parseTime(start)
    val endMinute = TimeBlockValidator.parseEndTime(end)
    val value = if (parsedDate != null && startMinute != null && endMinute != null) QuickAvailability(parsedDate, startMinute, endMinute, weekly) else null
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("补充一段可用时间") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                readNotice?.invoke()
                Text("只需确认日期和起止时间。保存后重新排序并生成预览；确认预览前不会改动原计划。课程、休息和固定事项仍不可覆盖。")
                OutlinedTextField(date, { date = it }, label = { Text("日期（年-月-日）") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("quick-availability-date"), singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text("开始时间（例如 18:00）") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("quick-availability-start"), singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("结束时间（例如 20:00）") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("quick-availability-end"), singleLine = true)
                Row { Checkbox(checked = weekly, onCheckedChange = { weekly = it }, enabled = !busy)
                    Text("保存为每周同一天的常用时段", Modifier.padding(top = 12.dp)) }
                Text("${if (weekly) "每周重复，从现在生效；日期用于确定星期" else "仅此日期"}；保存的时间可在“我的”时间设置中修改。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = !busy && canSave && value?.isValid() == true,
            modifier = Modifier.heightIn(min = 48.dp).testTag("quick-availability-save")) { Text(if (busy) "正在保存…" else "保存并生成预览") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("暂不补充") } })
}
