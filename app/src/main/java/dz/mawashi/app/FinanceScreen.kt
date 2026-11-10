package dz.mawashi.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private const val FIN_PREFS = "mawashi_finance_v24"
private const val FIN_INVOICES = "invoices"
private const val FIN_MEDICINES = "medicine_stock"
private const val FIN_WOOL = "wool_stock"
private const val FIN_OTHER = "other_entries"
private val FinGreen = Color(0xFF526B55)
private val FinCream = Color(0xFFF7F3EA)
private val FinInk = Color(0xFF27332A)
private val FinMuted = Color(0xFF778078)
private val FinLine = Color(0xFFE8E4DA)

internal data class FinanceInvoice(val id:String,val type:String,val category:String,val party:String,val item:String,val quantity:Double,val unit:String,val unitPrice:Double,val total:Double,val cost:Double,val timestamp:String,val notes:String)

private fun readInvoices(c:Context):List<FinanceInvoice>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(FIN_INVOICES,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);FinanceInvoice(x.getString("id"),x.getString("type"),x.optString("category"),x.optString("party"),x.optString("item"),x.optDouble("quantity"),x.optString("unit"),x.optDouble("unitPrice"),x.optDouble("total"),x.optDouble("cost"),x.optString("timestamp"),x.optString("notes"))}.getOrNull()}.sortedByDescending{it.timestamp}
}
private fun writeInvoice(c:Context,v:FinanceInvoice){
 val p=c.getSharedPreferences(FIN_PREFS,0);val a=JSONArray(p.getString(FIN_INVOICES,"[]")?:"[]")
 a.put(JSONObject().apply{put("id",v.id);put("type",v.type);put("category",v.category);put("party",v.party);put("item",v.item);put("quantity",v.quantity);put("unit",v.unit);put("unitPrice",v.unitPrice);put("total",v.total);put("cost",v.cost);put("timestamp",v.timestamp);put("notes",v.notes)})
 check(p.edit().putString(FIN_INVOICES,a.toString()).commit())
}
private fun readMedicineStock(c:Context):MutableMap<String,Double>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(FIN_MEDICINES,"[]")?:"[]");val m=mutableMapOf<String,Double>()
 for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;m[x.optString("name")]=x.optDouble("quantity").coerceAtLeast(0.0)}
 return m
}
private fun writeMedicineStock(c:Context,m:Map<String,Double>){val a=JSONArray();m.forEach{(n,q)->a.put(JSONObject().put("name",n).put("quantity",q.coerceAtLeast(0.0)))};check(c.getSharedPreferences(FIN_PREFS,0).edit().putString(FIN_MEDICINES,a.toString()).commit())}
private fun readWool(c:Context)=c.getSharedPreferences(FIN_PREFS,0).getString(FIN_WOOL,"0")?.toDoubleOrNull()?:0.0
private fun writeWool(c:Context,q:Double){check(c.getSharedPreferences(FIN_PREFS,0).edit().putString(FIN_WOOL,q.coerceAtLeast(0.0).toString()).commit())}
private fun otherTotals(c:Context):Pair<Double,Double>{val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(FIN_OTHER,"[]")?:"[]");var i=0.0;var e=0.0;for(n in 0 until a.length()){val x=a.getJSONObject(n);if(x.optString("type")=="income")i+=x.optDouble("amount")else e+=x.optDouble("amount")};return i to e}
private fun addOther(c:Context,t:String,n:String,v:Double){val p=c.getSharedPreferences(FIN_PREFS,0);val a=JSONArray(p.getString(FIN_OTHER,"[]")?:"[]");a.put(JSONObject().put("type",t).put("name",n).put("amount",v));check(p.edit().putString(FIN_OTHER,a.toString()).commit())}
private fun dinar(v:Double)=String.format(Locale.US,"%,.0f دج",v)
private fun invoiceTime(s:String)=runCatching{LocalDateTime.parse(s).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))}.getOrDefault(s)

private val medicineGroups=linkedMapOf(
 "اللقاحات والتطعيمات" to listOf("لقاح التسمم المعوي","لقاح الجدري","لقاح الحمى القلاعية","لقاح طاعون المجترات الصغيرة PPR","لقاح الكلوستريديا","لقاح البروسيلا"),
 "مضادات الطفيليات الداخلية" to listOf("ألبيندازول (Albendazole)","فينبندازول (Fenbendazole)","إيفرمكتين (Ivermectin)","ليفاميزول (Levamisole)","تريكلابندازول"),
 "الجروح والعناية" to listOf("بخاخات الجروح","مطهرات الحوافر","مستحضرات التهابات الضرع"),
 "الجهاز الهضمي والجفاف" to listOf("محاليل الأملاح والسوائل","التولترازوريل (Toltrazuril)","مستحضرات علاج النفاخ","محاليل علاج الإسهال"),
 "التكاثر والولادة" to listOf("أوكسيتوسين (Oxytocin)","البروستاغلاندين","مستحضرات الكالسيوم","البروبيلين غليكول"),
 "رعاية الحملان" to listOf("العناية بسرة الحملان","بدائل اللبأ")
)
private enum class FinancePage{HOME,INVOICES,REPORTS,PURCHASES}
private enum class PurchaseType(val title:String){ANIMAL("شراء القطيع"),FEED("شراء الأعلاف"),MEDICINE("شراء الأدوية")}
private enum class SaleType(val title:String){ANIMAL("بيع رؤوس الأغنام"),WOOL("بيع الصوف"),FEED("بيع الأعلاف"),MEDICINE("بيع الأدوية")}

