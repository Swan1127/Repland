package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.WeeklyBlockConflict
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.ui.components.currentLocale
import java.time.format.TextStyle

/**
 * Says out loud what the timeline draws as if it were deliberate.
 *
 * The week columns resolve an overlap by giving each row its own lane, so two double-booked blocks
 * look exactly like two intentionally parallel ones. This banner is the only place that tells them
 * apart, and it is the reason the week view is its home: a conflict is a property of the whole
 * week, not of the day the user happens to be looking at.
 *
 * Renders nothing when there is nothing to say. A permanently visible "0 conflicts" line would be
 * one more thing to scroll past, on a screen whose whole job is to show the shape of the week.
 */
@Composable
fun WeeklyConflictBanner(
    conflicts: List<WeeklyBlockConflict>,
    onOpenBlock: (WeeklyTimeBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conflicts.isEmpty()) return
    val locale = currentLocale()
    Surface(
        modifier = modifier.fillMaxWidth().testTag("conflict-banner"),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.conflict_banner_title, conflicts.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.conflict_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            conflicts.forEachIndexed { index, conflict ->
                // Opening the first row is enough to act on the pair: the editor shows that row's
                // own day and minutes, which is where the mistake almost always is. Naming both
                // rows in the text is what lets the user judge which one to move.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBlock(conflict.first) }
                        .testTag("conflict-row-$index")
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buildString {
                            append(conflict.dayOfWeek.getDisplayName(TextStyle.SHORT, locale))
                            append(' ')
                            append(TimeBlockValidator.formatTime(conflict.startMinute))
                            append('–')
                            append(TimeBlockValidator.formatTime(conflict.endMinute))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.conflict_row_pair,
                            conflict.first.title,
                            conflict.second.title,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
