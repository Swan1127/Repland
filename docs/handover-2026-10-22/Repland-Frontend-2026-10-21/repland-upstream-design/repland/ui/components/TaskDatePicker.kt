package com.swan1127.repland.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swan1127.repland.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A date-only control shared by capture and editing. A deadline belongs to a calendar date in
 * the current domain model; the Material picker deliberately keeps both calendar and numeric
 * input modes available instead of coercing people into a handful of shortcuts.
 *
 * The two button labels arrive as **resource ids, not strings**. They used to default to the
 * Chinese literals `"确定"` / `"取消"` written into the parameter list, which is a hard-coded
 * language in the one place that is easiest to overlook: a default value is not a UI string, so
 * no `values-en/` file can reach it and no reviewer reading the call sites ever sees it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    @StringRes confirmLabel: Int = R.string.date_picker_confirm,
    @StringRes dismissLabel: Int = R.string.cancel,
) {
    val initialMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayedMonthMillis = initialMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = pickerState.selectedDateMillis ?: return@TextButton
                    onDateSelected(
                        Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate(),
                    )
                },
                enabled = pickerState.selectedDateMillis != null,
                modifier = Modifier.testTag("task-date-picker-confirm"),
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("task-date-picker-dismiss"),
            ) { Text(stringResource(dismissLabel)) }
        },
    ) {
        // showModeToggle is enabled by default: calendar navigation and precise numeric input
        // are equally available, including dates outside the common shortcut range.
        DatePicker(
            state = pickerState,
            modifier = Modifier.testTag("task-date-picker"),
        )
    }
}