@Composable
fun FinanceScreen(modifier:Modifier=Modifier){
 val c=LocalContext.current
 var page by rememberSaveable{mutableStateOf(FinancePage.HOME)}
 var list by remember{mutableStateOf(readInvoices(c))}
 var herd by remember{mutableStateOf(loadHerd(c))}
 var feeds by remember{mutableStateOf(loadFeedStocks(c))}
 var meds by remember{mutableStateOf(readMedicineStock(c))}
 var wool by remember{mutableDoubleStateOf(readWool(c))}
 var sale by remember{mutableStateOf(false)}
 var otherType by remember{mutableStateOf("")}
 var notice by remember{mutableStateOf<String?>(null)}
 fun refresh(){list=readInvoices(c);herd=loadHerd(c);feeds=loadFeedStocks(c);meds=readMedicineStock(c);wool=readWool(c)}
 val sales=list.filter{it.type=="sale"};val purchases=list.filter{it.type=="purchase"}
 val extra=otherTotals(c);val revenue=sales.sumOf{it.total}+extra.first;val cogs=sales.sumOf{it.cost};val net=revenue-cogs-extra.second
 Column(modifier.fillMaxSize().background(FinCream).padding(horizontal=16.dp)){
  Spacer(Modifier.height(12.dp))
  Row(verticalAlignment=Alignment.CenterVertically){
   if(page!=FinancePage.HOME)IconButton(onClick={page=FinancePage.HOME}){Icon(Icons.Outlined.ArrowBack,"رجوع",tint=FinGreen)}
   Column(Modifier.weight(1f)){Text(if(page==FinancePage.PURCHASES)"متجر المزرعة" else "المالية",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=FinInk);Text(if(page==FinancePage.PURCHASES)"أقسام منظمة لاحتياجات المزرعة" else "المشتريات والمبيعات والفواتير والأرباح",fontSize=11.sp,color=FinMuted)}
   Icon(Icons.Outlined.AccountBalanceWallet,null,tint=FinGreen,modifier=Modifier.size(28.dp))
  }
  if(page==FinancePage.PURCHASES){ PurchaseScreen(c,herd,feeds,meds,{page=FinancePage.HOME}){notice=it;refresh();page=FinancePage.HOME} }else if(page==FinancePage.HOME){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=22.dp)){
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("إجمالي المداخيل",dinar(revenue),Icons.Outlined.TrendingUp,Modifier.weight(1f));FinMetric("تكلفة الأصناف المباعة",dinar(cogs),Icons.Outlined.Inventory2,Modifier.weight(1f))}}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("صافي الأرباح",dinar(net.coerceAtLeast(0.0)),Icons.Outlined.AccountBalance,Modifier.weight(1f));FinMetric("الخسائر",dinar((-net).coerceAtLeast(0.0)+extra.second),Icons.Outlined.TrendingDown,Modifier.weight(1f))}}
    item{Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("النتيجة المالية",fontWeight=FontWeight.Bold,color=FinInk);Text("الإيرادات − تكلفة المباع − المصاريف",fontSize=9.sp,color=FinMuted)};Text(dinar(net),fontWeight=FontWeight.ExtraBold,color=if(net>=0)FinGreen else Color(0xFFB54D42),fontSize=17.sp)}}}
    item{Text("إدارة العمليات",fontSize=16.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinAction("متجر المزرعة","تسوّق احتياجات المزرعة",Icons.Outlined.Storefront,Modifier.weight(1f)){page=FinancePage.PURCHASES};FinAction("المبيعات","القطيع والصوف والمخزون",Icons.Outlined.PointOfSale,Modifier.weight(1f)){sale=true}}}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinAction("الفواتير",list.size.toString()+" فاتورة",Icons.Outlined.ReceiptLong,Modifier.weight(1f)){page=FinancePage.INVOICES};FinAction("التقارير","تحليل النشاط",Icons.Outlined.BarChart,Modifier.weight(1f)){page=FinancePage.REPORTS}}}
    item{Text("المخزون المرتبط تلقائياً",fontSize=15.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("رؤوس القطيع",herd.size.toString()+" رأس",Icons.Outlined.Pets,Modifier.weight(1f));FinMetric("مخزون العلف",String.format(Locale.US,"%.1f كغ",feeds.values.sum()),Icons.Outlined.Inventory2,Modifier.weight(1f))}}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("أصناف الأدوية",meds.size.toString(),Icons.Outlined.MedicalServices,Modifier.weight(1f));FinMetric("الصوف",String.format(Locale.US,"%.1f كغ",wool),Icons.Outlined.Spa,Modifier.weight(1f))}}
    item{Text("عمليات مالية أخرى",fontSize=14.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={otherType="income"},modifier=Modifier.weight(1f)){Text("إضافة إيراد")};OutlinedButton(onClick={otherType="expense"},modifier=Modifier.weight(1f)){Text("إضافة مصروف")}}}
    item{Text("أحدث الفواتير",fontSize=14.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    items(list.take(4),key={it.id}){v->InvoiceRow(v){printFinanceInvoice(c,v)}}
    if(list.isEmpty())item{EmptyFinance("لا توجد فواتير بعد","ستظهر هنا فواتير الشراء والبيع")}
   }
  }else if(page==FinancePage.INVOICES){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=20.dp)){
    item{Text("سجل الفواتير مرتبة حسب التاريخ والساعة",fontWeight=FontWeight.Bold,color=FinInk,fontSize=15.sp)}
    items(list,key={it.id}){v->InvoiceRow(v){printFinanceInvoice(c,v)}}
    if(list.isEmpty())item{EmptyFinance("لا توجد فواتير","تُحفظ فواتير الشراء والبيع تلقائياً")}
   }
  }else{
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=20.dp)){
    item{Text("التقارير المالية",fontWeight=FontWeight.Bold,color=FinInk,fontSize=16.sp)}
    item{FinReport("إجمالي المبيعات",dinar(sales.sumOf{it.total}),sales.size.toString()+" فاتورة")}
    item{FinReport("تكلفة الأصناف المباعة",dinar(cogs),"وفق متوسط تكلفة الشراء المسجل")}
    item{FinReport("إجمالي المشتريات",dinar(purchases.sumOf{it.total}),purchases.size.toString()+" فاتورة")}
    item{FinReport("الإيرادات الأخرى",dinar(extra.first),"")}
    item{FinReport("المصاريف التشغيلية",dinar(extra.second),"")}
    item{FinReport("صافي النتيجة",dinar(net),if(net>=0)"فائض مالي" else "عجز مالي")}
    sales.groupBy{it.category}.forEach{(k,v)->item{FinReport(k,dinar(v.sumOf{it.total}),v.size.toString()+" عملية بيع")}}
   }
  }
 }
 if(sale)SaleDialog(c,herd,feeds,meds,wool,{sale=false;refresh()},{notice=it;sale=false;refresh()})
 if(otherType.isNotBlank())OtherDialog(otherType,{otherType=""}){name,amount->addOther(c,otherType,name,amount);otherType="";refresh()}
 notice?.let{AlertDialog(onDismissRequest={notice=null},title={Text("تم تسجيل العملية")},text={Text(it)},confirmButton={TextButton(onClick={notice=null}){Text("حسناً")}})}
}

