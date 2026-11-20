package dz.mawashi.app

import android.content.Context
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class HerdLifecycleIntegrationTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun resetStore() {
        listOf(
            "mawashi_herd_local",
            "mawashi_herd_care",
            "mawashi_annual_vaccination_v1",
            "mawashi_herd_care_notifications"
        ).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun scanBirthAndWeaningUpdateOnePersistedEweAndItsLinkedLamb() {
        resetStore()
        val today = LocalDate.now()
        val ewe = HerdAnimal(
            id = "permanent-ewe-id", tag = "E-900", kind = "نعجة",
            ageMonths = 30, health = "جيدة", notes = "سجل دائم",
            productionState = "نعجة جافة"
        )
        saveHerd(context, listOf(ewe))

        assertTrue(savePregnancyScanToHerd(
            context, ewe.tag, "حامل", today.toString(), 80, 1, "", "أنثى"
        ))
        var stored = loadHerd(context)
        assertEquals(1, stored.count { it.tag == ewe.tag })
        assertEquals(ewe.id, stored.single { it.tag == ewe.tag }.id)
        assertEquals(listOf(ewe.tag), herdAnimalsForCareCategory(context, "pregnant", today).map { it.tag })

        val birthDate = today.plusDays(70)
        assertTrue(confirmEweBirth(context, ewe.tag, birthDate.toString(), birthDate))
        val bornLamb = HerdAnimal(
            id = "permanent-lamb-id", tag = "L-900", kind = "حمل/رخلة",
            ageMonths = 0, health = "جيدة", notes = "",
            birthDate = birthDate.toString(), motherTag = ewe.tag,
            productionState = "حمل يرضع", sex = "أنثى"
        )
        saveHerd(context, loadHerd(context) + bornLamb)
        stored = loadHerd(context)
        val lactating = stored.single { it.tag == ewe.tag }
        assertEquals(ewe.id, lactating.id)
        assertEquals("نعجة مرضعة", lactating.productionState)
        assertEquals(birthDate.toString(), lactating.birthConfirmedDate)
        assertEquals(listOf(ewe.tag), herdAnimalsForCareCategory(context, "lactating", birthDate).map { it.tag })

        val weaningDate = birthDate.plusDays(65)
        assertTrue(confirmEweWeaning(context, ewe.tag, weaningDate.toString(), weaningDate))
        stored = loadHerd(context)
        val weaned = stored.single { it.tag == ewe.tag }
        val retainedLamb = stored.single { it.tag == bornLamb.tag }
        assertEquals(ewe.id, weaned.id)
        assertEquals(ewe.tag, weaned.tag)
        assertEquals("نعجة جافة", weaned.productionState)
        assertEquals(bornLamb.id, retainedLamb.id)
        assertEquals(ewe.tag, retainedLamb.motherTag)
        assertEquals("خروف", retainedLamb.productionState)
        assertEquals(listOf(ewe.tag), herdAnimalsForCareCategory(context, "dry", weaningDate).map { it.tag })
    }

    @Test fun vaccineHistoryAndCompletedCareRecordsSurviveCategoryReconciliation() {
        resetStore()
        val today = LocalDate.now()
        val nextDate = today.plusMonths(6)
        val history = JSONArray().put(
            JSONObject()
                .put("id", "vax-e900-1")
                .put("tag", "E-900")
                .put("vaccine", "clostridial")
                .put("date", today.minusMonths(6).toString())
                .put("number", 1)
                .put("dose", "جرعة مسجلة")
                .put("product", "منتج اختباري")
                .put("batch", "B-1")
                .put("expiry", "")
                .put("vet", "")
                .put("nextDate", nextDate.toString())
                .put("notes", "")
        )
        context.getSharedPreferences("mawashi_annual_vaccination_v1", Context.MODE_PRIVATE)
            .edit().putString("records", history.toString()).commit()

        val dry = HerdAnimal(
            id = "permanent-ewe-id", tag = "E-900", kind = "نعجة",
            ageMonths = 30, health = "جيدة", notes = "",
            productionState = "نعجة جافة", expectedBirthDate = ""
        )
        val completed = HerdCareReminder(
            id = "annual-calendar:E-900:manual-completed:2026",
            tag = "E-900", kind = "التطعيمات السنوية",
            title = "إجراء موثق سابقاً", date = today.minusDays(1).toString(),
            notes = "سجل مكتمل", completed = true
        )
        val states = listOf(
            dry,
            dry.copy(productionState = "نعجة حامل", expectedBirthDate = today.plusDays(100).toString()),
            dry.copy(productionState = "نعجة مرضعة", birthConfirmedDate = today.toString()),
            dry.copy(productionState = "نعجة جافة", weaningConfirmedDate = today.toString())
        )

        states.forEach { state ->
            val reconciled = reconcileAnnualVaccinationCalendar(context, listOf(state), listOf(completed), today)
            assertTrue(reconciled.any { it.id == completed.id && it.completed })
            assertTrue(reconciled.any { it.id == "annual-calendar:E-900:record-vax-e900-1:${nextDate.year}" })
            assertEquals(1, reconciled.count { it.id == "annual-calendar:E-900:record-vax-e900-1:${nextDate.year}" })
        }
    }

    @Test fun repeatedAnnualCalendarReconciliationDoesNotDuplicateTasks() {
        resetStore()
        val today = LocalDate.now()
        val ewe = HerdAnimal(
            id = "same-id", tag = "E-901", kind = "نعجة",
            ageMonths = 24, health = "جيدة", notes = "",
            productionState = "نعجة جافة"
        )
        val first = reconcileAnnualVaccinationCalendar(context, listOf(ewe), emptyList(), today)
        val second = reconcileAnnualVaccinationCalendar(context, listOf(ewe), first, today)
        assertEquals(second.map { it.id }.toSet().size, second.size)
        assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet())
    }
}
