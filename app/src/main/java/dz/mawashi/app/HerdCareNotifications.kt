package dz.mawashi.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class HerdCareNotificationData(val id:String,val tag:String,val title:String,val date:String,val completed:Boolean)

internal fun isActiveHerdCareReminder(reminders: List<HerdCareNotificationData>, id: String): Boolean =
    reminders.any { it.id == id && !it.completed }

internal fun isSchedulableHerdCareDate(value: String): Boolean =
    value.isNotBlank() && runCatching { LocalDate.parse(value) }.isSuccess

internal object HerdCareNotifications {
    private const val PREFS = "mawashi_herd_care_v27"
    private const val KEY = "scheduled_care"
    private const val IDS_KEY = "scheduled_care_alarm_ids"
    private const val DELIVERED_KEY = "delivered_care_notifications"
    private const val CHANNEL = "herd_care_reminders"

    internal fun triggerAt(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()

    internal fun deliveryKey(id: String, date: String): String = "$id|$date"

    internal fun isOverdue(date: LocalDate, today: LocalDate): Boolean = date.isBefore(today)

    private fun delivered(prefs: android.content.SharedPreferences, id: String, date: String): Boolean =
        prefs.getStringSet(DELIVERED_KEY, emptySet()).orEmpty().contains(deliveryKey(id, date))

    fun scheduleAll(context: Context, reminders: List<HerdCareNotificationData>) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentIds = reminders.map { it.id }.toSet()
        val previousIds = prefs.getStringSet(IDS_KEY, emptySet()).orEmpty()
        (previousIds - currentIds).forEach { removedId ->
            val removedIntent = Intent(context, HerdCareAlarmReceiver::class.java).apply {
                action = "dz.mawashi.HERD_CARE_REMINDER"
                putExtra("id", removedId)
            }
            val removedPending = PendingIntent.getBroadcast(context, removedId.hashCode() and 0x7fffffff,
                removedIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.cancel(removedPending)
            removedPending.cancel()
            NotificationManagerCompat.from(context).cancel(removedId.hashCode())
        }
        reminders.forEach { item ->
            val id = item.id.hashCode() and 0x7fffffff
            val intent = Intent(context, HerdCareAlarmReceiver::class.java).apply {
                action = "dz.mawashi.HERD_CARE_REMINDER"
                putExtra("id", item.id)
                putExtra("tag", item.tag)
                putExtra("title", item.title)
                putExtra("date", item.date)
                putExtra("completed", item.completed)
            }
            val pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (item.completed) {
                manager.cancel(pending)
                NotificationManagerCompat.from(context).cancel(id)
            } else {
                val date = item.date.takeIf(::isSchedulableHerdCareDate)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (date == null || delivered(prefs, item.id, item.date)) {
                    manager.cancel(pending)
                    if (date == null) NotificationManagerCompat.from(context).cancel(id)
                } else {
                    val today = LocalDate.now()
                    val due = triggerAt(date)
                    when {
                        date.isBefore(today) -> {
                            // Catch up overdue items once, immediately; never silently discard them.
                            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1_000L, pending)
                        }
                        date == today && due <= System.currentTimeMillis() -> {
                            // Items entered after 09:00 still notify once shortly after creation.
                            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, pending)
                        }
                        else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
                    }
                }
            }
        }
        prefs.edit().putStringSet(IDS_KEY, currentIds).apply()
    }

    fun rescheduleFromStorage(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val items = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val x = array.getJSONObject(i)
                HerdCareNotificationData(x.optString("id"), x.optString("tag"), x.optString("title"),
                    x.optString("date"), x.optBoolean("completed"))
            }.getOrNull()
        }
        scheduleAll(context, items)
    }

    internal fun show(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        if (intent.getBooleanExtra("completed", false)) return
        // An alarm can race with deletion/completion; trust the latest saved reminder, not stale extras.
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val saved = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val current = (0 until saved.length()).mapNotNull { index ->
            runCatching {
                val item = saved.getJSONObject(index)
                HerdCareNotificationData(item.optString("id"), item.optString("tag"),
                    item.optString("title"), item.optString("date"), item.optBoolean("completed"))
            }.getOrNull()
        }
        if (!isActiveHerdCareReminder(current, id)) return
        val latest = current.firstOrNull { it.id == id } ?: return
        val parsedDate = runCatching { LocalDate.parse(latest.date) }.getOrNull() ?: return
        val today = LocalDate.now()
        if (parsedDate.isAfter(today)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = deliveryKey(id, latest.date)
        synchronized(HerdCareNotifications::class.java) {
            val delivered = prefs.getStringSet(DELIVERED_KEY, emptySet()).orEmpty()
            if (key in delivered) return
            // Persist before posting so duplicate/racing alarms cannot show the same cycle twice.
            if (!prefs.edit().putStringSet(DELIVERED_KEY, delivered + key).commit()) return
        }
        val tag = latest.tag
        val title = latest.title
        val date = latest.date
        val overdue = isOverdue(parsedDate, today)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "مواعيد متابعة القطيع", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (overdue) "موعد متابعة متأخر" else "موعد متابعة القطيع")
            .setContentText("$tag • $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$tag • $title\nالتاريخ: $date" + if (overdue) "\nهذا الموعد متأخر ويحتاج مراجعة." else ""))
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }
}

class HerdCareAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HerdCareNotifications.show(context, intent)
    }
}

class HerdCareBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) HerdCareNotifications.rescheduleFromStorage(context)
    }
}
