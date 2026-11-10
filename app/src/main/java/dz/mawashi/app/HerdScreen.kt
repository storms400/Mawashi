package dz.mawashi.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val HerdGreen = Color(0xFF526B55)
private val HerdCream = Color(0xFFF7F3EA)
private val HerdInk = Color(0xFF27332A)
private val HerdMuted = Color(0xFF778078)
private val HerdLine = Color(0xFFE8E4DA)
private const val HERD_PREFS = "mawashi_herd_local"
private const val HERD_KEY = "animals"
internal data class HerdAnimal(val id:String,val tag:String,val kind:String,val ageMonths:Int,val health:String,val notes:String,val weightKg:Double?=null,val birthDate:String="",val birthWeightKg:Double?=null,val motherTag:String="",val productionState:String="نعجة جافة",val pregnancyStage:String="غير حامل",val fetusCount:Int=1,val nursingLambs:Int=1,val lifecycleStatus:String="نشط",val exitDate:String="",val exitReason:String="")

internal fun loadHerd(c:Context):List<HerdAnimal> {
 val raw=c.getSharedPreferences(HERD_PREFS,0).getString(HERD_KEY,"[]") ?: "[]"
 return try {
  val a=JSONArray(raw); val result=mutableListOf<HerdAnimal>()
  for(i in 0 until a.length()) {
   try {
    val x=a.getJSONObject(i)
    val id=x.optString("id").ifBlank{UUID.randomUUID().toString()}
    val tag=x.optString("tag").trim()
    if(tag.isNotBlank() && result.none{it.tag.equals(tag,true)}) result.add(
     HerdAnimal(id,tag,x.optString("kind","غير محدد"),x.optInt("ageMonths"),x.optString("health","غير محددة"),x.optString("notes"),
      if(x.has("weightKg")&&!x.isNull("weightKg"))x.optDouble("weightKg") else null,
      x.optString("birthDate"),if(x.has("birthWeightKg")&&!x.isNull("birthWeightKg"))x.optDouble("birthWeightKg") else null,x.optString("motherTag"),x.optString("productionState","نعجة جافة"),x.optString("pregnancyStage","غير حامل"),x.optInt("fetusCount",1),x.optInt("nursingLambs",1),x.optString("lifecycleStatus","نشط"),x.optString("exitDate"),x.optString("exitReason"))
    )
   } catch(_:Exception) { /* تجاهل السجل التالف مع الحفاظ على بقية القطيع */ }
  }
  result
 } catch(_:Exception){ emptyList() }
}
internal fun saveHerd(c:Context,list:List<HerdAnimal>) {
 val a=JSONArray()
 list.forEach{x->a.put(JSONObject().apply{
  put("id",x.id);put("tag",x.tag);put("kind",x.kind);put("ageMonths",x.ageMonths);put("health",x.health);put("notes",x.notes)
  if(x.weightKg==null)put("weightKg",JSONObject.NULL) else put("weightKg",x.weightKg)
  put("birthDate",x.birthDate)
  if(x.birthWeightKg==null)put("birthWeightKg",JSONObject.NULL) else put("birthWeightKg",x.birthWeightKg)
  put("motherTag",x.motherTag);put("productionState",x.productionState);put("pregnancyStage",x.pregnancyStage);put("fetusCount",x.fetusCount);put("nursingLambs",x.nursingLambs);put("lifecycleStatus",x.lifecycleStatus);put("exitDate",x.exitDate);put("exitReason",x.exitReason)
 })}
 check(c.getSharedPreferences(HERD_PREFS,0).edit().putString(HERD_KEY,a.toString()).commit()) { "تعذر حفظ بيانات القطيع" }
}