@Composable private fun PurchaseScreen(c:Context,herd:List<HerdAnimal>,feeds:Map<FeedKind,Double>,meds:Map<String,Double>,back:()->Unit,done:(String)->Unit){
 var type by remember{mutableStateOf<PurchaseType?>(null)}
 var supplier by rememberSaveable{mutableStateOf("")};var item by rememberSaveable{mutableStateOf("نعجة")};var qty by rememberSaveable{mutableStateOf("1")};var price by rememberSaveable{mutableStateOf("")};var weight by rememberSaveable{mutableStateOf("")};var age by rememberSaveable{mutableStateOf("12")};var health by rememberSaveable{mutableStateOf("جيدة")};var state by rememberSaveable{mutableStateOf("نعجة جافة")};var batch by rememberSaveable{mutableStateOf("")};var expiry by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")}
 val feedNames=FeedKind.values().map{it.label};val drugs=medicineGroups.values.flatten()
 val options=when(type){PurchaseType.ANIMAL->listOf("نعجة","كبش","حمل/رخلة");PurchaseType.FEED->feedNames;PurchaseType.MEDICINE->drugs;null->emptyList()}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=25.dp)){
  item{Surface(color=FinGreen,shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("متجر مزرعتك في مكان واحد",fontSize=19.sp,fontWeight=FontWeight.ExtraBold,color=Color.White);Text("اختر القسم ثم أضف مشترياتك إلى سجل المزرعة",fontSize=10.sp,color=Color.White.copy(alpha=.88f))};Icon(Icons.Outlined.ShoppingBasket,null,tint=Color.White,modifier=Modifier.size(38.dp))}}}
  item{Text(if(type==null)"تصفح أقسام المتجر" else "تفاصيل الشراء",fontSize=16.sp,fontWeight=FontWeight.Bold,color=FinInk)}
  if(type==null){
   item{PurchaseCategoryCard("القطيع","شراء رؤوس الأغنام","نعاج • كباش • حملان",Icons.Outlined.Pets,herd.size.toString()+" رأس"){type=PurchaseType.ANIMAL;item="نعجة"}}
   item{PurchaseCategoryCard("الأعلاف","شراء الأعلاف","تُضاف الكمية مباشرة إلى مخزون العلف",Icons.Outlined.Inventory2,String.format(Locale.US,"%.1f كغ",feeds.values.sum())){type=PurchaseType.FEED;item=feedNames.first()}}
   item{PurchaseCategoryCard("الأدوية واللقاحات","المستلزمات البيطرية","تسجيل التشغيلة وتاريخ الصلاحية",Icons.Outlined.Vaccines,meds.size.toString()+" صنف"){type=PurchaseType.MEDICINE;item=drugs.first()}}
   item{Surface(color=Color.White,shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,FinLine)){Column(Modifier.padding(14.dp)){Text("سلة المشتريات",fontWeight=FontWeight.Bold,color=FinInk);Text("بعد اختيار القسم، أدخل المورد والكمية والسعر. سيظهر الإجمالي قبل تأكيد الفاتورة.",fontSize=11.sp,color=FinMuted,lineHeight=17.sp)}}}
  }else{
   item{Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={type=null;error=""}){Icon(Icons.Outlined.ArrowForward,null);Text("كل الأقسام")};Spacer(Modifier.weight(1f));Text(type!!.title,color=FinGreen,fontWeight=FontWeight.Bold,fontSize=12.sp)}}
   item{Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,FinLine)){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
    Text("بيانات المورد",fontWeight=FontWeight.Bold,color=FinInk);OutlinedTextField(supplier,{supplier=it},label={Text("اسم المورد / البائع")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Text("الصنف والكمية",fontWeight=FontWeight.Bold,color=FinInk);var open by remember{mutableStateOf(false)}
    Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text(item);Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(open,{open=false}){options.forEach{o->DropdownMenuItem(text={Text(o)},onClick={item=o;open=false})}}}
    OutlinedTextField(qty,{qty=it.filter{ch->ch in "0123456789.,"}},label={Text(if(type==PurchaseType.ANIMAL)"عدد الرؤوس" else "الكمية")},keyboardOptions=KeyboardOptions(keyboardType=if(type==PurchaseType.ANIMAL)KeyboardType.Number else KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(price,{price=it.filter{ch->ch in "0123456789.,"}},label={Text("سعر الوحدة بالدينار الجزائري")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true)
    if(type==PurchaseType.ANIMAL){Divider(color=FinLine);Text("بيانات الحيوان",fontWeight=FontWeight.Bold,color=FinInk);OutlinedTextField(weight,{weight=it},label={Text("الوزن بالكيلوغرام")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());OutlinedTextField(age,{age=it},label={Text("العمر بالأشهر")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Text("الحالة الصحية",fontSize=11.sp,color=FinMuted);Row{listOf("جيدة","متوسطة","تحتاج متابعة").forEach{FilterChip(health==it,{health=it},label={Text(it,fontSize=9.sp)})}};Text("الحالة الإنتاجية",fontSize=11.sp,color=FinMuted);Column{listOf("نعجة جافة","نعجة حامل","نعجة مرضعة","كبش","خروف").forEach{FilterChip(state==it,{state=it},label={Text(it,fontSize=10.sp)})}}}
    if(type==PurchaseType.MEDICINE){Divider(color=FinLine);Text("بيانات الدواء",fontWeight=FontWeight.Bold,color=FinInk);OutlinedTextField(batch,{batch=it},label={Text("رقم التشغيلة")},modifier=Modifier.fillMaxWidth());OutlinedTextField(expiry,{expiry=it},label={Text("تاريخ الصلاحية YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())}
   }}}
   item{val q=qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0;Surface(color=Color(0xFFEAF1E9),shape=RoundedCornerShape(16.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("إجمالي السلة",fontSize=11.sp,color=FinMuted);Text(dinar(q*p),fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=FinGreen)};Icon(Icons.Outlined.ReceiptLong,null,tint=FinGreen,modifier=Modifier.size(28.dp))}}}
   if(error.isNotBlank())item{Text(error,color=Color(0xFFB54D42),fontSize=11.sp)}
   item{Button(onClick={
    val t=type?:return@Button;val raw=qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0;val count=raw.toInt()
    if(supplier.isBlank())error="أدخل اسم المورد" else if(raw<=0||raw>100000||p<=0)error="تحقق من الكمية والسعر" else if(t==PurchaseType.ANIMAL&&(raw!=count.toDouble()||count !in 1..500))error="عدد الرؤوس يجب أن يكون صحيحاً من 1 إلى 500" else if(t==PurchaseType.ANIMAL&&(weight.replace(',','.').toDoubleOrNull()?:0.0)<=0)error="أدخل وزن الرأس" else if(t==PurchaseType.MEDICINE&&batch.isBlank())error="أدخل رقم التشغيلة" else{
     val now=LocalDateTime.now().toString();val id="PUR-"+now.filter{it.isDigit()}.take(14)+"-"+UUID.randomUUID().toString().take(4).uppercase();val q=if(t==PurchaseType.ANIMAL)count.toDouble() else raw;val total=q*p;var detail=""
     when(t){
      PurchaseType.ANIMAL->{val tags=generateHerdTags(herd.map{it.tag},count);val kg=weight.replace(',','.').toDouble();val updated=herd.toMutableList();tags.forEach{tag->updated.add(HerdAnimal(UUID.randomUUID().toString(),tag,item,age.toIntOrNull()?:0,health,"شراء "+id,kg,productionState=if(item=="كبش")"كبش" else if(item=="حمل/رخلة")"خروف" else state))};saveHerd(c,updated);detail="أضيفت "+count+" رؤوس للقطيع بالمعرفات "+tags.first()+" إلى "+tags.last()+"."}
      PurchaseType.FEED->{val k=FeedKind.values().first{it.label==item};val stock=loadFeedStocks(c);stock[k]=(stock[k]?:0.0)+q;saveFeedStocks(c,stock);detail="أضيفت "+String.format(Locale.US,"%.2f",q)+" كغ إلى مخزون "+k.label+"."}
      PurchaseType.MEDICINE->{val stock=readMedicineStock(c);stock[item]=(stock[item]?:0.0)+q;writeMedicineStock(c,stock);detail="أضيف الدواء إلى قسم الأدوية. التشغيلة: "+batch+"، الصلاحية: "+expiry.ifBlank{"غير محددة"}+"."}
     }
     writeInvoice(c,FinanceInvoice(id,"purchase",t.title,supplier,item,q,if(t==PurchaseType.ANIMAL)"رأس" else if(t==PurchaseType.FEED)"كغ" else "وحدة",p,total,0.0,now,detail));done("تم حفظ الفاتورة "+id+" بقيمة "+dinar(total)+". "+detail)
    }
   },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FinGreen),shape=RoundedCornerShape(14.dp)){Icon(Icons.Outlined.CheckCircle,null);Spacer(Modifier.width(8.dp));Text("تأكيد الشراء وحفظ الفاتورة",fontWeight=FontWeight.Bold)}}
  }
 }
}
@Composable private fun PurchaseCategoryCard(title:String,subtitle:String,description:String,icon:androidx.compose.ui.graphics.vector.ImageVector,metric:String,onClick:()->Unit){
 Surface(color=Color.White,shape=RoundedCornerShape(19.dp),border=BorderStroke(1.dp,FinLine),modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)){
  Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Surface(color=Color(0xFFEAF1E9),shape=RoundedCornerShape(15.dp)){Icon(icon,null,tint=FinGreen,modifier=Modifier.padding(13.dp).size(29.dp))};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,fontSize=15.sp,fontWeight=FontWeight.ExtraBold,color=FinInk);Text(subtitle,fontSize=10.sp,color=FinGreen,fontWeight=FontWeight.SemiBold);Text(description,fontSize=10.sp,color=FinMuted)};Column(horizontalAlignment=Alignment.End){Text(metric,fontSize=11.sp,fontWeight=FontWeight.Bold,color=FinGreen);Icon(Icons.Outlined.ChevronLeft,null,tint=FinMuted)}}
 }
}
@Composable private fun SaleDialog(c:Context,herd:List<HerdAnimal>,feed:Map<FeedKind,Double>,medicine:Map<String,Double>,wool:Double,dismiss:()->Unit,done:(String)->Unit){
 var type by remember{mutableStateOf(SaleType.ANIMAL)};var buyer by rememberSaveable{mutableStateOf("")};var item by rememberSaveable{mutableStateOf(herd.firstOrNull()?.tag?:"")};var qty by rememberSaveable{mutableStateOf("1")};var price by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")}
 val options=when(type){SaleType.ANIMAL->herd.map{it.tag};SaleType.WOOL->listOf("الصوف");SaleType.FEED->feed.filterValues{it>0}.keys.map{it.label};SaleType.MEDICINE->medicine.filterValues{it>0}.keys.toList()}
 AlertDialog(onDismissRequest=dismiss,title={Text("فاتورة بيع")},text={LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  item{Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){SaleType.values().forEach{t->FilterChip(type==t,{type=t;item=when(t){SaleType.ANIMAL->herd.firstOrNull()?.tag?:"";SaleType.WOOL->"الصوف";SaleType.FEED->feed.keys.firstOrNull()?.label?:"";SaleType.MEDICINE->medicine.keys.firstOrNull()?:""};qty="1"},label={Text(t.title,fontSize=8.sp)})}}}
  item{OutlinedTextField(buyer,{buyer=it},label={Text("اسم المشتري")},modifier=Modifier.fillMaxWidth())}
  item{var open by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={open=true},enabled=options.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(item.ifBlank{"لا توجد أصناف متاحة"});Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(open,{open=false}){options.forEach{o->DropdownMenuItem(text={Text(o)},onClick={item=o;open=false})}}}}
  if(type!=SaleType.ANIMAL)item{OutlinedTextField(qty,{qty=it},label={Text("الكمية المراد بيعها")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
  item{OutlinedTextField(price,{price=it},label={Text("سعر الوحدة بالدينار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
  item{Text(when(type){SaleType.ANIMAL->"يُباع الحيوان المختار من رقمه التعريفي.";SaleType.WOOL->"الرصيد المتاح: "+wool+" كغ";SaleType.FEED->"الرصيد المتاح: "+(feed[FeedKind.values().firstOrNull{it.label==item}]?:0.0)+" كغ";SaleType.MEDICINE->"الرصيد المتاح: "+(medicine[item]?:0.0)+" وحدة"},fontSize=10.sp,color=FinMuted)}
  if(error.isNotBlank())item{Text(error,color=Color.Red,fontSize=11.sp)}
 }},confirmButton={TextButton(onClick={
  val q=if(type==SaleType.ANIMAL)1.0 else qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0
  if(buyer.isBlank())error="أدخل اسم المشتري" else if(options.isEmpty()||item.isBlank())error="لا توجد كمية متاحة" else if(q<=0||p<=0)error="تحقق من الكمية والسعر"
  else{
   var available=0.0;var costUnit=0.0;var detail=""
   when(type){
    SaleType.ANIMAL->{val a=herd.firstOrNull{it.tag==item};available=if(a==null)0.0 else 1.0;val bought=readInvoices(c).filter{it.type=="purchase"&&it.category==PurchaseType.ANIMAL.title&&it.item==a?.kind};costUnit=if(bought.isEmpty())0.0 else bought.sumOf{it.total}/bought.sumOf{it.quantity}}
    SaleType.WOOL->available=wool
    SaleType.FEED->{val k=FeedKind.values().firstOrNull{it.label==item};available=feed[k]?:0.0;costUnit=averagePurchaseCost(c,item,PurchaseType.FEED.title)}
    SaleType.MEDICINE->{available=medicine[item]?:0.0;costUnit=averagePurchaseCost(c,item,PurchaseType.MEDICINE.title)}
   }
   if(q>available+1e-8)error="الرصيد غير كافٍ. المتاح: "+available
   else{
    when(type){
     SaleType.ANIMAL->{val animal=herd.first{it.tag==item};val archiveId="SOLD-"+UUID.randomUUID().toString();archiveSoldAnimal(c,animal,archiveId);saveHerd(c,herd.filterNot{it.tag==item});detail="بيع الحيوان "+item+" وأُرشف ملفه التاريخي برقم "+archiveId+"."}
     SaleType.WOOL->writeWool(c,wool-q)
     SaleType.FEED->{val k=FeedKind.values().first{it.label==item};val s=loadFeedStocks(c);s[k]=(s[k]?:0.0)-q;saveFeedStocks(c,s)}
     SaleType.MEDICINE->{val s=readMedicineStock(c);s[item]=(s[item]?:0.0)-q;writeMedicineStock(c,s)}
    }
    val now=LocalDateTime.now().toString();val id="SAL-"+now.filter{it.isDigit()}.take(14)+"-"+UUID.randomUUID().toString().take(4).uppercase();val total=q*p
    writeInvoice(c,FinanceInvoice(id,"sale",type.title,buyer,item,q,if(type==SaleType.ANIMAL)"رأس" else if(type==SaleType.WOOL||type==SaleType.FEED)"كغ" else "وحدة",p,total,q*costUnit,now,detail))
    done("حُفظت فاتورة البيع "+id+" بقيمة "+dinar(total)+". تم خصم الكمية تلقائياً من القسم المعني.")
   }
  }
 }){Text("تأكيد البيع")}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}

private const val SOLD_ANIMALS_KEY = "sold_animals_history"
private fun archiveSoldAnimal(c:Context, animal:HerdAnimal, invoiceId:String){
 val p=c.getSharedPreferences(FIN_PREFS,0);val a=JSONArray(p.getString(SOLD_ANIMALS_KEY,"[]")?:"[]")
 a.put(JSONObject().apply{
  put("invoiceId",invoiceId);put("id",animal.id);put("tag",animal.tag);put("kind",animal.kind)
  put("ageMonths",animal.ageMonths);put("health",animal.health);put("notes",animal.notes)
  if(animal.weightKg==null)put("weightKg",JSONObject.NULL) else put("weightKg",animal.weightKg)
  put("birthDate",animal.birthDate);put("birthWeightKg",animal.birthWeightKg?:JSONObject.NULL)
  put("motherTag",animal.motherTag);put("productionState",animal.productionState)
  put("pregnancyStage",animal.pregnancyStage);put("fetusCount",animal.fetusCount);put("nursingLambs",animal.nursingLambs)
  put("soldAt",LocalDateTime.now().toString())
 })
 check(p.edit().putString(SOLD_ANIMALS_KEY,a.toString()).commit())
}

private fun averagePurchaseCost(c:Context,item:String,category:String):Double{val a=readInvoices(c).filter{it.type=="purchase"&&it.category==category&&it.item==item&&it.quantity>0};val q=a.sumOf{it.quantity};return if(q<=0)0.0 else a.sumOf{it.total}/q}

@Composable private fun OtherDialog(type:String,dismiss:()->Unit,save:(String,Double)->Unit){
 var name by rememberSaveable{mutableStateOf("")};var amount by rememberSaveable{mutableStateOf("")}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(type=="income")"إضافة إيراد" else "إضافة مصروف")},text={Column{OutlinedTextField(name,{name=it},label={Text("البيان")});OutlinedTextField(amount,{amount=it},label={Text("المبلغ بالدينار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}},confirmButton={TextButton(onClick={val v=amount.replace(',','.').toDoubleOrNull();if(name.isNotBlank()&&v!=null&&v>0)save(name,v)}){Text("حفظ")}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}

private data class MedicineInfo(val name:String,val group:String,val scientific:String,val purpose:String,val active:String,val concentration:String,val storage:String,val warnings:String,val withdrawal:String)
private fun medicineInfo(name:String,group:String):MedicineInfo{
 val data=when(name){
  "لقاح التسمم المعوي"->Triple("لقاح مضاد لمرض التسمم المعوي","مستضدات سموم المطثيات، يختلف تركيبها حسب المنتج","الوقاية ضمن برنامج تحصين يحدده الطبيب البيطري")
  "لقاح الجدري"->Triple("لقاح جدري الأغنام","فيروس جدري الأغنام المضعّف أو مستحضر معطل حسب المنتج","الوقاية من جدري الأغنام؛ نوع اللقاح وبرنامج التحصين حسب النشرة")
  "لقاح الحمى القلاعية"->Triple("لقاح الحمى القلاعية","مستضدات فيروس الحمى القلاعية للأنماط المصلية المحددة على العبوة","الوقاية وفق النمط المصلي المتداول والبرنامج البيطري الرسمي")
  "لقاح طاعون المجترات الصغيرة PPR"->Triple("لقاح طاعون المجترات الصغيرة","مستضد فيروس PPR؛ غالباً لقاح حي مضعّف بحسب المنتج","الوقاية من طاعون المجترات الصغيرة وفق البرنامج البيطري")
  "لقاح الكلوستريديا"->Triple("لقاح الكلوستريديا","ذوفانات/مستضدات كلوستريدية متعددة بحسب التركيبة","الوقاية من أمراض كلوستريدية محددة وفق تركيب اللقاح")
  "لقاح البروسيلا"->Triple("لقاح البروسيلا","سلالة لقاحية معتمدة حسب البرنامج الرسمي والمنتج","لا يستخدم إلا وفق تعليمات السلطات البيطرية والطبيب؛ توجد احتياطات خاصة")
  "ألبيندازول (Albendazole)"->Triple("Albendazole","ألبيندازول","مضاد لبعض الديدان الداخلية؛ لا يغطي كل الطفيليات")
  "فينبندازول (Fenbendazole)"->Triple("Fenbendazole","فينبندازول","مضاد لبعض الديدان المعوية والرئوية بحسب الطفيلي والمنتج")
  "إيفرمكتين (Ivermectin)"->Triple("Ivermectin","إيفرمكتين","مضاد لبعض الطفيليات الداخلية والخارجية؛ لا يعالج جميع أنواع الديدان")
  "ليفاميزول (Levamisole)"->Triple("Levamisole","ليفاميزول","مضاد لبعض الديدان الأسطوانية؛ هامش الأمان يتطلب دقة بيطرية")
  "تريكلابندازول"->Triple("Triclabendazole","تريكلابندازول","مضاد للفاشيولا (ديدان الكبد) في مراحل محددة حسب المنتج")
  "بخاخات الجروح"->Triple("مستحضرات موضعية للجروح","تختلف المواد المطهرة أو المضادة للعدوى حسب العلامة","تنظيف/حماية موضعية للجروح السطحية وفق النشرة؛ لا تغني عن فحص الجروح العميقة")
  "مطهرات الحوافر"->Triple("مطهرات بيطرية للحوافر","مواد مطهرة مختلفة بحسب التركيبة","العناية بنظافة الحافر والحد من التلوث؛ العرج يحتاج تشخيصاً")
  "مستحضرات التهابات الضرع"->Triple("مستحضرات بيطرية للضرع","تختلف؛ قد تتضمن مضاداً حيوياً أو مطهراً بحسب المنتج","لا تستخدم لعلاج التهاب الضرع دون تشخيص وخطة طبيب بيطري")
  "محاليل الأملاح والسوائل"->Triple("محاليل تعويض السوائل والأملاح","إلكتروليتات وأملاح وسكريات بنسب تختلف حسب المنتج","دعم تعويض السوائل في حالات محددة؛ الجفاف الشديد حالة طارئة")
  "التولترازوريل (Toltrazuril)"->Triple("Toltrazuril","تولترازوريل","مضاد للأوالي يُستخدم ضد الكوكسيديا في ظروف محددة")
  "مستحضرات علاج النفاخ"->Triple("مستحضرات بيطرية للنفاخ","تختلف المادة والتركيز حسب المنتج","النفاخ الحاد قد يهدد الحياة ويحتاج تدخلاً بيطرياً عاجلاً")
  "محاليل علاج الإسهال"->Triple("محاليل دعم حالات الإسهال","إلكتروليتات ومكونات داعمة تختلف حسب المنتج","تعويض السوائل والأملاح؛ لا تعالج سبب الإسهال بحد ذاتها")
  "أوكسيتوسين (Oxytocin)"->Triple("Oxytocin","أوكسيتوسين","دواء هرموني يؤثر في تقلص الرحم وإدرار الحليب؛ لا يستخدم إلا بقرار بيطري")
  "البروستاغلاندين"->Triple("Prostaglandin","نظائر البروستاغلاندين؛ المادة المحددة حسب المستحضر","تأثيرات تناسلية/رحمية محددة؛ ممنوع الاستخدام العشوائي")
  "مستحضرات الكالسيوم"->Triple("مستحضرات الكالسيوم","أملاح كالسيوم مختلفة حسب المنتج","تستخدم في حالات نقص الكالسيوم المشخصة؛ طريقة الإعطاء مهمة وخطرة عند الخطأ")
  "البروبيلين غليكول"->Triple("Propylene glycol","بروبيلين غليكول","مصدر طاقة في حالات استقلابية محددة؛ الكمية والملاءمة يحددهما الطبيب")
  "العناية بسرة الحملان"->Triple("مطهرات سرة الحملان","مطهرات موضعية تختلف حسب المنتج","تعقيم السرة بعد الولادة للحد من دخول العدوى")
  "بدائل اللبأ"->Triple("بديل اللبأ للحملان","مسحوق/مستحضر غذائي يحتوي مكونات مناعية بحسب المنتج","بديل عند تعذر الحصول على لبأ الأم؛ ليس كل بديل حليب بديلاً للبأ")
  else->Triple(name,"المكونات الفعالة تختلف باختلاف الشركة والتركيبة","يستخدم فقط للغرض المكتوب على النشرة البيطرية")
 }
 val concentration=when(name){
  "ألبيندازول (Albendazole)"->"تختلف النسبة والتركيز حسب الشكل التجاري؛ تحقق من الملصق"
  "فينبندازول (Fenbendazole)"->"تختلف النسبة والتركيز حسب الشكل التجاري؛ تحقق من الملصق"
  "إيفرمكتين (Ivermectin)"->"توجد تراكيز وأشكال متعددة؛ لا تفترض أن المستحضرات متكافئة"
  "ليفاميزول (Levamisole)"->"التركيز يختلف بين المستحضرات؛ راجع العبوة بدقة"
  "تريكلابندازول"->"يختلف التركيز حسب المستحضر التجاري"
  "التولترازوريل (Toltrazuril)"->"يختلف التركيز والشكل الصيدلاني حسب المنتج"
  "أوكسيتوسين (Oxytocin)"->"يكتب بوحدات دولية على العبوة؛ التركيز حسب المنتج"
  else->"غير موحد؛ يُرجع إلى ملصق المنتج ونشرته"
 }
 val storage=if(group=="اللقاحات والتطعيمات")"اتبع سلسلة التبريد المحددة على العبوة؛ كثير من اللقاحات تحفظ مبردة ولا تُجمّد. لا تستخدم عبوة تعرضت لخلل تخزين دون استشارة." else "يُحفظ حسب النشرة، بعيداً عن الحرارة والرطوبة والضوء عند الحاجة، وبعيداً عن متناول الأطفال."
 val warning=if(name=="لقاح البروسيلا")"قد يشكل خطراً على الإنسان، خصوصاً الحوامل وذوي المناعة الضعيفة. لا يُتعامل معه إلا بواسطة مختص ووفق البرنامج الرسمي." else if(name=="أوكسيتوسين (Oxytocin)"||name=="البروستاغلاندين")"أدوية هرمونية ذات موانع استعمال ومخاطر؛ لا تستخدم لتحفيز الولادة أو التناسل دون فحص وقرار بيطري." else "لا تحدد الجرعة أو طريقة الإعطاء من اسم المادة وحده. راجع الطبيب البيطري ونشرة المنتج المسجل، وتحقق من ملاءمته للأغنام والحمل والرضاعة."
 return MedicineInfo(name,group,data.first,data.third,data.second,concentration,storage,warning,"فترة السحب للحوم والحليب تختلف باختلاف المادة والتركيز والشركة والجرعة المعتمدة. اتبع النشرة المحلية ولا تعتمد رقماً عاماً؛ دوّن تاريخ آخر معالجة.")
}


private const val HERD_CARE_PREFS="mawashi_herd_care_v27"
private const val HERD_CARE_KEY="scheduled_care"
private data class HerdCareReminder(val id:String,val tag:String,val kind:String,val title:String,val date:String,val notes:String,val completed:Boolean=false)
private fun readHerdCare(c:Context):List<HerdCareReminder>{
 val a=JSONArray(c.getSharedPreferences(HERD_CARE_PREFS,0).getString(HERD_CARE_KEY,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);HerdCareReminder(x.optString("id"),x.optString("tag"),x.optString("kind"),x.optString("title"),x.optString("date"),x.optString("notes"),x.optBoolean("completed"))}.getOrNull()}
}
private fun saveHerdCare(c:Context,list:List<HerdCareReminder>){
 val a=JSONArray();list.forEach{x->a.put(JSONObject().put("id",x.id).put("tag",x.tag).put("kind",x.kind).put("title",x.title).put("date",x.date).put("notes",x.notes).put("completed",x.completed))}
 check(c.getSharedPreferences(HERD_CARE_PREFS,0).edit().putString(HERD_CARE_KEY,a.toString()).commit())
}
private fun herdAgeLabel(a:HerdAnimal):String{
 val months=runCatching{if(a.birthDate.isNotBlank())ChronoUnit.MONTHS.between(LocalDate.parse(a.birthDate),LocalDate.now()).toInt().coerceAtLeast(0) else a.ageMonths}.getOrDefault(a.ageMonths)
 return if(months<1)"أقل من شهر" else if(months<12)"$months شهر" else "\${months/12} سنة و \${months%12} شهر"
}
@Composable private fun HerdHealthMonitor(onBack:()->Unit){
 val context=LocalContext.current
 var animals by remember{mutableStateOf(loadHerd(context))}
 var reminders by remember{mutableStateOf(readHerdCare(context))}
 var selectedTag by rememberSaveable{mutableStateOf<String?>(null)}
 var dialog by remember{mutableStateOf(false)}
 var kind by remember{mutableStateOf("تطعيم")}
 var title by remember{mutableStateOf("")}
 var date by remember{mutableStateOf(LocalDate.now().plusDays(7).toString())}
 var notes by remember{mutableStateOf("")}
 val selected=animals.firstOrNull{it.tag==selectedTag}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   TextButton(onClick=onBack){Icon(Icons.Outlined.ArrowForward,null);Text("العودة إلى دليل الأدوية")}
   Spacer(Modifier.weight(1f))
   Text("متابعة القطيع",fontWeight=FontWeight.ExtraBold,color=FinGreen,fontSize=15.sp)
  }
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
   FinMetric("إجمالي الرؤوس",animals.size.toString(),Icons.Outlined.Pets,Modifier.weight(1f))
   FinMetric("مواعيد مسجلة",reminders.count{!it.completed}.toString(),Icons.Outlined.Event,Modifier.weight(1f))
  }
  if(selected==null){
   Text("اختر الحيوان لمتابعة ملفه الصحي والإنتاجي",fontWeight=FontWeight.Bold,color=FinInk,fontSize=13.sp)
   if(animals.isEmpty())EmptyFinance("لا توجد حيوانات في قسم القطيع","أضف الحيوانات أولاً من قسم القطيع لتظهر هنا")
   LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    items(animals,key={it.id}){a->
     Surface(color=Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,FinLine),modifier=Modifier.fillMaxWidth().clickable{selectedTag=a.tag}){
      Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Outlined.Pets,null,tint=FinGreen,modifier=Modifier.size(26.dp))
       Spacer(Modifier.width(10.dp))
       Column(Modifier.weight(1f)){Text(a.tag+" • "+a.kind,fontWeight=FontWeight.Bold,color=FinInk,fontSize=12.sp);Text("العمر: "+herdAgeLabel(a)+"  |  الوزن: "+(a.weightKg?.let{"%.1f كغ".format(Locale.US,it)}?:"غير مسجل"),fontSize=10.sp,color=FinMuted);Text("الحالة: "+a.health+" • "+a.productionState,fontSize=10.sp,color=FinGreen)}
       Icon(Icons.Outlined.ChevronLeft,null,tint=FinMuted)
      }
     }
    }
   }
  }else{
   TextButton(onClick={selectedTag=null}){Icon(Icons.Outlined.ArrowBack,null);Text("كل القطيع")}
   Surface(color=Color.White,shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,FinLine)){
    Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text(selected.tag+" • "+selected.kind,fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=17.sp)
     Text("العمر: "+herdAgeLabel(selected)+"   |   الوزن الحالي: "+(selected.weightKg?.let{"%.1f كغ".format(Locale.US,it)}?:"غير مسجل"),fontSize=11.sp,color=FinMuted)
     Text("الحالة الصحية المسجلة: "+selected.health,fontSize=11.sp,color=FinInk)
     Text("الحالة الإنتاجية: "+selected.productionState+" • "+selected.pregnancyStage,fontSize=11.sp,color=FinInk)
     if(selected.kind.contains("حمل")||selected.ageMonths<6){
      Text("متابعة المواليد: راقب الرضاعة وتناول اللبأ في الساعات الأولى، وسجّل وزن الميلاد ثم أعد الوزن دورياً، وراقب السرة والإسهال والتنفس والنشاط. أي خمول أو امتناع عن الرضاعة يستدعي تقييماً بيطرياً عاجلاً.",fontSize=10.sp,color=FinGreen,lineHeight=15.sp)
     }
     if(selected.productionState.contains("حامل")||selected.pregnancyStage!="غير حامل"){
      Text("متابعة النعجة الحامل: راقب درجة حالة الجسم (BCS) والوزن وتغير الشهية، واضبط العليقة حسب مرحلة الحمل وعدد الأجنة بإشراف مختص.",fontSize=10.sp,color=FinGreen,lineHeight=15.sp)
     }
     Text("الحالة التغذوية: لا توجد درجة حالة جسم مسجلة؛ أضف تقييم BCS ووزن دوري في سجل القطيع بدلاً من افتراضها.",fontSize=10.sp,color=FinMuted,lineHeight=15.sp)
    }
   }
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("التطعيمات والعلاجات القادمة",fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=13.sp,modifier=Modifier.weight(1f))
    Button(onClick={kind="تطعيم";title="";date=LocalDate.now().plusDays(7).toString();notes="";dialog=true},colors=ButtonDefaults.buttonColors(containerColor=FinGreen),contentPadding=PaddingValues(horizontal=10.dp,vertical=5.dp)){Icon(Icons.Outlined.Add,null);Text("موعد جديد",fontSize=10.sp)}
   }
   val animalReminders=reminders.filter{it.tag==selected.tag}.sortedBy{it.date}
   if(animalReminders.isEmpty())Text("لا توجد مواعيد مسجلة لهذا الحيوان.",fontSize=11.sp,color=FinMuted)
   animalReminders.forEach{r->
    Surface(color=Color.White,shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,FinLine)){
     Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
      Icon(if(r.kind=="تطعيم")Icons.Outlined.Vaccines else Icons.Outlined.Medication,null,tint=FinGreen)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)){Text(r.title,fontWeight=FontWeight.Bold,color=FinInk,fontSize=11.sp);Text(r.kind+" • "+r.date,fontSize=10.sp,color=FinGreen);if(r.notes.isNotBlank())Text(r.notes,fontSize=9.sp,color=FinMuted)}
      Checkbox(checked=r.completed,onCheckedChange={v->reminders=reminders.map{if(it.id==r.id)it.copy(completed=v)else it};saveHerdCare(context,reminders)})
     }
    }
   }
   Text("التوصيات الصحية",fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=13.sp)
   Surface(color=Color(0xFFF6EEDF),shape=RoundedCornerShape(13.dp)){Text("لا تُفرض جرعات أو أدوية تلقائياً. يختلف برنامج التحصين وتوقيت مضادات الطفيليات باختلاف الولاية والوضع الوبائي واللقاح المسجل وعمر الحيوان وحالته والحمل وفترة السحب. سجّل موعد الطبيب أو البرنامج البيطري المحلي هنا، ولا تعتمد موعداً عاماً بديلاً عن الطبيب.",Modifier.padding(12.dp),fontSize=10.sp,color=FinInk,lineHeight=15.sp)}
  }
 }
 if(dialog){
  AlertDialog(onDismissRequest={dialog=false},title={Text("إضافة موعد متابعة")},text={
   Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
    Text("نوع المتابعة",fontSize=11.sp,color=FinMuted)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
     FilterChip(selected=kind=="تطعيم",onClick={kind="تطعيم"},label={Text("تطعيم")})
     FilterChip(selected=kind=="علاج / فحص",onClick={kind="علاج / فحص"},label={Text("علاج / فحص")})
     FilterChip(selected=kind=="وزن",onClick={kind="وزن"},label={Text("وزن")})
    }
    OutlinedTextField(title,{title=it},label={Text("اسم التطعيم أو المتابعة")},singleLine=true)
    OutlinedTextField(date,{date=it},label={Text("التاريخ YYYY-MM-DD")},singleLine=true)
    OutlinedTextField(notes,{notes=it},label={Text("ملاحظات الطبيب / التشغيلة / الجرعة الموصوفة")},minLines=2)
   }
  },confirmButton={TextButton(onClick={
   val valid=runCatching{LocalDate.parse(date);true}.getOrDefault(false)
   if(title.isNotBlank()&&valid){
    val item=HerdCareReminder(UUID.randomUUID().toString(),selected!!.tag,kind,title.trim(),date,notes.trim())
    reminders=reminders+item;saveHerdCare(context,reminders);dialog=false
   }
  }){Text("حفظ")}},dismissButton={TextButton(onClick={dialog=false}){Text("إلغاء")}})
 }
}

