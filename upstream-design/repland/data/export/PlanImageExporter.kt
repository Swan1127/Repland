package com.swan1127.repland.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws the plan or the weekly timetable onto a bitmap so it can be saved as a PNG.
 *
 * Rendering on a Canvas instead of screenshotting the UI keeps the output independent of
 * screen size, scroll position and theme — the PNG stays legible when it is opened later,
 * on another device, or printed. Colours are pinned to a light palette on purpose: a dark
 * screenshot of a plan is unreadable on paper and awkward to send to anyone.
 *
 * The bitmap is handed to a SAF destination chosen by the user, so this needs **no storage
 * permission at all** on any API level, and nothing leaves the device.
 *
 * Everything the image says arrives in [PlanImageTexts] and is turned into words by the functions
 * in `PlanImageText.kt`; this object names no language of its own.
 */
object PlanImageExporter {

    private const val WIDTH = 1080
    private const val MARGIN = 64f
    private const val TIME_COLUMN = 300f
    private const val TEXT_GAP = 16f
    private const val ROW_PADDING = 22f
    private const val ROW_GAP = 14f
    private const val SECTION_GAP = 28f

    private val ink = Color.parseColor("#1B1B1B")
    private val inkSoft = Color.parseColor("#6B7280")
    private val paper = Color.parseColor("#F7F7F2")
    private val accent = Color.parseColor("#276454")
    private val card = Color.WHITE

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 58f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkSoft
        textSize = 34f
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 38f
    }
    private val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkSoft
        textSize = 30f
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkSoft
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card }

    /**
     * The clock is deliberately **not** localised: it is a pair of numbers, `Locale.US` guarantees
     * ASCII digits, and a timetable that printed Eastern Arabic numerals next to Latin course names
     * would be harder to read, not easier. Nothing here is language.
     */
    private fun clock(minute: Int): String =
        String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

    private val nameColumnWidth = (WIDTH - (MARGIN + TIME_COLUMN) - MARGIN).toInt()

    /** Measured once, then drawn verbatim: row heights follow content instead of clipping it. */
    internal data class TextBlock(val layout: StaticLayout, val left: Float, val top: Float) {
        val bottom: Float get() = top + layout.height
        val text: String get() = layout.text.toString()
    }

    internal data class Row(val sourceId: String, val card: RectF, val texts: List<TextBlock>)
    internal data class Section(val heading: TextBlock?, val rows: List<Row>)
    internal data class ImageLayout(val header: List<TextBlock>, val sections: List<Section>, val height: Int)

    private fun textLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(8f, 1f)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun header(title: String, subtitle: String, note: String? = null): List<TextBlock> {
        var top = MARGIN
        return buildList {
            listOfNotNull(title to titlePaint, subtitle to subtitlePaint, note?.let { it to detailPaint })
                .forEach { (text, paint) ->
                    val block = TextBlock(textLayout(text, paint, (WIDTH - MARGIN * 2).toInt()), MARGIN, top)
                    add(block)
                    top = block.bottom + TEXT_GAP
                }
        }
    }

    private fun row(sourceId: String, top: Float, time: String, title: String, detail: String? = null): Row {
        val timeText = TextBlock(textLayout(time, timePaint, (TIME_COLUMN - 24).toInt()), MARGIN, top + ROW_PADDING)
        val titleText = TextBlock(textLayout(title, bodyPaint, nameColumnWidth), MARGIN + TIME_COLUMN, top + ROW_PADDING)
        val detailText = detail?.let {
            TextBlock(textLayout(it, detailPaint, nameColumnWidth), MARGIN + TIME_COLUMN, titleText.bottom + 8f)
        }
        val texts = listOfNotNull(timeText, titleText, detailText)
        return Row(sourceId, RectF(MARGIN - 20, top, WIDTH - MARGIN + 20, texts.maxOf { it.bottom } + ROW_PADDING), texts)
    }

    /**
     * Renders a single day of the plan.
     *
     * [segments] is expected to be already filtered to [date] and sorted by start minute —
     * the caller owns that ordering, so this renderer holds no opinion about the planner.
     */
    fun renderPlan(
        date: LocalDate,
        segments: List<PlannedSegment>,
        tasks: List<Task>,
        texts: PlanImageTexts,
    ): Bitmap = draw(layoutPlan(date, segments, tasks, texts))

    internal fun layoutPlan(
        date: LocalDate,
        segments: List<PlannedSegment>,
        tasks: List<Task>,
        texts: PlanImageTexts,
    ): ImageLayout {
        val nameById = tasks.associate { it.id to it.displayName }
        val header = header(planHeaderDate(date, texts), planSubtitle(segments.size, texts))
        var top = header.last().bottom + 32f
        val rows = segments.map { segment ->
            row(
                segment.id, top, "${clock(segment.startMinute)}–${clock(segment.endMinute)}",
                nameById[segment.taskId] ?: texts.missingTask,
            ).also { top = it.card.bottom + ROW_GAP }
        }
        return ImageLayout(header, listOf(Section(null, rows)), ceil(top + MARGIN).toInt().coerceAtLeast(720))
    }

    /** All teaching weeks are shown; each course keeps its full week notation below its title. */
    fun renderTimetable(blocks: List<WeeklyTimeBlock>, texts: PlanImageTexts): Bitmap =
        draw(layoutTimetable(blocks, texts))

    internal fun layoutTimetable(blocks: List<WeeklyTimeBlock>, texts: PlanImageTexts): ImageLayout {
        val header = header(
            texts.weekTitle,
            timetableSubtitle(blocks.size, texts),
            texts.timetableOverview.takeIf { blocks.isNotEmpty() },
        )
        var top = header.last().bottom + 32f
        val sections = DayOfWeek.entries.mapNotNull { day ->
            val dayBlocks = blocks.filter { it.dayOfWeek == day }.sortedBy { it.startMinute }
            if (dayBlocks.isEmpty()) return@mapNotNull null
            val heading = TextBlock(
                textLayout(weekdayName(day, texts), dayPaint, (WIDTH - MARGIN * 2).toInt()), MARGIN, top,
            )
            top = heading.bottom + TEXT_GAP
            val rows = dayBlocks.map { block ->
                row(
                    block.id, top, "${clock(block.startMinute)}–${clock(block.endMinute)}", block.title,
                    timetableWeekDetail(block.weekPattern, texts),
                ).also { top = it.card.bottom + ROW_GAP }
            }
            top += SECTION_GAP
            Section(heading, rows)
        }
        return ImageLayout(header, sections, ceil(top + MARGIN).toInt().coerceAtLeast(720))
    }

    private fun draw(layout: ImageLayout): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paper)
        layout.header.forEach { drawText(canvas, it) }
        layout.sections.forEach { section ->
            section.heading?.let { drawText(canvas, it) }
            section.rows.forEach { row ->
                canvas.drawRoundRect(row.card, 16f, 16f, cardPaint)
                row.texts.forEach { drawText(canvas, it) }
            }
        }
        return bitmap
    }

    private fun drawText(canvas: Canvas, block: TextBlock) {
        val save = canvas.save()
        canvas.translate(block.left, block.top)
        block.layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    /** Writes [bitmap] to a destination the user picked through SAF. */
    fun writeTo(context: Context, bitmap: Bitmap, uri: Uri): Boolean = runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
        stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.getOrDefault(false)

    fun fileName(date: LocalDate?): String =
        if (date == null) "repland-timetable.png" else "repland-$date.png"
}
