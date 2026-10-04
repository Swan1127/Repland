package com.swan1127.repland.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskPageAppBar(
    title: String,
    canUndo: Boolean,
    undoEnabled: Boolean,
    sortEnabled: Boolean,
    onUndo: () -> Unit,
    onSort: () -> Unit,
) {
    val actions: @Composable () -> Unit = {
        if (canUndo) TextButton(onClick = onUndo, enabled = undoEnabled,
            modifier = Modifier.heightIn(min = 48.dp).testTag("undo-task-sort")) { Text("撤销排序") }
        TextButton(onClick = onSort, enabled = sortEnabled,
            modifier = Modifier.heightIn(min = 48.dp).testTag("auto-sort-tasks")) { Text("自动排序") }
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(horizontal = 16.dp)) {
            if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 360.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("task-page-title"))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { actions() }
                }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).testTag("task-page-title"))
                    actions()
                }
            }
        }
    }
}

@Composable
internal fun TaskCaptureButton(
    onClick: () -> Unit,
    insets: WindowInsets = WindowInsets.safeDrawing,
) {
    Box(Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)).testTag("task-add-safe-area")) {
        androidx.compose.material3.FloatingActionButton(onClick = onClick, modifier = Modifier.testTag("add-task")) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Add, contentDescription = "添加任务")
        }
    }
}
