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
    
    @Test
    fun derivesLambAgeFromBirthDateAndFallsBackForLegacyOrInvalidDates() {
        val today = LocalDate.parse("2026-11-10")
        assertEquals(6, ageMonthsFromBirthDate("2026-05-10", 0, today))
        assertEquals(0, ageMonthsFromBirthDate("2026-11-10", 0, today))
        assertEquals(8, ageMonthsFromBirthDate("invalid", 8, today))
        assertEquals(8, ageMonthsFromBirthDate("2026-11-11", 8, today))
    }

    @Test
    fun estimatedAgeAdvancesByElapsedDaysWhenBirthDateIsUnknown() {
        val registered = animal.copy(ageMonths = 6, birthDate = "", ageRecordedDate = "2026-11-19")
        val registrationDay = LocalDate.parse("2026-11-19")
        val oneMonthLater = LocalDate.parse("2026-12-19")
        assertEquals(180L, herdAnimalAgeDays(registered, registrationDay))
        assertEquals(210L, herdAnimalAgeDays(registered, oneMonthLater))
        assertEquals(6, herdAnimalAgeMonths(registered, registrationDay))
        assertEquals(7, herdAnimalAgeMonths(registered, oneMonthLater))
        assertTrue(herdAnimalAgeLabel(registered, oneMonthLater).contains("7 شهر"))
    }

    @Test
    fun knownBirthDateUsesExactCalendarAgeInsteadOfEstimatedAnchor() {
        val registered = animal.copy(ageMonths = 6, birthDate = "2026-05-19", ageRecordedDate = "2026-11-19")
        val today = LocalDate.parse("2026-12-19")
        assertEquals(214L, herdAnimalAgeDays(registered, today))
        assertEquals(7, herdAnimalAgeMonths(registered, today))
        assertTrue(herdAnimalAgeLabel(registered, today).contains("7 شهر"))
    }

    @Test
    fun acceptsOnlyRegisteredEweAsMotherAndRejectsSelfOrOtherKinds() {
        val ewe = animal.copy(id = "mother", tag = "E-1", kind = "نعجة")
        val ram = animal.copy(id = "ram", tag = "R-1", kind = "كبش")
        val lamb = animal.copy(id = "lamb", tag = "L-1", kind = "حمل/رخلة")
        assertTrue(isValidMotherTag("E-1", listOf(ewe, lamb), lamb.id))
        assertFalse(isValidMotherTag("R-1", listOf(ram, lamb), lamb.id))
        assertFalse(isValidMotherTag("missing", listOf(ewe, lamb), lamb.id))
        assertFalse(isValidMotherTag("L-1", listOf(lamb), lamb.id))
    }

    @Test
    fun pregnancySexMustBeSelectedAndMixedIsOnlyValidForMultipleFetuses() {
        assertTrue(isValidPregnancyOffspringSex("ذكر", 1))
        assertTrue(isValidPregnancyOffspringSex("أنثى", 1))
        assertFalse(isValidPregnancyOffspringSex("", 1))
        assertFalse(isValidPregnancyOffspringSex("مختلط", 1))
        assertTrue(isValidPregnancyOffspringSex("مختلط", 2))
        assertTrue(isValidPregnancyOffspringSex("مختلط", 3))
        assertFalse(isValidPregnancyOffspringSex("غير محدد", 2))
    }

    @Test
    fun confirmedBirthAndWeaningDriveTheSameEweRecord() {
        val born = animal.copy(
            productionState = "نعجة حامل",
            birthConfirmedDate = "2026-10-01",
            weaningConfirmedDate = ""
        )
        val lactating = reclassifyHerdAnimal(born, LocalDate.parse("2026-11-10"))
        assertEquals("animal-1", lactating.id)
        assertEquals("Q-0001", lactating.tag)
        assertEquals("نعجة مرضعة", lactating.productionState)
        val weaned = reclassifyHerdAnimal(lactating.copy(weaningConfirmedDate = "2026-11-10"), LocalDate.parse("2026-11-10"))
        assertEquals("نعجة جافة", weaned.productionState)
        assertEquals(lactating.id, weaned.id)
    }

    @Test
    fun newbornEntryRequiresAConfirmedActualBirthDate() {
        val today = LocalDate.parse("2026-11-10")
        assertTrue(isValidActualBirthDate("2026-11-10", today))
        assertTrue(isValidActualBirthDate("2026-11-09", today))
        assertFalse(isValidActualBirthDate("2026-11-11", today))
        assertFalse(isValidActualBirthDate("bad-date", today))
    }

    @Test
    fun weaningRequiresEveryLinkedLambToBeAtLeastSixtyDaysOld() {
        val lambA = animal.copy(id = "l1", tag = "L-1", kind = "حمل/رخلة", birthDate = "2026-08-01", motherTag = "Q-0001")
        val lambB = animal.copy(id = "l2", tag = "L-2", kind = "حمل/رخلة", birthDate = "2026-09-20", motherTag = "Q-0001")
        assertTrue(isEligibleForWeaning(listOf(lambA), "2026-10-01"))
        assertFalse(isEligibleForWeaning(listOf(lambA, lambB), "2026-11-10"))
        assertFalse(isEligibleForWeaning(emptyList(), "2026-11-10"))
    }

    @Test
    fun matureLambChangesCategoryWithoutLosingIdentityOrMotherLink() {
        val lamb = animal.copy(
            id = "lamb-id", tag = "Q-0010", kind = "حمل/رخلة",
            birthDate = "2026-03-10", sex = "ذكر", motherTag = "Q-0001",
            productionState = "خروف"
        )
        val ram = reclassifyHerdAnimal(lamb, LocalDate.parse("2026-11-10"))
        assertEquals("كبش", ram.kind)
        assertEquals("كبش", ram.productionState)
        assertEquals("lamb-id", ram.id)
        assertEquals("Q-0010", ram.tag)
        assertEquals("Q-0001", ram.motherTag)
    }

    @Test
    fun femaleLambMaturesIntoEweAndArchivedAnimalsDoNotChange() {
        val lamb = animal.copy(kind = "حمل/رخلة", birthDate = "2026-03-10", sex = "أنثى", motherTag = "Q-0001")
        val ewe = reclassifyHerdAnimal(lamb, LocalDate.parse("2026-11-10"))
        assertEquals("نعجة", ewe.kind)
        assertEquals("نعجة جافة", ewe.productionState)
        val archived = reclassifyHerdAnimal(lamb.copy(lifecycleStatus = "مؤرشف"), LocalDate.parse("2026-11-10"))
        assertEquals("حمل/رخلة", archived.kind)
    }

    @Test
    fun pregnancyTransitionKeepsSameAnimalIdentityAndLineageForProgramReclassification() {
        val dry = animal.copy(tag = "E-0099", motherTag = "", productionState = "نعجة جافة")
        val pregnant = dry.copy(
            productionState = "نعجة حامل",
            pregnancyStage = "حامل",
            pregnancyScanDate = "2026-11-19",
            pregnancyScanOutcome = "حامل",
            expectedBirthDate = "2027-04-15"
        )
        val reclassified = reclassifyHerdAnimal(pregnant, LocalDate.parse("2026-11-19"))
        assertEquals(dry.id, reclassified.id)
        assertEquals(dry.tag, reclassified.tag)
        assertEquals("نعجة حامل", reclassified.productionState)
        assertEquals("حامل", reclassified.pregnancyStage)
        assertEquals("2027-04-15", reclassified.expectedBirthDate)
    }

    @Test
    fun annualHistoryRemainsAttachedToAnimalTagAcrossProductionStateChanges() {
        val dry = animal.copy(tag = "E-0100", productionState = "نعجة جافة")
        val pregnant = dry.copy(productionState = "نعجة حامل", pregnancyStage = "حامل")
        val lactating = pregnant.copy(productionState = "نعجة مرضعة", birthConfirmedDate = "2027-04-15")
        assertEquals(dry.tag, pregnant.tag)
        assertEquals(dry.tag, lactating.tag)
        assertEquals(dry.id, lactating.id)
    }

    @Test
    fun validatesProductionStateAgainstAnimalKind() {
        assertTrue(isValidProductionState("نعجة", "نعجة حامل"))
        assertTrue(isValidProductionState("كبش", "كبش"))
        assertTrue(isValidProductionState("حمل/رخلة", "حمل يرضع"))
        assertFalse(isValidProductionState("كبش", "نعجة حامل"))
        assertFalse(isValidProductionState("حمل/رخلة", "نعجة مرضعة"))
    }
}
