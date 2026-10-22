package com.swan1127.repland.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.swan1127.repland.R
import com.swan1127.repland.data.export.PlanImageTexts

/**
 * Resolves [PlanImageTexts] for the current configuration.
 *
 * This is the boundary where the export image learns what language to draw in.
 * [com.swan1127.repland.data.export.PlanImageExporter] deliberately has no `Context` and no
 * resource lookup — it draws on a `Canvas` — so the words are resolved here, in composition, and
 * handed to it as data.
 *
 * The date pattern comes from the same `date_pattern_*` family the screens use, so an exported
 * image and the screen it was exported from can never disagree about what day it is.
 */
@Composable
fun rememberPlanImageTexts(): PlanImageTexts = PlanImageTexts(
    datePattern = stringResource(R.string.date_pattern_month_day_weekday),
    locale = currentLocale(),
    weekTitle = stringResource(R.string.plan_image_title_week),
    emptyPlan = stringResource(R.string.plan_image_empty_plan),
    emptyTimetable = stringResource(R.string.plan_image_empty_timetable),
    segmentCount = stringResource(R.string.plan_image_segment_count),
    blockCount = stringResource(R.string.plan_image_block_count),
    missingTask = stringResource(R.string.plan_image_missing_task),
)
