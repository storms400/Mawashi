package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LambPreventionTest {
    private fun dose(id: String, date: String = "2026-11-20", number: Int = 1) =
        LambDose(id, "L-001", "clostridial", number, date, "منتج", "B-1", "", "طبيب", "")

    @Test fun doseSaveMergePreservesPersistedRecordsWhenUiSubmitsStalePartialList() {
        val first = dose("dose-1")
        val second = dose("dose-2", "2026-12-18", 2)
        val merged = mergeLambDoseRecords(listOf(first, second), emptyList())
        assertEquals(listOf(first, second), merged)
    }

    @Test fun doseSaveMergeAppendsNewDoseAndKeepsExistingHistory() {
        val first = dose("dose-1")
        val second = dose("dose-2", "2026-12-18", 2)
        val merged = mergeLambDoseRecords(listOf(first), listOf(first, second))
        assertEquals(listOf(first, second), merged)
    }

    @Test fun retryingSameDoseRecordIsIdempotent() {
        val first = dose("dose-1")
        assertEquals(listOf(first), mergeLambDoseRecords(listOf(first), listOf(first)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateDoseWithDifferentRecordIdIsRejected() {
        mergeLambDoseRecords(listOf(dose("dose-1")), listOf(dose("dose-2")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun changingPersistedDoseUnderSameIdIsRejected() {
        mergeLambDoseRecords(listOf(dose("dose-1")), listOf(dose("dose-1", "2026-12-01")))
    }
}
