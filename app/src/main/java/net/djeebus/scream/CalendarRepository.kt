package net.djeebus.scream

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit

data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    // Explicit per-event color (what Google Calendar's "Event color" sets); null if the
    // event just inherits its calendar's color.
    val eventColor: Int?,
)

object CalendarRepository {
    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_COLOR,
    )

    fun upcoming(context: Context, days: Int): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(days.toLong())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, end)
        }.build()

        return query(context, uri)
    }

    private fun query(context: Context, uri: android.net.Uri): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri, PROJECTION, null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                events += CalendarEvent(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty().ifBlank { "(no title)" },
                    beginMillis = c.getLong(2),
                    endMillis = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    eventColor = if (c.isNull(5) || c.getInt(5) == 0) null else c.getInt(5),
                )
            }
        }
        return events
    }

    // Looks up one occurrence, e.g. to re-check an alarm right before it fires.
    fun find(context: Context, eventId: Long, beginMillis: Long): CalendarEvent? {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, beginMillis - 1)
            ContentUris.appendId(it, beginMillis + 1)
        }.build()
        return query(context, uri).firstOrNull { it.id == eventId && it.beginMillis == beginMillis }
    }
}
