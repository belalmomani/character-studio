package app.characterstudio.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import app.characterstudio.CharacterStudioApp
import app.characterstudio.R
import app.characterstudio.settings.DailyReminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onInfo: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = (ctx.applicationContext as CharacterStudioApp).container.settings
    val s by store.state.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }

    fun change(f: (app.characterstudio.settings.ReminderSettings) -> app.characterstudio.settings.ReminderSettings) {
        DailyReminder.schedule(ctx, store.update(f), ExistingWorkPolicy.REPLACE)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_notifications_desc), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_daily_reminder), Modifier.weight(1f))
                Switch(s.enabled, { v ->
                    change { it.copy(enabled = v) }
                    allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                    if (v && !allowed && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            if (s.enabled && !allowed) {
                Text(stringResource(R.string.settings_permission_needed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.settings_grant_permission))
                    }
                }
            }
            OutlinedButton(onClick = { showTime = true }, enabled = s.enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_time, "%02d:%02d".format(s.hour, s.minute)))
            }
            HorizontalDivider()
            listOf(
                "help" to R.string.info_help_title, "privacy" to R.string.info_privacy_title,
                "terms" to R.string.info_terms_title, "about" to R.string.info_about_title,
            ).forEach { (key, title) ->
                OutlinedButton(onClick = { onInfo(key) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(title)) }
            }
        }
    }

    if (showTime) {
        val tp = rememberTimePickerState(initialHour = s.hour, initialMinute = s.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { change { it.copy(hour = tp.hour, minute = tp.minute) }; showTime = false }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = tp) } },
        )
    }
}
