package dz.mawashi.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

private const val FEED_PREFS="mawashi_feed_stock"
private const val FEED_STOCK_KEY="stock"
private const val FEED_LEDGER_KEY="ledger"
private const val FEED_PASTURE_KEY="pasture"
private const val FEED_LAST_RUN_KEY="last_consumption_date"
private val FeedGreen=Color(0xFF526B55)
private val FeedCream=Color(0xFFF7F3EA)
private val FeedInk=Color(0xFF27332A)
private val FeedMuted=Color(0xFF778078)

private fun loadFeedStocks(c:Context):MutableMap<FeedKind,Double>{
 val p=c.getSharedPreferences(FEED_PREFS,0);val a=JSONArray(p.getString(FEED_STOCK_KEY,"[]")?:"[]")
 val m=mutableMapOf<FeedKind,Double>()
 for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;val k=runCatching{FeedKind.valueOf(x.optString("kind"))}.getOrNull()?:continue;m[k]=x.optDouble("quantity",0.0).coerceAtLeast(0.0)}
 FeedKind.values().forEach{m.putIfAbsent(it,0.0)}
 return m
}
private fun saveFeedStocks(c:Context,m:Map<FeedKind,Double>){val a=JSONArray();m.forEach{(k,v)->a.put(JSONObject().put("kind",k.name).put("quantity",v.coerceAtLeast(0.0)))};check(c.getSharedPreferences(FEED_PREFS,0).edit().putString(FEED_STOCK_KEY,a.toString()).commit())}
private fun loadShares(c:Context):Map<FeedKind,Double>{val p=c.getSharedPreferences(FEED_PREFS,0);val a=JSONArray(p.getString("shares","[]")?:"[]");val m=mutableMapOf<FeedKind,Double>();for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;runCatching{FeedKind.valueOf(x.optString("kind"))}.getOrNull()?.let{m[it]=x.optDouble("share",0.0)}};return m}
private fun saveShares(c:Context,m:Map<FeedKind,Double>){val a=JSONArray();m.forEach{(k,v)->a.put(JSONObject().put("kind",k.name).put("share",v.coerceIn(0.0,100.0)))};check(c.getSharedPreferences(FEED_PREFS,0).edit().putString("shares",a.toString()).commit())}
private fun recordFeedTransaction(c:Context,type:String,kind:FeedKind,qty:Double,note:String){val p=c.getSharedPreferences(FEED_PREFS,0);val a=JSONArray(p.getString(FEED_LEDGER_KEY,"[]")?:"[]");a.put(JSONObject().put("date",LocalDate.now().toString()).put("type",type).put("kind",kind.name).put("quantityKg",qty).put("note",note));check(p.edit().putString(FEED_LEDGER_KEY,a.toString()).commit())}
private fun applyDailyFeedConsumption(c:Context,animals:List<HerdAnimal>,pasture:String,shares:Map<FeedKind,Double>):String{
 val p=c.getSharedPreferences(FEED_PREFS,0);val today=LocalDate.now().toString()
 if(p.getString(FEED_LAST_RUN_KEY,"")==today)return "استهلاك اليوم مسجل مسبقاً"
 val stocks=loadFeedStocks(c)
 val inputs=animals.mapNotNull{a->val w=a.weightKg?.takeIf{it>0}?:return@mapNotNull null
  FeedAnimalInput(a.tag,w,a.productionState,a.pregnancyStage,a.fetusCount,a.nursingLambs,a.ageMonths)}
 val needs=inputs.map{SheepNutrition.calculate(it,pasture)}
 val active=FeedKind.values().filter{it!=FeedKind.MINERALS && (stocks[it]?:0.0)>0 && (shares[it]?:0.0)>0}
 val shareTotal=active.sumOf{shares[it]?:0.0}
 if(inputs.isEmpty())return "لا توجد حيوانات بأوزان وحالات صالحة للحساب"
 if(shareTotal<=0.0)return "حدد نسب توزيع الأعلاف المتوفرة قبل بدء الخصم"
 val totalDm=needs.sumOf{it.supplementDryMatterKg}
 val uses=active.associateWith{k->(totalDm*((shares[k]?:0.0)/shareTotal))/k.dryMatter}
 val shortages=uses.filter{(k,v)->v>(stocks[k]?:0.0)+1e-8}
 if(shortages.isNotEmpty())return "المخزون غير كافٍ: "+shortages.keys.joinToString("، "){it.label}+"؛ لم يُخصم أي شيء"
 val updated=stocks.toMutableMap();uses.forEach{(k,v)->updated[k]=(updated[k]?:0.0)-v}
 val a=JSONArray(p.getString(FEED_LEDGER_KEY,"[]")?:"[]")
 uses.forEach{(k,v)->a.put(JSONObject().put("date",today).put("type","consumption").put("kind",k.name).put("quantityKg",v).put("note","خصم يومي محسوب لـ ${inputs.size} حيوان"))}
 val edit=p.edit().putString(FEED_STOCK_KEY,stockJson(updated)).putString(FEED_LEDGER_KEY,a.toString()).putString(FEED_LAST_RUN_KEY,today)
 check(edit.commit())
 return "تم تسجيل استهلاك اليوم مرة واحدة لـ ${inputs.size} حيوان"
}
private fun stockJson(m:Map<FeedKind,Double>):String{val a=JSONArray();m.forEach{(k,v)->a.put(JSONObject().put("kind",k.name).put("quantity",v.coerceAtLeast(0.0)))};return a.toString()}

