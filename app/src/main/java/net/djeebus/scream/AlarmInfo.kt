package net.djeebus.scream

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

data class AlarmInfo(val eventId: Long, val beginMillis: Long, val title: String) {
    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_EVENT_ID, eventId)
        .putExtra(EXTRA_BEGIN, beginMillis)
        .putExtra(EXTRA_TITLE, title)

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_BEGIN = "begin"
        const val EXTRA_TITLE = "title"

        fun from(intent: Intent) = AlarmInfo(
            eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1),
            beginMillis = intent.getLongExtra(EXTRA_BEGIN, 0),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
        )
    }
}

// The alarm currently ringing, if any. AlarmActivity watches this to know when to close.
object AlarmState {
    val current = MutableStateFlow<AlarmInfo?>(null)
}
