package com.swan1127.repland.ui.agent

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class AgentTaskPlacement(
    val taskId: String,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String,
    val date: LocalDate,
)

data class AgentReceiptPlacement(
    val segmentId: String,
    val taskId: String,
    val taskTitle: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String,
)

data class AgentActionReceipt(
    val id: String = UUID.randomUUID().toString(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val date: LocalDate,
    val createdCount: Int,
    val pendingCount: Int,
    val arrangedCount: Int,
    val taskIds: List<String>,
    val taskTitles: List<String> = emptyList(),
    val isComplete: Boolean = true,
    val placements: List<AgentReceiptPlacement> = emptyList(),
)

sealed interface AgentSubmissionResult {
    data class Success(val receipt: AgentActionReceipt) : AgentSubmissionResult
    data class Failure(
        val message: String,
        val receipt: AgentActionReceipt? = null,
        val savedTaskIds: Map<String, String> = emptyMap(),
    ) : AgentSubmissionResult
}

class AgentUiStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("agent_ui_state", Context.MODE_PRIVATE)

    fun clear() {
        check(preferences.edit().clear().commit())
    }

    fun loadDraft(entry: String): String = preferences.getString("draft_$entry", "").orEmpty()

    fun saveDraft(entry: String, text: String) {
        preferences.edit().putString("draft_$entry", text).apply()
    }

    fun loadProposals(entry: String, date: LocalDate): List<AgentTaskProposal> = runCatching {
        val stored = JSONArray(preferences.getString("proposals_${entry}_$date", "[]"))
        (0 until stored.length()).map { index ->
            val item = stored.getJSONObject(index)
            AgentTaskProposal(
                id = item.getString("id"),
                text = item.getString("text"),
                category = TaskCategory.valueOf(item.getString("category")),
                durationMinutes = item.optInt("duration", -1).takeIf { it > 0 },
                timeHint = ArrangementTimeHint(
                    explicitStartMinute = item.optInt("start", -1).takeIf { it >= 0 },
                    windowLabel = item.optString("window").takeIf { it.isNotBlank() },
                ),
                needsClarification = item.optJSONArray("missing").toStrings().map { ArrangementClarification.valueOf(it) }.toSet(),
                placementSource = ArrangementPlacementSource.valueOf(item.getString("source")),
                trackId = item.getString("track"),
                priority = item.optString("priority").takeIf { it.isNotBlank() }?.let(TaskPriority::valueOf),
                durationEstimated = item.optBoolean("estimated", false),
            )
        }
    }.getOrDefault(emptyList())

    fun saveProposals(entry: String, date: LocalDate, proposals: List<AgentTaskProposal>) {
        val stored = JSONArray()
        proposals.forEach { proposal ->
            stored.put(JSONObject().apply {
                put("id", proposal.id)
                put("text", proposal.text)
                put("category", proposal.category.name)
                put("duration", proposal.durationMinutes ?: -1)
                put("start", proposal.timeHint.explicitStartMinute ?: -1)
                put("window", proposal.timeHint.windowLabel.orEmpty())
                put("missing", JSONArray(proposal.needsClarification.map { it.name }))
                put("source", proposal.placementSource.name)
                put("track", proposal.trackId)
                put("priority", proposal.priority?.name.orEmpty())
                put("estimated", proposal.durationEstimated)
            })
        }
        preferences.edit().putString("proposals_${entry}_$date", stored.toString()).apply()
    }

    fun receipts(): List<AgentActionReceipt> = runCatching {
        val stored = JSONArray(preferences.getString("receipts", "[]"))
        (0 until stored.length()).mapNotNull { index ->
            val item = stored.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                AgentActionReceipt(
                    id = item.getString("id"),
                    createdAtEpochMillis = item.getLong("createdAt"),
                    date = LocalDate.parse(item.getString("date")),
                    createdCount = item.getInt("created"),
                    pendingCount = item.getInt("pending"),
                    arrangedCount = item.getInt("arranged"),
                    taskIds = item.optJSONArray("taskIds").toStrings(),
                    taskTitles = item.optJSONArray("taskTitles").toStrings(),
                    isComplete = item.optBoolean("complete", true),
                    placements = item.optJSONArray("placements").toReceiptPlacements(),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun record(receipt: AgentActionReceipt) {
        val history = (listOf(receipt) + receipts().filterNot { it.id == receipt.id }).take(30)
        val stored = JSONArray()
        history.forEach { item ->
            stored.put(JSONObject().apply {
                put("id", item.id)
                put("createdAt", item.createdAtEpochMillis)
                put("date", item.date.toString())
                put("created", item.createdCount)
                put("pending", item.pendingCount)
                put("arranged", item.arrangedCount)
                put("taskIds", JSONArray(item.taskIds))
                put("taskTitles", JSONArray(item.taskTitles))
                put("complete", item.isComplete)
                put("placements", JSONArray().apply {
                    item.placements.forEach { placement ->
                        put(JSONObject().apply {
                            put("segmentId", placement.segmentId)
                            put("taskId", placement.taskId)
                            put("taskTitle", placement.taskTitle)
                            put("date", placement.date.toString())
                            put("start", placement.startMinute)
                            put("end", placement.endMinute)
                            put("track", placement.trackId)
                        })
                    }
                })
            })
        }
        preferences.edit().putString("receipts", stored.toString()).apply()
    }
}

private fun JSONArray?.toStrings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { index -> getString(index) }

private fun JSONArray?.toReceiptPlacements(): List<AgentReceiptPlacement> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        runCatching {
            val item = getJSONObject(index)
            val start = item.getInt("start")
            val end = item.getInt("end")
            require(start in 0 until 1_440 && end in 1..1_440 && start < end)
            AgentReceiptPlacement(
                segmentId = item.getString("segmentId"),
                taskId = item.getString("taskId"),
                taskTitle = item.getString("taskTitle"),
                date = LocalDate.parse(item.getString("date")),
                startMinute = start,
                endMinute = end,
                trackId = item.getString("track"),
            )
        }.getOrNull()
    }

internal fun receiptTimeLabel(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@Composable
fun AgentReceiptCard(receipt: AgentActionReceipt, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("agent-receipt-${receipt.id}"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when {
                    !receipt.isComplete -> "部分事项已保存"
                    receipt.arrangedCount > 0 -> "已安排到具体时间"
                    else -> "已记下，待安排"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "${receipt.date} · 新增 ${receipt.createdCount} 项 · 待安排 ${receipt.pendingCount} 项 · 已安排 ${receipt.arrangedCount} 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (receipt.taskTitles.isNotEmpty()) {
                Text(receipt.taskTitles.joinToString("、"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            receipt.placements.forEach { placement ->
                Text(
                    "${placement.taskTitle} · ${placement.date} ${receiptTimeLabel(placement.startMinute)}–${receiptTimeLabel(placement.endMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                Instant.ofEpochMilli(receipt.createdAtEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun AgentReceiptHistoryDialog(receipts: List<AgentActionReceipt>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最近变更") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("这里保存最近 30 次实际写入结果。回执用于回看，撤销以当前可用操作为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (receipts.isEmpty()) Text("还没有已确认的变更。")
                receipts.forEach { receipt -> AgentReceiptCard(receipt) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
