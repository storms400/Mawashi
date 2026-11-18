package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SmartVaccinationProgramTest {
    private fun record(tag: String, stepId: String) = HerdCareReminder(
        id = "done-$stepId", tag = tag, kind = "تحصين مرحلي",
        title = stepId, date = "2026-01-01", notes = "SMART_VAX_DONE:$stepId", completed = true
    )

    @Test fun dueDatesAreCalculatedFromBirthDate() {
        val birth = LocalDate.parse("2026-11-18")
        assertEquals(LocalDate.parse("2027-01-18"), smartVaccinationDueDate(birth, SMART_VACCINATION_STEPS[0]))
        assertEquals(LocalDate.parse("2027-02-18"), smartVaccinationDueDate(birth, SMART_VACCINATION_STEPS[1]))
        assertEquals(LocalDate.parse("2027-03-18"), smartVaccinationDueDate(birth, SMART_VACCINATION_STEPS[2]))
    }

    @Test fun nextStageRemainsLockedUntilPreviousStepIsRecorded() {
        val records = emptyList<HerdCareReminder>()
        assertTrue(smartVaccinationUnlocked("L-1", 0, records))
        assertFalse(smartVaccinationUnlocked("L-1", 1, records))
        assertTrue(smartVaccinationUnlocked("L-1", 1, records + record("L-1", SMART_VACCINATION_STEPS[0].id)))
        assertFalse(smartVaccinationUnlocked("L-1", 2, records + record("L-1", SMART_VACCINATION_STEPS[0].id)))
    }

    @Test fun nextStepRequiresPreviousStageThreeVaccination() {
        val records = listOf(record("L-1", SMART_VACCINATION_STEPS[0].id), record("L-1", SMART_VACCINATION_STEPS[1].id))
        assertTrue(smartVaccinationUnlocked("L-1", 2, records))
        assertFalse(smartVaccinationUnlocked("L-1", 3, records))
        assertTrue(smartVaccinationUnlocked("L-1", 3, records + record("L-1", SMART_VACCINATION_STEPS[2].id)))
    }

    @Test fun reminderReconciliationKeepsOtherCareRecordsAndAddsOnlyNextUnlockedStep() {
        val animal = HerdAnimal(id="a",tag="L-1",kind="حمل",ageMonths=0,health="جيدة",notes="",birthDate="2026-01-01")
        val unrelated = HerdCareReminder("other","L-1","متابعة","فحص","2026-12-01","",false)
        val reconciled = reconcileSmartVaccinationReminders(listOf(animal), listOf(unrelated), LocalDate.parse("2026-02-01"))
        assertTrue(reconciled.any { it.id == "other" })
        assertTrue(reconciled.any { it.notes == "SMART_VAX_PENDING:stage1_enteric" })
        assertFalse(reconciled.any { it.notes == "SMART_VAX_PENDING:stage2_hemorrhagic" })
    }
}
