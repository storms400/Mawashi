package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SmartVaccinationProgramTest {
    private fun record(tag: String, stepId: String) = HerdCareReminder(
        id = "done-$stepId", tag = tag, kind = "تحصين مرحلي",
        title = stepId, date = "2026-01-01", notes = "SMART_VAX_DONE:$stepId", completed = true
    )

    @Test fun liveHerdCategoryMappingMatchesEveryCareProgram() {
        val today = LocalDate.parse("2026-11-20")
        val dry = HerdAnimal(id="dry", tag="E-1", kind="نعجة", ageMonths=24, health="جيدة", notes="", productionState="نعجة جافة")
        val pregnant = dry.copy(id="preg", tag="E-2", productionState="نعجة حامل")
        val lactating = dry.copy(id="lact", tag="E-3", productionState="نعجة مرضعة")
        val ram = dry.copy(id="ram", tag="R-1", kind="كبش", productionState="كبش", sex="ذكر")
        val lamb = dry.copy(id="lamb", tag="L-1", kind="حمل/رخلة", productionState="حمل يرضع", sex="أنثى", ageMonths=2, birthDate="2026-09-20")
        val archived = dry.copy(id="archived", tag="E-4", lifecycleStatus="مؤرشف")
        assertEquals("dry", smartVaccinationCategoryForAnimal(dry, today))
        assertEquals("pregnant", smartVaccinationCategoryForAnimal(pregnant, today))
        assertEquals("lactating", smartVaccinationCategoryForAnimal(lactating, today))
        assertEquals("ram", smartVaccinationCategoryForAnimal(ram, today))
        assertEquals("lamb", smartVaccinationCategoryForAnimal(lamb, today))
        assertNull(smartVaccinationCategoryForAnimal(archived, today))
    }

    @Test fun legacyVaccineReminderWithSameAnimalVaccineAndDateIsSuppressed() {
        val legacy = HerdCareReminder("old-1", "Q-0001", "تحصين", "التسمم المعوي والكزاز",
            "2026-11-19", "جرعة مسجلة", true)
        assertTrue(isDuplicateLegacyVaccineReminder(legacy, "Q-0001", LocalDate.parse("2026-11-19"),
            "clostridial", "التسمم المعوي والكزاز"))
        assertFalse(isDuplicateLegacyVaccineReminder(legacy, "Q-0002", LocalDate.parse("2026-11-19"),
            "clostridial", "التسمم المعوي والكزاز"))
        assertFalse(isDuplicateLegacyVaccineReminder(legacy, "Q-0001", LocalDate.parse("2026-11-20"),
            "clostridial", "التسمم المعوي والكزاز"))
    }

    @Test fun separateVaccineOnSameDateIsNotMistakenForDuplicate() {
        val legacy = HerdCareReminder("old-2", "Q-0001", "تحصين", "جدري الأغنام",
            "2026-11-19", "", false)
        assertFalse(isDuplicateLegacyVaccineReminder(legacy, "Q-0001", LocalDate.parse("2026-11-19"),
            "pasteurella", "الباستوريلا"))
    }

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
        assertEquals(LocalDate.parse("2027-01-19"), smartLambClostridialBoosterDate(first, 18))
        assertEquals(LocalDate.parse("2027-02-10"), smartLambClostridialBoosterDate(first, 40))
    }

    @Test fun annualWindowKeepsAllUnresolvedOverdueAndNextTwelveMonths() {
        val today = LocalDate.parse("2027-01-01")
        assertTrue(smartAnnualWindowContains(LocalDate.parse("2026-12-15"), today))
        assertTrue(smartAnnualWindowContains(LocalDate.parse("2027-12-31"), today))
        assertTrue(smartAnnualWindowContains(LocalDate.parse("2026-11-01"), today))
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
        val tasks = smartAdultAnnualCoreTasks("lactating", emptyList(), today)
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.due == today })
        assertTrue(tasks.all { it.title.startsWith("مراجعة تأسيس التحصين") })
        assertTrue(tasks.all { it.reason.contains("ليس أمراً بإعطاء جرعة") })
    }

    @Test fun lactatingEweGetsAnnualReviewOneYearAfterRecordedDose() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartAdultAnnualCoreTasks("lactating", listOf(annualRecord("clostridial", "2026-11-19")), today)
        val clostridial = tasks.single { it.vaccineId == "clostridial" }
        assertEquals(LocalDate.parse("2027-11-19"), clostridial.due)
        assertTrue(clostridial.title.contains("مراجعة الاستحقاق السنوي"))
    }

    @Test fun productSpecificNextDatePreventsGenericAnnualReview() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartAdultAnnualCoreTasks("lactating", listOf(annualRecord("clostridial", "2026-01-01", "2026-08-01")), today)
        assertFalse(tasks.any { it.vaccineId == "clostridial" })
        assertTrue(tasks.any { it.vaccineId == "pasteurella" && it.due == today })
    }

    @Test fun allAdultProgramsReceiveDatedCoreReviewTasks() {
        val today = LocalDate.parse("2026-11-19")
        listOf("dry", "pregnant", "lactating", "ram").forEach { category ->
            val tasks = smartAdultAnnualCoreTasks(category, emptyList(), today)
            assertEquals(category, 2, tasks.size)
            assertTrue(category, tasks.all { it.due == today })
        }
    }

    @Test fun pregnantEweClostridialReviewIsScheduledBeforeExpectedLambing() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartAdultAnnualCoreTasks("pregnant", emptyList(), today, "2026-12-10")
        val clostridial = tasks.single { it.vaccineId == "clostridial" }
        assertEquals(LocalDate.parse("2026-11-19"), clostridial.due)
        assertTrue(clostridial.title.contains("ما قبل الولادة"))
    }

    @Test fun recordedProductNextDateIsRespectedWithoutDuplicateCoreReview() {
        val today = LocalDate.parse("2026-11-19")
        val tasks = smartAdultAnnualCoreTasks("ram", listOf(annualRecord("clostridial", "2026-01-01", "2027-01-01")), today)
        assertFalse(tasks.any { it.vaccineId == "clostridial" })
    }

    @Test fun lambCampaignReviewsAreCalculatedFromBirthAndSex() {
        val eweLamb = HerdAnimal(id="a", tag="L-2", kind="حمل/رخلة", ageMonths=0, health="جيدة", notes="", birthDate="2026-11-19", sex="أنثى")
        val tasks = smartLambCampaignReviewTasks(eweLamb)
        assertEquals(LocalDate.parse("2027-02-19"), tasks.single { it.vaccineId == "brucella" }.due)
        listOf("pox", "ppr", "fmd").forEach { id -> assertEquals(LocalDate.parse("2027-03-19"), tasks.single { it.vaccineId == id }.due) }
        assertTrue(tasks.all { it.reason.contains("ليس أمراً تلقائياً") || it.reason.contains("لا يُعطى") })
        val ramLamb = eweLamb.copy(tag="L-3", sex="ذكر")
        assertFalse(smartLambCampaignReviewTasks(ramLamb).any { it.vaccineId == "brucella" })
    }

    @Test fun breedingSeasonCreatesCategorySpecificReviewDates() {
        val today = LocalDate.parse("2026-11-19")
        val start = LocalDate.parse("2027-02-01")
        val ewe = smartBreedingSeasonReviewTasks("dry", "E-1", start, "R-1", setOf("E-1"), today)
        assertEquals(LocalDate.parse("2026-12-03"), ewe.single().due)
        assertTrue(ewe.single().reason.contains("ليست أمراً") || ewe.single().reason.contains("لا تعطَ"))
        val ram = smartBreedingSeasonReviewTasks("ram", "R-1", start, "R-1", setOf("E-1"), today)
        assertEquals(LocalDate.parse("2026-12-18"), ram.single().due)
        assertTrue(smartBreedingSeasonReviewTasks("dry", "E-2", start, "R-1", setOf("E-1"), today).isEmpty())
        assertTrue(smartBreedingSeasonReviewTasks("pregnant", "E-1", start, "R-1", setOf("E-1"), today).isEmpty())
    }

    @Test fun conditionalVaccinesReceiveDatedEligibilityReviewsWithoutInjectionOrders() {
        val today = LocalDate.parse("2026-11-19")
        listOf("lamb", "dry", "pregnant", "lactating", "ram").forEach { category ->
            val tasks = smartConditionalAnnualReviewTasks(category, today)
            assertTrue(category, tasks.isNotEmpty())
            assertTrue(category, tasks.all { it.due == today })
            assertTrue(category, tasks.all { it.reason.contains("ليس موعد إعطاء لقاح") })
        }
    }
    @Test fun annualDoseDuplicateIsSameAnimalSameVaccineSameDateOnly() {
        val existing = annualRecord("clostridial", "2026-11-19")
        val sameDose = existing.copy(id = "new-id", number = 2)
        val otherAnimal = sameDose.copy(tag = "Q-0002")
        val otherVaccine = sameDose.copy(vaccineId = "pasteurella")
        val otherDate = sameDose.copy(date = "2026-11-20")
        assertTrue(isDuplicateAnnualVaccineRecord(listOf(existing), sameDose))
        assertFalse(isDuplicateAnnualVaccineRecord(listOf(existing), otherAnimal))
        assertFalse(isDuplicateAnnualVaccineRecord(listOf(existing), otherVaccine))
        assertFalse(isDuplicateAnnualVaccineRecord(listOf(existing), otherDate))
    }

    @Test fun annualDoseDuplicateCheckIsCaseInsensitiveForAnimalTag() {
        val existing = annualRecord("clostridial", "2026-11-19").copy(tag = "Q-0001")
        val candidate = existing.copy(id = "new-id", tag = "q-0001")
        assertTrue(isDuplicateAnnualVaccineRecord(listOf(existing), candidate))
    }

    @Test fun lambDoseDuplicateRequiresSameAnimalVaccineDateAndDoseNumber() {
        val existing = LambDose("old", "L-1", "clostridial", 1, "2026-11-20", "", "", "", "", "")
        val duplicate = existing.copy(id = "new")
        assertTrue(isDuplicateLambDoseRecord(listOf(existing), duplicate))
        assertFalse(isDuplicateLambDoseRecord(listOf(existing), duplicate.copy(tag = "L-2")))
        assertFalse(isDuplicateLambDoseRecord(listOf(existing), duplicate.copy(vaccineId = "pasteurella")))
        assertFalse(isDuplicateLambDoseRecord(listOf(existing), duplicate.copy(date = "2026-11-21")))
        assertFalse(isDuplicateLambDoseRecord(listOf(existing), duplicate.copy(doseNumber = 2)))
    }


    @Test fun unknownMaternalVaccinationCreatesUndatedDataReviewNotDoseAppointment() {
        val task = AnnualVaccineTask(
            id = "lamb-first", vaccineId = "clostridial",
            title = "الجرعة الأولى • التسمم المعوي والكزاز", due = null,
            reason = "حالة تحصين الأم مجهولة؛ تحقق منها قبل تحديد الموعد.",
            group = "تحصين أساسي مشروط"
        )
        val reminder = maternalHistoryReviewReminder("L-009", task)!!
        assertEquals("annual-calendar:L-009:lamb-first:missing-maternal-status", reminder.id)
        assertEquals("", reminder.date)
        assertFalse(reminder.completed)
        assertTrue(reminder.title.contains("استكمال حالة تحصين الأم"))
        assertTrue(reminder.notes.contains("REQUIRES_MATERNAL_HISTORY"))
    }

    @Test fun maternalHistoryReviewIsNotCreatedWhenStatusIsKnownOrTaskHasDueDate() {
        val unknown = AnnualVaccineTask("lamb-first", "clostridial", "الجرعة الأولى", null,
            "حالة تحصين الأم مجهولة؛ تحقق منها قبل تحديد الموعد.", "تحصين أساسي")
        assertEquals(null, maternalHistoryReviewReminder("L-009", unknown.copy(reason = "الأم محصنة")))
        assertEquals(null, maternalHistoryReviewReminder("L-009", unknown.copy(due = LocalDate.parse("2026-12-01"))))
        assertEquals(null, maternalHistoryReviewReminder("L-009", unknown.copy(id = "lamb-booster")))
    }


    @Test fun confirmedBirthRemovesOnlyTheOpenPredictedBirthReminder() {
        val expected = HerdCareReminder("expected", "E-101", "متابعة", "موعد ولادة متوقع", "2026-12-20", "", false)
        val completed = expected.copy(id = "old-completed", completed = true)
        val scan = expected.copy(id = "scan", title = "إعادة فحص السونار")
        val other = expected.copy(id = "other", tag = "E-102")
        val original = listOf(expected, completed, scan, other)
        val afterBirth = removeOpenHerdCareRemindersForAnimal(original, "e-101", setOf("موعد ولادة متوقع"))
        assertEquals(listOf("old-completed", "scan", "other"), afterBirth.map { it.id })
        assertEquals(afterBirth, removeOpenHerdCareRemindersForAnimal(afterBirth, "E-101", setOf("موعد ولادة متوقع")))
    }

    @Test fun eweLifecycleTransitionsKeepOnePermanentHerdIdentity() {
        val today = LocalDate.parse("2026-11-20")
        val dry = HerdAnimal(
            id = "permanent-id", tag = "E-101", kind = "نعجة",
            ageMonths = 30, health = "جيدة", notes = "",
            productionState = "نعجة جافة"
        )
        val pregnant = dry.copy(
            productionState = "نعجة حامل",
            pregnancyScanOutcome = "حامل",
            pregnancyStartDate = "2026-08-01",
            expectedBirthDate = "2026-12-26"
        )
        val lactating = pregnant.copy(
            birthConfirmedDate = "2026-12-20",
            weaningConfirmedDate = ""
        )
        val weaned = lactating.copy(weaningConfirmedDate = "2027-03-01")

        val states = listOf(dry, pregnant, lactating, weaned).map {
            reclassifyHerdAnimal(it, today)
        }

        assertEquals(listOf("نعجة جافة", "نعجة حامل", "نعجة مرضعة", "نعجة جافة"),
            states.map { it.productionState })
        assertTrue(states.all { it.id == "permanent-id" && it.tag == "E-101" })
        assertEquals(listOf("dry", "pregnant", "lactating", "dry"),
            states.map { smartVaccinationCategoryForAnimal(it, today) })
    }

    @Test fun archivedEweDoesNotReenterLifecyclePrograms() {
        val archived = HerdAnimal(
            id = "archived-id", tag = "E-102", kind = "نعجة",
            ageMonths = 24, health = "جيدة", notes = "",
            productionState = "نعجة حامل", lifecycleStatus = "مؤرشف",
            birthConfirmedDate = "2026-11-01"
        )
        val result = reclassifyHerdAnimal(archived, LocalDate.parse("2026-11-20"))
        assertEquals(archived, result)
        assertNull(smartVaccinationCategoryForAnimal(result, LocalDate.parse("2026-11-20")))
    }

    @Test fun lambBecomesAdultWithoutChangingItsIdentityOrMotherLink() {
        val lamb = HerdAnimal(
            id = "lamb-id", tag = "L-101", kind = "حمل/رخلة",
            ageMonths = 8, health = "جيدة", notes = "",
            birthDate = "2026-03-01", motherTag = "E-101", sex = "أنثى"
        )
        val adult = reclassifyHerdAnimal(lamb, LocalDate.parse("2026-11-01"))
        assertEquals("نعجة", adult.kind)
        assertEquals("نعجة جافة", adult.productionState)
        assertEquals("lamb-id", adult.id)
        assertEquals("L-101", adult.tag)
        assertEquals("E-101", adult.motherTag)
        assertEquals("dry", smartVaccinationCategoryForAnimal(adult, LocalDate.parse("2026-11-01")))
    }

}
