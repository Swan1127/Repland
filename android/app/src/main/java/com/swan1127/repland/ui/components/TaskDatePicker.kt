package com.swan1127.repland.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A date-only control shared by capture and editing. A deadline belongs to a calendar date in
 * the current domain model; the Material picker deliberately keeps both calendar and numeric
 * input modes available instead of coercing people into a handful of shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
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
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("task-date-picker-dismiss"),
            ) { Text(dismissLabel) }
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

fun taskDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    else -> if (date.year == today.year) "${date.monthValue} 月 ${date.dayOfMonth} 日" else "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"
}
