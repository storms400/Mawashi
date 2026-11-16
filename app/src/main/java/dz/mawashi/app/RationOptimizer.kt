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
        meMjPerKgDm in 0.1..20.0 &&
        crudeProteinFraction in 0.0..0.7 &&
        ndfFraction in 0.0..1.0 &&
        starchFraction in 0.0..1.0
}

/**
 * Reference averages are provisional ingredient data, not an analysis of the user's feed batch.
 * Update these values only from a traceable feed table or a laboratory certificate.
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
    val minimumForageFraction: Double = 0.0,
    val maximumStarchFraction: Double? = null,
    val maximumEnergyMj: Double? = null,
    val minimumNdfFraction: Double? = null,
    val maximumIngredientFractions: Map<FeedKind, Double> = emptyMap()
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
    val message: String,
    val suppliedNdfKg: Double = 0.0,
    val suppliedStarchKg: Double = 0.0,
    val forageFraction: Double = 0.0
)

internal object RationOptimizer {
    fun solve(targets: RationTargets, ingredients: List<RationIngredient>): RationOptimizationResult {
        require(targets.dryMatterKg.isFinite() && targets.dryMatterKg >= 0.0)
        require(targets.metabolizableEnergyMj.isFinite() && targets.metabolizableEnergyMj >= 0.0)
        require(targets.crudeProteinKg.isFinite() && targets.crudeProteinKg >= 0.0)
        require(targets.minimumForageFraction in 0.0..1.0)
        require(targets.maximumStarchFraction == null || targets.maximumStarchFraction in 0.0..1.0)
        require(targets.maximumEnergyMj == null || (targets.maximumEnergyMj.isFinite() && targets.maximumEnergyMj >= 0.0))
        require(targets.minimumNdfFraction == null || targets.minimumNdfFraction in 0.0..1.0)
        require(targets.maximumIngredientFractions.values.all { it.isFinite() && it in 0.0..1.0 })

        if (targets.dryMatterKg <= 1e-8) {
            return RationOptimizationResult(true, emptyList(), 0.0, 0.0, 0.0, 0.0,
                emptyList(), "لا يلزم مكمل جاف وفق المدخلات الحالية")
        }

        val usable = ingredients.filter {
            !it.kind.isMineral && it.stockAsFedKg.isFinite() && it.stockAsFedKg > 0.0 &&
                it.pricePerAsFedKg.isFinite() && it.pricePerAsFedKg >= 0.0 && it.profile.isValid()
        }.distinctBy { it.kind }
        if (usable.isEmpty()) {
            return failure("لا توجد مواد علفية متاحة لها مخزون وسعر وبطاقة غذائية صالحة.")
        }

        val constraints = mutableListOf<LinearConstraint>()
        val dm = DoubleArray(usable.size) { 1.0 }
        constraints += LinearConstraint(dm, Relationship.EQ, targets.dryMatterKg)

        constraints += LinearConstraint(
            DoubleArray(usable.size) { usable[it].profile.meMjPerKgDm },
            Relationship.GEQ, targets.metabolizableEnergyMj
        )
        constraints += LinearConstraint(
            DoubleArray(usable.size) { usable[it].profile.crudeProteinFraction },
            Relationship.GEQ, targets.crudeProteinKg
        )

        targets.maximumEnergyMj?.let { maximum ->
            constraints += LinearConstraint(
                DoubleArray(usable.size) { usable[it].profile.meMjPerKgDm },
                Relationship.LEQ, maximum
            )
        }
        if (targets.minimumForageFraction > 0.0) {
            constraints += LinearConstraint(
                DoubleArray(usable.size) { if (usable[it].profile.forage) 1.0 else 0.0 },
                Relationship.GEQ, targets.dryMatterKg * targets.minimumForageFraction
            )
        }
        targets.maximumStarchFraction?.let { maximum ->
            constraints += LinearConstraint(
                DoubleArray(usable.size) { usable[it].profile.starchFraction },
                Relationship.LEQ, targets.dryMatterKg * maximum
            )
        }
        targets.minimumNdfFraction?.let { minimum ->
            constraints += LinearConstraint(
                DoubleArray(usable.size) { usable[it].profile.ndfFraction },
                Relationship.GEQ, targets.dryMatterKg * minimum
            )
        }

        usable.forEachIndexed { index, item ->
            val cap = DoubleArray(usable.size)
            cap[index] = 1.0
            // Convert available as-fed stock into dry matter before applying the solver cap.
            constraints += LinearConstraint(
                cap, Relationship.LEQ, item.stockAsFedKg * item.profile.dryMatterFraction
            )
            targets.maximumIngredientFractions[item.kind]?.let { fraction ->
                constraints += LinearConstraint(
                    cap, Relationship.LEQ, targets.dryMatterKg * fraction
                )
            }
        }

        // Objective is cost per kg of dry matter, not cost per as-fed kg.
        val objective = LinearObjectiveFunction(
            DoubleArray(usable.size) {
                usable[it].pricePerAsFedKg / usable[it].profile.dryMatterFraction
            }, 0.0
        )

        return try {
            val point: PointValuePair = SimplexSolver(1e-9, 10, 1e-12).optimize(
                MaxIter(2000), objective, LinearConstraintSet(constraints),
                GoalType.MINIMIZE, NonNegativeConstraint(true)
            )
            val uses = usable.mapIndexedNotNull { index, item ->
                val dmKg = point.point[index].coerceAtLeast(0.0)
                if (dmKg <= 1e-7) null else {
                    val asFed = dmKg / item.profile.dryMatterFraction
                    OptimizedFeedUse(item.kind, asFed, dmKg, asFed * item.pricePerAsFedKg)
                }
            }
            val suppliedDm = uses.sumOf { it.dryMatterKg }
            val profiles = usable.associateBy { it.kind }
            val energy = uses.sumOf { it.dryMatterKg * profiles.getValue(it.kind).profile.meMjPerKgDm }
            val protein = uses.sumOf { it.dryMatterKg * profiles.getValue(it.kind).profile.crudeProteinFraction }
            val ndf = uses.sumOf { it.dryMatterKg * profiles.getValue(it.kind).profile.ndfFraction }
            val starch = uses.sumOf { it.dryMatterKg * profiles.getValue(it.kind).profile.starchFraction }
            val forageDm = uses.filter { profiles.getValue(it.kind).profile.forage }.sumOf { it.dryMatterKg }
            val warnings = buildList {
                if (usable.any { it.profile.reference.contains("estimate", true) }) {
                    add("بعض القيم الغذائية تقديرية؛ يلزم تحليل الأعلاف المحلية قبل اعتماد الخلطة نهائياً.")
                }
                if (usable.any { it.kind == FeedKind.SOY }) {
                    add("طابق بروتين كسبة الصويا مع بطاقة المنتج أو التحليل المخبري.")
                }
                if (targets.maximumStarchFraction != null) {
                    add("حد النشا قيد احترازي في المكمل، ويجب مراجعة إجمالي العليقة والانتقال التدريجي للحبوب.")
                }
                add("الأملاح المعدنية مستبعدة من حل الخلطة؛ جرعتها وتركيبها يحددان من بطاقة المنتج مع الانتباه للنحاس.")
            }
            RationOptimizationResult(
                feasible = true, uses = uses, totalCost = uses.sumOf { it.cost },
                suppliedDryMatterKg = suppliedDm, suppliedEnergyMj = energy,
                suppliedCrudeProteinKg = protein, warnings = warnings,
                message = "تم إيجاد تركيبة تحقق القيود الرياضية والحدود المدخلة.",
                suppliedNdfKg = ndf, suppliedStarchKg = starch,
                forageFraction = if (suppliedDm > 0.0) forageDm / suppliedDm else 0.0
            )
        } catch (_: NoFeasibleSolutionException) {
            failure("المخزون والأسعار والقيود الحالية لا تسمح بتركيبة تحقق الاحتياجات؛ لم يتم اقتراح خلطة.")
        } catch (_: Exception) {
            failure("تعذر حل الخلطة بهذه المدخلات. راجع الأوزان والأسعار والبطاقات الغذائية.")
        }
    }

    private fun failure(message: String) = RationOptimizationResult(
        false, emptyList(), 0.0, 0.0, 0.0, 0.0, listOf(message), message
    )
}
