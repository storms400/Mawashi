package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun overdueReminderIsRecognizedForCatchUpDelivery() {
        val today = LocalDate.parse("2026-11-19")
        assertEquals(true, HerdCareNotifications.isOverdue(LocalDate.parse("2026-11-18"), today))
        assertEquals(false, HerdCareNotifications.isOverdue(today, today))
        assertEquals(false, HerdCareNotifications.isOverdue(LocalDate.parse("2026-11-20"), today))
    }

    @Test fun deliveryKeyChangesWhenReminderDateChangesAndStaysStableOtherwise() {
        assertEquals("annual-1|2026-11-18", HerdCareNotifications.deliveryKey("annual-1", "2026-11-18"))
        assertEquals(HerdCareNotifications.deliveryKey("annual-1", "2026-11-18"),
            HerdCareNotifications.deliveryKey("annual-1", "2026-11-18"))
        assertNotEquals(HerdCareNotifications.deliveryKey("annual-1", "2026-11-18"),
            HerdCareNotifications.deliveryKey("annual-1", "2026-11-19"))
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
