package dz.mawashi.app

import java.time.LocalDate

internal data class SmartVaccinationStep(
    val id: String,
    val stage: Int,
    val title: String,
    val ageMonths: Long
)

internal val SMART_VACCINATION_STEPS = listOf(
    SmartVaccinationStep("stage1_enteric", 1, "التسمم المعوي", 2),
    SmartVaccinationStep("stage2_hemorrhagic", 2, "التسمم الدموي", 3),
    SmartVaccinationStep("stage3_sheep_pox", 3, "جدري الأغنام", 4),
    SmartVaccinationStep("stage3_ppr", 3, "طاعون المجترات الصغيرة (PPR)", 4),
    SmartVaccinationStep("stage3_fmd", 3, "الحمى القلاعية (FMD)", 4)
)

internal fun smartVaccinationDueDate(birthDate: LocalDate, step: SmartVaccinationStep): LocalDate =
    birthDate.plusMonths(step.ageMonths)

internal fun smartVaccinationDone(tag: String, stepId: String, records: List<HerdCareReminder>): Boolean =
    records.any { it.tag.equals(tag, true) && it.kind == "تحصين مرحلي" && it.notes == "SMART_VAX_DONE:$stepId" && it.completed }

internal fun smartVaccinationUnlocked(tag: String, stepIndex: Int, records: List<HerdCareReminder>): Boolean =
    stepIndex == 0 || smartVaccinationDone(tag, SMART_VACCINATION_STEPS[stepIndex - 1].id, records)

internal fun smartVaccinationCurrentStep(tag: String, records: List<HerdCareReminder>): SmartVaccinationStep? =
    SMART_VACCINATION_STEPS.firstOrNull { step -> !smartVaccinationDone(tag, step.id, records) }

internal fun reconcileSmartVaccinationReminders(
    animals: List<HerdAnimal>,
    records: List<HerdCareReminder>,
    today: LocalDate = LocalDate.now()
): List<HerdCareReminder> {
    val preserved = records.filterNot { it.kind == "تحصين مرحلي" && it.notes.startsWith("SMART_VAX_PENDING:") }
    val pending = mutableListOf<HerdCareReminder>()
    animals.filter { it.lifecycleStatus != "مؤرشف" }.forEach { animal ->
        val birth = runCatching { LocalDate.parse(animal.birthDate) }.getOrNull() ?: return@forEach
        if (birth.isAfter(today)) return@forEach
        val nextIndex = SMART_VACCINATION_STEPS.indexOfFirst { !smartVaccinationDone(animal.tag, it.id, preserved) }
        if (nextIndex < 0) return@forEach
        val step = SMART_VACCINATION_STEPS[nextIndex]
        if (!smartVaccinationUnlocked(animal.tag, nextIndex, preserved)) return@forEach
        val due = smartVaccinationDueDate(birth, step)
        pending += HerdCareReminder(
            id = "smart-vax-${animal.tag}-${step.id}",
            tag = animal.tag,
            kind = "تحصين مرحلي",
            title = step.title,
            date = due.toString(),
            notes = "SMART_VAX_PENDING:${step.id}",
            completed = false
        )
    }
    return preserved + pending
}
