package dz.mawashi.app

import org.junit.Assert.*
import org.junit.Test

class RationOptimizerTest {
    private fun ingredient(
        kind: FeedKind,
        stock: Double,
        price: Double,
        me: Double,
        cp: Double,
        ndf: Double,
        starch: Double,
        forage: Boolean
    ) = RationIngredient(
        kind, stock, price,
        FeedNutrientProfile(kind, 0.90, me, cp, ndf, starch, forage, "test profile")
    )

    @Test
    fun choosesLeastCostFeasibleRationAndRespectsForageFloor() {
        val result = RationOptimizer.solve(
            RationTargets(
                dryMatterKg = 1.0,
                metabolizableEnergyMj = 10.0,
                crudeProteinKg = 0.12,
                minimumForageFraction = 0.20,
                maximumStarchFraction = 0.40
            ),
            listOf(
                ingredient(FeedKind.BARLEY, 2.0, 10.0, 12.0, 0.12, 0.20, 0.60, false),
                ingredient(FeedKind.ALFALFA, 2.0, 8.0, 8.0, 0.18, 0.45, 0.03, true),
                ingredient(FeedKind.BRAN, 2.0, 9.0, 10.0, 0.17, 0.45, 0.23, false)
            )
        )

        assertTrue(result.message, result.feasible)
        assertEquals(1.0, result.suppliedDryMatterKg, 1e-6)
        assertTrue(result.suppliedEnergyMj >= 10.0 - 1e-6)
        assertTrue(result.suppliedCrudeProteinKg >= 0.12 - 1e-6)
        assertTrue(result.uses.any { it.kind == FeedKind.ALFALFA })
        assertTrue(result.uses.sumOf { it.cost } > 0.0)
    }

    @Test
    fun reportsInfeasibleWhenInventoryCannotMeetForageConstraint() {
        val result = RationOptimizer.solve(
            RationTargets(1.0, 8.0, 0.08, minimumForageFraction = 0.20),
            listOf(ingredient(FeedKind.BARLEY, 5.0, 10.0, 12.0, 0.12, 0.20, 0.60, false))
        )
        assertFalse(result.feasible)
        assertTrue(result.uses.isEmpty())
    }

    @Test
    fun mineralsAreNeverOptimizedAsBulkFeed() {
        val result = RationOptimizer.solve(
            RationTargets(1.0, 8.0, 0.08),
            listOf(
                ingredient(FeedKind.MINERALS_YELLOW, 100.0, 1.0, 0.0, 0.0, 0.0, 0.0, false)
            )
        )
        assertFalse(result.feasible)
    }

    @Test
    fun stockCapsAreAppliedOnAsFedToDryMatterConversion() {
        val result = RationOptimizer.solve(
            RationTargets(1.0, 8.0, 0.08),
            listOf(ingredient(FeedKind.ALFALFA, 0.5, 10.0, 9.0, 0.18, 0.45, 0.03, true))
        )
        assertFalse(result.feasible)
    }
    @Test
    fun refusesRationThatMustExceedTheEnergyCeiling() {
        val result = RationOptimizer.solve(
            RationTargets(1.0, 8.0, 0.08, maximumEnergyMj = 9.0),
            listOf(ingredient(FeedKind.BARLEY, 2.0, 5.0, 12.0, 0.12, 0.20, 0.60, false))
        )
        assertFalse(result.feasible)
        assertTrue(result.uses.isEmpty())
    }
}