@Composable fun MedicineScreen(modifier:Modifier=Modifier){
 val c=LocalContext.current
 var stock by remember{mutableStateOf(readMedicineStock(c))}
 var selected by remember{mutableStateOf<String?>(null)}
 var showHerdMonitor by rememberSaveable{mutableStateOf(false)}
 val groups=medicineGroups
 val totalProducts=groups.values.sumOf{it.size}
 val available=groups.values.flatten().count{(stock[it]?:0.0)>0.0}
 Column(modifier.fillMaxSize().background(FinCream).padding(horizontal=16.dp)){
  Spacer(Modifier.height(12.dp))
  Text("الأدوية والتطعيمات",fontSize=24.sp,fontWeight=FontWeight.ExtraBold,color=FinInk)
  Text("دليل بيطري مصور وبطاقات تعريفية ومتابعة المخزون",fontSize=11.sp,color=FinMuted)
  Spacer(Modifier.height(12.dp))
  if(!showHerdMonitor){
   Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine),modifier=Modifier.fillMaxWidth().clickable{showHerdMonitor=true}){
    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
     Surface(color=Color(0xFFEAF0E8),shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.Pets,null,tint=FinGreen,modifier=Modifier.padding(10.dp).size(25.dp))}
     Spacer(Modifier.width(10.dp))
     Column(Modifier.weight(1f)){Text("القطيع",fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=15.sp);Text("متابعة صحية وإنتاجية وتغذوية لكل رأس ومواعيده القادمة",fontSize=10.sp,color=FinMuted)}
     Icon(Icons.Outlined.ChevronLeft,null,tint=FinGreen)
    }
   }
  }else{
   HerdHealthMonitor(onBack={showHerdMonitor=false})
  }
  if(showHerdMonitor) return@Column
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   FinMetric("أصناف الدليل",totalProducts.toString(),Icons.Outlined.MedicalServices,Modifier.weight(1f))
   FinMetric("أصناف متوفرة",available.toString(),Icons.Outlined.Inventory2,Modifier.weight(1f))
  }
  Spacer(Modifier.height(8.dp))
  if(selected==null){
   LazyColumn(contentPadding=PaddingValues(start=0.dp,end=0.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    groups.forEach{(group,names)->
     item{Text(group,fontWeight=FontWeight.ExtraBold,color=FinGreen,fontSize=14.sp,modifier=Modifier.padding(top=7.dp))}
     items(names){name->
      val info=medicineInfo(name,group)
      Surface(color=Color.White,shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,FinLine),modifier=Modifier.fillMaxWidth().clickable{selected=name}){
       Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
        MedicinePackArt(name,group,Modifier.width(76.dp).height(86.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
         Text(name,fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=12.sp)
         Text(info.scientific,fontSize=10.sp,color=FinGreen,fontWeight=FontWeight.SemiBold,maxLines=1)
         Text(info.purpose,fontSize=9.sp,color=FinMuted,maxLines=2,lineHeight=13.sp)
         Text("المخزون: "+String.format(Locale.US,"%.2f",stock[name]?:0.0),fontSize=10.sp,color=if((stock[name]?:0.0)>0)FinGreen else FinMuted,fontWeight=FontWeight.Bold)
        }
        Icon(Icons.Outlined.ChevronLeft,null,tint=FinMuted)
       }
      }
     }
    }
    item{Text("الصور في الدليل رسومات توضيحية لشكل المستحضر وليست صوراً لعبوات تجارية بعلامات محددة.",fontSize=9.sp,color=FinMuted,lineHeight=14.sp,modifier=Modifier.padding(6.dp))}
   }
  }else{
   val group=groups.entries.firstOrNull{selected in it.value}?.key?: "الأدوية البيطرية"
   val info=medicineInfo(selected!!,group)
   LazyColumn(contentPadding=PaddingValues(start=0.dp,end=0.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    item{TextButton(onClick={selected=null}){Icon(Icons.Outlined.ArrowForward,null);Text("العودة إلى الدليل")}}
    item{Surface(color=Color.White,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,FinLine)){
     Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
      MedicinePackArt(info.name,group,Modifier.width(132.dp).height(145.dp))
      Text(info.name,fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=FinInk)
      Text(info.scientific,fontSize=12.sp,color=FinGreen,fontWeight=FontWeight.Bold)
      Text(info.group,fontSize=10.sp,color=FinMuted)
      Text("الرصيد المسجل: "+String.format(Locale.US,"%.2f",stock[selected]?:0.0),fontSize=12.sp,color=FinGreen,fontWeight=FontWeight.Bold)
     }
    }}
    item{MedicineInfoCard("ما هو؟",info.scientific,Icons.Outlined.Science)}
    item{MedicineInfoCard("ما استعماله؟",info.purpose,Icons.Outlined.MedicalServices)}
    item{MedicineInfoCard("المادة الفعالة",info.active,Icons.Outlined.Biotech)}
    item{MedicineInfoCard("التركيز",info.concentration,Icons.Outlined.Opacity)}
    item{MedicineInfoCard("طريقة الحفظ",info.storage,Icons.Outlined.Inventory2)}
    item{MedicineInfoCard("التحذيرات",info.warnings,Icons.Outlined.WarningAmber)}
    item{MedicineInfoCard("فترة السحب",info.withdrawal,Icons.Outlined.Schedule)}
    item{Surface(color=Color(0xFFF6EEDF),shape=RoundedCornerShape(15.dp)){Text("تنبيه بيطري: هذه البطاقة للتعريف والتثقيف فقط، ولا تقدم جرعات أو تشخيصاً أو بديلاً عن الطبيب البيطري. اعتمد دائماً النشرة الخاصة بالمنتج المسجل في بلدك.",Modifier.padding(13.dp),fontSize=10.sp,color=FinInk,lineHeight=16.sp)}}
   }
  }
 }
}

