package io.clfm.multitimer.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.clfm.multitimer.R

/**
 * Receives alarms that are broadcast when timers finish and sends notifications.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = ContextCompat.getSystemService(
            context,
            NotificationManager::class.java
        ) as NotificationManager

        val timerId = intent.extras?.get(IntentExtraKeys.TIMER_ID) as Int?
        if (timerId == null) {
            Log.w(
                TAG,
                "Failed to create notification. Missing '${IntentExtraKeys.TIMER_ID}' extra in notification intent."
            )
            return
        }

        val timerName = intent.extras?.get(IntentExtraKeys.TIMER_NAME) as String?
        val notificationMessage = if ((timerName ?: "").isBlank()) {
            context.getString(R.string.timer_finished_message)
        } else {
            context.getString(R.string.timer_finished_message_with_name, timerName)
        }

        notificationManager.sendTimerFinishedNotification(timerId, notificationMessage, context)
    }

}
