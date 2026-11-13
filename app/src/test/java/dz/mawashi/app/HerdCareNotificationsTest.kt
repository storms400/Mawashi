package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HerdCareNotificationsTest {
    @Test fun deletedOrCompletedReminderCannotShowNotification() {
        val active = HerdCareNotificationData("a", "A1", "تطعيم", "2026-11-14", false)
        val completed = active.copy(id = "b", completed = true)
        assertEquals(true, isActiveHerdCareReminder(listOf(active, completed), "a"))
        assertEquals(false, isActiveHerdCareReminder(listOf(active, completed), "b"))
        assertEquals(false, isActiveHerdCareReminder(listOf(active, completed), "deleted"))
    }

    @Test fun schedulesReminderAtNineInLocalTime() {
        val zone = ZoneId.of("Africa/Algiers")
        val date = LocalDate.of(2026, 11, 14)
        val actual = java.time.Instant.ofEpochMilli(HerdCareNotifications.triggerAt(date, zone)).atZone(zone)
        assertEquals(date, actual.toLocalDate())
        assertEquals(9, actual.hour)
        assertEquals(0, actual.minute)
    }
}