@Composable private fun MedicinePackArt(name:String,group:String,modifier:Modifier=Modifier){
 val isVaccine=group=="اللقاحات والتطعيمات"
 val vaccineMark=when{
  name.contains("التسمم المعوي")->"ET"
  name.contains("الجدري")->"POX"
  name.contains("الحمى القلاعية")->"FMD"
  name.contains("PPR")->"PPR"
  name.contains("الكلوستريديا")->"CLO"
  name.contains("البروسيلا")->"BRU"
  else->"VAX"
 }
 val isSpray=name.contains("بخاخ")
 val isPowder=name.contains("بدائل اللبأ")||name.contains("الأملاح")||name.contains("الإسهال")
 val isTube=name.contains("مطهرات")||name.contains("التهابات الضرع")||name.contains("سرة")
 val isSyringe=name.contains("أوكسيتوسين")||name.contains("البروستاغلاندين")
 val accent=when{
  name.contains("الجدري")->Color(0xFF9A7652)
  name.contains("الحمى القلاعية")->Color(0xFF56899A)
  name.contains("PPR")->Color(0xFF9276A6)
  name.contains("البروسيلا")->Color(0xFFB36A55)
  isVaccine->Color(0xFF4F806B)
  group=="مضادات الطفيليات الداخلية"->Color(0xFFB18443)
  group=="الجروح والعناية"->Color(0xFF56899A)
  group=="الجهاز الهضمي والجفاف"->Color(0xFF4D8F88)
  group=="التكاثر والولادة"->Color(0xFF9276A6)
  else->Color(0xFF66836A)
 }
 val label=when{isVaccine->"VACCINE";isSpray->"SPRAY";isPowder->"ORAL";isTube->"CARE";isSyringe->"INJECT";else->"VET"}
 Box(modifier.background(Color(0xFFF4F1E8),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){
  when{
   isSyringe->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
    Box(Modifier.width(8.dp).height(38.dp).background(Color(0xFFCBD4D0),RoundedCornerShape(3.dp)))
    Box(Modifier.width(43.dp).height(13.dp).background(Color.White,RoundedCornerShape(3.dp)),contentAlignment=Alignment.Center){Box(Modifier.fillMaxWidth(.72f).height(5.dp).background(accent.copy(alpha=.35f)))}
    Box(Modifier.width(13.dp).height(3.dp).background(accent))
   }
   isTube->Row(verticalAlignment=Alignment.CenterVertically){
    Column(horizontalAlignment=Alignment.CenterHorizontally){
     Box(Modifier.width(20.dp).height(8.dp).background(accent,RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
     Box(Modifier.width(35.dp).height(45.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Healing,null,tint=accent,modifier=Modifier.size(19.dp));Box(Modifier.width(23.dp).height(4.dp).background(accent.copy(alpha=.3f),RoundedCornerShape(2.dp)))}}
    }
   }
   isPowder->Column(horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.width(37.dp).height(9.dp).background(accent,RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
    Box(Modifier.width(54.dp).height(42.dp).background(Color.White,RoundedCornerShape(bottomStart=8.dp,bottomEnd=8.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Science,null,tint=accent,modifier=Modifier.size(19.dp));Text("POWDER",fontSize=6.sp,color=accent,fontWeight=FontWeight.ExtraBold)}}
   }
   isSpray->Column(horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.width(18.dp).height(11.dp).background(accent,RoundedCornerShape(topStart=3.dp,topEnd=3.dp)))
    Box(Modifier.width(12.dp).height(8.dp).background(accent.copy(alpha=.65f)))
    Box(Modifier.width(35.dp).height(47.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Healing,null,tint=accent,modifier=Modifier.size(23.dp))}
   }
   else->Column(horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.width(if(isVaccine)27.dp else 34.dp).height(9.dp).background(accent,RoundedCornerShape(topStart=3.dp,topEnd=3.dp)))
    Box(Modifier.width(if(isVaccine)39.dp else 53.dp).height(if(isVaccine)49.dp else 55.dp).background(Color.White,RoundedCornerShape(bottomStart=8.dp,bottomEnd=8.dp)),contentAlignment=Alignment.Center){
     Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
      Icon(if(isVaccine)Icons.Outlined.Vaccines else Icons.Outlined.Medication,null,tint=accent,modifier=Modifier.size(if(isVaccine)23.dp else 27.dp))
      Spacer(Modifier.height(3.dp))
      Box(Modifier.fillMaxWidth(.8f).height(4.dp).background(accent.copy(alpha=.28f),RoundedCornerShape(3.dp)))
      Text(if(isVaccine)vaccineMark else label,fontSize=6.sp,color=accent,fontWeight=FontWeight.ExtraBold)
     }
    }
   }
  }
  Text(if(isVaccine)"VACCINE" else "VETERINARY",fontSize=6.sp,color=accent.copy(alpha=.8f),fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp))
 }
}

