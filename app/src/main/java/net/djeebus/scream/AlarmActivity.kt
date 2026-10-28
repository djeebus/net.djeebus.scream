package net.djeebus.scream

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Full-screen alarm UI, shown over the lock screen via the notification's full-screen intent.
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                    AlarmScreen(onDone = ::finish)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val info by AlarmState.current.collectAsState()

    // The alarm was dismissed (here or from the notification), or there is nothing ringing.
    LaunchedEffect(info) { if (info == null) onDone() }

    val alarm = info ?: return
    val startsAt = DateUtils.formatDateTime(context, alarm.beginMillis, DateUtils.FORMAT_SHOW_TIME)

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(alarm.title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        if (alarm.beginMillis > 0) Text("Starts at $startsAt", style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = { context.startService(AlarmService.commandIntent(context, AlarmService.ACTION_DISMISS)) },
            modifier = Modifier.fillMaxWidth().height(96.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Dismiss", style = MaterialTheme.typography.headlineMedium) }
        OutlinedButton(
            onClick = { context.startService(AlarmService.commandIntent(context, AlarmService.ACTION_SNOOZE)) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text("Snooze ${AlarmScheduler.SNOOZE_MINUTES} min", style = MaterialTheme.typography.titleLarge) }
    }
}
