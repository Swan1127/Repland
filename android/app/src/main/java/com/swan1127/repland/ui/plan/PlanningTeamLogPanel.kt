package com.swan1127.repland.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.domain.planteam.PlanTeamResult
import com.swan1127.repland.domain.planteam.runtime.AcceptanceFinished
import com.swan1127.repland.domain.planteam.runtime.LogTone
import com.swan1127.repland.domain.planteam.runtime.TeamEvent
import com.swan1127.repland.domain.planteam.runtime.ToolCalled
import com.swan1127.repland.domain.planteam.runtime.displayName
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.theme.MotionDisclosure
import com.swan1127.repland.ui.theme.LocalReducedMotion

@Composable
fun PlanningTeamLogPanel(
    events: List<TeamEvent>,
    result: PlanTeamResult? = null,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    var expanded by rememberSaveable(events.first().atMillis) { mutableStateOf(false) }
    val acceptance = events.filterIsInstance<AcceptanceFinished>().lastOrNull()
    Surface(
        modifier = modifier.fillMaxWidth().testTag("team-log"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.planning_team_log), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.planning_team_counts, events.mapNotNull { it.agent }.distinct().size, events.count { it is ToolCalled }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            acceptance?.takeIf { result != null || !it.passed }?.let {
                Text(
                    stringResource(if (it.passed) R.string.planning_team_checked else R.string.planning_team_check_failed),
                    color = if (it.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!expanded) Text(events.last().summary, style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp).testTag("team-log-expand"),
            ) { Text(stringResource(if (expanded) R.string.planning_team_log_collapse else R.string.planning_team_log_expand)) }
            MotionDisclosure(expanded) {
                events.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        event.agent?.let { Text(it.displayName(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        Text(
                            event.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (event.tone == LogTone.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                result?.hints?.profileNotes?.distinct()?.forEach { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PlanningTeamStatusDialog(
    isGenerating: Boolean,
    events: List<TeamEvent>,
    error: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.planning_team_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.planning_team_description), style = MaterialTheme.typography.bodyMedium)
                if (isGenerating) {
                    if (!LocalReducedMotion.current) CircularProgressIndicator()
                    Text(stringResource(R.string.planning_team_generating))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PlanningTeamLogPanel(events)
            }
        },
        confirmButton = {
            if (!isGenerating) DesignButton(onClick = onRetry) { Text(stringResource(R.string.planning_team_retry)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
