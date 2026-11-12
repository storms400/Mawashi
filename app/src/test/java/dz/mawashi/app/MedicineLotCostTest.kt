package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MedicineLotCostTest {
    private val today = LocalDate.parse("2026-11-13")

    @Test
    fun valuesSaleByFefoLotCosts() {
        val lots = listOf(
            MedicineLot("دواء", "B", "2026-12-01", 5.0, "مورد 2", "2026-11-10", 20.0),
            MedicineLot("دواء", "A", "2026-11-20", 2.0, "مورد 1", "2026-11-01", 10.0)
        )
        assertEquals(40.0, medicineLotCostForSale("دواء", 3.0, lots, today, 99.0)!!, 0.0001)
    }

    @Test
    fun legacyLotsUseWeightedAverageFallbackWhenUnitCostMissing() {
        val lots = listOf(
            MedicineLot("دواء", "OLD", "2026-12-01", 2.0, "مورد", "2026-10-01")
        )
        assertEquals(24.0, medicineLotCostForSale("دواء", 2.0, lots, today, 12.0)!!, 0.0001)
    }

    @Test
    fun expiredLotsAreExcludedFromSaleCost() {
        val lots = listOf(
            MedicineLot("دواء", "OLD", "2026-11-12", 5.0, "مورد", "2026-10-01", 8.0)
        )
        assertNull(medicineLotCostForSale("دواء", 1.0, lots, today, 12.0))
    }

    @Test
    fun insufficientUsableLotsDoNotProduceCost() {
        val lots = listOf(
            MedicineLot("دواء", "A", "2026-12-01", 1.0, "مورد", "2026-11-01", 10.0)
        )
        assertNull(medicineLotCostForSale("دواء", 2.0, lots, today, 12.0))
    }
}
