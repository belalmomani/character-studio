package app.characterstudio.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            hour = prefs.getInt(KEY_HOUR, 18),
            minute = prefs.getInt(KEY_MINUTE, 0),
        ).normalized(),
    )
    val state: StateFlow<ReminderSettings> = _state

    fun update(f: (ReminderSettings) -> ReminderSettings): ReminderSettings {
        val next = f(_state.value).normalized()
        prefs.edit().putBoolean(KEY_ENABLED, next.enabled).putInt(KEY_HOUR, next.hour).putInt(KEY_MINUTE, next.minute).apply()
        _state.value = next
        return next
    }

    private companion object {
        const val KEY_ENABLED = "reminder_enabled"
        const val KEY_HOUR = "reminder_hour"
        const val KEY_MINUTE = "reminder_minute"
    }
}
