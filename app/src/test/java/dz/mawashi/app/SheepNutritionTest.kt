package dz.mawashi.app

import org.junit.Assert.*
import org.junit.Test

class SheepNutritionTest {
 @Test fun maintenanceIntakeScalesWithWeightAndPasture(){
  val a=FeedAnimalInput("Q-1",35.0,"نعجة جافة")
  val weak=SheepNutrition.calculate(a,"ضعيف")
  val good=SheepNutrition.calculate(a,"جيد")
  assertEquals(0.70,weak.dryMatterKg,0.0001)
  assertTrue(weak.supplementDryMatterKg>good.supplementDryMatterKg)
 }
 @Test fun twinLatePregnancyNeedsMoreThanSingle(){
  val single=SheepNutrition.calculate(FeedAnimalInput("Q-1",50.0,"نعجة حامل","الأربعة أسابيع الأخيرة",1),"متوسط")
  val twins=SheepNutrition.calculate(FeedAnimalInput("Q-2",50.0,"نعجة حامل","الأربعة أسابيع الأخيرة",2),"متوسط")
  assertTrue(twins.meMj>single.meMj)
  assertTrue(twins.dryMatterKg>single.dryMatterKg)
 }
 @Test fun lactatingTwinEweNeedsMoreThanDryEwe(){
  val dry=SheepNutrition.calculate(FeedAnimalInput("Q-1",50.0,"نعجة جافة"),"متوسط")
  val lactating=SheepNutrition.calculate(FeedAnimalInput("Q-2",50.0,"نعجة مرضعة",nursingLambs=2),"متوسط")
  assertTrue(lactating.meMj>dry.meMj)
  assertTrue(lactating.crudeProteinKg>dry.crudeProteinKg)
 }
 @Test fun selectedIngredientWithoutStockIsReportedInsteadOfSilentlyRedistributing(){
  val stocks=mapOf(FeedKind.BARLEY to 20.0,FeedKind.BRAN to 0.0)
  val shares=mapOf(FeedKind.BARLEY to 60.0,FeedKind.BRAN to 40.0)
  assertEquals(listOf(FeedKind.BRAN),unavailableSelectedFeedKinds(stocks,shares))
 }
 @Test fun mineralsAreNotTreatedAsBulkRationIngredients(){
  val stocks=mapOf(FeedKind.MINERALS to 0.0)
  val shares=mapOf(FeedKind.MINERALS to 100.0)
  assertTrue(unavailableSelectedFeedKinds(stocks,shares).isEmpty())
 }
 @Test fun mineralSaltIsNotIncludedInBulkRationAllocation(){
  val uses=calculateSupplementUses(listOf(SheepNutrition.calculate(FeedAnimalInput("Q-1",35.0,"نعجة جافة"),"متوسط")),listOf(FeedStock(FeedKind.MINERALS,10.0,100.0),FeedStock(FeedKind.BARLEY,100.0,100.0)))
  assertEquals(1,uses.size)
  assertEquals(FeedKind.BARLEY,uses.first().kind)
 }
}
