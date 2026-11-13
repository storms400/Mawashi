package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HerdCareNotificationsTest {
    @Test fun schedulesReminderAtNineInLocalTime() {
        val zone = ZoneId.of("Africa/Algiers")
        val date = LocalDate.of(2026, 11, 14)
        val actual = java.time.Instant.ofEpochMilli(HerdCareNotifications.triggerAt(date, zone)).atZone(zone)
        assertEquals(date, actual.toLocalDate())
        assertEquals(9, actual.hour)
        assertEquals(0, actual.minute)
    }
}
