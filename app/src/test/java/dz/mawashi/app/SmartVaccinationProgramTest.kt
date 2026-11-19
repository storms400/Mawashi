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

    @Test fun lambClostridialFirstDoseUsesDamStatus() {
        val birth = LocalDate.parse("2026-11-19")
        assertEquals(LocalDate.parse("2026-12-31"), smartLambClostridialFirstDoseDate(birth, true))
        assertEquals(LocalDate.parse("2026-12-03"), smartLambClostridialFirstDoseDate(birth, false))
    }

    @Test fun lambBoosterUsesProductIntervalWithinThreeToFourWeeks() {
        val first = LocalDate.parse("2027-01-01")
        assertEquals(LocalDate.parse("2027-01-29"), smartLambClostridialBoosterDate(first))
        assertEquals(LocalDate.parse("2027-01-22"), smartLambClostridialBoosterDate(first, 18))
        assertEquals(LocalDate.parse("2027-01-29"), smartLambClostridialBoosterDate(first, 40))
    }

    @Test fun annualWindowKeepsOverdueAndNextTwelveMonthsOnly() {
        val today = LocalDate.parse("2027-01-01")
        assertTrue(smartAnnualWindowContains(LocalDate.parse("2026-12-15"), today))
        assertTrue(smartAnnualWindowContains(LocalDate.parse("2027-12-31"), today))
        assertFalse(smartAnnualWindowContains(LocalDate.parse("2026-11-01"), today))
        assertFalse(smartAnnualWindowContains(LocalDate.parse("2028-01-02"), today))
    }

    private fun annualRecord(
        vaccineId: String,
        date: String,
        nextDate: String = ""
    ) = AnnualVaccineRecord(
        id = "r-$vaccineId", tag = "Q-0001", vaccineId = vaccineId,
        date = date, number = 1, dose = "", product = "منتج مسجل",
        batch = "", expiry = "", vet = "", nextDate = nextDate,
        notes = "", source = "test"
    )

    @Test fun lactatingEweWithoutHistoryGetsReviewTasksNotInjectionOrders() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartLactatingAnnualReviewTasks(emptyList(), today)
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.due == today })
        assertTrue(tasks.all { it.title.startsWith("مراجعة سجل التحصين") })
        assertTrue(tasks.all { it.reason.contains("ليس أمراً بإعطاء جرعة") })
    }

    @Test fun lactatingEweGetsAnnualReviewOneYearAfterRecordedDose() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartLactatingAnnualReviewTasks(
            listOf(annualRecord("clostridial", "2026-11-19")), today
        )
        assertEquals(1, tasks.size)
        assertEquals(LocalDate.parse("2027-11-19"), tasks.single().due)
        assertTrue(tasks.single().title.contains("مراجعة الاستحقاق السنوي"))
    }

    @Test fun productSpecificNextDatePreventsGenericAnnualReview() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartLactatingAnnualReviewTasks(
            listOf(annualRecord("clostridial", "2026-01-01", "2026-08-01")), today
        )
        assertTrue(tasks.isEmpty())
    }
}
