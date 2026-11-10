package dz.mawashi.app

import kotlin.math.max
import kotlin.math.pow

internal enum class FeedKind(val label:String, val dryMatter:Double, val meMjPerKgDm:Double, val crudeProteinPercent:Double, val roughage:Boolean=false) {
    BARLEY("الشعير المجروش",0.89,12.5,11.5),
    BRAN("النخالة (القمح المطحون)",0.89,10.5,16.0),
    MAIZE("الذرة الصفراء",0.89,13.5,9.0),
    WHEAT("القمح",0.89,13.0,12.0),
    SOY("كسبة الصويا",0.89,12.0,44.0),
    STRAW("التبن",0.90,6.0,4.0,true),
    ALFALFA("الفصة",0.88,8.5,18.0,true),
    OAT_HAY("الخرطال (الشوفان)",0.89,8.5,9.0,true),
    MINERALS("الأملاح المعدنية",0.98,0.0,0.0)
}

internal data class FeedAnimalInput(
    val tag:String, val weightKg:Double, val state:String,
    val pregnancyStage:String="غير حامل", val fetusCount:Int=1,
    val nursingLambs:Int=1, val ageMonths:Int=12
)

internal fun feedInputsForAnimals(animals:List<HerdAnimal>):List<FeedAnimalInput> =
    activeHerdAnimals(animals).mapNotNull { animal ->
        val weight=animal.weightKg?.takeIf { it>0.0 } ?: return@mapNotNull null
        FeedAnimalInput(animal.tag,weight,animal.productionState,animal.pregnancyStage,animal.fetusCount,animal.nursingLambs,animal.ageMonths)
    }
internal data class DailyNeed(
    val dryMatterKg:Double, val meMj:Double, val crudeProteinKg:Double,
    val pastureShare:Double, val supplementDryMatterKg:Double
)

internal object SheepNutrition {
    fun pastureShare(level:String):Double = when(level) {
        "ضعيف" -> 0.20
        "متوسط" -> 0.40
        "جيد" -> 0.60
        else -> 0.40
    }

    fun calculate(a:FeedAnimalInput, pastureLevel:String):DailyNeed {
        val w=a.weightKg.coerceIn(2.0,180.0)
        val pregnant=a.state=="نعجة حامل"
        val lactating=a.state=="نعجة مرضعة"
        val dry=a.state=="نعجة جافة"
        val ram=a.state=="كبش"
        val suckling=a.state=="حمل يرضع"
        val lamb=a.state=="خروف"
        val twin= a.fetusCount>=2
        val nursingTwins=a.nursingLambs>=2

        val dmiPct=when {
            lactating -> if(nursingTwins) 0.032 else 0.028
            pregnant && a.pregnancyStage=="الأربعة أسابيع الأخيرة" -> if(twin) 0.027 else 0.024
            pregnant && a.pregnancyStage=="منتصف الحمل" -> 0.022
            pregnant -> 0.020
            suckling -> 0.018
            lamb -> if(a.ageMonths<6) 0.035 else 0.030
            ram -> 0.020
            dry -> 0.020
            else -> 0.020
        }
        val dmi=w*dmiPct
        val maintenanceMe=0.42*w.pow(0.75)*1.15 // grazing activity allowance
        val meFactor=when {
            lactating -> if(nursingTwins) 2.05 else 1.75
            pregnant && a.pregnancyStage=="الأربعة أسابيع الأخيرة" -> if(twin) 1.65 else 1.40
            pregnant && a.pregnancyStage=="منتصف الحمل" -> 1.15
            pregnant -> 1.05
            ram -> 1.15
            lamb || suckling -> 1.45
            else -> 1.0
        }
        val cpPercent=when {
            lactating -> if(nursingTwins) 0.16 else 0.14
            pregnant && a.pregnancyStage=="الأربعة أسابيع الأخيرة" -> if(twin) 0.13 else 0.11
            pregnant -> 0.10
            lamb || suckling -> 0.16
            else -> 0.08
        }
        val pasture=pastureShare(pastureLevel)
        return DailyNeed(dmi,maintenanceMe*meFactor,dmi*cpPercent,pasture,dmi*(1.0-pasture))
    }
}

internal data class FeedStock(val kind:FeedKind,val quantityKg:Double,val sharePercent:Double=0.0)
internal data class FeedUse(val kind:FeedKind,val asFedKg:Double,val dryMatterKg:Double)

internal fun calculateSupplementUses(needs:List<DailyNeed>,stocks:List<FeedStock>):List<FeedUse> {
    val eligible=stocks.filter{it.kind!=FeedKind.MINERALS && it.sharePercent>0}
    val shareTotal=eligible.sumOf{it.sharePercent}
    if(shareTotal<=0.0)return emptyList()
    val dm=needs.sumOf{it.supplementDryMatterKg}
    return eligible.map { s ->
        val part=dm*(s.sharePercent/shareTotal)
        FeedUse(s.kind,part/s.kind.dryMatter,part)
    }
}