@Composable private fun MedicineInfoCard(title:String,body:String,icon:androidx.compose.ui.graphics.vector.ImageVector){
 Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){
  Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.Top){
   Surface(color=Color(0xFFEAF0E8),shape=RoundedCornerShape(10.dp)){Icon(icon,null,tint=FinGreen,modifier=Modifier.padding(8.dp).size(19.dp))}
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.Bold,color=FinInk,fontSize=12.sp);Text(body,color=FinMuted,fontSize=10.sp,lineHeight=16.sp)}
  }
 }
}

@Composable private fun FinMetric(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier){Surface(modifier,color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=FinGreen,modifier=Modifier.size(23.dp));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(value,color=FinGreen,fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text(title,color=FinMuted,fontSize=9.sp)}}}}
@Composable private fun FinAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){Surface(modifier.clickable(onClick=onClick),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){Column(Modifier.padding(13.dp)){Icon(icon,null,tint=FinGreen,modifier=Modifier.size(24.dp));Spacer(Modifier.height(6.dp));Text(title,fontWeight=FontWeight.Bold,color=FinInk,fontSize=13.sp);Text(subtitle,color=FinMuted,fontSize=9.sp)}}}
@Composable private fun InvoiceRow(v:FinanceInvoice,onPrint:()->Unit){Surface(color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,FinLine)){Row(Modifier.fillMaxWidth().padding(11.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(v.type=="sale")Icons.Outlined.TrendingUp else Icons.Outlined.ShoppingCart,null,tint=FinGreen);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(v.id,fontWeight=FontWeight.Bold,color=FinInk,fontSize=11.sp);Text(v.category+" • "+v.item,fontSize=10.sp,color=FinMuted);Text(v.party+" • "+invoiceTime(v.timestamp),fontSize=9.sp,color=FinMuted)};Column(horizontalAlignment=Alignment.End){Text(dinar(v.total),fontWeight=FontWeight.Bold,color=FinGreen,fontSize=11.sp);TextButton(onClick=onPrint){Icon(Icons.Outlined.Print,null);Text("طباعة",fontSize=9.sp)}}}}}
@Composable private fun FinReport(title:String,value:String,sub:String){Surface(color=Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,FinLine)){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,color=FinInk,fontSize=12.sp);Text(sub,color=FinMuted,fontSize=9.sp)};Text(value,fontWeight=FontWeight.ExtraBold,color=FinGreen,fontSize=13.sp)}}}
@Composable private fun EmptyFinance(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.ReceiptLong,null,tint=FinGreen,modifier=Modifier.size(34.dp));Text(title,fontWeight=FontWeight.Bold,color=FinInk);Text(subtitle,fontSize=10.sp,color=FinMuted)}}