internal fun generateHerdTags(existingTags:List<String>,count:Int):List<String>{
 require(count in 1..500)
 val used=existingTags.mapNotNull{Regex("^Q-([0-9]+)$",RegexOption.IGNORE_CASE).matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull()}.toMutableSet()
 val result=mutableListOf<String>();var n=1
 while(result.size<count){if(n !in used){result.add("Q-"+n.toString().padStart(4,'0'));used.add(n)};n++}
 return result
}
private fun herdQrBitmap(value:String,size:Int=480):Bitmap{
 val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,size,size,mapOf(EncodeHintType.MARGIN to 1))
 val pixels=IntArray(size*size)
 for(y in 0 until size)for(x in 0 until size)pixels[y*size+x]=if(matrix.get(x,y))AndroidColor.BLACK else AndroidColor.WHITE
 return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
}
private class HerdQrPrintAdapter(context:Context,private val animals:List<HerdAnimal>):PrintDocumentAdapter(){
 private val app=context.applicationContext
 override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes,cancellationSignal:CancellationSignal,callback:LayoutResultCallback,metadata:Bundle?){
  if(cancellationSignal.isCanceled){callback.onLayoutCancelled();return}
  callback.onLayoutFinished(PrintDocumentInfo.Builder("Mawashi-QR.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(((animals.size+7)/8).coerceAtLeast(1)).build(),true)
 }
 override fun onWrite(pages:Array<PageRange>,destination:ParcelFileDescriptor,cancellationSignal:CancellationSignal,callback:WriteResultCallback){
  val attrs=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("mawashi","QR",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
  val pdf=PrintedPdfDocument(app,attrs);val total=((animals.size+7)/8).coerceAtLeast(1)
  try{
   for(p in 0 until total){if(cancellationSignal.isCanceled){callback.onWriteCancelled();return};if(pages.none{p in it.start..it.end})continue
    val page=pdf.startPage(p+1);val c=page.canvas;val cw=c.width/2f-34f;val ch=c.height/4f-25f
    for(slot in 0 until 8){val i=p*8+slot;if(i>=animals.size)break
     val a=animals[i];val left=20f+(slot%2)*(cw+24f);val top=18f+(slot/2)*(ch+14f)
     val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textAlign=android.graphics.Paint.Align.CENTER}
     paint.style=android.graphics.Paint.Style.STROKE;paint.color=AndroidColor.LTGRAY;c.drawRoundRect(left,top,left+cw,top+ch,10f,10f,paint)
     paint.style=android.graphics.Paint.Style.FILL;paint.color=AndroidColor.BLACK;paint.textSize=14f;c.drawText("مواشي",left+cw/2,top+20f,paint)
     val qr=herdQrBitmap(a.id+"|"+a.tag,220);val q=minOf(ch-42f,108f);val x=left+(cw-q)/2;val y=top+25f
     c.drawBitmap(qr,null,android.graphics.RectF(x,y,x+q,y+q),paint);paint.textSize=16f;paint.isFakeBoldText=true;c.drawText(a.tag,left+cw/2,top+ch-9f,paint)
    };pdf.finishPage(page)
   }
   FileOutputStream(destination.fileDescriptor).use{pdf.writeTo(it)};callback.onWriteFinished(arrayOf(PageRange(0,total-1)))
  }catch(e:Exception){callback.onWriteFailed(e.message)}finally{pdf.close()}
 }
}
private fun printHerdQr(context:Context,animals:List<HerdAnimal>){
 if(animals.isEmpty())return
 val attrs=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("mawashi","QR",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
 (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("مواشي - رموز القطيع",HerdQrPrintAdapter(context,animals),attrs)
}

@Composable fun HerdScreen(modifier:Modifier=Modifier){
 val context=LocalContext.current
 val animals=remember{mutableStateListOf<HerdAnimal>().apply{addAll(loadHerd(context))}}
 var search by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("الكل")}
 var editor by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<HerdAnimal?>(null)};var deleting by remember{mutableStateOf<HerdAnimal?>(null)};var exitDate by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var exitReason by rememberSaveable{mutableStateOf("بيع")};var batchDialog by remember{mutableStateOf(false)};var batchCount by rememberSaveable{mutableStateOf("10")}
 val visible=animals.filter{it.tag.contains(search.trim(),true)&&(if(filter=="الأرشيف")it.lifecycleStatus=="مؤرشف" else it.lifecycleStatus=="نشط"&&(filter=="الكل"||it.kind==filter))}
 Column(modifier.fillMaxSize().background(HerdCream).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Spacer(Modifier.height(7.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("القطيع",color=HerdInk,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text("متابعة كل نعجة وحمل بصحة ووضوح",color=HerdMuted,fontSize=12.sp)};Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){FilledTonalButton(onClick={batchDialog=true},contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Outlined.QrCode2,null);Text("ترقيم دفعة",fontSize=10.sp)};FilledTonalIconButton(onClick={selected=null;editor=true}){Icon(Icons.Outlined.Add,"إضافة فردية",tint=HerdGreen)}}}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){HerdStat("إجمالي القطيع",animals.count{it.lifecycleStatus=="نشط"}.toString(),Modifier.weight(1f));HerdStat("النعاج",animals.count{it.lifecycleStatus=="نشط"&&it.kind=="نعجة"}.toString(),Modifier.weight(1f));HerdStat("الحملان",animals.count{it.lifecycleStatus=="نشط"&&it.kind=="حمل/رخلة"}.toString(),Modifier.weight(1f))}
  if(animals.isNotEmpty())OutlinedButton(onClick={printHerdQr(context,animals.toList())},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Print,null);Text("طباعة رموز QR للقطيع (${animals.size})")}
  OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("ابحث برقم التعريف",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("الكل","نعجة","كبش","حمل/رخلة","غير محدد","الأرشيف").forEach{FilterChip(filter==it,{filter=it},label={Text(it,fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}}
  if(visible.isEmpty()){
   Column(Modifier.fillMaxWidth().weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    EweLogo(76);Spacer(Modifier.height(12.dp));Text(if(animals.isEmpty())"ابدأ بتسجيل حيوانات القطيع" else "لا توجد نتائج مطابقة",color=HerdInk,fontWeight=FontWeight.Bold)
    Text(if(animals.isEmpty())"امنح كل حيوان رقم تعريف لتسهيل متابعته" else "جرّب تغيير البحث أو نوع الحيوان",color=HerdMuted,fontSize=11.sp)
    if(animals.isEmpty()){Spacer(Modifier.height(12.dp));Button(onClick={batchDialog=true},colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Icon(Icons.Outlined.QrCode2,null);Text("ترقيم مجموعة")}}
   }
  }else{
   Text("الحيوانات المسجلة (${visible.size})",color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp)
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){items(visible,key={it.id}){a->Surface(onClick={selected=a},modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){EweLogo(45);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.tag,color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp);Text("${a.kind} • ${a.ageMonths} شهر • ${a.health}",color=HerdMuted,fontSize=10.sp);Text("الوزن الحالي: ${a.weightKg?.let{"%.2f".format(java.util.Locale.US,it)}?.plus(" كغ")?:"غير مسجل"}",color=HerdGreen,fontSize=10.sp,fontWeight=FontWeight.SemiBold);Text("الحالة الإنتاجية: ${a.productionState}${if(a.productionState=="نعجة حامل")" • ${a.pregnancyStage} • ${if(a.fetusCount>=2)"توأم" else "جنين واحد"}" else if(a.productionState=="نعجة مرضعة")" • ${a.nursingLambs} حمل" else ""}",color=HerdMuted,fontSize=9.sp);if(a.kind=="حمل/رخلة")Text("الميلاد: ${a.birthDate.ifBlank{"غير مسجل"}} • وزن الميلاد: ${a.birthWeightKg?.let{"%.2f".format(java.util.Locale.US,it)+" كغ"}?:"غير مسجل"} • الأم: ${a.motherTag.ifBlank{"غير مسجل"}}",color=HerdMuted,fontSize=9.sp);if(a.lifecycleStatus=="مؤرشف")Text("مؤرشف • ${a.exitReason} • ${a.exitDate}",color=Color(0xFF9A5B32),fontSize=10.sp,fontWeight=FontWeight.SemiBold);if(a.notes.isNotBlank())Text(a.notes,color=HerdMuted,fontSize=10.sp,maxLines=1)};IconButton(onClick={selected=a;editor=true}){Icon(Icons.Outlined.Edit,"تعديل",tint=HerdGreen)};if(a.lifecycleStatus=="نشط")IconButton(onClick={deleting=a;exitDate=LocalDate.now().toString();exitReason="بيع"}){Icon(Icons.Outlined.Archive,"أرشفة الحيوان",tint=HerdMuted)} else IconButton(onClick={val i=animals.indexOfFirst{it.id==a.id};if(i>=0){animals[i]=a.copy(lifecycleStatus="نشط",exitDate="",exitReason="");saveHerd(context,animals.toList())}}){Icon(Icons.Outlined.Restore,"إعادة إلى القطيع",tint=HerdGreen)}}}}}
 }
 }
 if(batchDialog)AlertDialog(onDismissRequest={batchDialog=false},title={Text("ترقيم مجموعة من القطيع")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("أدخل عدد الحيوانات الجديدة. سينشئ التطبيق رقماً متسلسلاً ورمز QR فريداً لكل حيوان.",fontSize=12.sp,color=HerdMuted)
  OutlinedTextField(batchCount,{batchCount=it.filter(Char::isDigit)},label={Text("عدد الحيوانات")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
  Text("مثال: Q-0001، Q-0002، Q-0003",color=HerdGreen,fontSize=11.sp)
  Text("تُنشأ البطاقات بنوع غير محدد لتعبئة العمر والوزن والحالة الصحية لاحقاً.",fontSize=11.sp,color=HerdMuted)
 }},confirmButton={TextButton(onClick={val n=batchCount.toIntOrNull();if(n!=null&&n in 1..500){generateHerdTags(animals.map{it.tag},n).forEach{tag->animals.add(HerdAnimal(UUID.randomUUID().toString(),tag,"غير محدد",0,"غير محددة",""))};saveHerd(context,animals.toList());batchDialog=false}}){Text("إنشاء الأرقام",color=HerdGreen)}},dismissButton={TextButton(onClick={batchDialog=false}){Text("إلغاء")}})
 if(editor)HerdEditor(selected,animals.filter{it.id!=selected?.id}.map{it.tag},{editor=false;selected=null}){a->val i=animals.indexOfFirst{it.id==a.id};if(i>=0)animals[i]=a else animals.add(a);saveHerd(context,animals.toList());editor=false;selected=null}
 if(!editor)selected?.let{a->
  val qr=remember(a.id,a.tag){herdQrBitmap(a.id+"|"+a.tag)}
  AlertDialog(onDismissRequest={selected=null},title={Text("بطاقة الحيوان")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
   Image(qr.asImageBitmap(),contentDescription="رمز QR للحيوان",modifier=Modifier.size(170.dp))
   Text(a.tag,color=HerdGreen,fontSize=20.sp,fontWeight=FontWeight.ExtraBold)
   Text("النوع: ${a.kind} • العمر: ${a.ageMonths} شهر",fontSize=11.sp)
   Text("الوزن: ${a.weightKg?.let{"%.2f كغ".format(java.util.Locale.US,it)}?:"غير مسجل"}",fontSize=11.sp)
   Text("الحالة الصحية: ${a.health}",fontSize=11.sp)
   Text("الحالة الإنتاجية: ${a.productionState}",fontSize=11.sp)
   if(a.productionState=="نعجة حامل")Text("مرحلة الحمل: ${a.pregnancyStage} • عدد الأجنة: ${a.fetusCount}",fontSize=10.sp)
   if(a.productionState=="نعجة مرضعة")Text("عدد الحملان المرضعة: ${a.nursingLambs}",fontSize=10.sp)
   if(a.kind=="حمل/رخلة"){Text("تاريخ الميلاد: ${a.birthDate}",fontSize=10.sp);Text("وزن الميلاد: ${a.birthWeightKg?:0.0} كغ",fontSize=10.sp);Text("الأم: ${a.motherTag}",fontSize=10.sp)}
  }},confirmButton={Row{TextButton(onClick={printHerdQr(context,listOf(a))}){Icon(Icons.Outlined.Print,null);Text("طباعة QR")};TextButton(onClick={editor=true}){Icon(Icons.Outlined.Edit,null);Text("تعديل")}}},dismissButton={TextButton(onClick={selected=null}){Text("إغلاق")}})
 }
 deleting?.let{a->AlertDialog(onDismissRequest={deleting=null},title={Text("إخراج الحيوان من القطيع")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("سيُنقل ${a.tag} إلى الأرشيف مع الاحتفاظ بجميع بياناته وسجلاته.");Text("سبب الخروج");Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("بيع","نفوق","استبعاد").forEach{FilterChip(exitReason==it,{exitReason=it},label={Text(it,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};OutlinedTextField(exitDate,{exitDate=it},label={Text("تاريخ الخروج YYYY-MM-DD")},singleLine=true);Text("لن يُحذف سجل الحيوان نهائياً.",color=HerdMuted,fontSize=11.sp)}},confirmButton={TextButton(onClick={val i=animals.indexOfFirst{it.id==a.id};if(i>=0){animals[i]=a.copy(lifecycleStatus="مؤرشف",exitDate=exitDate,exitReason=exitReason);saveHerd(context,animals.toList())};deleting=null}){Text("تأكيد الأرشفة",color=HerdGreen)}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})}
}

@Composable private fun EweLogo(size:Int){
 Box(Modifier.size(size.dp).background(Color(0xFFE8EDE5),CircleShape),contentAlignment=Alignment.Center){
  Text("🐑",fontSize=(size*.55f).sp)
 }
}
@Composable private fun HerdStat(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,HerdLine)){Column(Modifier.padding(10.dp)){Text(value,color=HerdGreen,fontWeight=FontWeight.ExtraBold,fontSize=20.sp);Text(label,color=HerdMuted,fontSize=9.sp,maxLines=1)}}}
@Composable private fun HerdEditor(initial:HerdAnimal?,existing:List<String>,dismiss:()->Unit,save:(HerdAnimal)->Unit){
 var tag by remember(initial){mutableStateOf(initial?.tag?:"")};var kind by remember(initial){mutableStateOf(initial?.kind?:"نعجة")};var age by remember(initial){mutableStateOf(initial?.ageMonths?.toString()?:"")};var health by remember(initial){mutableStateOf(initial?.health?.takeUnless{it=="غير محددة"}?:"جيدة")};var notes by remember(initial){mutableStateOf(initial?.notes?:"")};var weight by remember(initial){mutableStateOf(initial?.weightKg?.toString()?:"")};var birthDate by remember(initial){mutableStateOf(initial?.birthDate?:"")};var birthWeight by remember(initial){mutableStateOf(initial?.birthWeightKg?.toString()?:"")};var motherTag by remember(initial){mutableStateOf(initial?.motherTag?:"")};var productionState by remember(initial){mutableStateOf(initial?.productionState?:if(initial?.kind=="كبش")"كبش" else if(initial?.kind=="حمل/رخلة")"خروف" else "نعجة جافة")};var pregnancyStage by remember(initial){mutableStateOf(initial?.pregnancyStage?:"غير حامل")};var fetusCount by remember(initial){mutableStateOf(initial?.fetusCount?.toString()?:"1")};var nursingLambs by remember(initial){mutableStateOf(initial?.nursingLambs?.toString()?:"1")};var error by remember{mutableStateOf<String?>(null)}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(initial==null)"إضافة حيوان للقطيع" else "تعديل بيانات الحيوان")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(tag,{tag=it;error=null},label={Text("رقم التعريف / الوسم")},singleLine=true,modifier=Modifier.fillMaxWidth());Text("نوع الحيوان");Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf("نعجة","كبش","حمل/رخلة","غير محدد").forEach{FilterChip(kind==it,{kind=it},label={Text(it,fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};OutlinedTextField(age,{age=it.filter(Char::isDigit);error=null},label={Text("العمر بالأشهر")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(weight,{weight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("الوزن الحالي (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth());if(kind=="حمل/رخلة"){Text("بيانات الميلاد",fontWeight=FontWeight.Bold,color=HerdGreen);OutlinedTextField(birthDate,{birthDate=it;error=null},label={Text("تاريخ الميلاد (YYYY-MM-DD)")},placeholder={Text("2026-11-10")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(birthWeight,{birthWeight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("وزن الميلاد (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(motherTag,{motherTag=it;error=null},label={Text("رقم تعريف الأم")},singleLine=true,modifier=Modifier.fillMaxWidth())};Text("الحالة الإنتاجية والتغذوية",fontWeight=FontWeight.Bold,color=HerdGreen);Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("نعجة جافة","نعجة حامل","نعجة مرضعة","كبش","حمل يرضع","خروف").forEach{FilterChip(productionState==it,{productionState=it},label={Text(it,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};if(productionState=="نعجة حامل"){Text("مرحلة الحمل");Row(modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf("بداية الحمل","منتصف الحمل","الأربعة أسابيع الأخيرة").forEach{FilterChip(pregnancyStage==it,{pregnancyStage=it},label={Text(it,fontSize=9.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};Row(verticalAlignment=Alignment.CenterVertically){Text("عدد الأجنة");listOf("1","2").forEach{FilterChip(fetusCount==it,{fetusCount=it},label={Text(if(it=="1")"جنين واحد" else "توأم")},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}}};if(productionState=="نعجة مرضعة"){Row(verticalAlignment=Alignment.CenterVertically){Text("عدد الحملان المرضعة");listOf("1","2").forEach{FilterChip(nursingLambs==it,{nursingLambs=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}}};Text("الحالة الصحية");Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){listOf("جيدة","تحتاج متابعة","مريضة").forEach{FilterChip(health==it,{health=it},label={Text(it,fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};OutlinedTextField(notes,{notes=it},label={Text("ملاحظات اختيارية")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3);error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp)}}},confirmButton={TextButton(onClick={val t=tag.trim();val m=age.toIntOrNull();val w=weight.replace(",",".").toDoubleOrNull();val bw=birthWeight.replace(",",".").toDoubleOrNull();val mother=motherTag.trim();error=when{t.isBlank()->"أدخل رقم التعريف.";existing.any{it.equals(t,true)}->"رقم التعريف مستخدم.";m==null||m !in 0..240->"أدخل عمراً صحيحاً من 0 إلى 240 شهراً.";weight.isNotBlank()&&(w==null||w<=0.0||w>300.0)->"أدخل وزناً صحيحاً بالكيلوغرام.";kind=="حمل/رخلة"&&!birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))->"أدخل تاريخ الميلاد بصيغة YYYY-MM-DD.";kind=="حمل/رخلة"&&(bw==null||bw<=0.0||bw>20.0)->"أدخل وزن الميلاد بالكيلوغرام.";kind=="حمل/رخلة"&&mother.isBlank()->"أدخل رقم تعريف الأم.";else->null};if(error==null)save(HerdAnimal(initial?.id?:java.util.UUID.randomUUID().toString(),t,kind,m!!,health,notes.trim(),w,if(kind=="حمل/رخلة")birthDate.trim() else "",if(kind=="حمل/رخلة")bw else null,if(kind=="حمل/رخلة")mother else "",productionState,if(productionState=="نعجة حامل")pregnancyStage else "غير حامل",if(productionState=="نعجة حامل")fetusCount.toIntOrNull()?:1 else 1,if(productionState=="نعجة مرضعة")nursingLambs.toIntOrNull()?:1 else 1,initial?.lifecycleStatus?:"نشط",initial?.exitDate?:"",initial?.exitReason?:""))}){Text("حفظ",color=HerdGreen)}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}
