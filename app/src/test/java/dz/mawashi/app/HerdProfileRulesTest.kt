package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HerdProfileRulesTest {
    @Test
    fun ageLabelShowsExactDaysForYoungLamb() {
        val animal = HerdAnimal(
            id = "lamb-1",
            tag = "Q-0001",
            kind = "حمل/رخلة",
            ageMonths = 0,
            health = "جيدة",
            notes = "",
            birthDate = "2026-11-15"
        )

        assertTrue(herdAnimalAgeLabel(animal, LocalDate.parse("2026-11-16")).contains("1 يوم"))
    }

    @Test
    fun ageLabelShowsMonthsDaysAndTotalDays() {
        val animal = HerdAnimal(
            id = "lamb-2",
            tag = "Q-0002",
            kind = "حمل/رخلة",
            ageMonths = 1,
            health = "جيدة",
            notes = "",
            birthDate = "2026-10-01"
        )

        val label = herdAnimalAgeLabel(animal, LocalDate.parse("2026-11-16"))
        assertTrue(label.contains("1 شهر و 15 يوم"))
        assertTrue(label.contains("46 يوم إجمالاً"))
    }

    @Test
    fun pregnancyDatesMustBeOrderedAndStartCannotBeFuture() {
        val today = LocalDate.parse("2026-11-16")
        assertTrue(isValidPregnancyDates("2026-10-01", "2027-02-28", today))
        assertFalse(isValidPregnancyDates("2026-10-01", "2026-09-30", today))
        assertFalse(isValidPregnancyDates("2026-11-17", "2027-04-16", today))
    }

    @Test
    fun motherMustBeAnActiveEwe() {
        val activeEwe = HerdAnimal("mother-1", "E-01", "نعجة", 24, "جيدة", "")
        val archivedEwe = HerdAnimal(
            "mother-2", "E-02", "نعجة", 30, "جيدة", "",
            lifecycleStatus = "مؤرشف"
        )
        val ram = HerdAnimal("ram-1", "R-01", "كبش", 24, "جيدة", "")

        assertTrue(isValidMotherTag("E-01", listOf(activeEwe, archivedEwe, ram)))
        assertFalse(isValidMotherTag("E-02", listOf(activeEwe, archivedEwe, ram)))
        assertFalse(isValidMotherTag("R-01", listOf(activeEwe, archivedEwe, ram)))
    }
}
