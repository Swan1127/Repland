package com.swan1127.repland.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.swan1127.repland.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date formatting that follows the user's language, not the developer's.
 *
 * **The defect this exists to remove.** A date was previously written as
 * `DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)`. Both halves are wrong for
 * anyone who does not read Chinese, and neither shows up as an error:
 *
 *   - the **pattern** has CJK characters baked into it, so it can only ever print Chinese;
 *   - the **locale** is hard-coded, so `E` and `EEEE` (the weekday) resolve to 星期三 whatever the
 *     device is set to.
 *
 * The consequence is the one thing an English resource file cannot fix: translate every string in
 * `values-en/strings.xml` and the screen still shows "10月7日 星期三", because the date is not a
 * string resource — it is code.
 *
 * **The fix, and why it is split in two.** The pattern moves into `values/` + `values-en/` (where
 * [com.swan1127.repland.i18n.I18nResourceTest] already enforces that the two files agree key for
 * key), and the locale comes from the running configuration. [stringResource] resolves the pattern
 * for the *current* configuration and [currentLocale] reads the same configuration, so the two can
 * never disagree — pairing a resource pattern with `Locale.getDefault()` would be a subtle bug the
 * day the app language differs from the system language.
 *
 * **Why a composable and not a plain function.** These read the current configuration, which is
 * only available inside composition.
 */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Builds a [DateTimeFormatter] from a pattern resource, for the current language.
 *
 * Keyed on both the resolved pattern and the locale, so switching the system language rebuilds it
 * rather than reusing a formatter that was memoised in the previous language.
 */
@Composable
fun rememberDateFormatter(@StringRes patternRes: Int): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    val locale = currentLocale()
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/**
 * Everything [taskDateLabel] needs to say a day in words.
 *
 * **Why this is a value and not a `Context`.** The label has to survive being called from a
 * non-composable string builder (a task card assembles its whole summary in one pure expression),
 * and it has to be testable without an Android runtime. So the two relative words and the two
 * patterns are resolved once, at the composable boundary, and handed down as data. A `Context`
 * parameter would have dragged resources into the middle of the domain-ish summary logic and made
 * the only interesting part — "which branch wins" — unreachable from a plain JVM test.
 */
data class TaskDateLabels(
    /** The word for the day the user is looking at, e.g. 今天 / Today. */
    val today: String,
    /** The word for the following day, e.g. 明天 / Tomorrow. */
    val tomorrow: String,
    /** Pattern for a date inside the reference year, e.g. `M月d日` / `MMM d`. */
    val monthDay: String,
    /** Pattern for a date in another year, e.g. `yyyy年M月d日` / `MMM d, yyyy`. */
    val yearMonthDay: String,
)

/** Resolves [TaskDateLabels] for the current configuration. */
@Composable
fun rememberTaskDateLabels(): TaskDateLabels = TaskDateLabels(
    today = stringResource(R.string.task_date_today),
    tomorrow = stringResource(R.string.task_date_tomorrow),
    monthDay = stringResource(R.string.date_pattern_month_day),
    yearMonthDay = stringResource(R.string.date_pattern_year_month_day),
)

/**
 * Turns a day into words: 今天 / 明天 for the two days that have a word, and an explicit date
 * otherwise.
 *
 * Only today and tomorrow get words — a past date deliberately reads as an explicit day rather
 * than "昨天", because the card is usually describing something that still needs doing.
 *
 * The year appears **exactly when it differs from [today]'s year**, not when the date is "far":
 * 20 December of this year omits it while 3 January of the next includes it, even though the two
 * are weeks apart. Pinned by `TaskDateLabelTest` so nobody "fixes" it into a proximity rule.
 *
 * Pure by construction — [today] and [locale] are parameters, so the tests never depend on when
 * they run or on what the machine's default locale happens to be.
 */
fun taskDateLabel(
    date: LocalDate,
    today: LocalDate,
    labels: TaskDateLabels,
    locale: Locale,
): String = when (date) {
    today -> labels.today
    today.plusDays(1) -> labels.tomorrow
    else -> DateTimeFormatter
        .ofPattern(if (date.year == today.year) labels.monthDay else labels.yearMonthDay, locale)
        .format(date)
}

/**
 * Composable convenience: the same label with the current configuration's words and locale.
 *
 * Kept as an overload rather than a rename so that UI call sites keep reading `taskDateLabel(date)`.
 */
@Composable
fun taskDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    taskDateLabel(date, today, rememberTaskDateLabels(), currentLocale())
