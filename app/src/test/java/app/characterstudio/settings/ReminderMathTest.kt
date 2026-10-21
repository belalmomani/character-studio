package app.characterstudio.settings

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMathTest {
    private val amman = ZoneId.of("Asia/Amman")
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 10, 21, h, m, s, 0, amman)
    private fun hours(n: Long) = Duration.ofHours(n).toMillis()

    @Test fun laterTodayWhenTimeNotPassed() = assertEquals(hours(1), ReminderMath.delayMillis(at(17, 0), 18, 0))
    @Test fun tomorrowWhenTimePassed() = assertEquals(hours(23), ReminderMath.delayMillis(at(19, 0), 18, 0))
    @Test fun exactlyNowMeansTomorrow() = assertEquals(hours(24), ReminderMath.delayMillis(at(18, 0), 18, 0))
    @Test fun minutesAreRespected() =
        assertEquals(Duration.ofMinutes(45).toMillis(), ReminderMath.delayMillis(at(18, 0), 18, 45))
    @Test fun outOfRangeInputsAreClamped() =
        assertEquals(ReminderMath.delayMillis(at(17, 0), 23, 0), ReminderMath.delayMillis(at(17, 0), 99, -5))
    @Test fun settingsNormalize() {
        val s = ReminderSettings(hour = 30, minute = -1).normalized()
        assertEquals(23, s.hour); assertEquals(0, s.minute)
    }
}
