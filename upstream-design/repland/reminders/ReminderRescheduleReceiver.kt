package com.swan1127.repland.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swan1127.repland.ReplandApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores local alarms after device restart, clock, or timezone changes without using network. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as ReplandApplication).appContainer
                val settings = container.reminderSettingsRepository.observe().first()
                val currentPlan = container.planRepository.observeCurrentPlan().first()
                val history = container.planRepository.observePlanHistory().first()
                val tasks = container.taskRepository.observeTasks().first()
                LocalReminderScheduler(context.applicationContext).sync(
                    isEnabled = settings.isEnabled,
                    currentPlan = currentPlan,
                    planHistory = history,
                    tasks = tasks,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
