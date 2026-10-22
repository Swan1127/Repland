package com.swan1127.repland.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.swan1127.repland.MainActivity
import com.swan1127.repland.R
import com.swan1127.repland.ReplandApplication
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.TimelineEntry
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The home-screen widget: what the day looks like from outside the app.
 *
 * This class contains no decisions. Which block to name is [widgetState], when to redraw is
 * [nextRefreshAt], and what the words are lives in the resource table. What is left here is the
 * part that genuinely needs Android — reading the database, drawing `RemoteViews`, and arming the
 * next redraw — and every one of those is a straight line with no branch worth testing.
 *
 * **Reading the data the same way the app does.** The entries come from
 * [ScheduleTimeline.entries] with the same arguments the Today tab passes, including
 * `semesterFirstWeekMonday`, so a course block that is not in session this week is absent from the
 * widget exactly as it is absent from the app. Rebuilding the timeline here would be the classic
 * widget bug: a second, subtly different copy of the schedule that agrees with the real one until
 * the first edge case.
 *
 * **Why the work is async.** The database reads happen on [Dispatchers.IO] behind
 * `BroadcastReceiver.goAsync()`, the same shape [com.swan1127.repland.reminders.ReminderRescheduleReceiver]
 * uses. A widget update that blocked the main thread for a Room query would be an ANR waiting for
 * a slow disk, and the platform's own guidance is explicit that a widget receiver should hand its
 * result back rather than hold the broadcast open.
 *
 * **Why a failure here is swallowed.** This receiver runs in the app's process, so an exception
 * escaping it is an app crash attributed to whatever the user happened to be doing when the alarm
 * fired. A widget that could not be redrawn is a stale widget; it is not worth a crash, and the
 * next boundary or the periodic update will try again.
 */
class HomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val now = LocalDateTime.now()
                    val entries = todayEntries(appContext, now)
                    appWidgetManager.updateAppWidget(appWidgetIds, render(appContext, widgetState(entries, now)))
                    scheduleNextRefresh(appContext, appWidgetIds, nextRefreshAt(entries, now))
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        // The last instance is gone, so the boundary alarm has nothing left to redraw. Leaving it
        // armed would keep waking the app for a widget that no longer exists.
        cancelRefresh(context.applicationContext)
    }

    // ------------------------------------------------------------------ the data

    /** Today's timeline, built from the same inputs the Today tab builds it from. */
    private suspend fun todayEntries(context: Context, now: LocalDateTime): List<TimelineEntry> {
        val container = (context as ReplandApplication).appContainer
        return ScheduleTimeline.entries(
            date = now.toLocalDate(),
            weeklyBlocks = container.timeRepository.observeWeeklyBlocks().first(),
            dateOverrides = container.timeRepository.observeDateOverrides().first(),
            segments = container.planRepository.observeCurrentPlan().first()?.segments.orEmpty(),
            tasks = container.taskRepository.observeTasks().first(),
            semesterFirstWeekMonday = container.timeRepository.observeTimeConstraintSettings().first()
                .semesterFirstWeekMonday,
        )
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Turns the state into views. Every branch here is one of the three states, and each sets
     * *all* of the views it owns — including the ones it hides — because `RemoteViews` reuses the
     * previous update's visibility when a view is not mentioned, so a row hidden for an empty day
     * would otherwise still be hidden on the next update that has something to show.
     */
    private fun render(context: Context, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_home)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.today_title))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

        when (state) {
            is WidgetState.Schedule -> {
                views.setViewVisibility(R.id.widget_message, View.GONE)
                bindLine(
                    context, views, state.lines[0],
                    rowId = R.id.widget_primary_row,
                    labelId = R.id.widget_primary_label,
                    timeId = R.id.widget_primary_time,
                    titleId = R.id.widget_primary_title,
                )
                val second = state.lines.getOrNull(1)
                if (second == null) {
                    views.setViewVisibility(R.id.widget_secondary_row, View.GONE)
                } else {
                    bindLine(
                        context, views, second,
                        rowId = R.id.widget_secondary_row,
                        labelId = R.id.widget_secondary_label,
                        timeId = R.id.widget_secondary_time,
                        titleId = R.id.widget_secondary_title,
                    )
                }
                if (state.remainingCount > 0) {
                    views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_footer,
                        context.getString(R.string.widget_remaining, state.remainingCount),
                    )
                } else {
                    views.setViewVisibility(R.id.widget_footer, View.GONE)
                }
            }

            WidgetState.Empty -> {
                views.setViewVisibility(R.id.widget_primary_row, View.GONE)
                views.setViewVisibility(R.id.widget_secondary_row, View.GONE)
                views.setViewVisibility(R.id.widget_message, View.VISIBLE)
                views.setTextViewText(R.id.widget_message, context.getString(R.string.widget_empty_title))
                views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_empty_hint))
            }

            WidgetState.Done -> {
                views.setViewVisibility(R.id.widget_primary_row, View.GONE)
                views.setViewVisibility(R.id.widget_secondary_row, View.GONE)
                views.setViewVisibility(R.id.widget_message, View.VISIBLE)
                views.setTextViewText(R.id.widget_message, context.getString(R.string.widget_done_title))
                views.setViewVisibility(R.id.widget_footer, View.GONE)
            }
        }
        return views
    }

    private fun bindLine(
        context: Context,
        views: RemoteViews,
        line: WidgetLine,
        rowId: Int,
        labelId: Int,
        timeId: Int,
        titleId: Int,
    ) {
        views.setViewVisibility(rowId, View.VISIBLE)
        views.setTextViewText(labelId, context.getString(line.emphasis.labelRes))
        views.setTextViewText(
            timeId,
            context.getString(
                R.string.widget_time_range,
                TimeBlockValidator.formatTime(line.startMinute),
                TimeBlockValidator.formatTime(line.endMinute),
            ),
        )
        views.setTextViewText(titleId, line.title)
    }

    /** The whole widget is one target: there is nothing in it to aim at except "open the app". */
    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {

        /**
         * Asks the widget to redraw now, because something the user did changed the answer.
         *
         * Called from `MainActivity.onStop` rather than from every ViewModel: leaving the app is
         * the one moment that is both cheap and certain — the user has finished editing the plan
         * and is looking at the home screen they are about to see the widget on. Wiring it to the
         * repositories instead would redraw on every keystroke in a task editor, for a widget
         * nobody is looking at.
         *
         * The broadcast is explicit and addressed to ourselves, so it is delivered whether or not
         * the receiver is exported.
         */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(updateIntent(context, ids))
        }

        private fun scheduleNextRefresh(context: Context, appWidgetIds: IntArray, at: LocalDateTime) {
            if (appWidgetIds.isEmpty()) return
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.set(
                // RTC, not RTC_WAKEUP. The widget is only visible while the screen is on, so there
                // is nothing to wake the device for: an RTC alarm fires on the next wake-up and
                // the redraw happens before the user can read a stale row. RTC_WAKEUP would spend
                // battery waking a sleeping phone to repaint a screen nobody is looking at.
                AlarmManager.RTC,
                at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                PendingIntent.getBroadcast(context, 0, updateIntent(context, appWidgetIds), pendingIntentFlags()),
            )
        }

        private fun cancelRefresh(context: Context) {
            // FLAG_NO_CREATE with the same component and action finds the armed PendingIntent
            // without creating one; extras take no part in matching, so an empty id array is
            // enough to name the alarm that was set.
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                updateIntent(context, IntArray(0)),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        /**
         * The standard update broadcast, addressed to this provider.
         *
         * Reusing the platform's own action rather than inventing one means the alarm, the app-side
         * refresh and the system's own periodic update all arrive through `onUpdate` — one code
         * path, so there is no second way for the widget to be drawn that could disagree with the
         * first.
         */
        private fun updateIntent(context: Context, appWidgetIds: IntArray): Intent =
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, HomeWidgetProvider::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }

        private fun pendingIntentFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
