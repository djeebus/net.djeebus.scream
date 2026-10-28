package net.djeebus.scream

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

private const val PREVIEW_DAYS = 30
private val LEAD_OPTIONS = listOf(0, 5, 10, 15)

class MainActivity : ComponentActivity() {
    // Bumped on every resume so permission state and the event list refresh after returning from system settings.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen(resumeTick) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun MainScreen(resumeTick: Int) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var calendarGranted by remember(resumeTick) { mutableStateOf(has(Manifest.permission.READ_CALENDAR)) }
    var notificationsGranted by remember(resumeTick) { mutableStateOf(has(Manifest.permission.POST_NOTIFICATIONS)) }
    val fullScreenAllowed = remember(resumeTick) {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarGranted = it
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    if (!calendarGranted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Scream needs to read your calendar to find events that should alarm.")
            Button(
                onClick = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant calendar access") }
        }
        return
    }

    val events = remember(resumeTick) { CalendarRepository.upcoming(context, PREVIEW_DAYS) }
    var alarmColor by remember { mutableStateOf(settings.alarmColor) }
    var leadMinutes by remember { mutableIntStateOf(settings.leadMinutes) }

    // Keep the registered alarms in sync with what the preview shows.
    LaunchedEffect(resumeTick, alarmColor, leadMinutes) { AlarmScheduler.reschedule(context) }

    // Offer the colors actually in use on upcoming events, plus the saved one.
    val colors = (events.mapNotNull { it.eventColor } + listOfNotNull(alarmColor)).distinct()
    val matching = events.count { settings.matches(it) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        if (!notificationsGranted) item {
            SetupRow("Notifications are off, so alarms can't show.", "Allow") {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!fullScreenAllowed) item {
            SetupRow("Full-screen alerts are off, so the alarm won't take over a locked screen.", "Allow") {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:${context.packageName}")),
                )
            }
        }
        if (notificationsGranted && fullScreenAllowed) item { Text("All permissions granted.") }
        item {
            OutlinedButton(onClick = {
                context.startForegroundService(
                    AlarmService.startIntent(context, AlarmInfo(-1, System.currentTimeMillis(), "Test alarm")),
                )
            }) { Text("Test alarm now") }
        }

        item { Text("Alarm color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (colors.isEmpty()) Text("No colored events in the next $PREVIEW_DAYS days")
                colors.forEach { color ->
                    ColorChip(color, selected = color == alarmColor) {
                        alarmColor = if (alarmColor == color) null else color
                        settings.alarmColor = alarmColor
                    }
                }
            }
        }

        item { Text("Alarm before event", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAD_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = leadMinutes == minutes,
                        onClick = { leadMinutes = minutes; settings.leadMinutes = minutes },
                        label = { Text(if (minutes == 0) "At start" else "$minutes min") },
                    )
                }
            }
        }

        item {
            Text(
                if (alarmColor == null) "No color selected: nothing will alarm."
                else "$matching of ${events.size} events in the next $PREVIEW_DAYS days will alarm.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(events) { EventRow(it, scary = settings.matches(it), leadMinutes = leadMinutes) }
        item { Box(Modifier.size(16.dp)) }
    }
}

@Composable
private fun SetupRow(message: String, buttonLabel: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, Modifier.weight(1f))
        Button(onClick = onClick) { Text(buttonLabel) }
    }
}

@Composable
private fun ColorChip(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EventRow(event: CalendarEvent, scary: Boolean, leadMinutes: Int) {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
        (if (event.allDay) 0 else DateUtils.FORMAT_SHOW_TIME) or DateUtils.FORMAT_ABBREV_ALL
    val whenText = DateUtils.formatDateTime(context, event.beginMillis, flags)
    val alarmAt = event.beginMillis - TimeUnit.MINUTES.toMillis(leadMinutes.toLong())

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (scary) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(event.eventColor?.let { Color(it) } ?: Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            Text(whenText, style = MaterialTheme.typography.bodySmall)
        }
        if (scary) Column(horizontalAlignment = Alignment.End) {
            Text("ALARM", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            Text(
                DateUtils.formatDateTime(context, alarmAt, DateUtils.FORMAT_SHOW_TIME),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
