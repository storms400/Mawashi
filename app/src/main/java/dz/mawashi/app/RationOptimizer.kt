package dz.mawashi.app

import org.apache.commons.math3.optim.MaxIter
import org.apache.commons.math3.optim.PointValuePair
import org.apache.commons.math3.optim.linear.LinearConstraint
import org.apache.commons.math3.optim.linear.LinearConstraintSet
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException
import org.apache.commons.math3.optim.linear.NonNegativeConstraint
import org.apache.commons.math3.optim.linear.Relationship
import org.apache.commons.math3.optim.linear.SimplexSolver
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType

internal data class FeedNutrientProfile(
    val kind: FeedKind,
    val dryMatterFraction: Double,
    val meMjPerKgDm: Double,
    val crudeProteinFraction: Double,
    val ndfFraction: Double,
    val starchFraction: Double,
    val forage: Boolean,
    val reference: String
) {
    fun isValid(): Boolean =
        dryMatterFraction in 0.5..1.0 &&
            meMjPerKgDm > 0.0 &&
            crudeProteinFraction in 0.0..0.7 &&
            ndfFraction in 0.0..1.0 &&
            starchFraction in 0.0..1.0
}

/**
 * Reference-average ingredient data. Values are not a substitute for a local laboratory analysis.
 * DM/CP/NDF/starch are based on Feedipedia averages where available; ME remains a working
 * reference estimate and must be calibrated against local feed analyses and the chosen sheep system.
 */
internal object FeedProfileCatalog {
    private const val FEEDIPEDIA = "Feedipedia average; local batch not analysed"
    private const val ESTIMATE = "Reference estimate; verify local batch"

    val profiles: Map<FeedKind, FeedNutrientProfile> = listOf(
        FeedNutrientProfile(FeedKind.BARLEY, 0.871, 12.5, 0.118, 0.217, 0.597, false, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.BRAN, 0.870, 10.5, 0.173, 0.452, 0.231, false, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.MAIZE, 0.862, 13.5, 0.088, 0.136, 0.735, false, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.WHEAT, 0.870, 13.0, 0.126, 0.139, 0.691, false, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.SOY, 0.880, 12.0, 0.440, 0.142, 0.057, false, ESTIMATE),
        FeedNutrientProfile(FeedKind.STRAW, 0.910, 6.0, 0.042, 0.775, 0.010, true, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.ALFALFA, 0.894, 8.5, 0.182, 0.448, 0.033, true, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.OAT_HAY, 0.892, 8.5, 0.091, 0.617, 0.177, true, FEEDIPEDIA),
        FeedNutrientProfile(FeedKind.OATS, 0.879, 8.5, 0.110, 0.355, 0.406, false, FEEDIPEDIA)
    ).associateBy { it.kind }
}

internal data class RationIngredient(
    val kind: FeedKind,
    val stockAsFedKg: Double,
    val pricePerAsFedKg: Double,
    val profile: FeedNutrientProfile = FeedProfileCatalog.profiles[kind]
        ?: error("لا توجد بطاقة غذائية للصنف")
)

internal data class RationTargets(
    val dryMatterKg: Double,
    val metabolizableEnergyMj: Double,
    val crudeProteinKg: Double,
    /** A forage floor is enabled only for a production group whose feeding protocol requires it. */
    val minimumForageFraction: Double = 0.0,
    /** Optional operating guardrail; must be reviewed for the breed and production stage. */
    val maximumStarchFraction: Double? = null
)

internal data class OptimizedFeedUse(
    val kind: FeedKind,
    val asFedKg: Double,
    val dryMatterKg: Double,
    val cost: Double
)

internal data class RationOptimizationResult(
    val feasible: Boolean,
    val uses: List<OptimizedFeedUse>,
    val totalCost: Double,
    val suppliedDryMatterKg: Double,
    val suppliedEnergyMj: Double,
    val suppliedCrudeProteinKg: Double,
    val warnings: List<String>,
    val message: String
)

