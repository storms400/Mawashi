package dz.mawashi.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restore
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
private val FeedGreen=Color(0xFF34734B)
private val FeedCream=Color(0xFFF7F3EA)
private val FeedInk=Color(0xFF27332A)
private val FeedMuted=Color(0xFF778078)
private val FeedLine=Color(0xFFE8E4DA)

internal fun loadFeedStocks(c:Context):MutableMap<FeedKind,Double>{
 val p=c.getSharedPreferences(FEED_PREFS,0)
 val a=JSONArray(p.getString(FEED_STOCK_KEY,"[]")?:"[]")
 val m=mutableMapOf<FeedKind,Double>()
 for(i in 0 until a.length()){
  val x=a.optJSONObject(i)?:continue
  val k=runCatching{FeedKind.valueOf(x.optString("kind"))}.getOrNull()?:continue
  m[k]=x.optDouble("quantity",0.0).coerceAtLeast(0.0)
 }
 return m
}
private fun stockJson(m:Map<FeedKind,Double>):String{
 val a=JSONArray()
 m.forEach{(k,v)->a.put(JSONObject().put("kind",k.name).put("quantity",v.coerceAtLeast(0.0)))}
 return a.toString()
}
internal fun saveFeedStocks(c:Context,m:Map<FeedKind,Double>){
 check(c.getSharedPreferences(FEED_PREFS,0).edit().putString(FEED_STOCK_KEY,stockJson(m)).commit())
}
private fun loadShares(c:Context):Map<FeedKind,Double>{
 val a=JSONArray(c.getSharedPreferences(FEED_PREFS,0).getString("shares","[]")?:"[]")
 val m=mutableMapOf<FeedKind,Double>()
 for(i in 0 until a.length()){
  val x=a.optJSONObject(i)?:continue
  runCatching{FeedKind.valueOf(x.optString("kind"))}.getOrNull()?.let{m[it]=x.optDouble("share",0.0).coerceIn(0.0,100.0)}
 }
 return m
}
private fun saveShares(c:Context,m:Map<FeedKind,Double>){
 val a=JSONArray()
 m.forEach{(k,v)->a.put(JSONObject().put("kind",k.name).put("share",v.coerceIn(0.0,100.0)))}
 check(c.getSharedPreferences(FEED_PREFS,0).edit().putString("shares",a.toString()).commit())
}
private fun recordFeedTransaction(c:Context,type:String,kind:FeedKind,qty:Double,note:String){
 val p=c.getSharedPreferences(FEED_PREFS,0)
 val a=JSONArray(p.getString(FEED_LEDGER_KEY,"[]")?:"[]")
 a.put(JSONObject().put("date",LocalDate.now().toString()).put("type",type).put("kind",kind.name).put("quantityKg",qty).put("note",note))
 check(p.edit().putString(FEED_LEDGER_KEY,a.toString()).commit())
}
private fun applyDailyFeedConsumption(c:Context,animals:List<HerdAnimal>,pasture:String,shares:Map<FeedKind,Double>):String{
 val p=c.getSharedPreferences(FEED_PREFS,0)
 val today=LocalDate.now().toString()
 if(p.getString(FEED_LAST_RUN_KEY,"")==today)return "استهلاك اليوم مسجل مسبقاً؛ لم يتكرر الخصم"
 val inputs=feedInputsForAnimals(animals)
 if(inputs.isEmpty())return "لا توجد حيوانات بأوزان مسجلة صالحة للحساب"
 val stocks=loadFeedStocks(c)
 val selected=FeedKind.values().filter{it!=FeedKind.MINERALS && (shares[it]?:0.0)>0.0}
 if(selected.isEmpty())return "حدد نسب مكونات الخلطة قبل الخصم"
 val missing=unavailableSelectedFeedKinds(stocks,shares)
 if(missing.isNotEmpty())return "لا يوجد مخزون من: "+missing.joinToString("، "){it.label}+"؛ لم يُخصم أي شيء ولم تُعدّل نسب الخلطة"
 val shareTotal=selected.sumOf{shares[it]?:0.0}
 if(shareTotal<=0.0)return "حدد نسب مكونات الخلطة قبل الخصم"
 val needs=inputs.map{SheepNutrition.calculate(it,pasture)}
 val totalDm=needs.sumOf{it.supplementDryMatterKg}
 val uses=selected.associateWith{k->(totalDm*((shares[k]?:0.0)/shareTotal))/k.dryMatter}
 val shortages=uses.filter{(k,v)->v>(stocks[k]?:0.0)+1e-8}
 if(shortages.isNotEmpty())return "المخزون غير كافٍ: "+shortages.keys.joinToString("، "){it.label}+"؛ لم يُخصم أي شيء"
 val updated=stocks.toMutableMap()
 uses.forEach{(k,v)->updated[k]=(updated[k]?:0.0)-v}
 val a=JSONArray(p.getString(FEED_LEDGER_KEY,"[]")?:"[]")
 uses.forEach{(k,v)->a.put(JSONObject().put("date",today).put("type","consumption").put("kind",k.name).put("quantityKg",v).put("note","استهلاك يومي تقديري لـ ${inputs.size} حيوان"))}
 val edit=p.edit().putString(FEED_STOCK_KEY,stockJson(updated)).putString(FEED_LEDGER_KEY,a.toString()).putString(FEED_LAST_RUN_KEY,today)
 check(edit.commit())
 return "تم تسجيل الخصم اليومي مرة واحدة لـ ${inputs.size} حيواناً"
}

