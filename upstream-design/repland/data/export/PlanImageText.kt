package com.swan1127.repland.data.export

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The words and date formatting an exported image is drawn with.
 *
 * **Why this exists as a parameter.** [PlanImageExporter] used to bake the language in — a literal
 * `ofPattern("M月d日 EEEE", Locale.CHINA)` for the header and `Locale.CHINA` for the weekday names
 * — so a user reading the app in English still shared an image headed "10月7日 星期三". The rest of
 * the image was Chinese too, and the two halves were the same defect: **the exporter had no way to
 * be told what language it was drawing in**.
 *
 * It takes a value rather than a `Context` because the exporter draws on a `Canvas` and never
 * touches resources. Keeping it that way is what lets the decisions below live in plain functions
 * with a JVM test.
 */
data class PlanImageTexts(
    /** `DateTimeFormatter` pattern for the header date, e.g. `M月d日 EEEE`. */
    val datePattern: String,
    /** Locale for that pattern, for weekday names, and for number formatting in the counts. */
    val locale: Locale,
    /** Title of the weekly-timetable image. */
    val weekTitle: String,
    /** Shown when the day has no segments at all. */
    val emptyPlan: String,
    /** Shown when the timetable has no fixed blocks at all. */
    val emptyTimetable: String,
    /** `%1$d` is the segment count. */
    val segmentCount: String,
    /** `%1$d` is the block count. */
    val blockCount: String,
    /** Stands in for a segment whose task no longer exists. */
    val missingTask: String,
    /** Explains that the weekly export is an overview, rather than the classes of one date. */
    val timetableOverview: String,
    /** `%1$s` is the complete, unmodified week pattern. */
    val weekPattern: String,
    val unrestrictedWeeks: String,
)

/*
 * The four functions below are the exact lines that used to carry `Locale.CHINA`.
 *
 * They live in this file — **which imports nothing from `android.*`** — rather than inside
 * [PlanImageExporter], for a concrete reason: reading a member of that `object` runs its static
 * initialiser, which builds `Paint` and `Color` instances, and those are Android stubs that throw
 * under a plain JVM test. Anything left inside it is therefore unreachable from `PlanImageExporterTest`,
 * which is why the drawing code is not what these tests cover.
 */

/** The header date of a plan image, in the image's own language. */
internal fun planHeaderDate(date: LocalDate, texts: PlanImageTexts): String =
    date.format(DateTimeFormatter.ofPattern(texts.datePattern, texts.locale))

/** A weekday heading in the timetable image, in the image's own language. */
internal fun weekdayName(day: DayOfWeek, texts: PlanImageTexts): String =
    day.getDisplayName(TextStyle.FULL, texts.locale)

/** The plan image's subtitle: a count when there is anything to show, otherwise a reassurance. */
internal fun planSubtitle(segmentCount: Int, texts: PlanImageTexts): String =
    if (segmentCount == 0) texts.emptyPlan
    else String.format(texts.locale, texts.segmentCount, segmentCount)

/** The timetable image's subtitle, same rule as [planSubtitle]. */
internal fun timetableSubtitle(blockCount: Int, texts: PlanImageTexts): String =
    if (blockCount == 0) texts.emptyTimetable
    else String.format(texts.locale, texts.blockCount, blockCount)

internal fun timetableWeekDetail(pattern: String?, texts: PlanImageTexts): String =
    String.format(texts.locale, texts.weekPattern, pattern?.trim()?.takeIf(String::isNotEmpty) ?: texts.unrestrictedWeeks)
