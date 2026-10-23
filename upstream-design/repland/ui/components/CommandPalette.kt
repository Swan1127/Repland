package com.swan1127.repland.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R

/**
 * Sections of the command palette. Declaration order is the on-screen order, so the
 * most-used destinations sit at the top and rarely-touched settings sink to the bottom.
 */
enum class CommandGroup(@param:StringRes val labelRes: Int) {
    NAVIGATE(R.string.command_group_navigate),
    TASK(R.string.command_group_task),
    PLAN(R.string.command_group_plan),
    TIME(R.string.command_group_time),
    REVIEW(R.string.command_group_review),
    METHOD(R.string.command_group_method),
    DATA(R.string.command_group_data),
    APPEARANCE(R.string.command_group_appearance),
}

/**
 * One addressable action. Every entry is fully described by data, so the palette itself
 * has no knowledge of screens: adding a capability means adding an entry, not editing
 * the palette. That is what keeps the shell (3 tabs) small while every feature stays
 * reachable in two taps.
 */
data class CommandEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val group: CommandGroup,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onSelect: () -> Unit,
)

/**
 * Case-insensitive match over title, subtitle and the keyword list.
 *
 * The keyword list exists because Chinese UI text cannot be matched by romanised input:
 * typing "kebiao" would never hit "课表". Each entry therefore carries latin aliases
 * alongside the Chinese label.
 */
private fun CommandEntry.matches(query: String): Boolean {
    if (query.isEmpty()) return true
    val q = query.lowercase()
    if (title.lowercase().contains(q)) return true
    if (subtitle?.lowercase()?.contains(q) == true) return true
    return keywords.any { it.lowercase().contains(q) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    entries: List<CommandEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query, entries) { entries.filter { it.matches(query) } }
    val grouped = remember(filtered) {
        CommandGroup.entries.mapNotNull { group ->
            filtered.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { group to it }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("command-palette"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.command_open_palette)) },
                placeholder = { Text(stringResource(R.string.command_search_hint)) },
                leadingIcon = { Icon(PlannerIcons.Schedule, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("command-search"),
            )

            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.command_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.command_result_count, filtered.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    grouped.forEach { (group, groupEntries) ->
                        item(key = "header-${group.name}") {
                            Text(
                                text = stringResource(group.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(groupEntries, key = { it.id }) { entry ->
                            CommandRow(entry = entry, onDismiss = onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(entry: CommandEntry, onDismiss: () -> Unit) {
    val alpha = if (entry.enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = entry.enabled) {
                entry.onSelect()
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("command-${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}
