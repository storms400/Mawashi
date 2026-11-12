package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HerdArchiveTest {
    private val today = LocalDate.parse("2026-11-13")
    private val animal = HerdAnimal(
        id = "archive-test-id",
        tag = "Q-0042",
        kind = "نعجة",
        ageMonths = 36,
        health = "جيدة",
        notes = "سجل صحي محفوظ",
        weightKg = 58.5,
        birthDate = "2023-11-13",
        birthWeightKg = 4.2,
        motherTag = "Q-0007",
        productionState = "نعجة حامل",
        pregnancyStage = "الأربعة أسابيع الأخيرة",
        fetusCount = 2,
        nursingLambs = 0
    )

    @Test
    fun archivePreservesAnimalDataAndRecordsExit() {
        val archived = archiveHerdAnimal(animal, "2026-11-13", "بيع", today)

        assertEquals("مؤرشف", archived.lifecycleStatus)
        assertEquals("2026-11-13", archived.exitDate)
        assertEquals("بيع", archived.exitReason)
        assertEquals(animal.id, archived.id)
        assertEquals(animal.tag, archived.tag)
        assertEquals(animal.weightKg, archived.weightKg)
        assertEquals(animal.productionState, archived.productionState)
        assertEquals(animal.pregnancyStage, archived.pregnancyStage)
        assertEquals(animal.fetusCount, archived.fetusCount)
        assertEquals(animal.notes, archived.notes)
    }

    @Test
    fun archiveRejectsFutureExitDate() {
        assertFalse(isValidHerdExitDate("2026-11-14", today))
        try {
            archiveHerdAnimal(animal, "2026-11-14", "بيع", today)
            throw AssertionError("Expected future exit date to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: future exit dates must not be accepted.
        }
    }

    @Test
    fun archiveRejectsUnknownExitReason() {
        try {
            archiveHerdAnimal(animal, "2026-11-13", "سبب غير معروف", today)
            throw AssertionError("Expected invalid exit reason to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: only sale, death, and culling are supported.
        }
    }

    @Test
    fun restoreReturnsAnimalToActiveStateAndClearsExitMetadata() {
        val archived = archiveHerdAnimal(animal, "2026-11-12", "نفوق", today)
        val restored = restoreHerdAnimal(archived)

        assertEquals("نشط", restored.lifecycleStatus)
        assertEquals("", restored.exitDate)
        assertEquals("", restored.exitReason)
        assertEquals(animal.id, restored.id)
        assertEquals(animal.tag, restored.tag)
        assertEquals(animal.weightKg, restored.weightKg)
    }

    @Test
    fun activeHerdExcludesArchivedAnimals() {
        val archived = archiveHerdAnimal(animal, "2026-11-12", "استبعاد", today)
        assertEquals(listOf(animal), activeHerdAnimals(listOf(animal, archived)))
    }

    @Test
    fun generatedTagsSkipExistingNumbersWithoutDuplicates() {
        val tags = generateHerdTags(listOf("Q-0001", "q-0003", "legacy-tag"), 3)
        assertEquals(listOf("Q-0002", "Q-0004", "Q-0005"), tags)
        assertEquals(tags.size, tags.toSet().size)
    }
}
