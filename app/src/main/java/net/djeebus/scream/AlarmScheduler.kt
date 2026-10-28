package net.djeebus.scream

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.TimeUnit

object AlarmScheduler {
    const val ACTION_FIRE = "net.djeebus.scream.FIRE"
    const val ACTION_RESCHEDULE = "net.djeebus.scream.RESCHEDULE"
    const val EXTRA_VERIFY = "verify"

    private const val HORIZON_DAYS = 14
    const val SNOOZE_MINUTES = 5

    // Registers one alarm per matching upcoming event, replacing whatever was registered before.
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = Settings(context)

        settings.scheduledKeys.forEach { am.cancel(firePendingIntent(context, "event/$it")) }

        val now = System.currentTimeMillis()
        val scheduled = mutableSetOf<String>()
        for (event in CalendarRepository.upcoming(context, HORIZON_DAYS)) {
            if (!settings.matches(event)) continue
            val triggerAt = event.beginMillis - TimeUnit.MINUTES.toMillis(settings.leadMinutes.toLong())
            if (triggerAt <= now) continue
            val key = "${event.id}/${event.beginMillis}"
            val info = AlarmInfo(event.id, event.beginMillis, event.title)
            setAlarm(context, am, "event/$key", triggerAt, info, verify = true)
            scheduled += key
        }
        settings.scheduledKeys = scheduled

        // Hourly refresh picks up events that were added, moved or recolored since.
        am.setInexactRepeating(
            AlarmManager.RTC,
            now + TimeUnit.HOURS.toMillis(1),
            AlarmManager.INTERVAL_HOUR,
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, RescheduleReceiver::class.java).setAction(ACTION_RESCHEDULE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun snooze(context: Context, info: AlarmInfo) {
        val am = context.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(SNOOZE_MINUTES.toLong())
        // Not verified against the calendar: you asked to be reminded, so remind.
        setAlarm(context, am, "snooze/${info.eventId}", triggerAt, info, verify = false)
    }

    // setAlarmClock is exempt from Doze and doesn't need the exact-alarm permission.
    private fun setAlarm(
        context: Context, am: AlarmManager, path: String, triggerAt: Long, info: AlarmInfo, verify: Boolean,
    ) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, show),
            firePendingIntent(context, path, info, verify),
        )
    }

    // Identity is the intent's action + data, so extras don't affect cancel().
    private fun firePendingIntent(
        context: Context, path: String, info: AlarmInfo? = null, verify: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("scream://alarm/$path"))
            .putExtra(EXTRA_VERIFY, verify)
        info?.putInto(intent)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
