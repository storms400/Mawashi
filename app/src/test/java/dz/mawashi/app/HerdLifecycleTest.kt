package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HerdLifecycleTest {
    private val animal = HerdAnimal(
        id = "animal-1",
        tag = "Q-0001",
        kind = "نعجة",
        ageMonths = 24,
        health = "جيدة",
        notes = "سجل تجريبي",
        weightKg = 52.0
    )

    @Test fun archivesWithoutLosingAnimalIdentityOrDetails() {
        val archived = archiveHerdAnimal(animal, "2026-11-09", "بيع", LocalDate.parse("2026-11-10"))
        assertEquals("animal-1", archived.id)
        assertEquals("Q-0001", archived.tag)
        assertEquals(52.0, archived.weightKg!!, 0.0)
        assertEquals("مؤرشف", archived.lifecycleStatus)
        assertEquals("بيع", archived.exitReason)
        assertEquals("2026-11-09", archived.exitDate)
    }

    @Test fun restoresArchivedAnimalAndClearsExitMetadata() {
        val archived = archiveHerdAnimal(animal, "2026-11-10", "نفوق", LocalDate.parse("2026-11-10"))
        val restored = restoreHerdAnimal(archived)
        assertEquals("نشط", restored.lifecycleStatus)
        assertEquals("", restored.exitDate)
        assertEquals("", restored.exitReason)
        assertEquals(animal.id, restored.id)
    }

    @Test fun activeHerdExcludesArchivedAnimalsButKeepsLegacyActiveRecords() {
        val active = animal.copy(tag = "Q-0002")
        val archived = archiveHerdAnimal(animal, "2026-11-10", "بيع", LocalDate.parse("2026-11-10"))
        assertEquals(listOf("Q-0002"), activeHerdAnimals(listOf(archived, active)).map { it.tag })
    }

    @Test fun feedInputsExcludeArchivedAnimalsAndAnimalsWithoutValidWeight() {
        val active = animal.copy(tag = "Q-0002")
        val archived = archiveHerdAnimal(animal, "2026-11-10", "نفوق", LocalDate.parse("2026-11-10"))
        val noWeight = active.copy(tag = "Q-0003", weightKg = null)
        val inputs = feedInputsForAnimals(listOf(archived, active, noWeight))
        assertEquals(1, inputs.size)
        assertEquals("Q-0002", inputs.single().tag)
    }

    @Test fun rejectsImpossibleOrFutureLambBirthDates() {
        val today = LocalDate.parse("2026-11-10")
        assertFalse(isValidLambBirthDate("2026-02-31", today))
        assertFalse(isValidLambBirthDate("2026-11-11", today))
        assertFalse(isValidLambBirthDate("not-a-date", today))
        assertTrue(isValidLambBirthDate("2026-11-10", today))
        assertTrue(isValidLambBirthDate("2026-11-09", today))
    }

    @Test fun rejectsInvalidOrFutureExitDates() {
        val today = LocalDate.parse("2026-11-10")
        assertFalse(isValidHerdExitDate("2026-02-31", today))
        assertFalse(isValidHerdExitDate("2026-11-11", today))
        assertTrue(isValidHerdExitDate("2026-11-10", today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownExitReason() {
        archiveHerdAnimal(animal, "2026-11-10", "سبب غير معروف", LocalDate.parse("2026-11-10"))
    }
}
