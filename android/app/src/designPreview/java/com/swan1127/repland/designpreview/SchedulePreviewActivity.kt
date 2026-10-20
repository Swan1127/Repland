package com.swan1127.repland.designpreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.components.DesignNavigationItem
import com.swan1127.repland.ui.schedule.ScheduleCreateCard
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.theme.ReplandTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only design showcase. No view models, repositories, keys, or stored task data. */
class SchedulePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ReplandTheme {
            val day = remember { LocalDate.of(2026, 10, 15) }
            val priorities = remember { mapOf("high" to TaskPriority.HIGH, "middle" to TaskPriority.MEDIUM, "low" to TaskPriority.LOW) }
            val entries = remember { listOf(
                TimelineEntry("high", "完成阅读报告", TimelineKind.TASK, day, 540, 600, taskId = "high", note = "图书馆 · 先整理核心观点"),
                TimelineEntry("middle", "整理课堂笔记", TimelineKind.TASK, day, 570, 630, taskId = "middle", lane = 1, note = "主校区 3 号楼 405"),
                TimelineEntry("low", "复习英语词汇", TimelineKind.TASK, day, 570, 600, taskId = "low", lane = 2),
                TimelineEntry("done-reading", "晨间阅读", TimelineKind.TASK, day, 420, 450, taskId = "done-reading", taskClosed = true, note = "图书馆 · 已确认完成"),
                TimelineEntry("done-notes", "整理学习资料", TimelineKind.TASK, day, 460, 500, taskId = "done-notes", taskClosed = true, note = "主校区 3 号楼"),
            ) }
            var message by remember { mutableStateOf(false) }
            Scaffold(
                topBar = { Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(day.format(DateTimeFormatter.ofPattern("yyyy/MM/dd E", Locale.CHINA)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("演示日程 · 不写入你的计划", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } },
                bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    listOf("日程" to DesignIcons.Calendar, "任务" to DesignIcons.Tasks, "我的" to DesignIcons.Profile).forEachIndexed { index, (label, icon) ->
                        DesignNavigationItem(selected = index == 0, onClick = { if (index != 0) finish() }, icon = { Icon(icon, null) }, label = { Text(label) }, accentIndex = if (index == 2) 4 else index)
                    }
                } },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScheduleCreateCard(onAdd = { message = true })
                    TimelineDashboard(entries, EngagementMode.GUIDED, {}, { message = true },
                        taskPriorities = priorities, completedTaskIds = setOf("done-reading", "done-notes"), clockNow = day.atTime(9, 45))
                }
            }
            if (message) AlertDialog(onDismissRequest = { message = false }, title = { Text("这是演示日程") },
                text = { Text("可以切换总计、详细，查看时间与优先级。实际新增和编辑请返回应用主页。") },
                confirmButton = { TextButton(onClick = { message = false }) { Text("知道了") } })
        } }
    }
}
