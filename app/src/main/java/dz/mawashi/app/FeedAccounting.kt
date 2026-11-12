package dz.mawashi.app

import java.time.LocalDate

internal data class FeedConsumptionEntry(val item:String,val quantityKg:Double,val date:String)
internal data class FeedPurchaseCost(val item:String,val quantity:Double,val total:Double,val date:String)
internal data class FeedAccountingResult(val totalCost:Double,val uncostedQuantityKg:Double,val costByItem:Map<String,Double>)

internal fun calculateFeedConsumptionAccounting(
 consumption:List<FeedConsumptionEntry>,
 purchases:List<FeedPurchaseCost>
):FeedAccountingResult {
 val validPurchases=purchases.mapNotNull{purchase->
  val date=runCatching{LocalDate.parse(purchase.date)}.getOrNull()
  if(date!=null&&purchase.quantity.isFinite()&&purchase.quantity>0.0&&purchase.total.isFinite()&&purchase.total>=0.0) purchase to date else null
 }
 var uncosted=0.0
 val costs=linkedMapOf<String,Double>()
 consumption.forEach{entry->
  if(!entry.quantityKg.isFinite()||entry.quantityKg<=0.0)return@forEach
  val consumptionDate=runCatching{LocalDate.parse(entry.date)}.getOrNull()
  if(consumptionDate==null){uncosted+=entry.quantityKg;return@forEach}
  val eligible=validPurchases.filter{(purchase,date)->purchase.item==entry.item&&!date.isAfter(consumptionDate)}
  val quantity=eligible.sumOf{it.first.quantity}
  if(quantity<=0.0){uncosted+=entry.quantityKg;return@forEach}
  val total=eligible.sumOf{it.first.total}
  val averageCost=total/quantity
  val cost=entry.quantityKg*averageCost
  costs[entry.item]=(costs[entry.item]?:0.0)+cost
 }
 return FeedAccountingResult(costs.values.sum(),uncosted,costs)
}
