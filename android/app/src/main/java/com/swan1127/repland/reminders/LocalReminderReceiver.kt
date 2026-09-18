package com.swan1127.repland.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.swan1127.repland.MainActivity
import com.swan1127.repland.R
import com.swan1127.repland.ReplandApplication
import com.swan1127.repland.domain.model.ReminderKind
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Validates an alarm against current local data before showing a notification. */
class LocalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocalReminderScheduler.ACTION_DELIVER_REMINDER) return
        val kind = intent.getStringExtra(LocalReminderScheduler.EXTRA_KIND)
            ?.let { value -> runCatching { ReminderKind.valueOf(value) }.getOrNull() } ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ReplandApplication
                val container = app.appContainer
                val settings = container.reminderSettingsRepository.observe().first()
                val tasks = container.taskRepository.observeTasks().first()
                val currentPlan = container.planRepository.observeCurrentPlan().first()
                if (!settings.isEnabled || !isStillEligible(intent, kind, currentPlan, tasks)) return@launch
                if (canPostNotifications(context)) {
                    post(context, intent, kind)
                }
                if (kind == ReminderKind.DAILY_REVIEW) {
                    val history = container.planRepository.observePlanHistory().first()
                    LocalReminderScheduler(context.applicationContext).sync(
                        isEnabled = true,
                        currentPlan = currentPlan,
                        planHistory = history,
                        tasks = tasks,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isStillEligible(
        intent: Intent,
        kind: ReminderKind,
        currentPlan: com.swan1127.repland.domain.model.ConfirmedPlan?,
        tasks: List<com.swan1127.repland.domain.model.Task>,
    ): Boolean = when (kind) {
        ReminderKind.SEGMENT_START -> {
            val planId = intent.getStringExtra(LocalReminderScheduler.EXTRA_PLAN_ID)
            val segmentId = intent.getStringExtra(LocalReminderScheduler.EXTRA_SEGMENT_ID)
            val taskId = intent.getStringExtra(LocalReminderScheduler.EXTRA_TASK_ID)
            currentPlan?.let { plan ->
                plan.id == planId &&
                    plan.segments.any { it.id == segmentId && it.taskId == taskId } &&
                    tasks.firstOrNull { it.id == taskId }?.status?.isActive == true
            } == true
        }

        ReminderKind.DEADLINE -> {
            val taskId = intent.getStringExtra(LocalReminderScheduler.EXTRA_TASK_ID)
            val dueEpochDay = intent.getLongExtra(
                LocalReminderScheduler.EXTRA_DUE_DATE_EPOCH_DAY,
                LocalReminderScheduler.NO_DUE_DATE,
            )
            tasks.firstOrNull { it.id == taskId }?.let { task ->
                task.status.isActive && task.dueDate?.toEpochDay() == dueEpochDay
            } == true
        }

        ReminderKind.DAILY_REVIEW -> currentPlan != null
    }

    private fun post(context: Context, intent: Intent, kind: ReminderKind) {
        val taskName = intent.getStringExtra(LocalReminderScheduler.EXTRA_TASK_NAME)
        val (title, content) = when (kind) {
            ReminderKind.SEGMENT_START -> {
                context.getString(R.string.reminder_start_title, taskName) to
                    context.getString(R.string.reminder_start_content)
            }

            ReminderKind.DEADLINE -> {
                val epochDay = intent.getLongExtra(LocalReminderScheduler.EXTRA_DUE_DATE_EPOCH_DAY, 0)
                context.getString(R.string.reminder_deadline_title, taskName) to
                    context.getString(R.string.reminder_deadline_content, LocalDate.ofEpochDay(epochDay))
            }

            ReminderKind.DAILY_REVIEW -> context.getString(R.string.reminder_review_title) to
                context.getString(R.string.reminder_review_content)
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            intent.dataString.hashCode(),
            NotificationCompat.Builder(context, LocalReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
}