internal object RationOptimizer {
    fun solve(
        targets: RationTargets,
        ingredients: List<RationIngredient>
    ): RationOptimizationResult {
        require(targets.dryMatterKg.isFinite() && targets.dryMatterKg >= 0.0)
        require(targets.metabolizableEnergyMj.isFinite() && targets.metabolizableEnergyMj >= 0.0)
        require(targets.crudeProteinKg.isFinite() && targets.crudeProteinKg >= 0.0)
        require(targets.minimumForageFraction in 0.0..1.0)
        require(targets.maximumStarchFraction == null || targets.maximumStarchFraction in 0.0..1.0)

        if (targets.dryMatterKg <= 1e-8) {
            return RationOptimizationResult(true, emptyList(), 0.0, 0.0, 0.0, 0.0,
                emptyList(), "لا يلزم مكمل جاف وفق المدخلات الحالية")
        }

        val candidates = ingredients.filter {
            !it.kind.isMineral &&
                it.stockAsFedKg.isFinite() && it.stockAsFedKg > 0.0 &&
                it.pricePerAsFedKg.isFinite() && it.pricePerAsFedKg > 0.0 &&
                it.profile.isValid()
        }.distinctBy { it.kind }

        if (candidates.isEmpty()) {
            return failure("لا توجد أصناف متاحة لها كمية وسعر وبطاقة غذائية صالحة.")
        }

        val constraints = mutableListOf<LinearConstraint>()
        val dm = DoubleArray(candidates.size) { 1.0 }
        constraints += LinearConstraint(dm, Relationship.EQ, targets.dryMatterKg)

        val energy = DoubleArray(candidates.size) { candidates[it].profile.meMjPerKgDm }
        constraints += LinearConstraint(energy, Relationship.GEQ, targets.metabolizableEnergyMj)

        val protein = DoubleArray(candidates.size) { candidates[it].profile.crudeProteinFraction }
        constraints += LinearConstraint(protein, Relationship.GEQ, targets.crudeProteinKg)

        if (targets.minimumForageFraction > 0.0) {
            val forage = DoubleArray(candidates.size) { if (candidates[it].profile.forage) 1.0 else 0.0 }
            constraints += LinearConstraint(
                forage, Relationship.GEQ, targets.dryMatterKg * targets.minimumForageFraction
            )
        }

        targets.maximumStarchFraction?.let { maximum ->
            val starch = DoubleArray(candidates.size) { candidates[it].profile.starchFraction }
            constraints += LinearConstraint(starch, Relationship.LEQ, targets.dryMatterKg * maximum)
        }

        candidates.forEachIndexed { index, item ->
            val cap = DoubleArray(candidates.size)
            cap[index] = 1.0
            constraints += LinearConstraint(
                cap, Relationship.LEQ, item.stockAsFedKg * item.profile.dryMatterFraction
            )
        }

        val objective = LinearObjectiveFunction(
            DoubleArray(candidates.size) {
                candidates[it].pricePerAsFedKg / candidates[it].profile.dryMatterFraction
            }, 0.0
        )

        return try {
            val point: PointValuePair = SimplexSolver(1e-9, 10, 1e-12).optimize(
                MaxIter(2000),
                objective,
                LinearConstraintSet(constraints),
                GoalType.MINIMIZE,
                NonNegativeConstraint(true)
            )
            val uses = candidates.mapIndexedNotNull { index, item ->
                val dmKg = point.point[index].coerceAtLeast(0.0)
                if (dmKg <= 1e-7) null else OptimizedFeedUse(
                    item.kind,
                    dmKg / item.profile.dryMatterFraction,
                    dmKg,
                    (dmKg / item.profile.dryMatterFraction) * item.pricePerAsFedKg
                )
            }
            val suppliedDm = uses.sumOf { it.dryMatterKg }
            val suppliedEnergy = uses.sumOf {
                it.dryMatterKg * candidates.first { candidate -> candidate.kind == it.kind }.profile.meMjPerKgDm
            }
            val suppliedProtein = uses.sumOf {
                it.dryMatterKg * candidates.first { candidate -> candidate.kind == it.kind }.profile.crudeProteinFraction
            }
            val warnings = buildList {
                if (candidates.any { it.profile.reference.contains("estimate", true) }) {
                    add("توجد قيم مرجعية تقديرية؛ يلزم تحليل الأعلاف المحلية قبل اعتماد الخلطة نهائياً.")
                }
                if (candidates.any { it.kind == FeedKind.SOY }) {
                    add("قيمة كسبة الصويا تقديرية؛ طابقها مع نسبة البروتين المكتوبة على كيس المنتج.")
                }
                if (targets.maximumStarchFraction != null) {
                    add("حد النشا قيد احترازي قابل للمراجعة وليس تشخيصاً بيطرياً.")
                }
                add("الأملاح المعدنية لا تدخل في حل الخلطة؛ تُضبط وفق بطاقة المنتج ونسبة النحاس المسموح بها للأغنام.")
            }
            RationOptimizationResult(
                true, uses, uses.sumOf { it.cost }, suppliedDm, suppliedEnergy, suppliedProtein,
                warnings, "تم إيجاد خلطة تحقق القيود الرياضية المدخلة."
            )
        } catch (_: NoFeasibleSolutionException) {
            failure("المخزون والأسعار والقيود الحالية لا تسمح بتركيبة تحقق الاحتياجات. لا توجد خلطة معتمدة.")
        } catch (_: Exception) {
            failure("تعذر حل الخلطة بهذه المدخلات. راجع القيم الغذائية والأسعار والمخزون.")
        }
    }

    private fun failure(message: String) = RationOptimizationResult(
        false, emptyList(), 0.0, 0.0, 0.0, 0.0, listOf(message), message
    )
}
