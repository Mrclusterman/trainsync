package io.github.mrclusterman.trainsync.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.systemToday
import java.util.Calendar

// Recordatorio diario opcional (VISION fase 3): una alarma al día con 15 min de margen (setWindow no pide el permiso de
// alarmas exactas; setInexactRepeating puede retrasarse horas). Al sonar se calcula el texto y se programa la siguiente.

private const val CHANNEL = "daily"

private fun core(context: Context) = TrainSyncApp(PrefsStore(context), KeystoreStore(context), { systemToday() })

private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 0,
    Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Programa (o quita, con null) el aviso diario a esa hora. */
fun scheduleReminder(context: Context, hour: Int?) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    alarms.cancel(alarmIntent(context))
    if (hour == null) return
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarms.setWindow(AlarmManager.RTC_WAKEUP, next.timeInMillis, 15 * 60 * 1000L, alarmIntent(context))
}

fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Suena la alarma: avisa solo si hoy queda algo por hacer. Al reiniciar el móvil, vuelve a programarla. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = core(context)
        scheduleReminder(context, app.data.reminderHour)  // la de mañana (o la de hoy, tras reiniciar)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) return
        if (app.data.reminderHour == null || !canNotify(context)) return
        val text = runCatching { app.reminderText(systemToday().toString()) }.getOrNull() ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1, android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hoy toca")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}
