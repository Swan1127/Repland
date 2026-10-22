package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.ui.tools.SuggestionReason
import com.swan1127.repland.ui.tools.ToolId
import com.swan1127.repland.ui.tools.ToolSuggestion

/**
 * The metacognition tools, offered where the user already is.
 *
 * The tools themselves are full-screen destinations reached from the command palette, and that
 * stays true — this is not a second launcher. It is the answer to "which one, right now", which is
 * the question a palette cannot answer: a palette is searched by someone who already knows what
 * they want, and the whole point of these five tools is that the user has never heard of them.
 *
 * Renders nothing when there is nothing situational to say. An empty section heading would train
 * the user to scroll past the one moment the strip matters.
 */
@Composable
fun ToolSuggestionStrip(
    suggestions: List<ToolSuggestion>,
    onOpenTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth().testTag("tool-suggest"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.tool_suggest_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTool(suggestion.tool) }
                        .testTag("tool-suggest-${suggestion.tool.name.lowercase()}")
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(suggestion.tool.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = reasonText(suggestion),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.tool_suggest_open),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Fills the one substitution the reason string declares.
 *
 * Exhaustive over [SuggestionReason] on purpose: the sealed type and the resource travel together,
 * so a new kind of argument cannot be added without this `when` failing to compile. Passing the
 * wrong *type* would not be a compile error — `stringResource(id, arg)` boxes everything into
 * `Any` — which is why `ToolRecommendationResourceTest` reads the resource files and checks the
 * placeholder kind against the argument kind.
 */
@Composable
private fun reasonText(suggestion: ToolSuggestion): String =
    when (val reason = suggestion.reason) {
        null -> stringResource(suggestion.reasonRes)
        is SuggestionReason.TaskName -> stringResource(suggestion.reasonRes, reason.value)
        is SuggestionReason.Count -> stringResource(suggestion.reasonRes, reason.value)
    }
