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

internal object HerdCareNotifications {
    private const val PREFS = "mawashi_herd_care_v27"
    private const val KEY = "scheduled_care"
    private const val IDS_KEY = "scheduled_care_alarm_ids"
    private const val CHANNEL = "herd_care_reminders"

    internal fun triggerAt(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()

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
            } else {
                val due = runCatching { triggerAt(LocalDate.parse(item.date)) }.getOrNull() ?: return@forEach
                if (due > System.currentTimeMillis()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
                else manager.cancel(pending)
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
        val tag = intent.getStringExtra("tag").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val date = intent.getStringExtra("date").orEmpty()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "مواعيد متابعة القطيع", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("موعد متابعة القطيع")
            .setContentText("$tag • $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$tag • $title\nالتاريخ: $date"))
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
