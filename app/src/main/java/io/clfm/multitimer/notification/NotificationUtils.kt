package io.clfm.multitimer.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.clfm.multitimer.R
import io.clfm.multitimer.ui.MainActivity

fun NotificationManager.sendTimerFinishedNotification(
    timerId: Int,
    messageBody: String,
    context: Context
) {
    sendNotification(
        timerId,
        messageBody,
        context,
        context.getString(R.string.timer_finished_channel_id)
    )
}

private fun NotificationManager.sendNotification(
    notificationId: Int,
    messageBody: String,
    context: Context,
    channelId: String
) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_hourglass)
        .setContentTitle(context.getString(R.string.notification_title))
        .setContentText(messageBody)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    notify(notificationId, builder.build())
}

fun NotificationManager.cancelNotification(notificationId: Int) {
    cancel(notificationId)
}
