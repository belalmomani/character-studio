package app.characterstudio.settings

import java.time.Duration
import java.time.ZonedDateTime

data class ReminderSettings(val enabled: Boolean = true, val hour: Int = 18, val minute: Int = 0) {
    fun normalized() = copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))
}

object ReminderMath {
    /** المهلة حتى أقرب وقت قادم (اليوم إن لم يفت، وإلا غدًا) بحسب المنطقة الزمنية لـ now. */
    fun delayMillis(now: ZonedDateTime, hour: Int, minute: Int): Long {
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(now.zone)
        return Duration.between(now, next).toMillis()
    }
}
