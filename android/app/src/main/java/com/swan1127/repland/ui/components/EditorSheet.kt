package com.swan1127.repland.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Bounded editing surface: scrollable content keeps the footer reachable above the IME. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    saving: Boolean = false,
) {
    val latestSaving = rememberUpdatedState(saving)
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !latestSaving.value })
    ModalBottomSheet(
        modifier = Modifier.testTag("editor-sheet"),
        onDismissRequest = { if (!saving && !state.isVisible) onDismissRequest() },
        sheetState = state,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        val imeVisible = WindowInsets.isImeVisible
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        BackHandler {
            if (!saving) {
                if (imeVisible) { focus.clearFocus(); keyboard?.hide() }
                else scope.launch { state.hide(); if (!state.isVisible) onDismissRequest() }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { text() }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                dismissButton()
                Spacer(Modifier.width(8.dp))
                confirmButton()
            }
        }
    }
}
