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
internal data class MedicineLot(val item:String,val batch:String,val expiry:String,val quantity:Double,val supplier:String,val receivedAt:String)

internal fun usableMedicineStock(item:String,legacyStock:Double,lots:List<MedicineLot>,today:String):Double {
 val matching=lots.filter{it.item==item}
 return if(matching.isEmpty()) legacyStock.coerceAtLeast(0.0) else matching.filter{it.expiry>=today}.sumOf{it.quantity.coerceAtLeast(0.0)}
}
private const val MEDICINE_LOTS_KEY="medicine_stock_lots_v29"
private fun readMedicineLots(c:Context):List<MedicineLot>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(MEDICINE_LOTS_KEY,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);MedicineLot(x.optString("item"),x.optString("batch"),x.optString("expiry"),x.optDouble("quantity").coerceAtLeast(0.0),x.optString("supplier"),x.optString("receivedAt"))}.getOrNull()}
}
private fun saveMedicineLots(c:Context,lots:List<MedicineLot>){
 val a=JSONArray();lots.forEach{x->a.put(JSONObject().put("item",x.item).put("batch",x.batch).put("expiry",x.expiry).put("quantity",x.quantity.coerceAtLeast(0.0)).put("supplier",x.supplier).put("receivedAt",x.receivedAt))}
 check(c.getSharedPreferences(FIN_PREFS,0).edit().putString(MEDICINE_LOTS_KEY,a.toString()).commit())
}
private fun consumeMedicineLots(c:Context,item:String,quantity:Double){
 var remaining=quantity
 val lots=readMedicineLots(c).toMutableList()
 val today=LocalDate.now().toString()
 val indices=lots.indices.filter{lots[it].item==item&&lots[it].quantity>0.0&&lots[it].expiry>=today}.sortedBy{lots[it].expiry}
 for(i in indices){if(remaining<=1e-8)break;val old=lots[i];val used=minOf(old.quantity,remaining);lots[i]=old.copy(quantity=old.quantity-used);remaining-=used}
 saveMedicineLots(c,lots)
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
 "اللقاحات والتطعيمات الأساسية" to listOf("لقاح طاعون المجترات الصغيرة PPR","لقاح جدري الأغنام","لقاح التسمم المعوي","لقاح الكلوستريديا","لقاح الحمى القلاعية"),
 "لقاحات خاصة حسب الخطر والبرنامج الرسمي" to listOf("لقاح البروسيلا Rev.1","لقاح الإكثيما المعدية (Orf)"),
 "مضادات الطفيليات الداخلية" to listOf("ألبيندازول (Albendazole)","فينبندازول (Fenbendazole)","إيفرمكتين (Ivermectin)","ليفاميزول (Levamisole)","تريكلابندازول"),
 "مكافحة الطفيليات الخارجية" to listOf("مستحضرات الرش والغمس","مستحضرات السكب الموضعي","مستحضرات الجرب والقمل"),
 "الجروح والعناية" to listOf("بخاخات الجروح","مطهرات الحوافر","مطهرات الجلد والجروح","مستحضرات التهابات الضرع"),
 "الجهاز الهضمي والجفاف" to listOf("محاليل الأملاح والسوائل","التولترازوريل (Toltrazuril)","مستحضرات علاج النفاخ","محاليل علاج الإسهال"),
 "الفيتامينات والمعادن" to listOf("فيتامينات A وD وE","مكملات الأملاح المعدنية","مكملات العناصر النادرة"),
 "التكاثر والولادة" to listOf("أوكسيتوسين (Oxytocin)","البروستاغلاندين","مستحضرات الكالسيوم","البروبيلين غليكول"),
 "رعاية الحملان والمواليد" to listOf("مطهر سرة الحملان","بدائل اللبأ","بدائل حليب الحملان","زجاجة رضاعة الحملان"),
 "أدوية لا تصرف إلا بوصفة بيطرية" to listOf("مضادات حيوية بيطرية بوصفة","مضادات التهاب ومسكنات بوصفة")
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
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("صافي الأرباح",dinar(net.coerceAtLeast(0.0)),Icons.Outlined.AccountBalance,Modifier.weight(1f));FinMetric("الخسائر",dinar((-net).coerceAtLeast(0.0)),Icons.Outlined.TrendingDown,Modifier.weight(1f))}}
    item{Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("النتيجة المالية",fontWeight=FontWeight.Bold,color=FinInk);Text("الإيرادات − تكلفة المباع − المصاريف",fontSize=9.sp,color=FinMuted)};Text(dinar(net),fontWeight=FontWeight.ExtraBold,color=if(net>=0)FinGreen else Color(0xFFB54D42),fontSize=17.sp)}}}
    item{Text("إدارة العمليات",fontSize=16.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinAction("متجر المزرعة","تسوّق احتياجات المزرعة",Icons.Outlined.Storefront,Modifier.weight(1f)){page=FinancePage.PURCHASES};FinAction("المبيعات","القطيع والصوف والمخزون",Icons.Outlined.PointOfSale,Modifier.weight(1f)){sale=true}}}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinAction("الفواتير",list.size.toString()+" فاتورة",Icons.Outlined.ReceiptLong,Modifier.weight(1f)){page=FinancePage.INVOICES};FinAction("التقارير","تحليل النشاط",Icons.Outlined.BarChart,Modifier.weight(1f)){page=FinancePage.REPORTS}}}
    item{Text("المخزون المرتبط تلقائياً",fontSize=15.sp,fontWeight=FontWeight.Bold,color=FinInk)}
    item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FinMetric("رؤوس القطيع",activeHerdAnimals(herd).size.toString()+" رأس",Icons.Outlined.Pets,Modifier.weight(1f));FinMetric("مخزون العلف",String.format(Locale.US,"%.1f كغ",feeds.values.sum()),Icons.Outlined.Inventory2,Modifier.weight(1f))}}
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
 var supplier by rememberSaveable{mutableStateOf("")};var item by rememberSaveable{mutableStateOf("نعجة")};var qty by rememberSaveable{mutableStateOf("1")};var price by rememberSaveable{mutableStateOf("")};var weight by rememberSaveable{mutableStateOf("")};var age by rememberSaveable{mutableStateOf("12")};var birthDate by rememberSaveable{mutableStateOf("")};var birthWeight by rememberSaveable{mutableStateOf("")};var health by rememberSaveable{mutableStateOf("جيدة")};var state by rememberSaveable{mutableStateOf("نعجة جافة")};var batch by rememberSaveable{mutableStateOf("")};var expiry by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")}
 val feedNames=FeedKind.values().map{it.label};val drugs=medicineGroups.values.flatten()
 val options=when(type){PurchaseType.ANIMAL->listOf("نعجة","كبش","حمل/رخلة");PurchaseType.FEED->feedNames;PurchaseType.MEDICINE->drugs;null->emptyList()}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=25.dp)){
  item{Surface(color=FinGreen,shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("متجر مزرعتك في مكان واحد",fontSize=19.sp,fontWeight=FontWeight.ExtraBold,color=Color.White);Text("اختر القسم ثم أضف مشترياتك إلى سجل المزرعة",fontSize=10.sp,color=Color.White.copy(alpha=.88f))};Icon(Icons.Outlined.ShoppingBasket,null,tint=Color.White,modifier=Modifier.size(38.dp))}}}
  item{Text(if(type==null)"تصفح أقسام المتجر" else "تفاصيل الشراء",fontSize=16.sp,fontWeight=FontWeight.Bold,color=FinInk)}
  if(type==null){
   item{PurchaseCategoryCard("القطيع","شراء رؤوس الأغنام","نعاج • كباش • حملان",Icons.Outlined.Pets,activeHerdAnimals(herd).size.toString()+" رأس"){type=PurchaseType.ANIMAL;item="نعجة"}}
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
    if(type==PurchaseType.ANIMAL){Divider(color=FinLine);Text("بيانات الحيوان",fontWeight=FontWeight.Bold,color=FinInk);OutlinedTextField(weight,{weight=it},label={Text("الوزن بالكيلوغرام")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());if(item=="حمل/رخلة"){OutlinedTextField(birthDate,{birthDate=it},label={Text("تاريخ الميلاد YYYY-MM-DD")},modifier=Modifier.fillMaxWidth());OutlinedTextField(birthWeight,{birthWeight=it},label={Text("وزن الميلاد لكل حمل بالكيلوغرام")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}else OutlinedTextField(age,{age=it},label={Text("العمر بالأشهر")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Text("الحالة الصحية",fontSize=11.sp,color=FinMuted);Row{listOf("جيدة","متوسطة","تحتاج متابعة").forEach{FilterChip(health==it,{health=it},label={Text(it,fontSize=9.sp)})}};Text("الحالة الإنتاجية",fontSize=11.sp,color=FinMuted);Column{listOf("نعجة جافة","نعجة حامل","نعجة مرضعة","كبش","خروف").forEach{FilterChip(state==it,{state=it},label={Text(it,fontSize=10.sp)})}}}
    if(type==PurchaseType.MEDICINE){Divider(color=FinLine);Text("بيانات الدواء",fontWeight=FontWeight.Bold,color=FinInk);OutlinedTextField(batch,{batch=it},label={Text("رقم التشغيلة")},modifier=Modifier.fillMaxWidth());OutlinedTextField(expiry,{expiry=it},label={Text("تاريخ الصلاحية YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())}
   }}}
   item{val q=qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0;Surface(color=Color(0xFFEAF1E9),shape=RoundedCornerShape(16.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("إجمالي السلة",fontSize=11.sp,color=FinMuted);Text(dinar(q*p),fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=FinGreen)};Icon(Icons.Outlined.ReceiptLong,null,tint=FinGreen,modifier=Modifier.size(28.dp))}}}
   if(error.isNotBlank())item{Text(error,color=Color(0xFFB54D42),fontSize=11.sp)}
   item{Button(onClick={
    val t=type?:return@Button;val raw=qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0;val count=raw.toInt()
    if(supplier.isBlank())error="أدخل اسم المورد" else if(raw<=0||raw>100000||p<=0)error="تحقق من الكمية والسعر" else if(t==PurchaseType.ANIMAL&&(raw!=count.toDouble()||count !in 1..500))error="عدد الرؤوس يجب أن يكون صحيحاً من 1 إلى 500" else if(t==PurchaseType.ANIMAL&&(weight.replace(',','.').toDoubleOrNull()?:0.0)<=0)error="أدخل وزن الرأس" else if(t==PurchaseType.ANIMAL&&item=="حمل/رخلة"&&!isValidLambBirthDate(birthDate))error="أدخل تاريخ ميلاد صحيحاً للحمل، ولا يمكن أن يكون في المستقبل" else if(t==PurchaseType.ANIMAL&&item=="حمل/رخلة"&&(birthWeight.replace(',','.').toDoubleOrNull()?:0.0)<=0)error="أدخل وزن الميلاد لكل حمل" else if(t==PurchaseType.ANIMAL&&item=="حمل/رخلة"&&(birthWeight.replace(',','.').toDoubleOrNull()?:0.0)>(weight.replace(',','.').toDoubleOrNull()?:0.0))error="وزن الميلاد لا يمكن أن يتجاوز الوزن الحالي" else if(t==PurchaseType.MEDICINE&&batch.isBlank())error="أدخل رقم التشغيلة" else if(t==PurchaseType.MEDICINE&&runCatching{LocalDate.parse(expiry);true}.getOrDefault(false).not())error="أدخل تاريخ صلاحية صحيحاً بصيغة YYYY-MM-DD" else if(t==PurchaseType.MEDICINE&&expiry<LocalDate.now().toString())error="لا يمكن إضافة تشغيلة منتهية الصلاحية إلى المخزون القابل للاستخدام" else{
     val now=LocalDateTime.now().toString();val id="PUR-"+now.filter{it.isDigit()}.take(14)+"-"+UUID.randomUUID().toString().take(4).uppercase();val q=if(t==PurchaseType.ANIMAL)count.toDouble() else raw;val total=q*p;var detail=""
     when(t){
      PurchaseType.ANIMAL->{val tags=generateHerdTags(herd.map{it.tag},count);val kg=weight.replace(',','.').toDouble();val birthDateValue=if(item=="حمل/رخلة")birthDate.trim() else "";val birthWeightValue=if(item=="حمل/رخلة")birthWeight.replace(',','.').toDouble() else null;val animalAgeMonths=if(item=="حمل/رخلة")ChronoUnit.MONTHS.between(LocalDate.parse(birthDateValue),LocalDate.now()).toInt().coerceAtLeast(0) else age.toIntOrNull()?:0;val updated=herd.toMutableList();tags.forEach{tag->updated.add(HerdAnimal(id=UUID.randomUUID().toString(),tag=tag,kind=item,ageMonths=animalAgeMonths,health=health,notes="شراء "+id,weightKg=kg,birthDate=birthDateValue,birthWeightKg=birthWeightValue,productionState=if(item=="كبش")"كبش" else if(item=="حمل/رخلة")"خروف" else state))};saveHerd(c,updated);detail="أضيفت "+count+" رؤوس للقطيع بالمعرفات "+tags.first()+" إلى "+tags.last()+"."}
      PurchaseType.FEED->{val k=FeedKind.values().first{it.label==item};val stock=loadFeedStocks(c);stock[k]=(stock[k]?:0.0)+q;saveFeedStocks(c,stock);detail="أضيفت "+String.format(Locale.US,"%.2f",q)+" كغ إلى مخزون "+k.label+"."}
      PurchaseType.MEDICINE->{
       val stock=readMedicineStock(c);stock[item]=(stock[item]?:0.0)+q;writeMedicineStock(c,stock)
       val lots=readMedicineLots(c).toMutableList();lots.add(MedicineLot(item,batch.trim(),expiry.trim(),q,supplier.trim(),now));saveMedicineLots(c,lots)
       detail="أضيف الدواء إلى المخزون. التشغيلة: "+batch+"، الصلاحية: "+expiry+"."
      }
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
 val activeHerd=activeHerdAnimals(herd)
 var type by remember{mutableStateOf(SaleType.ANIMAL)};var buyer by rememberSaveable{mutableStateOf("")};var item by rememberSaveable{mutableStateOf(activeHerd.firstOrNull()?.tag?:"")};var qty by rememberSaveable{mutableStateOf("1")};var price by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")}
 val medicineLots=readMedicineLots(c)
 val today=LocalDate.now().toString()
 val usableMedicineItems=medicine.keys.filter{usableMedicineStock(it,medicine[it]?:0.0,medicineLots,today)>0.0}
 val options=when(type){SaleType.ANIMAL->activeHerd.map{it.tag};SaleType.WOOL->listOf("الصوف");SaleType.FEED->feed.filterValues{it>0}.keys.map{it.label};SaleType.MEDICINE->usableMedicineItems}
 AlertDialog(onDismissRequest=dismiss,title={Text("فاتورة بيع")},text={LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  item{Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){SaleType.values().forEach{t->FilterChip(type==t,{type=t;item=when(t){SaleType.ANIMAL->activeHerd.firstOrNull()?.tag?:"";SaleType.WOOL->"الصوف";SaleType.FEED->feed.filterValues{it>0}.keys.firstOrNull()?.label?:"";SaleType.MEDICINE->usableMedicineItems.firstOrNull()?:""};qty="1"},label={Text(t.title,fontSize=8.sp)})}}}
  item{OutlinedTextField(buyer,{buyer=it},label={Text("اسم المشتري")},modifier=Modifier.fillMaxWidth())}
  item{var open by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={open=true},enabled=options.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text(item.ifBlank{"لا توجد أصناف متاحة"});Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(open,{open=false}){options.forEach{o->DropdownMenuItem(text={Text(o)},onClick={item=o;open=false})}}}}
  if(type!=SaleType.ANIMAL)item{OutlinedTextField(qty,{qty=it},label={Text("الكمية المراد بيعها")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
  item{OutlinedTextField(price,{price=it},label={Text("سعر الوحدة بالدينار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
  item{Text(when(type){SaleType.ANIMAL->"يُباع الحيوان المختار من رقمه التعريفي.";SaleType.WOOL->"الرصيد المتاح: "+wool+" كغ";SaleType.FEED->"الرصيد المتاح: "+(feed[FeedKind.values().firstOrNull{it.label==item}]?:0.0)+" كغ";SaleType.MEDICINE->"الرصيد الصالح للبيع: "+usableMedicineStock(item,medicine[item]?:0.0,medicineLots,today)+" وحدة"},fontSize=10.sp,color=FinMuted)}
  if(error.isNotBlank())item{Text(error,color=Color.Red,fontSize=11.sp)}
 }},confirmButton={TextButton(onClick={
  val q=if(type==SaleType.ANIMAL)1.0 else qty.replace(',','.').toDoubleOrNull()?:0.0;val p=price.replace(',','.').toDoubleOrNull()?:0.0
  if(buyer.isBlank())error="أدخل اسم المشتري" else if(options.isEmpty()||item.isBlank())error="لا توجد كمية متاحة" else if(q<=0||p<=0)error="تحقق من الكمية والسعر"
  else{
   var available=0.0;var costUnit=0.0;var detail=""
   when(type){
    SaleType.ANIMAL->{val a=activeHerd.firstOrNull{it.tag==item};available=if(a==null)0.0 else 1.0;val bought=readInvoices(c).filter{it.type=="purchase"&&it.category==PurchaseType.ANIMAL.title&&it.item==a?.kind};costUnit=if(bought.isEmpty())0.0 else bought.sumOf{it.total}/bought.sumOf{it.quantity}}
    SaleType.WOOL->available=wool
    SaleType.FEED->{val k=FeedKind.values().firstOrNull{it.label==item};available=feed[k]?:0.0;costUnit=averagePurchaseCost(c,item,PurchaseType.FEED.title)}
    SaleType.MEDICINE->{
     available=usableMedicineStock(item,medicine[item]?:0.0,medicineLots,today)
     costUnit=averagePurchaseCost(c,item,PurchaseType.MEDICINE.title)
    }
   }
   if(q>available+1e-8)error="الرصيد غير كافٍ. المتاح: "+available
   else{
    when(type){
     SaleType.ANIMAL->{
      val animal=activeHerd.first{it.tag==item}
      val archived=archiveHerdAnimal(animal,LocalDate.now().toString(),"بيع")
      saveHerd(c,herd.map{if(it.id==animal.id)archived else it})
      detail="بيع الحيوان "+item+" وأُرشف ملفه مع الاحتفاظ ببياناته. تاريخ البيع: "+archived.exitDate+"."
     }
     SaleType.WOOL->writeWool(c,wool-q)
     SaleType.FEED->{val k=FeedKind.values().first{it.label==item};val s=loadFeedStocks(c);s[k]=(s[k]?:0.0)-q;saveFeedStocks(c,s)}
     SaleType.MEDICINE->{consumeMedicineLots(c,item,q);val s=readMedicineStock(c);s[item]=(s[item]?:0.0)-q;writeMedicineStock(c,s)}
    }
    val now=LocalDateTime.now().toString();val id="SAL-"+now.filter{it.isDigit()}.take(14)+"-"+UUID.randomUUID().toString().take(4).uppercase();val total=q*p
    writeInvoice(c,FinanceInvoice(id,"sale",type.title,buyer,item,q,if(type==SaleType.ANIMAL)"رأس" else if(type==SaleType.WOOL||type==SaleType.FEED)"كغ" else "وحدة",p,total,q*costUnit,now,detail))
    done("حُفظت فاتورة البيع "+id+" بقيمة "+dinar(total)+". تم خصم الكمية تلقائياً من القسم المعني.")
   }
  }
 }){Text("تأكيد البيع")}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}

private fun averagePurchaseCost(c:Context,item:String,category:String):Double{val a=readInvoices(c).filter{it.type=="purchase"&&it.category==category&&it.item==item&&it.quantity>0};val q=a.sumOf{it.quantity};return if(q<=0)0.0 else a.sumOf{it.total}/q}

@Composable private fun OtherDialog(type:String,dismiss:()->Unit,save:(String,Double)->Unit){
 var name by rememberSaveable{mutableStateOf("")};var amount by rememberSaveable{mutableStateOf("")}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(type=="income")"إضافة إيراد" else "إضافة مصروف")},text={Column{OutlinedTextField(name,{name=it},label={Text("البيان")});OutlinedTextField(amount,{amount=it},label={Text("المبلغ بالدينار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}},confirmButton={TextButton(onClick={val v=amount.replace(',','.').toDoubleOrNull();if(name.isNotBlank()&&v!=null&&v>0)save(name,v)}){Text("حفظ")}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}

private data class MedicineInfo(val name:String,val group:String,val scientific:String,val purpose:String,val active:String,val concentration:String,val storage:String,val warnings:String,val withdrawal:String)
private data class HerdMetricRecord(val tag:String,val date:String,val weightKg:Double,val bcs:Double,val note:String)
private const val HERD_METRICS_KEY="herd_metrics_v29"
private fun readHerdMetrics(c:Context):List<HerdMetricRecord>{
 val a=JSONArray(c.getSharedPreferences(HERD_CARE_PREFS,0).getString(HERD_METRICS_KEY,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);HerdMetricRecord(x.optString("tag"),x.optString("date"),x.optDouble("weightKg"),x.optDouble("bcs"),x.optString("note"))}.getOrNull()}
}
private fun saveHerdMetric(c:Context,v:HerdMetricRecord){
 val all=readHerdMetrics(c).toMutableList();all.add(v)
 val a=JSONArray();all.forEach{x->a.put(JSONObject().put("tag",x.tag).put("date",x.date).put("weightKg",x.weightKg).put("bcs",x.bcs).put("note",x.note))}
 check(c.getSharedPreferences(HERD_CARE_PREFS,0).edit().putString(HERD_METRICS_KEY,a.toString()).commit())
}
private fun medicineInfo(name:String,group:String):MedicineInfo{
 val data=when(name){
  "لقاح التسمم المعوي"->Triple("لقاح مضاد لمرض التسمم المعوي","مستضدات سموم المطثيات، يختلف تركيبها حسب المنتج","الوقاية ضمن برنامج تحصين يحدده الطبيب البيطري")
  "لقاح جدري الأغنام"->Triple("لقاح جدري الأغنام","فيروس جدري الأغنام المضعّف أو مستحضر معطل حسب المنتج","الوقاية من جدري الأغنام؛ نوع اللقاح وبرنامج التحصين حسب النشرة")
  "لقاح الجدري"->Triple("لقاح جدري الأغنام","فيروس جدري الأغنام المضعّف أو مستحضر معطل حسب المنتج","الوقاية من جدري الأغنام؛ نوع اللقاح وبرنامج التحصين حسب النشرة")
  "لقاح الحمى القلاعية"->Triple("لقاح الحمى القلاعية","مستضدات فيروس الحمى القلاعية للأنماط المصلية المحددة على العبوة","الوقاية وفق النمط المصلي المتداول والبرنامج البيطري الرسمي")
  "لقاح طاعون المجترات الصغيرة PPR"->Triple("لقاح طاعون المجترات الصغيرة","مستضد فيروس PPR؛ غالباً لقاح حي مضعّف بحسب المنتج","الوقاية من طاعون المجترات الصغيرة وفق البرنامج البيطري")
  "لقاح الكلوستريديا"->Triple("لقاح الكلوستريديا","ذوفانات/مستضدات كلوستريدية متعددة بحسب التركيبة","الوقاية من أمراض كلوستريدية محددة وفق تركيب اللقاح")
  "لقاح البروسيلا Rev.1"->Triple("لقاح البروسيلا Rev.1","سلالة لقاحية حية Rev.1","مقيد ضمن برنامج المكافحة الرسمي وتحت إشراف المصالح البيطرية فقط")
   "لقاح الإكثيما المعدية (Orf)"->Triple("لقاح الإكثيما المعدية","لقاح حي موضعي بحسب المنتج","يُبحث فقط عند وجود خطر محلي وبقرار بيطري")
  "ألبيندازول (Albendazole)"->Triple("Albendazole","ألبيندازول","مضاد لبعض الديدان الداخلية؛ لا يغطي كل الطفيليات")
  "فينبندازول (Fenbendazole)"->Triple("Fenbendazole","فينبندازول","مضاد لبعض الديدان المعوية والرئوية بحسب الطفيلي والمنتج")
  "إيفرمكتين (Ivermectin)"->Triple("Ivermectin","إيفرمكتين","مضاد لبعض الطفيليات الداخلية والخارجية؛ لا يعالج جميع أنواع الديدان")
  "ليفاميزول (Levamisole)"->Triple("Levamisole","ليفاميزول","مضاد لبعض الديدان الأسطوانية؛ هامش الأمان يتطلب دقة بيطرية")
  "تريكلابندازول"->Triple("Triclabendazole","تريكلابندازول","مضاد للفاشيولا (ديدان الكبد) في مراحل محددة حسب المنتج")
  "بخاخات الجروح"->Triple("مستحضرات موضعية للجروح","مطهرات أو مواد حماية تختلف حسب المنتج","تنظيف وحماية الجروح السطحية وفق النشرة؛ الجروح العميقة تحتاج فحصاً بيطرياً")
   "مستحضرات الرش والغمس"->Triple("مستحضرات مكافحة الطفيليات الخارجية","مواد فعالة وتركيزات تختلف حسب المنتج","مكافحة القراد والقمل والطفيليات الخارجية بعد التشخيص")
   "مستحضرات السكب الموضعي"->Triple("مستحضرات سكب موضعي","مواد جلدية تختلف حسب المنتج","مكافحة بعض الطفيليات الخارجية؛ لا تستخدم مستحضرات أنواع أخرى دون اعتماد للأغنام")
   "مستحضرات الجرب والقمل"->Triple("مستحضرات علاج الجرب والقمل","مواد مضادة للطفيليات الخارجية","تستخدم بعد تحديد الطفيلي ومعالجة المخالطين والبيئة")
   "مطهرات الجلد والجروح"->Triple("مطهرات جلدية بيطرية","يود أو كلورهيكسيدين بتراكيز مخصصة","تنظيف الجلد والجروح السطحية وفق نشرة المنتج")
   "فيتامينات A وD وE"->Triple("مكملات الفيتامينات","فيتامينات ذائبة في الدهون","تستخدم عند الحاجة؛ الإفراط قد يسبب سمية")
   "مكملات الأملاح المعدنية"->Triple("مكملات أملاح معدنية للأغنام","خليط معدني متوازن مخصص للأغنام","دعم العليقة؛ اختر خلطة خاصة بالأغنام")
   "مكملات العناصر النادرة"->Triple("مكملات العناصر النادرة","قد تشمل السيلينيوم والنحاس والزنك واليود","تُستخدم بعد تقييم العليقة؛ الزيادة قد تسبب تسمماً")
   "مطهر سرة الحملان"->Triple("مطهر سرة المواليد","مطهر موضعي بتركيز مناسب","تطهير السرة بعد الولادة والحد من العدوى")
   "بدائل حليب الحملان"->Triple("بديل حليب الحملان","مسحوق حليب مخصص للحملان","تغذية الحملان عند الحاجة بعد تأمين اللبأ أولاً")
   "زجاجة رضاعة الحملان"->Triple("زجاجة رضاعة الحملان","أداة تغذية وليست دواءً","إرضاع اللبأ أو الحليب مع تنظيف الأداة بعد كل استعمال")
   "مضادات حيوية بيطرية بوصفة"->Triple("مضادات حيوية بيطرية","مواد مضادة للبكتيريا تختلف حسب التشخيص","لا تستخدم إلا بوصفة؛ احترم الجرعة وفترة السحب")
   "مضادات التهاب ومسكنات بوصفة"->Triple("مضادات التهاب ومسكنات","مواد دوائية مختلفة ذات موانع استعمال","تستخدم بوصفة بعد تقييم الجفاف والحمل وفترة السحب")
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
 val storage=if(group.startsWith("اللقاحات"))"اتبع سلسلة التبريد المحددة على العبوة؛ كثير من اللقاحات تحفظ مبردة ولا تُجمّد. لا تستخدم عبوة تعرضت لخلل تخزين دون استشارة." else "يُحفظ حسب النشرة، بعيداً عن الحرارة والرطوبة والضوء عند الحاجة، وبعيداً عن متناول الأطفال."
 val warning=if(name.startsWith("لقاح البروسيلا"))"قد يشكل خطراً على الإنسان، خصوصاً الحوامل وذوي المناعة الضعيفة. لا يُتعامل معه إلا بواسطة مختص ووفق البرنامج الرسمي." else if(name=="أوكسيتوسين (Oxytocin)"||name=="البروستاغلاندين")"أدوية هرمونية ذات موانع استعمال ومخاطر؛ لا تستخدم لتحفيز الولادة أو التناسل دون فحص وقرار بيطري." else "لا تحدد الجرعة أو طريقة الإعطاء من اسم المادة وحده. راجع الطبيب البيطري ونشرة المنتج المسجل، وتحقق من ملاءمته للأغنام والحمل والرضاعة."
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
 var animals by remember{mutableStateOf(activeHerdAnimals(loadHerd(context)))}
 var reminders by remember{mutableStateOf(readHerdCare(context))}
 var selectedTag by rememberSaveable{mutableStateOf<String?>(null)}
 var dialog by remember{mutableStateOf(false)}
 var kind by remember{mutableStateOf("تطعيم")}
 var title by remember{mutableStateOf("")}
 var date by remember{mutableStateOf(LocalDate.now().plusDays(7).toString())}
 var notes by remember{mutableStateOf("")}
 var metricDialog by remember{mutableStateOf(false)}
 var metricWeight by remember{mutableStateOf("")}
 var metricBcs by remember{mutableStateOf("3.0")}
 var metricNote by remember{mutableStateOf("")}
 var metrics by remember{mutableStateOf(readHerdMetrics(context))}
 val selected=animals.firstOrNull{it.tag==selectedTag}
 val today=LocalDate.now()
 val openReminders=reminders.filter{!it.completed}.sortedBy{it.date}
 val overdueCount=openReminders.count{runCatching{LocalDate.parse(it.date).isBefore(today)}.getOrDefault(false)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   TextButton(onClick=onBack){Icon(Icons.Outlined.ArrowForward,null);Text("العودة إلى دليل الأدوية")}
   Spacer(Modifier.weight(1f))
   Text("متابعة القطيع",fontWeight=FontWeight.ExtraBold,color=FinGreen,fontSize=15.sp)
  }
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
   FinMetric("إجمالي الرؤوس",animals.size.toString(),Icons.Outlined.Pets,Modifier.weight(1f))
   FinMetric("مواعيد مسجلة",openReminders.size.toString(),Icons.Outlined.Event,Modifier.weight(1f))
  }
  if(openReminders.isNotEmpty()){
   Surface(color=if(overdueCount>0)Color(0xFFFFF0EA) else Color(0xFFEAF1E9),shape=RoundedCornerShape(14.dp)){
    Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
     Text(if(overdueCount>0)"لديك $overdueCount موعداً متأخراً" else "المتابعات القادمة",fontWeight=FontWeight.Bold,color=if(overdueCount>0)Color(0xFF9F443B) else FinGreen,fontSize=11.sp)
     openReminders.take(4).forEach{r->Text("${r.tag} • ${r.title} — ${r.date}",fontSize=9.sp,color=FinInk)}
    }
   }
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
     val animalMetrics=metrics.filter{it.tag==selected.tag}.sortedByDescending{it.date}
     Text("آخر تقييم تغذوي: "+(animalMetrics.firstOrNull()?.let{"BCS ${"%.1f".format(Locale.US,it.bcs)} • ${"%.1f".format(Locale.US,it.weightKg)} كغ • ${it.date}"}?:"لا يوجد سجل بعد"),fontSize=10.sp,color=FinGreen)
     OutlinedButton(onClick={metricWeight=selected.weightKg?.toString()?:"";metricBcs="3.0";metricNote="";metricDialog=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.MonitorWeight,null);Spacer(Modifier.width(6.dp));Text("تسجيل وزن وتقييم حالة الجسم BCS")}
     if(animalMetrics.isNotEmpty()){
      Text("سجل القياسات",fontWeight=FontWeight.Bold,color=FinInk,fontSize=11.sp)
      animalMetrics.take(5).forEach{m->Text("${m.date} • ${"%.1f".format(Locale.US,m.weightKg)} كغ • BCS ${"%.1f".format(Locale.US,m.bcs)}"+(if(m.note.isBlank())"" else " • "+m.note),fontSize=9.sp,color=FinMuted)}
     }
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
      val dueState=runCatching{val d=LocalDate.parse(r.date);when{r.completed->"مكتمل";d.isBefore(today)->"متأخر";d==today->"موعد اليوم";d<=today.plusDays(7)->"خلال 7 أيام";else->"قادم"}}.getOrDefault("تحقق من التاريخ")
      Column(Modifier.weight(1f)){Text(r.title,fontWeight=FontWeight.Bold,color=FinInk,fontSize=11.sp);Text(r.kind+" • "+r.date,fontSize=10.sp,color=FinGreen);Text(dueState,fontSize=9.sp,color=if(dueState=="متأخر")Color(0xFFB54D42) else FinGreen);if(r.notes.isNotBlank())Text(r.notes,fontSize=9.sp,color=FinMuted)}
      Checkbox(checked=r.completed,onCheckedChange={v->reminders=reminders.map{if(it.id==r.id)it.copy(completed=v)else it};saveHerdCare(context,reminders)})
     }
    }
   }
   Text("التوصيات الصحية",fontWeight=FontWeight.ExtraBold,color=FinInk,fontSize=13.sp)
   Surface(color=Color(0xFFF6EEDF),shape=RoundedCornerShape(13.dp)){Text("لا تُفرض جرعات أو أدوية تلقائياً. يختلف برنامج التحصين وتوقيت مضادات الطفيليات باختلاف الولاية والوضع الوبائي واللقاح المسجل وعمر الحيوان وحالته والحمل وفترة السحب. سجّل موعد الطبيب أو البرنامج البيطري المحلي هنا، ولا تعتمد موعداً عاماً بديلاً عن الطبيب.",Modifier.padding(12.dp),fontSize=10.sp,color=FinInk,lineHeight=15.sp)}
  }
 }
 if(metricDialog){
  AlertDialog(onDismissRequest={metricDialog=false},title={Text("تسجيل قياس صحي")},text={
   Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
    OutlinedTextField(metricWeight,{metricWeight=it},label={Text("الوزن بالكيلوغرام")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
    OutlinedTextField(metricBcs,{metricBcs=it},label={Text("درجة حالة الجسم BCS من 1 إلى 5")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
    OutlinedTextField(metricNote,{metricNote=it},label={Text("ملاحظات اختيارية")},minLines=2)
    Text("سجّل التقييم بالتحسس والفحص؛ لا تستنتج BCS من الوزن وحده.",fontSize=9.sp,color=FinMuted)
   }
  },confirmButton={TextButton(onClick={
   val w=metricWeight.replace(',','.').toDoubleOrNull();val b=metricBcs.replace(',','.').toDoubleOrNull()
   if(selected!=null&&w!=null&&w in 0.5..250.0&&b!=null&&b in 1.0..5.0){
    saveHerdMetric(context,HerdMetricRecord(selected.tag,LocalDate.now().toString(),w,b,metricNote.trim()))
    metrics=readHerdMetrics(context);metricDialog=false
   }
  }){Text("حفظ القياس")}},dismissButton={TextButton(onClick={metricDialog=false}){Text("إلغاء")}})
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
 val medicineLots=readMedicineLots(c)
 val today=LocalDate.now().toString()
 val available=groups.values.flatten().count{usableMedicineStock(it,stock[it]?:0.0,medicineLots,today)>0.0}
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
         val usable=usableMedicineStock(name,stock[name]?:0.0,medicineLots,today)
         Text("المخزون الصالح: "+String.format(Locale.US,"%.2f",usable),fontSize=10.sp,color=if(usable>0)FinGreen else FinMuted,fontWeight=FontWeight.Bold)
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
      Text("الرصيد الصالح للاستخدام: "+String.format(Locale.US,"%.2f",usableMedicineStock(selected!!,stock[selected]?:0.0,medicineLots,today)),fontSize=12.sp,color=FinGreen,fontWeight=FontWeight.Bold)
     }
    }}
    item{MedicineInfoCard("ما هو؟",info.scientific,Icons.Outlined.Science)}
    item{MedicineInfoCard("ما استعماله؟",info.purpose,Icons.Outlined.MedicalServices)}
    item{MedicineInfoCard("المادة الفعالة",info.active,Icons.Outlined.Biotech)}
    item{MedicineInfoCard("التركيز",info.concentration,Icons.Outlined.Opacity)}
    item{MedicineInfoCard("طريقة الحفظ",info.storage,Icons.Outlined.Inventory2)}
    item{
     val lots=readMedicineLots(c).filter{it.item==selected}.sortedBy{it.expiry}
     val today=LocalDate.now().toString()
     Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,FinLine)){
      Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Text("التشغيلات والصلاحية",fontWeight=FontWeight.Bold,color=FinInk,fontSize=12.sp)
       if(lots.isEmpty())Text("لا توجد بيانات تشغيلات مسجلة لهذا الصنف. أضفها عند الشراء.",fontSize=10.sp,color=FinMuted)
       lots.forEach{lot->
        val expired=lot.expiry.isNotBlank()&&lot.expiry<today
        Row(verticalAlignment=Alignment.CenterVertically){
         Column(Modifier.weight(1f)){Text("تشغيلة: "+lot.batch,fontSize=10.sp,fontWeight=FontWeight.Bold,color=FinInk);Text("الصلاحية: "+lot.expiry+" • المورد: "+lot.supplier,fontSize=9.sp,color=FinMuted)}
         val nearExpiry=runCatching{!expired&&LocalDate.parse(lot.expiry)<=LocalDate.now().plusDays(30)}.getOrDefault(false)
         Column(horizontalAlignment=Alignment.End){Text(String.format(Locale.US,"%.2f",lot.quantity),fontSize=11.sp,fontWeight=FontWeight.Bold,color=FinGreen);if(expired)Text("منتهية — لا تُستخدم",fontSize=9.sp,color=Color(0xFFB54D42)) else if(nearExpiry)Text("تنتهي خلال 30 يوماً",fontSize=9.sp,color=Color(0xFFB18443)) else Text("ضمن الصلاحية المسجلة",fontSize=9.sp,color=FinGreen)}
        }
       }
      }
     }
    }
    item{MedicineInfoCard("التحذيرات",info.warnings,Icons.Outlined.WarningAmber)}
    item{MedicineInfoCard("فترة السحب",info.withdrawal,Icons.Outlined.Schedule)}
    item{Surface(color=Color(0xFFF6EEDF),shape=RoundedCornerShape(15.dp)){Text("تنبيه بيطري: هذه البطاقة للتعريف والتثقيف فقط، ولا تقدم جرعات أو تشخيصاً أو بديلاً عن الطبيب البيطري. اعتمد دائماً النشرة الخاصة بالمنتج المسجل في بلدك.",Modifier.padding(13.dp),fontSize=10.sp,color=FinInk,lineHeight=16.sp)}}
   }
  }
 }
}

@Composable private fun MedicinePackArt(name:String,group:String,modifier:Modifier=Modifier){
 val vaccine=group.startsWith("اللقاحات")
 val spray=name.contains("رش")||name.contains("بخاخ")
 val tube=name.contains("مطهر")||name.contains("التهابات الضرع")||name.contains("الحوافر")
 val powder=name.contains("أملاح")||name.contains("الإسهال")||name.contains("اللبأ")||name.contains("فيتامينات")||name.contains("العناصر النادرة")
 val milk=name.contains("حليب الحملان")||name.contains("رضاعة الحملان")
 val pour=name.contains("السكب الموضعي")||name.contains("الجرب والقمل")
 val syringe=name.contains("أوكسيتوسين")||name.contains("البروستاغلاندين")
 val accent=when{
  name.contains("PPR")->Color(0xFF287D78);name.contains("الجدري")->Color(0xFF9A654A)
  name.contains("الكلوستريديا")||name.contains("التسمم")->Color(0xFF477B58)
  name.contains("الحمى القلاعية")->Color(0xFF477C9B);name.contains("البروسيلا")->Color(0xFF9B5B70)
  group.contains("الطفيليات")->Color(0xFFB18443);group.contains("الفيتامينات")->Color(0xFFB58A43)
  group.contains("الحملان")->Color(0xFF5C8B78);group.contains("الجروح")->Color(0xFF56899A)
  group.contains("الجفاف")->Color(0xFF4D8F88);group.contains("الولادة")->Color(0xFF9276A6)
  group.contains("بوصفة")->Color(0xFF9B6262);else->Color(0xFF66836A)
 }
 val mark=when{
  name.contains("PPR")->"PPR";name.contains("الجدري")->"POX"
  name.contains("الكلوستريديا")||name.contains("التسمم")->"CDT"
  name.contains("الحمى القلاعية")->"FMD";name.contains("البروسيلا")->"REV.1"
  name.contains("Orf")||name.contains("الإكثيما")->"ORF"
  spray->"SPRAY";powder->"ORAL";milk->"MILK";pour->"POUR-ON";tube->"CARE";syringe->"INJECT";else->"VET"
 }
 Box(modifier.background(Color(0xFFF4F1E8),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){
  when{
   vaccine->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(25.dp).height(8.dp).background(accent,RoundedCornerShape(3.dp)));Box(Modifier.width(40.dp).height(49.dp).background(Color.White,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Vaccines,null,tint=accent,modifier=Modifier.size(22.dp));Text(mark,fontSize=6.sp,color=accent,fontWeight=FontWeight.ExtraBold)}}}
   syringe->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(7.dp).height(35.dp).background(Color.LightGray,RoundedCornerShape(3.dp)));Box(Modifier.width(43.dp).height(13.dp).background(Color.White,RoundedCornerShape(3.dp)),contentAlignment=Alignment.Center){Box(Modifier.fillMaxWidth(.7f).height(5.dp).background(accent.copy(alpha=.4f)))};Box(Modifier.width(13.dp).height(3.dp).background(accent))}
   milk->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(29.dp).height(8.dp).background(accent,RoundedCornerShape(3.dp)));Box(Modifier.width(48.dp).height(51.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.LocalDrink,null,tint=accent,modifier=Modifier.size(22.dp));Text("LAMB",fontSize=6.sp,color=accent,fontWeight=FontWeight.Bold)}}}
   pour->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(17.dp).height(8.dp).background(accent));Box(Modifier.width(43.dp).height(47.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.WaterDrop,null,tint=accent,modifier=Modifier.size(22.dp))}}
   tube->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(19.dp).height(7.dp).background(accent));Box(Modifier.width(35.dp).height(46.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Healing,null,tint=accent,modifier=Modifier.size(20.dp))}}
   spray->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(17.dp).height(8.dp).background(accent));Box(Modifier.width(11.dp).height(7.dp).background(accent.copy(alpha=.7f)));Box(Modifier.width(34.dp).height(47.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Air,null,tint=accent,modifier=Modifier.size(22.dp))}}
   powder->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(39.dp).height(8.dp).background(accent));Box(Modifier.width(53.dp).height(43.dp).background(Color.White,RoundedCornerShape(7.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Science,null,tint=accent,modifier=Modifier.size(19.dp));Text(mark,fontSize=6.sp,color=accent,fontWeight=FontWeight.Bold)}}}
   else->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.width(18.dp).height(8.dp).background(accent));Box(Modifier.width(43.dp).height(52.dp).background(Color.White,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Medication,null,tint=accent,modifier=Modifier.size(22.dp));Text(mark,fontSize=6.sp,color=accent,fontWeight=FontWeight.Bold)}}}
  }
  Text(mark,fontSize=6.sp,color=accent,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp))
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
