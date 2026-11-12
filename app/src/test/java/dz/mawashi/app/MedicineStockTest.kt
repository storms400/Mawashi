package dz.mawashi.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineStockTest {
    @Test fun expiredLotsAreExcludedFromUsableStock() {
        val lots = listOf(
            MedicineLot("دواء", "A", "2026-10-01", 4.0, "مورد", "2026-01-01"),
            MedicineLot("دواء", "B", "2026-12-01", 3.0, "مورد", "2026-01-01")
        )
        assertEquals(3.0, usableMedicineStock("دواء", 7.0, lots, "2026-11-12"), 0.0)
    }

    @Test fun legacyStockWithoutLotsRemainsAvailable() {
        assertEquals(5.0, usableMedicineStock("دواء", 5.0, emptyList(), "2026-11-12"), 0.0)
    }

    @Test fun expiredOnlyLotsHaveNoUsableQuantity() {
        val lots = listOf(MedicineLot("دواء", "A", "2026-10-01", 4.0, "مورد", "2026-01-01"))
        assertEquals(0.0, usableMedicineStock("دواء", 4.0, lots, "2026-11-12"), 0.0)
    }

    @Test fun invalidExpiryDateIsExcluded() {
        val lots = listOf(
            MedicineLot("دواء", "bad", "2026-02-30", 8.0, "مورد", "2026-01-01"),
            MedicineLot("دواء", "text", "غير معروف", 6.0, "مورد", "2026-01-01"),
            MedicineLot("دواء", "valid", "2026-12-01", 2.0, "مورد", "2026-01-01")
        )
        assertEquals(2.0, usableMedicineStock("دواء", 16.0, lots, "2026-11-12"), 0.0)
    }

    @Test fun todayAndFutureExpiryDatesAreUsable() {
        val today = LocalDate.parse("2026-11-12")
        assertTrue(isMedicineLotUsable("2026-11-12", today))
        assertTrue(isMedicineLotUsable("2026-11-13", today))
        assertFalse(isMedicineLotUsable("2026-11-11", today))
    }

    @Test fun invalidTodayDateFailsClosed() {
        val lots = listOf(MedicineLot("دواء", "A", "2026-12-01", 4.0, "مورد", "2026-01-01"))
        assertEquals(0.0, usableMedicineStock("دواء", 5.0, lots, "2026-02-30"), 0.0)
        assertEquals(0.0, usableMedicineStock("قديم", 5.0, emptyList(), "not-a-date"), 0.0)
    }

    @Test fun nonFiniteOrNegativeLotQuantitiesAreNotCounted() {
        val lots = listOf(
            MedicineLot("دواء", "negative", "2026-12-01", -2.0, "مورد", "2026-01-01"),
            MedicineLot("دواء", "nan", "2026-12-01", Double.NaN, "مورد", "2026-01-01"),
            MedicineLot("دواء", "valid", "2026-12-01", 3.0, "مورد", "2026-01-01")
        )
        assertEquals(3.0, usableMedicineStock("دواء", 0.0, lots, "2026-11-12"), 0.0)
    }
}
