package dz.mawashi.app
import org.junit.Assert.assertEquals
import org.junit.Test
class FeedAccountingTest{
 @Test fun usesWeightedAveragePurchaseCost(){
  val r=calculateFeedConsumptionAccounting(listOf(FeedConsumptionEntry("شعير",10.0)),listOf(FeedPurchaseCost("شعير",20.0,1000.0),FeedPurchaseCost("شعير",30.0,1800.0)))
  assertEquals(560.0,r.totalCost,0.001);assertEquals(0.0,r.uncostedQuantityKg,0.001)
 }
 @Test fun exposesConsumptionWithoutPurchaseCost(){
  val r=calculateFeedConsumptionAccounting(listOf(FeedConsumptionEntry("نخالة",4.0),FeedConsumptionEntry("شعير",2.0)),listOf(FeedPurchaseCost("شعير",10.0,500.0)))
  assertEquals(100.0,r.totalCost,0.001);assertEquals(4.0,r.uncostedQuantityKg,0.001)
 }
 @Test fun ignoresInvalidRows(){
  val r=calculateFeedConsumptionAccounting(listOf(FeedConsumptionEntry("شعير",-5.0),FeedConsumptionEntry("شعير",Double.NaN)),listOf(FeedPurchaseCost("شعير",0.0,100.0),FeedPurchaseCost("شعير",10.0,-20.0)))
  assertEquals(0.0,r.totalCost,0.001);assertEquals(0.0,r.uncostedQuantityKg,0.001)
 }
}
