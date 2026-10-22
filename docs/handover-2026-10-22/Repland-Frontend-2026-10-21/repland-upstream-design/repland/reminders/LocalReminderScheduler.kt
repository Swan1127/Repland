package com.swan1127.repland.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.LocalReminder
import com.swan1127.repland.domain.model.LocalReminderPlanner
import com.swan1127.repland.domain.model.ReminderKind
import com.swan1127.repland.domain.model.Task

/** Schedules only one-time local alarms derived from confirmed plan data. */
class LocalReminderScheduler(
    private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun sync(
        isEnabled: Boolean,
        currentPlan: ConfirmedPlan?,
        planHistory: List<ConfirmedPlan>,
        tasks: List<Task>,
    ) {
        planHistory.forEach { plan ->
            plan.segments.forEach { segment ->
                cancel(ReminderKind.SEGMENT_START, "${plan.id}:${segment.id}")
            }
        }
        tasks.forEach { task -> cancel(ReminderKind.DEADLINE, task.id) }
        cancel(ReminderKind.DAILY_REVIEW, DAILY_REVIEW_KEY)
        if (!isEnabled || currentPlan == null) return

        LocalReminderPlanner.plan(currentPlan, tasks).forEach(::schedule)
    }

    private fun schedule(reminder: LocalReminder) {
        val intent = reminderIntent(reminder.kind, reminder.key).apply {
            putExtra(EXTRA_KIND, reminder.kind.name)
            putExtra(EXTRA_KEY, reminder.key)
            putExtra(EXTRA_PLAN_ID, reminder.planId)
            putExtra(EXTRA_SEGMENT_ID, reminder.segmentId)
            putExtra(EXTRA_TASK_ID, reminder.taskId)
            putExtra(EXTRA_TASK_NAME, reminder.taskName)
            putExtra(EXTRA_DUE_DATE_EPOCH_DAY, reminder.dueDate?.toEpochDay() ?: NO_DUE_DATE)
        }
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt.toEpochMilli(),
            PendingIntent.getBroadcast(context, 0, intent, pendingIntentFlags()),
        )
    }

    private fun cancel(kind: ReminderKind, key: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(kind, key),
            PendingIntent.FLAG_NO_CREATE or immutableFlag(),
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderIntent(kind: ReminderKind, key: String): Intent =
        Intent(context, LocalReminderReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            data = Uri.Builder()
                .scheme("repland")
                .authority("local-reminder")
                .appendPath(kind.name)
                .appendPath(key)
                .build()
        }

    companion object {
        const val CHANNEL_ID = "confirmed-plan-reminders"
        const val ACTION_DELIVER_REMINDER = "com.swan1127.repland.DELIVER_REMINDER"
        const val EXTRA_KIND = "reminder_kind"
        const val EXTRA_KEY = "reminder_key"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_SEGMENT_ID = "segment_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_DUE_DATE_EPOCH_DAY = "due_date_epoch_day"
        const val DAILY_REVIEW_KEY = "daily-review"
        const val NO_DUE_DATE = Long.MIN_VALUE

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.swan1127.repland.R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(com.swan1127.repland.R.string.reminder_channel_description)
                },
            )
        }

        private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()

        private fun immutableFlag(): Int = PendingIntent.FLAG_IMMUTABLE
    }
}