@Composable fun FeedScreen(modifier:Modifier=Modifier){
 val c=LocalContext.current
 var stocks by remember{mutableStateOf(loadFeedStocks(c))}
 var shares by remember{mutableStateOf(loadShares(c))}
 var pasture by rememberSaveable{mutableStateOf(c.getSharedPreferences(FEED_PREFS,0).getString(FEED_PASTURE_KEY,"متوسط")?:"متوسط")}
 var dialogKind by remember{mutableStateOf<FeedKind?>(null)}
 var qty by rememberSaveable{mutableStateOf("")}
 var dialogType by remember{mutableStateOf("شراء")}
 var message by remember{mutableStateOf<String?>(null)}
 val herd=remember{loadHerd(c)}
 val eligible=herd.mapNotNull{a->val w=a.weightKg?.takeIf{it>0}?:return@mapNotNull null;FeedAnimalInput(a.tag,w,a.productionState,a.pregnancyStage,a.fetusCount,a.nursingLambs,a.ageMonths)}
 val needs=eligible.map{SheepNutrition.calculate(it,pasture)}
 val totalDmi=needs.sumOf{it.dryMatterKg}
 val totalSupplement=needs.sumOf{it.supplementDryMatterKg}
 Column(modifier.fillMaxSize().background(FeedCream).padding(horizontal=16.dp)){
  Spacer(Modifier.height(12.dp))
  Text("مخزون العلف",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=FeedInk)
  Text("رصيد موحد مرتبط بالقطيع المسجل",fontSize=12.sp,color=FeedMuted)
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   FeedMetric("عدد الحيوانات المحسوبة",eligible.size.toString(),Modifier.weight(1f))
   FeedMetric("المادة الجافة اليومية",String.format(Locale.US,"%.2f كغ",totalDmi),Modifier.weight(1f))
  }
  FeedMetric("العلف التكميلي المطلوب من المخزون",String.format(Locale.US,"%.2f كغ مادة جافة/يوم",totalSupplement),Modifier.fillMaxWidth())
  Spacer(Modifier.height(10.dp))
  Text("مساهمة المرعى الطبيعي",fontWeight=FontWeight.Bold,color=FeedInk)
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("ضعيف","متوسط","جيد").forEach{FilterChip(pasture==it,{pasture=it;c.getSharedPreferences(FEED_PREFS,0).edit().putString(FEED_PASTURE_KEY,it).commit()},label={Text(it,fontSize=11.sp)})}}
  Text("النسب تقديرية لإدارة المرعى وليست تحليلاً مخبرياً للعلف.",fontSize=10.sp,color=FeedMuted)
  Spacer(Modifier.height(8.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Text("الأعلاف المسجلة",fontWeight=FontWeight.Bold,color=FeedInk);Spacer(Modifier.weight(1f));Text("اضغط لإضافة شراء أو تعديل",fontSize=10.sp,color=FeedMuted)}
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=16.dp)){
   items(FeedKind.values().toList(),key={it.name}){kind->
    val q=stocks[kind]?:0.0
    Surface(color=Color.White,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFE8E4DA))){
     Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(kind.label,color=FeedInk,fontWeight=FontWeight.SemiBold,fontSize=13.sp);Text(String.format(Locale.US,"المخزون: %.2f كغ",q),color=FeedGreen,fontSize=11.sp)};TextButton(onClick={dialogKind=kind;dialogType="شراء";qty=""}){Icon(Icons.Outlined.Add,null);Text("شراء")};TextButton(onClick={dialogKind=kind;dialogType="استهلاك يدوي";qty=""}){Text("صرف")}}
      if(kind!=FeedKind.MINERALS){
       val s=shares[kind]?:0.0
       Row(verticalAlignment=Alignment.CenterVertically){Text("نسبة العليقة: ${s.toInt()}%",fontSize=10.sp,color=FeedMuted);Spacer(Modifier.weight(1f));Text("تعديل النسبة",fontSize=10.sp,color=FeedGreen)}
       Slider(value=s.toFloat(),onValueChange={v->shares=shares.toMutableMap().apply{put(kind,v.toDouble())}},onValueChangeFinished={saveShares(c,shares)})
      }else Text("الأملاح تُسجل كمخزون مستقل؛ حدد جرعتها حسب بطاقة المنتج أو الطبيب البيطري.",fontSize=10.sp,color=FeedMuted)
     }
    }
   }
   item{Text("مجموع نسب الأعلاف: ${shares.filterKeys{it!=FeedKind.MINERALS}.values.sum().toInt()}% — تُطبّع النسب تلقائياً عند حساب العليقة.",fontSize=10.sp,color=FeedMuted)}
   item{Button(onClick={message=applyDailyFeedConsumption(c,herd,pasture,shares);stocks=loadFeedStocks(c)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FeedGreen)){Icon(Icons.Outlined.Inventory2,null);Text("احتساب وخصم استهلاك اليوم")}}
   item{Text("تقدير الاحتياج الغذائي",fontWeight=FontWeight.Bold,color=FeedInk,fontSize=15.sp)}
   items(eligible.zip(needs),key={it.first.tag}){(a,n)->Surface(color=Color.White,shape=RoundedCornerShape(12.dp)){Column(Modifier.fillMaxWidth().padding(10.dp)){Text("${a.tag} • ${"%.1f".format(Locale.US,a.weightKg)} كغ • ${a.state}",fontWeight=FontWeight.Bold,color=FeedInk,fontSize=12.sp);Text("المادة الجافة: ${"%.0f".format(Locale.US,n.dryMatterKg*1000)} غ/يوم • طاقة تقديرية: ${"%.1f".format(Locale.US,n.meMj)} ميغاجول/يوم",fontSize=10.sp,color=FeedMuted);Text("المكمل من المخزون: ${"%.0f".format(Locale.US,n.supplementDryMatterKg*1000)} غ مادة جافة/يوم",fontSize=10.sp,color=FeedGreen)}}}
  }
 }
 message?.let{AlertDialog(onDismissRequest={message=null},title={Text("نتيجة العملية")},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("حسناً")}})}
 dialogKind?.let{k->AlertDialog(onDismissRequest={dialogKind=null},title={Text("${dialogType}: ${k.label}")},text={Column{OutlinedTextField(qty,{qty=it.filter{ch->ch in "0123456789.,"}},label={Text("الكمية بالكيلوغرام")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);Text("سيتم تسجيل العملية في سجل المخزون.",fontSize=10.sp,color=FeedMuted)}},confirmButton={TextButton(onClick={val v=qty.replace(',','.').toDoubleOrNull();if(v!=null&&v>0){val updated=stocks.toMutableMap();updated[k]=if(dialogType=="شراء") (updated[k]?:0.0)+v else (updated[k]?:0.0)-v;if(updated[k]!!>=0){saveFeedStocks(c,updated);recordFeedTransaction(c,if(dialogType=="شراء")"purchase" else "manual_issue",k,v,"إدخال يدوي");stocks=updated;dialogKind=null}else message="الكمية المطلوبة أكبر من المخزون المتاح"}}){Text("تأكيد",color=FeedGreen)}},dismissButton={TextButton(onClick={dialogKind=null}){Text("إلغاء")}})}
}
@Composable private fun FeedMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,color=Color.White,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFE8E4DA))){Column(Modifier.padding(11.dp)){Text(value,color=FeedGreen,fontSize=19.sp,fontWeight=FontWeight.ExtraBold);Text(label,color=FeedMuted,fontSize=10.sp)}}}
