package net.djeebus.scream

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("scream", Context.MODE_PRIVATE)

    // Opt-in: no color selected means no event ever triggers an alarm.
    var alarmColor: Int?
        get() = if (prefs.contains(KEY_COLOR)) prefs.getInt(KEY_COLOR, 0) else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_COLOR) else putInt(KEY_COLOR, value)
            }.apply()
        }

    // How many minutes before the event start the alarm goes off.
    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD, DEFAULT_LEAD_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LEAD, value).apply()

    // Ringtone chosen via the system picker; null means the phone's default alarm sound.
    var alarmSound: String?
        get() = prefs.getString(KEY_SOUND, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SOUND) else putString(KEY_SOUND, value)
            }.apply()
        }

    // "eventId:begin" keys of alarms currently registered with AlarmManager, so they can be cancelled.
    var scheduledKeys: Set<String>
        get() = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_SCHEDULED, value).apply()

    fun matches(event: CalendarEvent): Boolean {
        val color = alarmColor ?: return false
        return !event.allDay && event.eventColor == color
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 5
        private const val KEY_COLOR = "alarm_color"
        private const val KEY_LEAD = "lead_minutes"
        private const val KEY_SOUND = "alarm_sound"
        private const val KEY_SCHEDULED = "scheduled"
    }
}
