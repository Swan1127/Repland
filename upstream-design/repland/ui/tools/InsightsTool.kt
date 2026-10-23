package com.swan1127.repland.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.CalibrationDirection
import com.swan1127.repland.domain.model.CalibrationSummary
import com.swan1127.repland.domain.model.CategoryMinutes
import com.swan1127.repland.domain.model.DayCount
import com.swan1127.repland.domain.model.DayMinutes
import com.swan1127.repland.domain.model.LearningInsights
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * What the app has learned about how this person actually works, as opposed to how they
 * planned to work.
 *
 * Every number here comes from [LearningInsights], which is pure and unit-tested; this file
 * only draws. The rule it follows throughout is that **an empty chart is never shown** — a
 * blank plot with axes reads as "you did nothing", which is both unkind and, on a fresh
 * install, untrue. Each section says what it is waiting for instead.
 */

private const val TREND_DAYS = 30
private const val HEATMAP_WEEKS = 8

@Composable
fun InsightsTool(
    tasks: List<Task>,
    logs: List<TaskExecutionLog>,
    reviewPointDates: List<LocalDate>,
    today: LocalDate,
    onClose: () -> Unit,
) {
    ToolScaffold(title = stringResource(R.string.insights_title), onClose = onClose) {
        val zone = remember { ZoneId.systemDefault() }
        val calibration = remember(tasks) { LearningInsights.calibration(tasks) }
        val trend = remember(logs, today) {
            LearningInsights.minutesByDay(
                logs = logs,
                zone = zone,
                from = today.minusDays((TREND_DAYS - 1).toLong()),
                days = TREND_DAYS,
            )
        }
        val categories = remember(tasks) { LearningInsights.categoryMinutes(tasks) }
        // Aligned to Monday so each heat-map column is a real week and each row is a real
        // weekday; without that the grid is decorative rather than readable.
        val heatmapStart = remember(today) {
            today.minusDays((HEATMAP_WEEKS * 7 - 1).toLong())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val reviewCounts = remember(reviewPointDates, heatmapStart) {
            LearningInsights.reviewPointCounts(
                dates = reviewPointDates,
                from = heatmapStart,
                days = HEATMAP_WEEKS * 7,
            )
        }

        SectionTitle("预估准不准")
        Text(
            calibration.direction.explanation(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (calibration.points.isEmpty()) {
            WaitingCard("完成一件任务并记录实际用时之后，这里会把你每次的「预计」和「实际」放在一起比较。")
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CalibrationChart(
                        summary = calibration,
                        accent = MaterialTheme.colorScheme.primary,
                        muted = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        "横轴是预计用时，纵轴是实际用时。虚线是「预计 = 实际」，点在虚线以上说明这件事比你想的更花时间。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    calibration.medianRatio?.let { median ->
                        Text(
                            "已完成 ${calibration.count} 件 · 中位数 实际/预计 = ${"%.2f".format(median)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        SectionTitle("最近 30 天的投入")
        if (trend.all { it.minutes == 0 }) {
            WaitingCard("记录过一次专注或一次任务反馈之后，这里会显示每天的投入时长。")
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TrendChart(
                        days = trend,
                        accent = MaterialTheme.colorScheme.primary,
                        muted = MaterialTheme.colorScheme.outlineVariant,
                    )
                    val totalMinutes = trend.sumOf(DayMinutes::minutes)
                    Text(
                        "合计 ${totalMinutes / 60} 小时 ${totalMinutes % 60} 分 · 最多的一天 ${trend.maxOf(DayMinutes::minutes)} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        SectionTitle("时间花在哪一类事情上")
        if (categories.isEmpty()) {
            WaitingCard("有任务记录实际用时之后，这里会按类别拆开显示。")
        } else {
            val palette = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.outline,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CategoryDonut(rows = categories, colors = palette)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEachIndexed { index, row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = palette[index % palette.size],
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.size(10.dp),
                                ) {}
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${row.category.label()} ${row.minutes} 分",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionTitle("复习点的节奏")
        if (reviewCounts.all { it.count == 0 }) {
            WaitingCard("在任务里记下卡住的地方之后，这里会显示你积累复习点的节奏。")
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ReviewHeatmap(
                        counts = reviewCounts,
                        accent = MaterialTheme.colorScheme.primary,
                        muted = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        "每列是一周，每行是周一到周日。颜色越深，那天记下的复习点越多。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "这些数字由本机记录计算；启用 AI 并确认请求后，相关记录摘要可能用于建议。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------- the charts

/**
 * Predicted against actual, one dot per finished task.
 *
 * Square on purpose: at any other aspect ratio the "took longer than planned" reading of
 * "above the line" stops matching the drawn angle, and the chart starts to look wrong even
 * though the maths is fine.
 */
@Composable
private fun CalibrationChart(summary: CalibrationSummary, accent: Color, muted: Color) {
    val maxMinutes = remember(summary) {
        summary.points
            .maxOfOrNull { maxOf(it.predictedMinutes, it.actualMinutes) }
            ?.coerceAtLeast(1)
            ?: 1
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("insights-calibration-chart"),
    ) {
        drawLine(
            color = muted,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
        )
        summary.points.forEach { point ->
            val x = point.predictedMinutes.toFloat() / maxMinutes * size.width
            val y = size.height - point.actualMinutes.toFloat() / maxMinutes * size.height
            drawCircle(color = accent.copy(alpha = 0.75f), radius = 7f, center = Offset(x, y))
        }
    }
}

@Composable
private fun TrendChart(days: List<DayMinutes>, accent: Color, muted: Color) {
    val maxMinutes = remember(days) { days.maxOfOrNull(DayMinutes::minutes)?.coerceAtLeast(1) ?: 1 }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .testTag("insights-trend-chart"),
    ) {
        drawLine(
            color = muted,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 3f,
        )
        if (days.size < 2) return@Canvas
        val step = size.width / (days.size - 1).toFloat()
        var previous: Offset? = null
        days.forEachIndexed { index, day ->
            val point = Offset(
                x = index * step,
                y = size.height - day.minutes.toFloat() / maxMinutes * size.height,
            )
            previous?.let { drawLine(color = accent, start = it, end = point, strokeWidth = 5f) }
            previous = point
        }
    }
}

@Composable
private fun CategoryDonut(rows: List<CategoryMinutes>, colors: List<Color>) {
    val total = rows.sumOf(CategoryMinutes::minutes).coerceAtLeast(1)
    Canvas(modifier = Modifier.size(132.dp).testTag("insights-category-donut")) {
        // The stroke is centred on the arc path, so the geometry is inset by half of it;
        // otherwise the outer half of every ring is clipped by the canvas bounds.
        val stroke = 30f
        val inset = stroke / 2f
        var startAngle = -90f
        rows.forEachIndexed { index, row ->
            val sweep = row.minutes.toFloat() / total * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun ReviewHeatmap(counts: List<DayCount>, accent: Color, muted: Color) {
    val maxCount = remember(counts) { counts.maxOfOrNull(DayCount::count)?.coerceAtLeast(1) ?: 1 }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HEATMAP_WEEKS.toFloat() / 7f)
            .testTag("insights-review-heatmap"),
    ) {
        if (counts.isEmpty()) return@Canvas
        val gap = 5f
        val columns = (counts.size + 6) / 7
        val cell = ((size.width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
        counts.forEachIndexed { index, day ->
            val column = index / 7
            val row = index % 7
            val intensity = day.count.toFloat() / maxCount
            drawRect(
                color = if (day.count == 0) muted else accent.copy(alpha = 0.25f + 0.75f * intensity),
                topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
            )
        }
    }
}

// ---------------------------------------------------------------------- small pieces

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** What a section is waiting for, instead of an empty chart that would read as "you did nothing". */
@Composable
private fun WaitingCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CalibrationDirection.explanation(): String = when (this) {
    CalibrationDirection.UNDERESTIMATED -> "你倾向于低估一件事需要多少时间。排计划时可以把预估再往上抬一点。"
    CalibrationDirection.OVERESTIMATED -> "你倾向于高估一件事需要多少时间，实际往往比预计更快。"
    CalibrationDirection.CALIBRATED -> "你的预估和实际比较接近，排出来的计划是可执行的。"
    CalibrationDirection.INSUFFICIENT_DATA -> "还看不出规律。至少完成 3 件并记录实际用时，这里才会给出结论。"
}

private fun TaskCategory.label(): String = when (this) {
    TaskCategory.COURSE -> "课程"
    TaskCategory.EXTRACURRICULAR -> "课外"
    TaskCategory.OFFICE -> "事务"
    TaskCategory.LEISURE -> "生活"
}
