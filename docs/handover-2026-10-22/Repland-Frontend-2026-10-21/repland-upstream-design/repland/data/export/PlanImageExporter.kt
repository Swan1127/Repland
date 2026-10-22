package com.swan1127.repland.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

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
    private const val ROW_HEIGHT = 66f
    private const val HEADER_HEIGHT = 220f
    private const val TIME_COLUMN = 300f

    private val ink = Color.parseColor("#1B1B1B")
    private val inkSoft = Color.parseColor("#6B7280")
    private val paper = Color.parseColor("#F7F7F2")
    private val accent = Color.parseColor("#276454")
    private val card = Color.WHITE

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 58f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkSoft
        textSize = 34f
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 38f
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    /**
     * The width a name has left once the time column and both margins are taken out.
     *
     * Was a fixed `take(16)`. That guessed at a character count, and guessed wrong twice over:
     * a long Chinese name got chopped with no ellipsis (so it read as if the name simply ended
     * there), while a Latin name was cut at the same 16 characters and left half the column
     * empty — Latin glyphs are roughly half the width of Chinese ones. Measuring against the
     * real pixel budget fixes both cases with one rule.
     */
    private val nameColumnWidth: Float = WIDTH - (MARGIN + TIME_COLUMN) - MARGIN

    private fun fitName(text: String): String =
        TextUtils.ellipsize(text, bodyPaint, nameColumnWidth, TextUtils.TruncateAt.END).toString()

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
    ): Bitmap {
        val nameById = tasks.associate { it.id to it.displayName }
        val height = (HEADER_HEIGHT + segments.size * ROW_HEIGHT + MARGIN).toInt().coerceAtLeast(720)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paper)

        canvas.drawText(
            planHeaderDate(date, texts),
            MARGIN,
            MARGIN + 60f,
            titlePaint,
        )
        canvas.drawText(
            planSubtitle(segments.size, texts),
            MARGIN,
            MARGIN + 120f,
            subtitlePaint,
        )

        var y = HEADER_HEIGHT
        segments.forEach { segment ->
            canvas.drawRoundRect(
                RectF(MARGIN - 20f, y - 42f, WIDTH - MARGIN + 20f, y + 24f),
                16f,
                16f,
                cardPaint,
            )
            canvas.drawText(
                "${clock(segment.startMinute)}–${clock(segment.endMinute)}",
                MARGIN,
                y,
                timePaint,
            )
            val name = nameById[segment.taskId] ?: texts.missingTask
            canvas.drawText(fitName(name), MARGIN + TIME_COLUMN, y, bodyPaint)
            y += ROW_HEIGHT
        }
        return bitmap
    }

    /** Renders the weekly timetable, grouped by day of week in Monday-first order. */
    fun renderTimetable(blocks: List<WeeklyTimeBlock>, texts: PlanImageTexts): Bitmap {
        val days = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        )
        val grouped = days.map { day ->
            day to blocks.filter { it.dayOfWeek == day }.sortedBy { it.startMinute }
        }
        val rows = blocks.size + grouped.count { it.second.isNotEmpty() }
        val height = (HEADER_HEIGHT + rows * ROW_HEIGHT + MARGIN).toInt().coerceAtLeast(720)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paper)

        canvas.drawText(texts.weekTitle, MARGIN, MARGIN + 60f, titlePaint)
        canvas.drawText(
            timetableSubtitle(blocks.size, texts),
            MARGIN,
            MARGIN + 120f,
            subtitlePaint,
        )

        var y = HEADER_HEIGHT
        grouped.forEach { (day, dayBlocks) ->
            if (dayBlocks.isEmpty()) return@forEach
            canvas.drawText(
                weekdayName(day, texts),
                MARGIN,
                y,
                dayPaint,
            )
            y += ROW_HEIGHT * 0.72f
            dayBlocks.forEach { block ->
                canvas.drawRoundRect(
                    RectF(MARGIN - 20f, y - 42f, WIDTH - MARGIN + 20f, y + 24f),
                    16f,
                    16f,
                    cardPaint,
                )
                canvas.drawText(
                    "${clock(block.startMinute)}–${clock(block.endMinute)}",
                    MARGIN,
                    y,
                    timePaint,
                )
                canvas.drawText(fitName(block.title), MARGIN + TIME_COLUMN, y, bodyPaint)
                y += ROW_HEIGHT
            }
        }
        return bitmap
    }

    /** Writes [bitmap] to a destination the user picked through SAF. */
    fun writeTo(context: Context, bitmap: Bitmap, uri: Uri): Boolean = runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
        stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.getOrDefault(false)

    fun fileName(date: LocalDate?): String =
        if (date == null) "repland-timetable.png" else "repland-$date.png"
}