private fun invoiceQr(value:String):Bitmap{val m=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,280,280,mapOf(EncodeHintType.MARGIN to 1));val p=IntArray(280*280);for(y in 0 until 280)for(x in 0 until 280)p[y*280+x]=if(m.get(x,y))AColor.BLACK else AColor.WHITE;return Bitmap.createBitmap(p,280,280,Bitmap.Config.ARGB_8888)}
private class FinancePrintAdapter(ctx:Context,private val v:FinanceInvoice):PrintDocumentAdapter(){
 private val app=ctx.applicationContext
 override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes,cancellationSignal:CancellationSignal,callback:LayoutResultCallback,metadata:Bundle?){if(cancellationSignal.isCanceled){callback.onLayoutCancelled();return};callback.onLayoutFinished(PrintDocumentInfo.Builder(v.id+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true)}
 override fun onWrite(pages:Array<PageRange>,destination:ParcelFileDescriptor,cancellationSignal:CancellationSignal,callback:WriteResultCallback){
  val attrs=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("mawashi","invoice",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();val pdf=PrintedPdfDocument(app,attrs)
  try{val page=pdf.startPage(1);val canvas=page.canvas;val paint=android.graphics.Paint(3);val x=canvas.width*.12f;var y=70f;paint.color=AColor.rgb(82,107,85);paint.textSize=32f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;canvas.drawText("MAWASHI | مواشي",x,y,paint);y+=55;paint.color=AColor.BLACK;paint.textSize=23f;canvas.drawText(if(v.type=="sale")"فاتورة بيع" else "فاتورة شراء",x,y,paint);y+=45;paint.textSize=16f;paint.typeface=android.graphics.Typeface.DEFAULT
   val lines=listOf("رقم الفاتورة: "+v.id,"التاريخ والساعة: "+invoiceTime(v.timestamp),(if(v.type=="sale")"المشتري: " else "المورد: ")+v.party,"التصنيف: "+v.category,"الصنف: "+v.item,"الكمية: "+v.quantity+" "+v.unit,"سعر الوحدة: "+dinar(v.unitPrice))
   lines.forEach{canvas.drawText(it,x,y,paint);y+=32};y+=15;paint.textSize=23f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD;canvas.drawText("الإجمالي: "+dinar(v.total),x,y,paint)
   val b=invoiceQr(v.id+"|"+v.timestamp+"|"+v.total);canvas.drawBitmap(b,null,android.graphics.RectF(canvas.width*.69f,canvas.height*.68f,canvas.width*.88f,canvas.height*.87f),paint);pdf.finishPage(page);FileOutputStream(destination.fileDescriptor).use{pdf.writeTo(it)};callback.onWriteFinished(arrayOf(PageRange(0,0)))
  }catch(e:Exception){callback.onWriteFailed(e.message)}finally{pdf.close()}
 }
}
private fun printFinanceInvoice(c:Context,v:FinanceInvoice){val a=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("mawashi","invoice",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();(c.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(v.id,FinancePrintAdapter(c,v),a)}
