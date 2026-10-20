package com.swan1127.repland.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swan1127.repland.ui.components.EditorSheet
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignOutlinedButton
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.components.DesignSectionHeading
import com.swan1127.repland.ui.theme.replandSettingsAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UsageSummarySection(viewModel: UsageSummaryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UsageSummaryCard(state)
}

/** The profile is a read-only explanation of ordinary usage, with optional evidence. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun UsageSummaryCard(state: UsageSummaryUiState) {
    var showSources by rememberSaveable { mutableStateOf(false) }
    val accent = replandSettingsAccent(0)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("usage-summary-card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignSectionHeading("近期使用总结", DesignIcons.Clock, accent = accent)
            Text(
                state.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("usage-summary-text"),
            )
            if (state.loading || state.updating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("usage-summary-progress"),
                    color = accent.ink, trackColor = accent.surface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val status = state.readError ?: state.notice ?: when {
                state.loading -> "正在整理你的使用记录…"
                state.updating -> "正在自动更新，先保留上次总结。"
                state.pendingChanges -> "已有新记录，将自动更新总结。"
                state.isAiSummary -> "AI 根据使用记录自动总结"
                else -> "根据使用记录自动整理"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("usage-summary-status").semantics { liveRegion = LiveRegionMode.Polite },
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    state.updatedAt?.let { "更新于 ${summaryDate(it)}" } ?: "正常使用后，会在这里逐渐形成总结",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp).testTag("usage-summary-updated-at"),
                )
                DesignOutlinedButton(
                    onClick = { showSources = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent.ink),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("usage-summary-sources"),
                ) { Text("查看依据") }
            }
        }
    }
    if (showSources) {
        EditorSheet(
            onDismissRequest = { showSources = false },
            title = { Text("总结依据") },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("usage-summary-basis"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.windowStart != null && state.windowEnd != null) {
                        Text("${state.windowStart} — ${state.windowEnd}", style = MaterialTheme.typography.labelMedium)
                    }
                    Text("自动整理任务、进度、实际执行和主动调整记录，无需填写或补录。", style = MaterialTheme.typography.bodyMedium)
                    if (state.basis.isEmpty()) {
                        Text("目前记录还不多。照常使用 App，有记录后会自动更新。")
                    } else {
                        state.basis.forEach { fact ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Text(fact.text, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text("操作时段与实际执行时段分别记录；没有反馈的任务保持未知。总结不会修改任务或日程。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                DesignButton(onClick = { showSources = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("usage-summary-close")) { Text("关闭") }
            },
        )
    }
}

private fun summaryDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
