package dz.mawashi.app

import java.time.LocalDate

internal data class FeedConsumptionEntry(val item:String,val quantityKg:Double,val date:String)
internal data class FeedPurchaseCost(val item:String,val quantity:Double,val total:Double,val date:String)
internal data class FeedSaleEntry(val invoiceId:String,val item:String,val quantityKg:Double,val date:String)
internal data class FeedAccountingResult(
 val totalCost:Double,
 val uncostedQuantityKg:Double,
 val costByItem:Map<String,Double>,
 val totalSaleCost:Double,
 val uncostedSaleQuantityKg:Double,
 val saleCostByInvoice:Map<String,Double>
)
private data class FeedAccountingEvent(
 val date:LocalDate,
 val purchase:FeedPurchaseCost?=null,
 val consumption:FeedConsumptionEntry?=null,
 val sale:FeedSaleEntry?=null,
 val order:Int
)

/** Replays dated purchases, feed sales and daily consumption using a moving weighted-average inventory cost. */
internal fun calculateFeedConsumptionAccounting(
 consumption:List<FeedConsumptionEntry>,
 purchases:List<FeedPurchaseCost>,
 sales:List<FeedSaleEntry> = emptyList()
):FeedAccountingResult {
 val events=mutableListOf<FeedAccountingEvent>()
 var uncosted=0.0
 var uncostedSales=0.0
 purchases.forEachIndexed{index,p->
  val date=runCatching{LocalDate.parse(p.date)}.getOrNull()
  if(p.item.isNotBlank()&&date!=null&&p.quantity.isFinite()&&p.quantity>0.0&&p.total.isFinite()&&p.total>=0.0)
   events+=FeedAccountingEvent(date,purchase=p,order=index)
 }
 consumption.forEachIndexed{index,e->
  if(!e.quantityKg.isFinite()||e.quantityKg<=0.0)return@forEachIndexed
  val date=runCatching{LocalDate.parse(e.date)}.getOrNull()
  if(date==null)uncosted+=e.quantityKg
  else events+=FeedAccountingEvent(date,consumption=e,order=index)
 }
 sales.forEachIndexed{index,s->
  if(!s.quantityKg.isFinite()||s.quantityKg<=0.0)return@forEachIndexed
  val date=runCatching{LocalDate.parse(s.date)}.getOrNull()
  if(s.item.isBlank()||date==null)uncostedSales+=s.quantityKg
  else events+=FeedAccountingEvent(date,sale=s,order=index)
 }
 val quantities=mutableMapOf<String,Double>()
 val values=mutableMapOf<String,Double>()
 val consumptionCosts=linkedMapOf<String,Double>()
 val saleCosts=linkedMapOf<String,Double>()
 events.sortedWith(
  compareBy<FeedAccountingEvent>{it.date}
   .thenBy{when{it.purchase!=null->0;it.sale!=null->1;else->2}}
   .thenBy{it.order}
 ).forEach{event->
  val p=event.purchase
  if(p!=null){
   quantities[p.item]=(quantities[p.item]?:0.0)+p.quantity
   values[p.item]=(values[p.item]?:0.0)+p.total
  }else{
   val item=event.sale?.item?:event.consumption?.item?:return@forEach
   val requested=event.sale?.quantityKg?:event.consumption!!.quantityKg
   val available=quantities[item]?:0.0
   val used=minOf(requested,available)
   if(used>0.0&&available>0.0){
    val inventoryValue=values[item]?:0.0
    val cost=used*(inventoryValue/available)
    if(event.sale!=null)saleCosts[event.sale.invoiceId]=(saleCosts[event.sale.invoiceId]?:0.0)+cost
    else consumptionCosts[item]=(consumptionCosts[item]?:0.0)+cost
    quantities[item]=(available-used).coerceAtLeast(0.0)
    values[item]=(inventoryValue-cost).coerceAtLeast(0.0)
   }
   val missing=(requested-used).coerceAtLeast(0.0)
   if(event.sale!=null)uncostedSales+=missing else uncosted+=missing
  }
 }
 return FeedAccountingResult(
  totalCost=consumptionCosts.values.sum(),
  uncostedQuantityKg=uncosted,
  costByItem=consumptionCosts,
  totalSaleCost=saleCosts.values.sum(),
  uncostedSaleQuantityKg=uncostedSales,
  saleCostByInvoice=saleCosts
 )
}
