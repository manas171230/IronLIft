package com.ironlog.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Persists the reminder list (synced from the web app via the JS bridge) and
 * schedules one repeating-daily alarm per enabled reminder. Alarms are
 * rescheduled +24h by ReminderReceiver each time they fire, and re-armed by
 * BootReceiver after a device restart, since Android does not let apps keep
 * exact alarms alive indefinitely on its own.
 */
object ReminderScheduler {

    private const val PREFS = "ironlog_prefs"
    private const val KEY_REMINDERS = "reminders_json"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveReminders(context: Context, json: String) {
        prefs(context).edit().putString(KEY_REMINDERS, json).apply()
    }

    fun loadReminders(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_REMINDERS, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    /** Cancels all currently-scheduled alarms and re-schedules from the saved list. */
    fun rescheduleAll(context: Context) {
        val reminders = loadReminders(context)
        for (i in 0 until reminders.length()) {
            val r = reminders.getJSONObject(i)
            val id = r.optString("id")
            cancel(context, id)
            if (r.optBoolean("enabled", true)) {
                val time = r.optString("time", "")
                val label = r.optString("label", "IronLog")
                val type = r.optString("type", "custom")
                if (time.matches(Regex("^\\d{2}:\\d{2}$"))) {
                    schedule(context, id, label, type, time)
                }
            }
        }
    }

    fun schedule(context: Context, id: String, label: String, type: String, time: String) {
        val parts = time.split(":")
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, id, label, type, time)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Falls back to an inexact alarm; the user should grant exact-alarm
            // permission from the app for on-time nudges (MainActivity prompts for this).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, id, "", "", "")
        am.cancel(pi)
    }

    private fun pendingIntent(context: Context, id: String, label: String, type: String, time: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("label", label)
            putExtra("type", type)
            putExtra("time", time)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }
}
