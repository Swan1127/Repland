package com.swan1127.repland.ui.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.components.DesignSectionHeading

private class AssistantImeBridge {
    var visible = false
    var closing = false
    var hide: () -> Unit = {}
}

/** A single input surface over the schedule, with the same dismissal rules as task capture. */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun AutonomousAssistantSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val latestBusy = rememberUpdatedState(busy)
    val ime = remember { AssistantImeBridge() }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            when {
                target != SheetValue.Hidden -> true
                latestBusy.value -> false
                ime.visible && !ime.closing -> { ime.hide(); false }
                else -> true
            }
        })
    fun close() {
        if (!latestBusy.value) {
            ime.closing = true
            ime.hide()
            scope.launch {
                sheet.hide()
                if (!sheet.isVisible) onDismiss() else ime.closing = false
            }
        }
    }
    ModalBottomSheet(
        modifier = Modifier.testTag("assistant-sheet"),
        onDismissRequest = { if (!latestBusy.value && !sheet.isVisible) onDismiss() },
        sheetState = sheet,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val imeVisible = WindowInsets.isImeVisible
        SideEffect {
            ime.visible = imeVisible
            ime.hide = { focus.clearFocus(); keyboard?.hide() }
        }
        BackHandler {
            if (!latestBusy.value) {
                if (imeVisible) ime.hide() else close()
            }
        }
        Column(Modifier.fillMaxWidth().imePadding()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp)
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                DesignSectionHeading("一句话处理", DesignIcons.Assistant, accentIndex = 2,
                    modifier = Modifier.weight(1f), large = true)
                IconButton(onClick = ::close, enabled = !busy, modifier = Modifier.testTag("assistant-close")) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭并保留输入")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
        }
    }
}
