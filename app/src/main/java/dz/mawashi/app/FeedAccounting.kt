package dz.mawashi.app
internal data class FeedConsumptionEntry(val item:String,val quantityKg:Double)
internal data class FeedPurchaseCost(val item:String,val quantity:Double,val total:Double)
internal data class FeedAccountingResult(val totalCost:Double,val uncostedQuantityKg:Double,val costByItem:Map<String,Double>)
internal fun calculateFeedConsumptionAccounting(consumption:List<FeedConsumptionEntry>,purchases:List<FeedPurchaseCost>):FeedAccountingResult{
 val averages=purchases.filter{it.quantity.isFinite()&&it.quantity>0.0&&it.total.isFinite()&&it.total>=0.0}.groupBy{it.item}.mapValues{(_,rows)->rows.sumOf{it.total}/rows.sumOf{it.quantity}}
 val quantities=consumption.filter{it.quantityKg.isFinite()&&it.quantityKg>0.0}.groupBy{it.item}.mapValues{(_,rows)->rows.sumOf{it.quantityKg}}
 var uncosted=0.0
 val costs=quantities.mapValues{(item,quantity)->val unit=averages[item];if(unit==null){uncosted+=quantity;0.0}else quantity*unit}
 return FeedAccountingResult(costs.values.sum(),uncosted,costs)
}
