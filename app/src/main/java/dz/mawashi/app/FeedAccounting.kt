package dz.mawashi.app

import java.time.LocalDate

internal data class FeedConsumptionEntry(val item:String,val quantityKg:Double,val date:String)
internal data class FeedPurchaseCost(val item:String,val quantity:Double,val total:Double,val date:String)
internal data class FeedAccountingResult(val totalCost:Double,val uncostedQuantityKg:Double,val costByItem:Map<String,Double>)
private data class FeedAccountingEvent(val date:LocalDate,val purchase:FeedPurchaseCost?=null,val consumption:FeedConsumptionEntry?=null,val order:Int)

/** Replays purchases and withdrawals chronologically using a moving weighted-average inventory cost. */
internal fun calculateFeedConsumptionAccounting(consumption:List<FeedConsumptionEntry>,purchases:List<FeedPurchaseCost>):FeedAccountingResult {
 val events=mutableListOf<FeedAccountingEvent>()
 var uncosted=0.0
 purchases.forEachIndexed{index,p->
  val date=runCatching{LocalDate.parse(p.date)}.getOrNull()
  if(date!=null&&p.quantity.isFinite()&&p.quantity>0.0&&p.total.isFinite()&&p.total>=0.0)events+=FeedAccountingEvent(date,purchase=p,order=index)
 }
 consumption.forEachIndexed{index,e->
  if(!e.quantityKg.isFinite()||e.quantityKg<=0.0)return@forEachIndexed
  val date=runCatching{LocalDate.parse(e.date)}.getOrNull()
  if(date==null)uncosted+=e.quantityKg else events+=FeedAccountingEvent(date,consumption=e,order=index)
 }
 val quantities=mutableMapOf<String,Double>();val values=mutableMapOf<String,Double>();val costs=linkedMapOf<String,Double>()
 events.sortedWith(compareBy<FeedAccountingEvent>{it.date}.thenBy{if(it.purchase!=null)0 else 1}.thenBy{it.order}).forEach{event->
  val p=event.purchase
  if(p!=null){
   quantities[p.item]=(quantities[p.item]?:0.0)+p.quantity
   values[p.item]=(values[p.item]?:0.0)+p.total
  }else{
   val e=event.consumption?:return@forEach
   val available=quantities[e.item]?:0.0
   val used=minOf(e.quantityKg,available)
   if(used>0.0&&available>0.0){
    val inventoryValue=values[e.item]?:0.0
    val cost=used*(inventoryValue/available)
    costs[e.item]=(costs[e.item]?:0.0)+cost
    quantities[e.item]=(available-used).coerceAtLeast(0.0)
    values[e.item]=(inventoryValue-cost).coerceAtLeast(0.0)
   }
   uncosted+=(e.quantityKg-used).coerceAtLeast(0.0)
  }
 }
 return FeedAccountingResult(costs.values.sum(),uncosted,costs)
}