@Composable fun FeedScreen(modifier:Modifier=Modifier){
 val c=LocalContext.current
 var stocks by remember{mutableStateOf(loadFeedStocks(c))}
 var shares by remember{mutableStateOf(loadShares(c))}
 var pasture by rememberSaveable{mutableStateOf(c.getSharedPreferences(FEED_PREFS,0).getString(FEED_PASTURE_KEY,"متوسط")?:"متوسط")}
 var dialogKind by remember{mutableStateOf<FeedKind?>(null)}
 var qty by rememberSaveable{mutableStateOf("")}
 var showPicker by remember{mutableStateOf(false)}
 var showConsumption by rememberSaveable{mutableStateOf(false)}
 var expandedTag by rememberSaveable{mutableStateOf("")}
 var message by remember{mutableStateOf<String?>(null)}
 val herd=activeHerdAnimals(loadHerd(c))
 val eligible=feedInputsForAnimals(herd)
 val needs=eligible.map{SheepNutrition.calculate(it,pasture)}
 val totalDmi=needs.sumOf{it.dryMatterKg}
 val totalSupplement=needs.sumOf{it.supplementDryMatterKg}
 val activeStocks=FeedKind.values().filter{(stocks[it]?:0.0)>0.0001}
 val selectedFeeds=FeedKind.values().filter{it!=FeedKind.MINERALS && (shares[it]?:0.0)>0}
 val unavailableSelected=selectedFeeds.filter{(stocks[it]?:0.0)<=0.0001}
 val estimatedUses=calculateSupplementUses(needs,selectedFeeds.map{FeedStock(it,stocks[it]?:0.0,shares[it]?:0.0)})
 val dailyAsFed=estimatedUses.sumOf{it.asFedKg}
 val totalStock=activeStocks.sumOf{stocks[it]?:0.0}
 val stockDays=estimatedUses.mapNotNull{use->if(use.asFedKg>0)(stocks[use.kind]?:0.0)/use.asFedKg else null}.minOrNull()
 LaunchedEffect(LocalDate.now().toString(),eligible,pasture,shares,stocks){
  val configured=activeStocks.filter{(shares[it]?:0.0)>0}
  if(eligible.isNotEmpty() && configured.isNotEmpty()){
   val outcome=applyDailyFeedConsumption(c,herd,pasture,shares)
   stocks=loadFeedStocks(c)
   if(outcome.startsWith("تم تسجيل"))message=outcome
  }
 }

 Column(modifier.fillMaxSize().background(FeedCream).padding(horizontal=16.dp)){
  Spacer(Modifier.height(12.dp))
  Row(verticalAlignment=Alignment.CenterVertically){
   if(showConsumption)IconButton(onClick={showConsumption=false;expandedTag=""}){Icon(Icons.Outlined.ArrowBack,"رجوع",tint=FeedGreen)}
   Column(Modifier.weight(1f)){
    Text(if(showConsumption)"استهلاك القطيع" else "مخزون العلف",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=FeedInk)
    Text(if(showConsumption)"استهلاك كل حيوان محسوباً على حدة" else "رصيد واحد مرتبط مباشرة بحيوانات قسم القطيع",fontSize=11.sp,color=FeedMuted)
   }
   if(!showConsumption)Icon(Icons.Outlined.Inventory2,null,tint=FeedGreen,modifier=Modifier.size(28.dp))
  }
  if(!showConsumption){
   Spacer(Modifier.height(12.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FeedMetric("إجمالي المخزون",String.format(Locale.US,"%.1f كغ",totalStock),Modifier.weight(1f))
    FeedMetric("الاستهلاك اليومي المقدر",String.format(Locale.US,"%.2f كغ",dailyAsFed),Modifier.weight(1f))
   }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FeedMetric("عدد حيوانات القطيع",herd.size.toString(),Modifier.weight(1f))
    FeedMetric("مدة كفاية الأصناف",stockDays?.let{String.format(Locale.US,"%.1f يوم",it)}?:"—",Modifier.weight(1f))
   }
   Text("المادة الجافة المطلوبة: "+String.format(Locale.US,"%.2f كغ/يوم",totalDmi)+" • المكمل المقدر: "+String.format(Locale.US,"%.2f كغ مادة جافة/يوم",totalSupplement),fontSize=10.sp,color=FeedMuted)
   if(unavailableSelected.isNotEmpty()){
    Surface(color=Color(0xFFFFF1D9),shape=RoundedCornerShape(12.dp)){
     Text("تنبيه: لا يوجد مخزون من "+unavailableSelected.joinToString("، "){it.label}+" رغم وجود نسبة لها في الخلطة. لن يعيد التطبيق توزيع هذه النسبة تلقائياً، ولن ينفذ خصم اليوم حتى توفيرها أو تعديل النسب.",modifier=Modifier.padding(11.dp),color=Color(0xFF81551A),fontSize=10.sp,lineHeight=15.sp)
    }
   }
   Spacer(Modifier.height(12.dp))
   Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FeedLine)){
    Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Text("مساهمة المرعى الطبيعي",fontWeight=FontWeight.Bold,color=FeedInk,fontSize=14.sp)
      Spacer(Modifier.weight(1f))
      Text("رعي مختلط",fontSize=10.sp,color=FeedGreen,fontWeight=FontWeight.SemiBold)
     }
     Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
      listOf("ضعيف","متوسط","جيد").forEach{FilterChip(selected=pasture==it,onClick={pasture=it;c.getSharedPreferences(FEED_PREFS,0).edit().putString(FEED_PASTURE_KEY,it).commit()},label={Text(it,fontSize=11.sp)})}
     }
     Text("تقدير مستوى المرعى لا يغني عن تحليل العلف أو تقييم الغطاء النباتي ميدانياً.",fontSize=10.sp,color=FeedMuted)
    }
   }
   Spacer(Modifier.height(12.dp))
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("الأعلاف الموجودة",fontWeight=FontWeight.Bold,color=FeedInk,fontSize=15.sp)
    Spacer(Modifier.weight(1f))
    TextButton(onClick={showPicker=true}){Icon(Icons.Outlined.Add,null);Text("إضافة مخزون")}
   }
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){
    if(activeStocks.isEmpty())item{
     Surface(color=Color.White,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FeedLine)){
      Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){
       Icon(Icons.Outlined.Inventory2,null,tint=FeedGreen,modifier=Modifier.size(34.dp))
       Text("لا يوجد مخزون مسجل",fontWeight=FontWeight.Bold,color=FeedInk)
       Text("أضف كمية من أحد أنواع العلف ليظهر هنا.",fontSize=11.sp,color=FeedMuted)
      }
     }
    }
    items(activeStocks,key={it.name}){kind->
     val q=stocks[kind]?:0.0
     Surface(color=Color.White,shape=RoundedCornerShape(15.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FeedLine)){
      Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
         Text(kind.label,color=FeedInk,fontWeight=FontWeight.Bold,fontSize=13.sp)
         Text(String.format(Locale.US,"الرصيد المتاح: %.2f كغ",q),color=FeedGreen,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
        }
       }
       if(kind!=FeedKind.MINERALS){
        Row(verticalAlignment=Alignment.CenterVertically){
         Text("نسبة الخلطة الإرشادية: ${(shares[kind]?:0.0).toInt()}%",fontSize=10.sp,color=FeedMuted)
         Spacer(Modifier.weight(1f))
        }
        Slider(value=(shares[kind]?:0.0).toFloat().coerceIn(0f,100f),onValueChange={v->shares=shares.toMutableMap().apply{put(kind,v.toDouble())}},onValueChangeFinished={saveShares(c,shares)})
       }else{
        Text("الأملاح المعدنية لا تُحسب ضمن طاقة العليقة؛ اتبع جرعة المنتج المخصص للأغنام.",fontSize=10.sp,color=FeedMuted)
       }
      }
     }
    }
    item{
     Text("مجموع النسب المدخلة: ${shares.filterKeys{it!=FeedKind.MINERALS}.values.sum().toInt()}% — النسب تستخدم لتوزيع تقديري للمكمل، ولا تثبت وحدها توازن الطاقة والبروتين.",fontSize=10.sp,color=FeedMuted)
    }
    item{
     Surface(color=Color(0xFFEAF1E9),shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth().clickable{showConsumption=true}){
      Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Outlined.Pets,null,tint=FeedGreen,modifier=Modifier.size(27.dp))
       Spacer(Modifier.width(10.dp))
       Column(Modifier.weight(1f)){
        Text("الاستهلاك",fontWeight=FontWeight.Bold,color=FeedGreen,fontSize=15.sp)
        Text("افتح قائمة الحيوانات وتفاصيل احتياج كل حيوان",fontSize=10.sp,color=FeedMuted)
       }
       Icon(Icons.Outlined.ArrowBack,null,tint=FeedGreen)
      }
     }
    }
    item{
     Button(onClick={message=applyDailyFeedConsumption(c,herd,pasture,shares);stocks=loadFeedStocks(c)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FeedGreen)){
      Icon(Icons.Outlined.Restore,null);Spacer(Modifier.width(7.dp));Text("احتساب وخصم استهلاك اليوم")
     }
    }
   }
  }else{
   Spacer(Modifier.height(10.dp))
   if(herd.isEmpty())Text("لا توجد حيوانات مسجلة في قسم القطيع.",color=FeedMuted,fontSize=12.sp)
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=16.dp)){
    items(herd,key={it.id}){animal->
     val weight=animal.weightKg?.takeIf{it>0}
     val need=weight?.let{SheepNutrition.calculate(FeedAnimalInput(animal.tag,it,animal.productionState,animal.pregnancyStage,animal.fetusCount,animal.nursingLambs,animal.ageMonths),pasture)}
     val expanded=expandedTag==animal.tag
     Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FeedLine),modifier=Modifier.fillMaxWidth().clickable{expandedTag=if(expanded)"" else animal.tag}){
      Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){
        Surface(color=Color(0xFFEAF1E9),shape=RoundedCornerShape(13.dp)){
         Icon(Icons.Outlined.Pets,null,tint=FeedGreen,modifier=Modifier.padding(9.dp).size(25.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)){
         Text(animal.tag,color=FeedInk,fontWeight=FontWeight.ExtraBold,fontSize=14.sp)
         Text("${animal.kind} • ${animal.ageMonths} شهر • ${animal.health}",color=FeedMuted,fontSize=10.sp)
        }
        Text(if(expanded)"إخفاء" else "التفاصيل",color=FeedGreen,fontSize=10.sp,fontWeight=FontWeight.Bold)
       }
       Text("الوزن: ${weight?.let{String.format(Locale.US,"%.1f كغ",it)}?:"غير مسجل"} • الحالة: ${animal.productionState}",color=FeedMuted,fontSize=10.sp)
       if(need==null){
        Text("أدخل الوزن الحالي في بطاقة الحيوان لاحتساب استهلاكه.",color=Color(0xFF9A6720),fontSize=11.sp)
       }else{
        Text("المادة الجافة المقدرة: ${String.format(Locale.US,"%.0f غ/يوم",need.dryMatterKg*1000)} • المكمل: ${String.format(Locale.US,"%.0f غ مادة جافة/يوم",need.supplementDryMatterKg*1000)}",color=FeedGreen,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
       }
       if(expanded && need!=null){
        Divider(color=FeedLine)
        Text("الاحتياج اليومي التقديري",color=FeedInk,fontWeight=FontWeight.Bold,fontSize=12.sp)
        Text("الطاقة: ${String.format(Locale.US,"%.1f ميغاجول",need.meMj)} • البروتين الخام المستهدف: ${String.format(Locale.US,"%.0f غ",need.crudeProteinKg*1000)}",color=FeedMuted,fontSize=10.sp)
        Text("مساهمة المرعى المقدرة: ${(need.pastureShare*100).toInt()}% من المادة الجافة • الباقي مكمل من المخزون",color=FeedMuted,fontSize=10.sp)
        val animalInputs=listOf(need)
        val animalUses=calculateSupplementUses(animalInputs,selectedFeeds.map{FeedStock(it,stocks[it]?:0.0,shares[it]?:0.0)})
        if(animalUses.isEmpty())Text("لا توجد أعلاف تكميلية متاحة ومحددة النسبة.",color=Color(0xFF9A6720),fontSize=10.sp)
        animalUses.forEach{use->
         Text("${use.kind.label}: ${String.format(Locale.US,"%.0f غ/يوم",use.asFedKg*1000)} (كما يُقدّم)",color=FeedGreen,fontSize=10.sp)
        }
        Text("هذه كميات توزيع أولية حسب النسب؛ لا تعتبر عليقة متوازنة نهائياً قبل إدخال تحليل المرعى والأعلاف والتحقق من الطاقة والبروتين والمعادن.",color=Color(0xFF9A6720),fontSize=10.sp,lineHeight=15.sp)
       }
      }
     }
    }
   }
  }
 }

 message?.let{AlertDialog(onDismissRequest={message=null},title={Text("نتيجة العملية")},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("حسناً")}})}
 if(showPicker)AlertDialog(onDismissRequest={showPicker=false},title={Text("اختر نوع العلف")},text={Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
  FeedKind.values().filter{(stocks[it]?:0.0)<=0.0001}.forEach{k->TextButton(onClick={dialogKind=k;qty="";showPicker=false},modifier=Modifier.fillMaxWidth()){Text(k.label,color=FeedGreen)}}
 }},confirmButton={TextButton(onClick={showPicker=false}){Text("إغلاق")}})
 dialogKind?.let{k->AlertDialog(onDismissRequest={dialogKind=null},title={Text("إضافة مخزون: ${k.label}")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  OutlinedTextField(qty,{qty=it.filter{ch->ch in "0123456789.,"}},label={Text("الكمية المضافة بالكيلوغرام")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  Text("ستُضاف الكمية إلى الرصيد الحالي ويُسجّل ذلك في سجل المخزون.",fontSize=10.sp,color=FeedMuted)
 }},confirmButton={TextButton(onClick={
  val v=qty.replace(',','.').toDoubleOrNull()
  if(v!=null&&v>0){
   val updated=stocks.toMutableMap();updated[k]=(updated[k]?:0.0)+v
   saveFeedStocks(c,updated);recordFeedTransaction(c,"stock_addition",k,v,"إضافة مخزون")
   stocks=updated;dialogKind=null
  }
 }){Text("تأكيد",color=FeedGreen)}},dismissButton={TextButton(onClick={dialogKind=null}){Text("إلغاء")}})}
}

@Composable private fun FeedMetric(label:String,value:String,modifier:Modifier=Modifier){
 Surface(modifier,color=Color.White,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,FeedLine)){
  Column(Modifier.padding(11.dp)){
   Text(value,color=FeedGreen,fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
   Text(label,color=FeedMuted,fontSize=10.sp)
  }
 }
}
