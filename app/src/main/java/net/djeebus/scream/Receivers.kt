package net.djeebus.scream

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val info = AlarmInfo.from(intent)
        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_VERIFY, false)) {
            // The event may have been deleted or recolored since this alarm was registered.
            val event = CalendarRepository.find(context, info.eventId, info.beginMillis)
            if (event == null || !Settings(context).matches(event)) return
        }
        context.startForegroundService(AlarmService.startIntent(context, info))
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val canRead = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (canRead) AlarmScheduler.reschedule(context)
    }
}
