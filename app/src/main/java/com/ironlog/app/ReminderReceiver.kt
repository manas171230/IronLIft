package com.ironlog.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val label = intent.getStringExtra("label") ?: "IronLog"
        val type = intent.getStringExtra("type") ?: "custom"
        val time = intent.getStringExtra("time") ?: ""

        showNotification(context, id, label, type)

        // Re-arm the same reminder for tomorrow at the same time.
        if (time.isNotEmpty()) {
            ReminderScheduler.schedule(context, id, label, type, time)
        }
    }

    private fun showNotification(context: Context, id: String, label: String, type: String) {
        val channelId = "ironlog_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "IronLog reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Meal, water and workout reminders" }
            nm.createNotificationChannel(channel)
        }

        val title = when (type) {
            "meal" -> "Time to eat"
            "water" -> "Hydration check"
            "lift" -> "Training time"
            else -> "IronLog"
        }

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = PendingIntent.getActivity(
            context, id.hashCode(), openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        nm.notify(id.hashCode(), notification)
    }
}
