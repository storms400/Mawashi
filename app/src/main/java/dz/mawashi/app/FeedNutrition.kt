package dz.mawashi.app

import kotlin.math.pow

internal enum class FeedKind(val label:String, val dryMatter:Double, val meMjPerKgDm:Double, val crudeProteinPercent:Double, val roughage:Boolean=false) {
    BARLEY("الشعير المجروش",0.89,12.5,11.5),
    BRAN("نخالة القمح",0.89,10.5,16.0),
    MAIZE("الذرة الصفراء",0.89,13.5,9.0),
    WHEAT("القمح",0.89,13.0,12.0),
    SOY("كسبة الصويا",0.89,12.0,44.0),
    STRAW("التبن",0.90,6.0,4.0,true),
    ALFALFA("الفصة",0.88,8.5,18.0,true),
    OAT_HAY("الخرطال",0.89,8.5,9.0,true),
    OATS("الشوفان",0.89,8.5,9.0,true),
    MINERALS("الأملاح المعدنية (قديم)",0.98,0.0,0.0),
    MINERALS_YELLOW("صخرة الأملاح المعدنية الصفراء",0.98,0.0,0.0),
    MINERALS_RED("صخرة الأملاح المعدنية الحمراء",0.98,0.0,0.0);
    val isMineral:Boolean get() = name.startsWith("MINERALS")
}

internal data class FeedAnimalInput(
    val tag:String, val weightKg:Double, val state:String,
    val pregnancyStage:String="غير حامل", val fetusCount:Int=1,
    val nursingLambs:Int=1, val ageMonths:Int=12
)

internal fun feedInputsForAnimals(animals:List<HerdAnimal>):List<FeedAnimalInput> =
    activeHerdAnimals(animals).mapNotNull { animal ->
        val weight=animal.weightKg?.takeIf { it.isFinite() && it in 2.0..180.0 } ?: return@mapNotNull null
        FeedAnimalInput(animal.tag,weight,animal.productionState,animal.pregnancyStage,animal.fetusCount,animal.nursingLambs,animal.ageMonths)
    }

internal data class DailyNeed(
    val dryMatterKg:Double,
    val meMj:Double,
    val crudeProteinKg:Double,
    val pastureShare:Double,
    val supplementDryMatterKg:Double,
    val pastureDryMatterKg:Double=0.0,
    val pastureEnergyMj:Double=0.0,
    val pastureCrudeProteinKg:Double=0.0,
    val supplementEnergyMj:Double=0.0,
    val supplementCrudeProteinKg:Double=0.0,
    val referenceNote:String="تقدير مرجعي يحتاج معايرة محلية"
)

internal data class PastureProfile(
    val dryMatterShare:Double,
    val meMjPerKgDm:Double,
    val crudeProteinFraction:Double,
    val referenceNote:String
)

/**
 * Pasture quality bands are working estimates only. A pasture label (weak/medium/good)
 * cannot replace measuring actual intake, botanical composition or laboratory analysis.
 */
internal object PastureNutrition {
    fun profile(level:String):PastureProfile = when(level) {
        "ضعيف" -> PastureProfile(0.20,7.0,0.06,"مرعى ضعيف: قيمة تقديرية غير محللة")
        "جيد" -> PastureProfile(0.60,10.0,0.16,"مرعى جيد: قيمة تقديرية غير محللة")
        else -> PastureProfile(0.40,8.5,0.10,"مرعى متوسط: قيمة تقديرية غير محللة")
    }
}

/**
 * Mature-ewe baseline adapted from OSU Extension tables adapted from NRC (1985).
 * The 110-lb reference row is scaled by live weight. TDN is converted approximately
 * to ME (TDN kg x 15.13 MJ). Ram and lamb factors remain estimates pending local calibration.
 */
internal object SheepNutrition {
    private data class Stage(val dmiFraction:Double,val tdnFraction:Double,val cpFraction:Double)
    private fun stage(a:FeedAnimalInput):Stage {
        val twin=a.fetusCount>=2
        val nursingTwins=a.nursingLambs>=2
        return when {
            a.state=="نعجة مرضعة" && nursingTwins -> Stage(0.0482,0.6415,0.1623)
            a.state=="نعجة مرضعة" -> Stage(0.0418,0.6522,0.1457)
            a.state=="نعجة حامل" && a.pregnancyStage=="الأربعة أسابيع الأخيرة" && twin -> Stage(0.0336,0.6486,0.1162)
            a.state=="نعجة حامل" && a.pregnancyStage=="الأربعة أسابيع الأخيرة" -> Stage(0.0318,0.6000,0.1086)
            a.state=="نعجة حامل" && a.pregnancyStage=="منتصف الحمل" -> Stage(0.0236,0.5769,0.0962)
            a.state=="نعجة حامل" -> Stage(0.0236,0.5769,0.0962)
            a.state=="كبش" -> Stage(0.0230,0.5750,0.1050)
            a.state=="حمل يرضع" -> Stage(0.0180,0.6500,0.1600)
            a.state=="خروف" -> if(a.ageMonths<6) Stage(0.0400,0.7240,0.1450) else Stage(0.0320,0.7000,0.1200)
            else -> Stage(0.0200,0.5455,0.0955)
        }
    }

    fun calculate(a:FeedAnimalInput,pastureLevel:String):DailyNeed {
        val w=a.weightKg.coerceIn(2.0,180.0)
        val s=stage(a)
        val dmi=w*s.dmiFraction
        val energy=dmi*s.tdnFraction*15.13
        val protein=dmi*s.cpFraction
        val p=PastureNutrition.profile(pastureLevel)
        val pastureDm=dmi*p.dryMatterShare
        val pastureEnergy=pastureDm*p.meMjPerKgDm
        val pastureProtein=pastureDm*p.crudeProteinFraction
        return DailyNeed(
            dryMatterKg=dmi,
            meMj=energy,
            crudeProteinKg=protein,
            pastureShare=p.dryMatterShare,
            supplementDryMatterKg=(dmi-pastureDm).coerceAtLeast(0.0),
            pastureDryMatterKg=pastureDm,
            pastureEnergyMj=pastureEnergy,
            pastureCrudeProteinKg=pastureProtein,
            supplementEnergyMj=(energy-pastureEnergy).coerceAtLeast(0.0),
            supplementCrudeProteinKg=(protein-pastureProtein).coerceAtLeast(0.0),
            referenceNote="احتياجات مرجعية مستمدة من جداول OSU/NRC مع تقدير للمرعى؛ ليست تحليلاً مخبرياً"
        )
    }
}

internal data class FeedStock(val kind:FeedKind,val quantityKg:Double,val sharePercent:Double=0.0)
internal data class FeedUse(val kind:FeedKind,val asFedKg:Double,val dryMatterKg:Double)

internal fun unavailableSelectedFeedKinds(stocks:Map<FeedKind,Double>,shares:Map<FeedKind,Double>):List<FeedKind> =
    FeedKind.values().filter { !it.isMineral && (shares[it]?:0.0)>0.0 && (stocks[it]?:0.0)<=0.0 }

internal fun calculateSupplementUses(needs:List<DailyNeed>,stocks:List<FeedStock>):List<FeedUse> {
    val eligible=stocks.filter{!it.kind.isMineral && it.sharePercent>0}
    val shareTotal=eligible.sumOf{it.sharePercent}
    if(shareTotal<=0.0)return emptyList()
    val dm=needs.sumOf{it.supplementDryMatterKg}
    return eligible.map { s ->
        val part=dm*(s.sharePercent/shareTotal)
        FeedUse(s.kind,part/s.kind.dryMatter,part)
    }
}
