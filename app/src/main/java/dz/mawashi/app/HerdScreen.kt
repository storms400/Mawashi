package dz.mawashi.app

import android.content.Context
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.Period

private val HerdGreen = Color(0xFF526B55)
private val HerdCream = Color(0xFFF7F3EA)
private val HerdInk = Color(0xFF27332A)
private val HerdMuted = Color(0xFF778078)
private val HerdLine = Color(0xFFE8E4DA)
private const val HERD_PREFS = "mawashi_herd_local"
private const val HERD_KEY = "animals"
internal data class HerdAnimal(val id:String,val tag:String,val kind:String,val ageMonths:Int,val health:String,val notes:String,val weightKg:Double?=null,val birthDate:String="",val birthWeightKg:Double?=null,val motherTag:String="",val productionState:String="نعجة جافة",val pregnancyStage:String="غير حامل",val fetusCount:Int=1,val nursingLambs:Int=1,val lifecycleStatus:String="نشط",val exitDate:String="",val exitReason:String="",val acquisitionCost:Double?=null,val breed:String="غير محددة",val pregnancyStartDate:String="",val expectedBirthDate:String="",val pregnancyScanDate:String="",val pregnancyScanOutcome:String="",val pregnancyOffspringSex:String="",val sex:String="",val repeatScanDate:String="",val birthConfirmedDate:String="",val weaningConfirmedDate:String="",val ageRecordedDate:String="")

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
      x.optString("birthDate"),if(x.has("birthWeightKg")&&!x.isNull("birthWeightKg"))x.optDouble("birthWeightKg") else null,x.optString("motherTag"),x.optString("productionState","نعجة جافة"),x.optString("pregnancyStage","غير حامل"),x.optInt("fetusCount",1),x.optInt("nursingLambs",1),x.optString("lifecycleStatus","نشط"),x.optString("exitDate"),x.optString("exitReason"),if(x.has("acquisitionCost")&&!x.isNull("acquisitionCost"))x.optDouble("acquisitionCost").takeIf{it.isFinite()&&it>=0.0} else null,x.optString("breed","غير محددة"),x.optString("pregnancyStartDate"),x.optString("expectedBirthDate"),x.optString("pregnancyScanDate"),x.optString("pregnancyScanOutcome"),x.optString("pregnancyOffspringSex"),x.optString("sex"),x.optString("repeatScanDate"),x.optString("birthConfirmedDate"),x.optString("weaningConfirmedDate"),x.optString("ageRecordedDate"))
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
  put("motherTag",x.motherTag);put("productionState",x.productionState);put("pregnancyStage",x.pregnancyStage);put("fetusCount",x.fetusCount);put("nursingLambs",x.nursingLambs);put("lifecycleStatus",x.lifecycleStatus);put("exitDate",x.exitDate);put("exitReason",x.exitReason);if(x.acquisitionCost==null)put("acquisitionCost",JSONObject.NULL) else put("acquisitionCost",x.acquisitionCost);put("breed",x.breed);put("pregnancyStartDate",x.pregnancyStartDate);put("expectedBirthDate",x.expectedBirthDate);put("pregnancyScanDate",x.pregnancyScanDate);put("pregnancyScanOutcome",x.pregnancyScanOutcome);put("pregnancyOffspringSex",x.pregnancyOffspringSex);put("sex",x.sex);put("repeatScanDate",x.repeatScanDate);put("birthConfirmedDate",x.birthConfirmedDate);put("weaningConfirmedDate",x.weaningConfirmedDate);put("ageRecordedDate",x.ageRecordedDate)
 })}
 check(c.getSharedPreferences(HERD_PREFS,0).edit().putString(HERD_KEY,a.toString()).commit()) { "تعذر حفظ بيانات القطيع" }
}

internal fun savePregnancyScanToHerd(
 c:Context, tag:String, outcome:String, scanDate:String, gestationDays:Int, fetusCount:Int, repeatScanDate:String, offspringSex:String
):Boolean {
 val scan=runCatching{LocalDate.parse(scanDate)}.getOrNull()?:return false
 val animals=loadHerd(c).toMutableList()
 val index=animals.indexOfFirst{it.tag.equals(tag.trim(),true)&&it.kind.trim()=="نعجة"}
 if(index<0)return false
 val old=animals[index]
 val updated=when(outcome){
  "حامل"->{
   if(gestationDays !in 1..145 || fetusCount !in 1..3 || !isValidPregnancyOffspringSex(offspringSex,fetusCount))return false
   val start=scan.minusDays(gestationDays.toLong())
   val due=scan.plusDays(147L-gestationDays)
   val stage=when{gestationDays<50->"بداية الحمل";gestationDays<105->"منتصف الحمل";else->"الأربعة أسابيع الأخيرة"}
   old.copy(productionState="نعجة حامل",pregnancyStage=stage,fetusCount=fetusCount,
    pregnancyStartDate=start.toString(),expectedBirthDate=due.toString(),
    pregnancyScanDate=scan.toString(),pregnancyScanOutcome="حامل",pregnancyOffspringSex=offspringSex,repeatScanDate="",birthConfirmedDate="",weaningConfirmedDate="")
  }
  "غير حامل"->old.copy(productionState="نعجة جافة",pregnancyStage="غير حامل",fetusCount=1,
    pregnancyStartDate="",expectedBirthDate="",pregnancyScanDate=scan.toString(),pregnancyScanOutcome="غير حامل",pregnancyOffspringSex="",repeatScanDate="")
  "غير حاسم"->{
   val repeat=runCatching{LocalDate.parse(repeatScanDate)}.getOrNull()?:return false
   if(!repeat.isAfter(scan))return false
   old.copy(pregnancyScanDate=scan.toString(),pregnancyScanOutcome="غير حاسم",repeatScanDate=repeat.toString())
  }
  else->return false
 }
 animals[index]=updated
 val persisted=runCatching {
  saveHerd(c,animals)
  loadHerd(c).firstOrNull{it.id==updated.id && it.tag.equals(updated.tag,true)}
 }.getOrNull() ?: return false
 if(persisted.productionState!=updated.productionState || persisted.pregnancyStartDate!=updated.pregnancyStartDate || persisted.expectedBirthDate!=updated.expectedBirthDate || persisted.pregnancyScanDate!=updated.pregnancyScanDate || persisted.pregnancyScanOutcome!=updated.pregnancyScanOutcome || persisted.fetusCount!=updated.fetusCount || persisted.pregnancyOffspringSex!=updated.pregnancyOffspringSex || persisted.repeatScanDate!=updated.repeatScanDate) return false
 val reminderTitle = if(outcome=="غير حاسم") "إعادة فحص السونار" else "موعد ولادة متوقع"
 val retained = readHerdCare(c).filterNot { it.tag.equals(tag.trim(),true) && !it.completed && it.title in setOf("إعادة فحص السونار","موعد ولادة متوقع") }
 val reminderDate = when(outcome) {
  "حامل" -> updated.expectedBirthDate
  "غير حاسم" -> repeatScanDate
  else -> ""
 }
 val reminderSaved = if(reminderDate.isNotBlank() && runCatching{LocalDate.parse(reminderDate);true}.getOrDefault(false)) {
  val note = if(outcome=="حامل") "موعد تقديري محسوب من السونار؛ راقب النعجة واستعد للولادة." else "نتيجة السونار غير حاسمة؛ أعد الفحص في هذا الموعد."
  runCatching { saveHerdCare(c, retained + HerdCareReminder(UUID.randomUUID().toString(), updated.tag, "متابعة", reminderTitle, reminderDate, note, false)) }.isSuccess
 } else {
  runCatching { saveHerdCare(c, retained) }.isSuccess
 }
 if(!reminderSaved) android.widget.Toast.makeText(c, "تم حفظ بيانات النعجة لكن تعذرت جدولة التنبيه.", android.widget.Toast.LENGTH_LONG).show()
 return true
}


internal fun confirmEweBirth(context:Context, tag:String, actualBirthDate:String, today:LocalDate=LocalDate.now()):Boolean {
 if(!isValidActualBirthDate(actualBirthDate,today))return false
 val date=LocalDate.parse(actualBirthDate)
 val animals=loadHerd(context).toMutableList()
 val index=animals.indexOfFirst{it.tag.equals(tag.trim(),true)&&it.kind=="نعجة"}
 if(index<0)return false
 val mother=animals[index]
 if(mother.productionState!="نعجة حامل" || mother.pregnancyScanOutcome!="حامل" || mother.expectedBirthDate.isBlank())return false
 animals[index]=mother.copy(productionState="نعجة مرضعة",birthConfirmedDate=date.toString(),weaningConfirmedDate="",nursingLambs=mother.fetusCount.coerceIn(1,3))
 saveHerd(context,animals)
 val saved=loadHerd(context).firstOrNull{it.id==mother.id} ?: return false
 return saved.productionState=="نعجة مرضعة" && saved.birthConfirmedDate==date.toString()
}

internal fun confirmEweWeaning(context:Context, tag:String, weaningDate:String, today:LocalDate=LocalDate.now()):Boolean {
 val date=runCatching{LocalDate.parse(weaningDate)}.getOrNull()?:return false
 if(date.isAfter(today))return false
 val animals=loadHerd(context).toMutableList()
 val index=animals.indexOfFirst{it.tag.equals(tag.trim(),true)&&it.kind=="نعجة"}
 if(index<0)return false
 val mother=animals[index]
 if(mother.productionState!="نعجة مرضعة" || mother.birthConfirmedDate.isBlank())return false
 val linked=animals.filter{it.motherTag.equals(mother.tag,true)&&it.birthDate==mother.birthConfirmedDate&&it.kind=="حمل/رخلة"&&it.lifecycleStatus=="نشط"}
 if(!isEligibleForWeaning(linked,date.toString()))return false
 animals[index]=mother.copy(productionState="نعجة جافة",weaningConfirmedDate=date.toString(),nursingLambs=0)
 linked.forEach { child -> val i=animals.indexOfFirst{it.id==child.id}; if(i>=0) animals[i]=animals[i].copy(productionState="خروف") }
 saveHerd(context,animals)
 return loadHerd(context).firstOrNull{it.id==mother.id}?.productionState=="نعجة جافة"
}

/** Reclassifies the existing animal record; identity, tag, and mother link never change. */
internal fun isValidActualBirthDate(value:String,today:LocalDate=LocalDate.now()):Boolean =
 runCatching { val d=LocalDate.parse(value); !d.isAfter(today) }.getOrDefault(false)

internal fun isEligibleForWeaning(children:List<HerdAnimal>,date:String):Boolean {
 val weaning=runCatching{LocalDate.parse(date)}.getOrNull()?:return false
 return children.isNotEmpty() && children.all { child ->
  runCatching { ChronoUnit.DAYS.between(LocalDate.parse(child.birthDate),weaning)>=60 }.getOrDefault(false)
 }
}

internal fun reclassifyHerdAnimal(animal:HerdAnimal,today:LocalDate=LocalDate.now()):HerdAnimal {
 if(animal.lifecycleStatus=="مؤرشف")return animal
 var next=animal
 if(animal.kind=="نعجة" && animal.birthConfirmedDate.isNotBlank() && animal.weaningConfirmedDate.isBlank() && animal.productionState!="نعجة مرضعة") next=next.copy(productionState="نعجة مرضعة")
 if(animal.kind=="نعجة" && animal.weaningConfirmedDate.isNotBlank() && animal.productionState!="نعجة جافة") next=next.copy(productionState="نعجة جافة")
 if(animal.kind=="حمل/رخلة" && animal.sex in setOf("ذكر","أنثى") && animal.birthDate.isNotBlank()) {
  val birth=runCatching{LocalDate.parse(animal.birthDate)}.getOrNull()
  val months=birth?.let{ChronoUnit.MONTHS.between(it,today)} ?: -1
  if(months>=8) next=if(animal.sex=="ذكر") next.copy(kind="كبش",productionState="كبش") else next.copy(kind="نعجة",productionState="نعجة جافة")
 }
 return next
}

internal fun reconcileHerdLifecycle(context:Context, today:LocalDate=LocalDate.now()):List<HerdAnimal> {
 val previous=loadHerd(context)
 val animals=previous.map{animal->
  val anchored=if(animal.birthDate.isBlank() && animal.ageRecordedDate.isBlank()) animal.copy(ageRecordedDate=today.toString()) else animal
  reclassifyHerdAnimal(anchored,today)
 }
 if(animals!=previous) saveHerd(context,animals)
 return animals
}



internal fun isValidPregnancyOffspringSex(value:String,fetusCount:Int=1):Boolean = value=="ذكر" || value=="أنثى" || (value=="مختلط" && fetusCount in 2..3)

internal fun isValidLambSex(value:String):Boolean = value=="ذكر" || value=="أنثى"

internal fun herdAnimalCostForSale(animal:HerdAnimal?,legacyFallback:Double):Double =
 animal?.acquisitionCost?.takeIf{it.isFinite()&&it>=0.0} ?: legacyFallback.takeIf{it.isFinite()&&it>=0.0} ?: 0.0

internal fun isValidHerdExitDate(value:String,today:LocalDate=LocalDate.now()):Boolean =
 try { val date=LocalDate.parse(value); !date.isAfter(today) } catch(_:DateTimeParseException) { false }

internal fun isValidLambBirthDate(value:String,today:LocalDate=LocalDate.now()):Boolean =
 try { val date=LocalDate.parse(value); !date.isAfter(today) } catch(_:DateTimeParseException) { false }

internal fun ageMonthsFromBirthDate(value:String,fallback:Int,today:LocalDate=LocalDate.now()):Int =
 try {
  val birth=LocalDate.parse(value)
  if(birth.isAfter(today)) fallback else ChronoUnit.MONTHS.between(birth,today).toInt().coerceAtLeast(0)
 } catch(_:DateTimeParseException) { fallback }

internal fun herdAnimalAgeDays(animal:HerdAnimal,today:LocalDate=LocalDate.now()):Long {
 val birth=runCatching{LocalDate.parse(animal.birthDate)}.getOrNull()
 if(birth!=null && !birth.isAfter(today)) return ChronoUnit.DAYS.between(birth,today).coerceAtLeast(0)
 val anchor=runCatching{LocalDate.parse(animal.ageRecordedDate)}.getOrNull() ?: today
 val elapsed=ChronoUnit.DAYS.between(anchor,today).coerceAtLeast(0)
 return animal.ageMonths.coerceAtLeast(0).toLong()*30L+elapsed
}

internal fun herdAnimalAgeMonths(animal:HerdAnimal,today:LocalDate=LocalDate.now()):Int {
 val birth=runCatching{LocalDate.parse(animal.birthDate)}.getOrNull()
 return if(birth!=null && !birth.isAfter(today)) ageMonthsFromBirthDate(animal.birthDate,animal.ageMonths,today)
 else (herdAnimalAgeDays(animal,today)/30L).toInt()
}

internal fun herdAnimalAgeLabel(animal:HerdAnimal,today:LocalDate=LocalDate.now()):String {
 val birth=runCatching{LocalDate.parse(animal.birthDate)}.getOrNull()
 if(birth!=null && !birth.isAfter(today)) {
  val totalDays=ChronoUnit.DAYS.between(birth,today)
  val period=Period.between(birth,today)
  val age=when {
   period.years>0 -> "${period.years} سنة و ${period.months} شهر و ${period.days} يوم"
   period.months>0 -> "${period.months} شهر و ${period.days} يوم"
   else -> "${totalDays} يوم"
  }
  return "$age • $totalDays يوم إجمالاً"
 }
 if(animal.ageMonths<=0 && animal.ageRecordedDate.isBlank()) return "تاريخ الميلاد غير مسجل"
 val totalDays=herdAnimalAgeDays(animal,today)
 val months=totalDays/30L
 val years=months/12L
 val remainingMonths=months%12L
 val days=totalDays%30L
 val age=if(years>0) "$years سنة و $remainingMonths شهر و $days يوم" else "$months شهر و $days يوم"
 return "$age • نحو $totalDays يوم (تقديري)"
}

internal fun isValidPregnancyDates(start:String,due:String,today:LocalDate=LocalDate.now()):Boolean {
 val startDate=runCatching{LocalDate.parse(start)}.getOrNull()?:return false
 val dueDate=runCatching{LocalDate.parse(due)}.getOrNull()?:return false
 val gestationDays=ChronoUnit.DAYS.between(startDate,dueDate)
 return !startDate.isAfter(today) && gestationDays in 120..180
}

internal fun isValidMotherTag(motherTag:String,animals:List<HerdAnimal>,childId:String?=null):Boolean =
 motherTag.isNotBlank() && animals.any { it.id!=childId && it.tag.equals(motherTag.trim(),true) && it.kind=="نعجة" && it.lifecycleStatus=="نشط" }

internal fun isValidProductionState(kind:String,state:String):Boolean = when(kind) {
 "نعجة" -> state in setOf("نعجة جافة","نعجة حامل","نعجة مرضعة")
 "كبش" -> state=="كبش"
 "حمل/رخلة" -> state in setOf("حمل يرضع","خروف")
 "غير محدد" -> state in setOf("نعجة جافة","غير محدد")
 else -> false
}

internal fun archiveHerdAnimal(animal:HerdAnimal,date:String,reason:String,today:LocalDate=LocalDate.now()):HerdAnimal {
 require(isValidHerdExitDate(date,today)) { "تاريخ الخروج غير صحيح أو يقع في المستقبل" }
 require(reason in setOf("بيع","نفوق","استبعاد")) { "سبب الخروج غير صحيح" }
 return animal.copy(lifecycleStatus="مؤرشف",exitDate=date,exitReason=reason)
}

internal fun restoreHerdAnimal(animal:HerdAnimal):HerdAnimal =
 animal.copy(lifecycleStatus="نشط",exitDate="",exitReason="")

internal fun activeHerdAnimals(animals:List<HerdAnimal>):List<HerdAnimal> =
 animals.filter { it.lifecycleStatus!="مؤرشف" }

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

@Composable fun HerdScreen(modifier:Modifier=Modifier,refreshKey:Int=0){
 val context=LocalContext.current
 val animals=remember{mutableStateListOf<HerdAnimal>().apply{addAll(reconcileHerdLifecycle(context))}}
 var search by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("الكل")}
 var editor by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<HerdAnimal?>(null)};var deleting by remember{mutableStateOf<HerdAnimal?>(null)};var newbornDraft by remember{mutableStateOf<HerdAnimal?>(null)};var birthConfirmationFor by remember{mutableStateOf<HerdAnimal?>(null)};var actualBirthDate by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var weaningConfirmationFor by remember{mutableStateOf<HerdAnimal?>(null)};var actualWeaningDate by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var exitDate by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var exitReason by rememberSaveable{mutableStateOf("بيع")};var exitError by remember{mutableStateOf<String?>(null)};var batchDialog by remember{mutableStateOf(false)};var batchCount by rememberSaveable{mutableStateOf("10")}
 LaunchedEffect(refreshKey) {
  val latest=reconcileHerdLifecycle(context)
  animals.clear(); animals.addAll(latest)
  selected=selected?.let { current -> latest.firstOrNull { it.id==current.id } }
 }
 val visible=animals.filter{it.tag.contains(search.trim(),true)&&(if(filter=="الأرشيف")it.lifecycleStatus=="مؤرشف" else it.lifecycleStatus=="نشط"&&(filter=="الكل"||it.kind==filter))}
 Column(modifier.fillMaxSize().background(HerdCream).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Spacer(Modifier.height(7.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("القطيع",color=HerdInk,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text("متابعة كل نعجة وحمل بصحة ووضوح",color=HerdMuted,fontSize=12.sp)};Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){FilledTonalButton(onClick={batchDialog=true},contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Outlined.QrCode2,null);Text("ترقيم دفعة",fontSize=10.sp)};FilledTonalIconButton(onClick={selected=null;editor=true}){Icon(Icons.Outlined.Add,"إضافة فردية",tint=HerdGreen)}}}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){HerdStat("إجمالي القطيع",animals.count{it.lifecycleStatus=="نشط"}.toString(),Modifier.weight(1f));HerdStat("النعاج",animals.count{it.lifecycleStatus=="نشط"&&it.kind=="نعجة"}.toString(),Modifier.weight(1f));HerdStat("الحملان",animals.count{it.lifecycleStatus=="نشط"&&it.kind=="حمل/رخلة"}.toString(),Modifier.weight(1f))}
  if(animals.isNotEmpty())OutlinedButton(onClick={printHerdQr(context,animals.toList())},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Print,null);Text("طباعة رموز QR للقطيع (${animals.size})")}
  OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("ابحث برقم التعريف",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("الكل","نعجة","كبش","حمل/رخلة","غير محدد").forEach{FilterChip(filter==it,{filter=it},label={Text(it,fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}}
  Row(horizontalArrangement=Arrangement.Start,modifier=Modifier.fillMaxWidth()){
   FilterChip(filter=="الأرشيف",{filter="الأرشيف"},label={Text("الأرشيف",fontSize=10.sp)},leadingIcon={Icon(Icons.Outlined.Archive,contentDescription=null,modifier=Modifier.size(16.dp))},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))
  }
  if(visible.isEmpty()){
   Column(Modifier.fillMaxWidth().weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    EwePhoto(76);Spacer(Modifier.height(12.dp));Text(if(animals.isEmpty())"ابدأ بتسجيل حيوانات القطيع" else "لا توجد نتائج مطابقة",color=HerdInk,fontWeight=FontWeight.Bold)
    Text(if(animals.isEmpty())"امنح كل حيوان رقم تعريف لتسهيل متابعته" else "جرّب تغيير البحث أو نوع الحيوان",color=HerdMuted,fontSize=11.sp)
    if(animals.isEmpty()){Spacer(Modifier.height(12.dp));Button(onClick={batchDialog=true},colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Icon(Icons.Outlined.QrCode2,null);Text("ترقيم مجموعة")}}
   }
  }else{
   Text("الحيوانات المسجلة (${visible.size})",color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp)
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){items(visible,key={it.id}){a->Surface(onClick={selected=a},modifier=Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){EweLogo(45);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.tag,color=HerdInk,fontWeight=FontWeight.Bold,fontSize=14.sp);Text("${a.kind} • ${herdAnimalAgeLabel(a)} • ${a.health}",color=HerdMuted,fontSize=14.sp);Text("الوزن الحالي: ${a.weightKg?.let{"%.2f".format(java.util.Locale.US,it)}?.plus(" كغ")?:"غير مسجل"}",color=HerdGreen,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text("الحالة الإنتاجية: ${a.productionState}${if(a.productionState=="نعجة حامل")" • ${a.pregnancyStage} • ${when(a.fetusCount){2->"توأم";3->"ثلاثة أجنة";else->"جنين واحد"}} • الجنس: ${a.pregnancyOffspringSex.ifBlank{"غير محدد"}}" else if(a.productionState=="نعجة مرضعة")" • ${a.nursingLambs} حمل" else ""}",color=HerdMuted,fontSize=14.sp);
       if(a.productionState=="نعجة حامل" && a.pregnancyScanDate.isNotBlank()) Text("السونار: ${a.pregnancyScanDate} • الولادة المتوقعة: ${a.expectedBirthDate}",color=HerdGreen,fontSize=14.sp);if(a.kind=="حمل/رخلة")Text("الجنس: ${a.sex.ifBlank{"غير محدد"}} • الميلاد: ${a.birthDate.ifBlank{"غير مسجل"}} • وزن الميلاد: ${a.birthWeightKg?.let{"%.2f".format(java.util.Locale.US,it)+" كغ"}?:"غير مسجل"} • الأم: ${a.motherTag.ifBlank{"غير مسجل"}}",color=HerdMuted,fontSize=14.sp);if(a.lifecycleStatus=="مؤرشف")Text("مؤرشف • ${a.exitReason} • ${a.exitDate}",color=Color(0xFF9A5B32),fontSize=14.sp,fontWeight=FontWeight.SemiBold);if(a.notes.isNotBlank())Text(a.notes,color=HerdMuted,fontSize=14.sp,maxLines=1)};IconButton(onClick={selected=a;editor=true}){Icon(Icons.Outlined.Edit,"تعديل",tint=HerdGreen)};if(a.lifecycleStatus=="نشط")IconButton(onClick={deleting=a;exitDate=LocalDate.now().toString();exitReason="بيع";exitError=null}){Icon(Icons.Outlined.Archive,"أرشفة الحيوان",tint=HerdMuted)} else IconButton(onClick={val i=animals.indexOfFirst{it.id==a.id};if(i>=0){animals[i]=restoreHerdAnimal(a);saveHerd(context,animals.toList())}}){Icon(Icons.Outlined.Restore,"إعادة إلى القطيع",tint=HerdGreen)}}}}}
 }
 }
 if(batchDialog)AlertDialog(onDismissRequest={batchDialog=false},title={Text("ترقيم مجموعة من القطيع")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("أدخل عدد الحيوانات الجديدة. سينشئ التطبيق رقماً متسلسلاً ورمز QR فريداً لكل حيوان.",fontSize=12.sp,color=HerdMuted)
  OutlinedTextField(batchCount,{batchCount=it.filter(Char::isDigit)},label={Text("عدد الحيوانات")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
  Text("مثال: Q-0001، Q-0002، Q-0003",color=HerdGreen,fontSize=11.sp)
  Text("تُنشأ البطاقات بنوع غير محدد لتعبئة العمر والوزن والحالة الصحية لاحقاً.",fontSize=11.sp,color=HerdMuted)
 }},confirmButton={TextButton(onClick={val n=batchCount.toIntOrNull();if(n!=null&&n in 1..500){generateHerdTags(animals.map{it.tag},n).forEach{tag->animals.add(HerdAnimal(UUID.randomUUID().toString(),tag,"غير محدد",0,"غير محددة",""))};saveHerd(context,animals.toList());batchDialog=false}}){Text("إنشاء الأرقام",color=HerdGreen)}},dismissButton={TextButton(onClick={batchDialog=false}){Text("إلغاء")}})
 if(editor)HerdEditor(newbornDraft?:selected,animals.toList(),{editor=false;newbornDraft=null;selected=null}){a->if(newbornDraft!=null){if(animals.none{it.tag.equals(a.tag,true)})animals.add(a);val mi=animals.indexOfFirst{it.tag.equals(a.motherTag,true)};if(mi>=0){val mother=animals[mi];animals[mi]=mother.copy(productionState="نعجة مرضعة",nursingLambs=animals.count{it.motherTag.equals(mother.tag,true)&&it.birthDate==mother.birthConfirmedDate&&it.kind=="حمل/رخلة"}.coerceAtLeast(1))};newbornDraft=null}else{val i=animals.indexOfFirst{it.id==a.id};if(i>=0){animals[i]=a;selected=a}else{animals.add(a);selected=null}};saveHerd(context,animals.toList());editor=false}
 if(!editor)selected?.let{selectedAnimal->
  val a=animals.firstOrNull{it.id==selectedAnimal.id}?:selectedAnimal
  val qr=remember(a.id,a.tag){herdQrBitmap(a.id+"|"+a.tag)}
  val offspring=animals.filter{it.motherTag.equals(a.tag,true)}.sortedBy{it.birthDate}
  val currentLitter=offspring.filter{it.birthDate==a.birthConfirmedDate}
  val careRecords=readHerdCare(context).filter{it.tag==a.tag}
  val today=LocalDate.now()
  val pastVaccines=careRecords.filter{it.kind=="تطعيم"&&it.completed}.sortedByDescending{it.date}
  val overdueVaccines=careRecords.filter{it.kind=="تطعيم"&&!it.completed&&runCatching{LocalDate.parse(it.date).isBefore(today)}.getOrDefault(false)}.sortedBy{it.date}
  val upcomingVaccines=careRecords.filter{it.kind=="تطعيم"&&!it.completed&&runCatching{!LocalDate.parse(it.date).isBefore(today)}.getOrDefault(false)}.sortedBy{it.date}
  Dialog(onDismissRequest={selected=null}){
   Surface(modifier=Modifier.fillMaxWidth().heightIn(max=720.dp),color=HerdCream,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,HerdLine)){
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text("البطاقة التقنية",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=HerdInk);Text("ملف الحيوان رقم ${a.tag}",fontSize=15.sp,color=HerdMuted)}
      IconButton(onClick={selected=null}){Icon(Icons.Outlined.Close,contentDescription="إغلاق",tint=HerdMuted)}
     }
     Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,HerdLine)){
      Column(Modifier.fillMaxWidth().padding(13.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
       Image(qr.asImageBitmap(),contentDescription="رمز QR للحيوان",modifier=Modifier.size(108.dp))
       Text(a.tag,color=HerdGreen,fontSize=20.sp,fontWeight=FontWeight.ExtraBold)
       Text(a.kind,color=HerdMuted,fontSize=15.sp)
      }
     }
     Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){
      Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
       Text("المعلومات الأساسية",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=13.sp)
       Text("العمر: ${herdAnimalAgeLabel(a)}",fontSize=15.sp,color=HerdInk)
       Text("السلالة: ${a.breed.ifBlank{"غير محددة"}}",fontSize=15.sp,color=HerdInk)
       Text("آخر وزن مسجل: ${a.weightKg?.let{"%.2f كغ".format(java.util.Locale.US,it)}?:"غير مسجل"}",fontSize=15.sp,color=HerdInk)
       Text("الحالة الصحية: ${a.health}",fontSize=15.sp,color=HerdInk)
       Text("الحالة الإنتاجية: ${a.productionState}",fontSize=15.sp,color=HerdInk)
       if(a.productionState=="نعجة حامل"){
        Text("مرحلة الحمل: ${a.pregnancyStage} • ${if(a.fetusCount>=2)"توأم" else "جنين واحد"}",fontSize=15.sp,color=HerdInk)
        Text("بداية الحمل: ${a.pregnancyStartDate.ifBlank{"غير مسجلة"}}",fontSize=14.sp,color=HerdMuted)
        Text("الولادة المتوقعة: ${a.expectedBirthDate.ifBlank{"غير مسجلة"}}",fontSize=14.sp,color=HerdMuted)
        if(a.pregnancyScanDate.isNotBlank())Text("السونار: ${a.pregnancyScanDate} • النتيجة: ${a.pregnancyScanOutcome}",fontSize=14.sp,color=HerdGreen)
        if(a.repeatScanDate.isNotBlank())Text("إعادة السونار: ${a.repeatScanDate}",fontSize=14.sp,color=HerdGreen)
       }
      }
     }
     if(a.kind=="نعجة"){
      Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){
       Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("سجل الولادات",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=13.sp)
        Text(if(offspring.isNotEmpty())"ولدت من قبل: نعم • عدد المواليد المسجلين: ${offspring.size}" else "ولدت من قبل: لا توجد ولادات مسجلة",fontSize=15.sp,color=HerdInk)
        offspring.forEach{child->Text("• ${child.tag} — ${child.birthDate.ifBlank{"تاريخ غير مسجل"}}",fontSize=14.sp,color=HerdMuted)}
       }
      }
     }
     if(a.kind=="نعجة" && a.productionState=="نعجة حامل" && a.birthConfirmedDate.isBlank()){
      Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){
       Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("تأكيد الولادة الفعلية",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=14.sp)
        Text("موعد الولادة المتوقع: ${a.expectedBirthDate.ifBlank{"غير مسجل"}}. لا تظهر إضافة المولود قبل تسجيل حدوث الولادة فعلياً.",fontSize=12.sp,color=HerdMuted)
        Button(onClick={actualBirthDate=LocalDate.now().toString();birthConfirmationFor=a},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Text("تأكيد حدوث الولادة")}
       }
      }
     }
     if(a.kind=="نعجة" && a.productionState=="نعجة مرضعة" && a.birthConfirmedDate.isNotBlank() && a.weaningConfirmedDate.isBlank()){
      Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){
       Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("الولادة مؤكدة",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=14.sp)
        Text("تاريخ الولادة الفعلي: ${a.birthConfirmedDate} • مواليد هذه الولادة: ${currentLitter.size} / ${a.fetusCount.coerceAtLeast(1)}",fontSize=12.sp,color=HerdInk)
        if(currentLitter.size < a.fetusCount.coerceAtLeast(1)) {
         Button(onClick={val tag=generateHerdTags(animals.map{it.tag},1).first();newbornDraft=HerdAnimal(id=UUID.randomUUID().toString(),tag=tag,kind="حمل/رخلة",ageMonths=0,health="غير محددة",notes="",birthDate=a.birthConfirmedDate,motherTag=a.tag,productionState="حمل يرضع",sex="");editor=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Icon(Icons.Outlined.Add,null);Text("إضافة مولود إلى القطيع")}
        } else Text("تم تسجيل العدد المتوقع من مواليد هذه الولادة.",fontSize=12.sp,color=HerdGreen)
        if(a.productionState=="نعجة مرضعة" && a.weaningConfirmedDate.isBlank() && currentLitter.isNotEmpty() && currentLitter.all{child->runCatching{ChronoUnit.DAYS.between(LocalDate.parse(child.birthDate),LocalDate.now())>=60}.getOrDefault(false)}){
         OutlinedButton(onClick={actualWeaningDate=LocalDate.now().toString();weaningConfirmationFor=a},modifier=Modifier.fillMaxWidth()){Text("تأكيد الفطام")}
        }
        if(a.weaningConfirmedDate.isNotBlank())Text("تاريخ الفطام المسجل: ${a.weaningConfirmedDate}",fontSize=12.sp,color=HerdMuted)
       }
      }
     }
     Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HerdLine)){
      Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       Text("التطعيمات السابقة",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=13.sp)
       if(pastVaccines.isEmpty())Text("لا توجد تطعيمات مكتملة مسجلة.",fontSize=14.sp,color=HerdMuted)
       pastVaccines.forEach{v->Text("• ${v.title} — ${v.date}",fontSize=14.sp,color=HerdInk)}
       if(overdueVaccines.isNotEmpty()){
        Divider(color=HerdLine)
        Text("تطعيمات متأخرة",fontWeight=FontWeight.Bold,color=Color(0xFFB54D42),fontSize=13.sp)
        overdueVaccines.forEach{v->Text("• ${v.title} — ${v.date}",fontSize=14.sp,color=Color(0xFFB54D42))}
       }
       Divider(color=HerdLine)
       Text("التطعيمات القادمة",fontWeight=FontWeight.Bold,color=HerdGreen,fontSize=13.sp)
       if(upcomingVaccines.isEmpty())Text("لا توجد تطعيمات قادمة مسجلة.",fontSize=14.sp,color=HerdMuted)
       upcomingVaccines.forEach{v->Text("• ${v.title} — ${v.date}",fontSize=14.sp,color=HerdInk)}
      }
     }
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedButton(onClick={printHerdQr(context,listOf(a))},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Print,null);Text("طباعة QR")}
      Button(onClick={editor=true},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=HerdGreen)){Icon(Icons.Outlined.Edit,null);Text("تعديل البيانات")}
     }
     OutlinedButton(onClick={selected=null},modifier=Modifier.fillMaxWidth()){Text("إغلاق البطاقة")}
    }
   }
  }
 }

 birthConfirmationFor?.let{mother->AlertDialog(onDismissRequest={birthConfirmationFor=null},title={Text("تأكيد حدوث الولادة")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("لن يتم إنشاء مولود تلقائياً. بعد تأكيد الولادة ستظهر لك وظيفة إضافة المواليد في بطاقة الأم.");MawashiDatePickerField(actualBirthDate,{actualBirthDate=it},"تاريخ الولادة الفعلي",Modifier.fillMaxWidth())}},confirmButton={TextButton(onClick={if(confirmEweBirth(context,mother.tag,actualBirthDate)){val latest=loadHerd(context);animals.clear();animals.addAll(latest);selected=latest.firstOrNull{it.id==mother.id};birthConfirmationFor=null}else Toast.makeText(context,"تعذر تأكيد الولادة. تحقق من حالة الحمل والتاريخ.",Toast.LENGTH_LONG).show()}){Text("تأكيد",color=HerdGreen)}},dismissButton={TextButton(onClick={birthConfirmationFor=null}){Text("إلغاء")}})}
 weaningConfirmationFor?.let{mother->AlertDialog(onDismissRequest={weaningConfirmationFor=null},title={Text("تأكيد الفطام")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("سيتم تحويل الأم إلى نعجة جافة وتحديث حالة المواليد إلى مفطومة، مع الاحتفاظ بروابط الأمومة.");MawashiDatePickerField(actualWeaningDate,{actualWeaningDate=it},"تاريخ الفطام الفعلي",Modifier.fillMaxWidth())}},confirmButton={TextButton(onClick={if(confirmEweWeaning(context,mother.tag,actualWeaningDate)){val latest=loadHerd(context);animals.clear();animals.addAll(latest);selected=latest.firstOrNull{it.id==mother.id};weaningConfirmationFor=null}else Toast.makeText(context,"لا يمكن تأكيد الفطام قبل مرور 60 يوماً على الأقل من ميلاد جميع المواليد المرتبطين.",Toast.LENGTH_LONG).show()}){Text("تأكيد الفطام",color=HerdGreen)}},dismissButton={TextButton(onClick={weaningConfirmationFor=null}){Text("إلغاء")}})}
 deleting?.let{a->AlertDialog(onDismissRequest={deleting=null},title={Text("إخراج الحيوان من القطيع")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("سيُنقل ${a.tag} إلى الأرشيف مع الاحتفاظ بجميع بياناته وسجلاته.");Text("سبب الخروج");Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("بيع","نفوق","استبعاد").forEach{FilterChip(exitReason==it,{exitReason=it},label={Text(it,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color.White,labelColor=HerdInk,selectedLabelColor=HerdGreen))}};MawashiDatePickerField(exitDate,{exitDate=it;exitError=null},"تاريخ الخروج",Modifier.fillMaxWidth());exitError?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp)};Text("لن يُحذف سجل الحيوان نهائياً.",color=HerdMuted,fontSize=11.sp)}},confirmButton={TextButton(onClick={if(!isValidHerdExitDate(exitDate)){exitError="أدخل تاريخاً صحيحاً بصيغة YYYY-MM-DD، ولا يمكن أن يكون في المستقبل."}else{val i=animals.indexOfFirst{it.id==a.id};if(i>=0){val archived=archiveHerdAnimal(a,exitDate,exitReason);animals[i]=archived;saveHerd(context,animals.toList());runCatching{saveHerdCare(context,readHerdCare(context).filter{!it.tag.equals(a.tag,true)||it.completed})}};deleting=null}}){Text("تأكيد الأرشفة",color=HerdGreen)}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})}
}

private const val EWE_PHOTO_BASE64 = "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"

@Composable fun EwePhoto(size:Int){
 val bitmap = remember { val bytes=Base64.decode(EWE_PHOTO_BASE64,Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes,0,bytes.size) }
 bitmap?.let { Image(bitmap=it.asImageBitmap(),contentDescription="صورة النعجة",modifier=Modifier.size(size.dp),contentScale=ContentScale.Fit) }
}

@Composable fun EweLogo(size:Int){
 Text(
  text = "🐑",
  fontSize = (size * 0.82f).sp,
  lineHeight = (size * 0.9f).sp,
  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
  modifier = Modifier.size(size.dp)
 )
}
@Composable private fun HerdStat(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,color=Color.White,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,HerdLine)){Column(Modifier.padding(10.dp)){Text(value,color=HerdGreen,fontWeight=FontWeight.ExtraBold,fontSize=20.sp);Text(label,color=HerdMuted,fontSize=9.sp,maxLines=1)}}}
@Composable private fun HerdEditor(initial:HerdAnimal?,existingAnimals:List<HerdAnimal>,dismiss:()->Unit,save:(HerdAnimal)->Unit){
 var tag by remember(initial){mutableStateOf(initial?.tag?:"")}
 var kind by remember(initial){mutableStateOf(initial?.kind?:"نعجة")}
 var age by remember(initial){mutableStateOf(initial?.ageMonths?.toString()?:"")}
 var breed by remember(initial){mutableStateOf(initial?.breed?.takeUnless{it=="غير محددة"}?:"")}
 var health by remember(initial){mutableStateOf(initial?.health?.takeUnless{it=="غير محددة"}?:"جيدة")}
 var notes by remember(initial){mutableStateOf(initial?.notes?:"")}
 var weight by remember(initial){mutableStateOf(initial?.weightKg?.toString()?:"")}
 var birthDate by remember(initial){mutableStateOf(initial?.birthDate?:"")}
 var birthWeight by remember(initial){mutableStateOf(initial?.birthWeightKg?.toString()?:"")}
 var motherTag by remember(initial){mutableStateOf(initial?.motherTag?:"")}
 var sex by remember(initial){mutableStateOf(initial?.sex?:"")}
 var motherMenuExpanded by remember{mutableStateOf(false)}
 var productionState by remember(initial){mutableStateOf(initial?.productionState?:if(initial?.kind=="كبش")"كبش" else if(initial?.kind=="حمل/رخلة")"خروف" else "نعجة جافة")}
 var pregnancyStage by remember(initial){mutableStateOf(initial?.pregnancyStage?:"غير حامل")}
 var pregnancyStartDate by remember(initial){mutableStateOf(initial?.pregnancyStartDate?:"")}
 var expectedBirthDate by remember(initial){mutableStateOf(initial?.expectedBirthDate?:"")}
 var fetusCount by remember(initial){mutableStateOf(initial?.fetusCount?.toString()?:"1")}
 var nursingLambs by remember(initial){mutableStateOf(initial?.nursingLambs?.toString()?:"1")}
 var error by remember{mutableStateOf<String?>(null)}
 val motherOptions=existingAnimals.filter{it.kind=="نعجة"&&it.id!=initial?.id&&(it.lifecycleStatus=="نشط"||it.tag.equals(initial?.motherTag,true))}
 AlertDialog(
  onDismissRequest=dismiss,
  title={Text(if(initial==null || existingAnimals.none{it.id==initial.id})"إضافة مولود إلى القطيع" else "تعديل بيانات الحيوان")},
  text={
   Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedTextField(tag,{tag=it;error=null},label={Text("رقم التعريف / الوسم")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Text("نوع الحيوان",fontWeight=FontWeight.SemiBold,color=HerdGreen)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
     listOf("نعجة","كبش","حمل/رخلة","غير محدد").forEach{FilterChip(kind==it,{kind=it;error=null},label={Text(it,fontSize=10.sp)})}
    }
    OutlinedTextField(breed,{breed=it},label={Text("السلالة (اختياري)")},singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(age,{age=it.filter(Char::isDigit);error=null},label={Text("العمر بالأشهر عند عدم تسجيل تاريخ الميلاد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
    MawashiDatePickerField(birthDate,{birthDate=it;age=ageMonthsFromBirthDate(it,0).toString();error=null},"تاريخ الميلاد",Modifier.fillMaxWidth())
    OutlinedTextField(weight,{weight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("الوزن الحالي (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
    if(kind=="حمل/رخلة"){
     Text("بيانات المولود",fontWeight=FontWeight.Bold,color=HerdGreen)
     Text("جنس المولود",fontWeight=FontWeight.SemiBold,color=HerdGreen)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf("ذكر","أنثى").forEach{option->FilterChip(selected=sex==option,onClick={sex=option;error=null},label={Text(option,fontSize=12.sp)})}
     }
     OutlinedTextField(birthWeight,{birthWeight=it.filter{ch->ch in "0123456789.,"};error=null},label={Text("وزن الميلاد (كغ)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
     Box{
      OutlinedButton(onClick={motherMenuExpanded=true},modifier=Modifier.fillMaxWidth()){
       Text(motherOptions.firstOrNull{it.tag.equals(motherTag,true)}?.let{"الأم: ${it.tag}"}?:if(motherTag.isBlank())"اختيار الأم من إناث القطيع" else "الأم المسجلة: $motherTag",modifier=Modifier.weight(1f))
       Icon(Icons.Outlined.ExpandMore,null)
      }
      DropdownMenu(expanded=motherMenuExpanded,onDismissRequest={motherMenuExpanded=false}){
       motherOptions.forEach{mother->DropdownMenuItem(text={Text(mother.tag+" • نعجة")},onClick={motherTag=mother.tag;motherMenuExpanded=false;error=null})}
       if(motherOptions.isEmpty())DropdownMenuItem(text={Text("لا توجد إناث مسجلة للاختيار")},onClick={motherMenuExpanded=false},enabled=false)
      }
     }
    }
    Text("الحالة الإنتاجية والتغذوية",fontWeight=FontWeight.Bold,color=HerdGreen)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
     listOf("نعجة جافة","نعجة حامل","نعجة مرضعة","كبش","حمل يرضع","خروف").forEach{FilterChip(productionState==it,{productionState=it;error=null},label={Text(it,fontSize=11.sp)})}
    }
    if(productionState=="نعجة حامل"){
     Text("بيانات الحمل",fontWeight=FontWeight.Bold,color=HerdGreen)
     Text("سجّل تاريخ بداية الحمل والموعد المتوقع للولادة؛ تُحفظ هذه البيانات ضمن ملف النعجة.",fontSize=10.sp,color=HerdMuted)
     MawashiDatePickerField(pregnancyStartDate,{value->pregnancyStartDate=value;if(expectedBirthDate.isBlank())expectedBirthDate=runCatching{LocalDate.parse(value).plusDays(150).toString()}.getOrDefault("");error=null},"بداية الحمل",Modifier.fillMaxWidth())
     MawashiDatePickerField(expectedBirthDate,{expectedBirthDate=it;error=null},"موعد الولادة المتوقع",Modifier.fillMaxWidth())
     Text("مرحلة الحمل")
     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){
      listOf("بداية الحمل","منتصف الحمل","الأربعة أسابيع الأخيرة").forEach{FilterChip(pregnancyStage==it,{pregnancyStage=it},label={Text(it,fontSize=9.sp)})}
     }
     Row(verticalAlignment=Alignment.CenterVertically){Text("عدد الأجنة");listOf("1","2","3").forEach{FilterChip(fetusCount==it,{fetusCount=it},label={Text(when(it){"1"->"جنين واحد";"2"->"توأم";else->"ثلاثة"})})}}
    }
    if(productionState=="نعجة مرضعة"){
     Row(verticalAlignment=Alignment.CenterVertically){Text("عدد الحملان المرضعة");listOf("1","2").forEach{FilterChip(nursingLambs==it,{nursingLambs=it},label={Text(it)})}}
    }
    Text("الحالة الصحية")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){
     listOf("جيدة","تحتاج متابعة","مريضة").forEach{FilterChip(health==it,{health=it},label={Text(it,fontSize=10.sp)})}
    }
    OutlinedTextField(notes,{notes=it},label={Text("ملاحظات اختيارية")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3)
    error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp)}
   }
  },
  confirmButton={TextButton(onClick={
   val t=tag.trim();val m=age.toIntOrNull();val w=weight.replace(",",".").toDoubleOrNull();val bw=birthWeight.replace(",",".").toDoubleOrNull();val mother=motherTag.trim();val linkedMother=existingAnimals.firstOrNull{it.tag.equals(mother,true)}
   error=when{
    t.isBlank()->"أدخل رقم التعريف."
    existingAnimals.any{it.id!=initial?.id&&it.tag.equals(t,true)}->"رقم التعريف مستخدم."
    m==null||m !in 0..240->"أدخل عمراً صحيحاً من 0 إلى 240 شهراً."
    weight.isNotBlank()&&(w==null||w<=0.0||w>300.0)->"أدخل وزناً صحيحاً بالكيلوغرام."
    birthDate.isNotBlank()&&!isValidLambBirthDate(birthDate)->"تاريخ الميلاد غير صحيح أو يقع في المستقبل."
    kind=="حمل/رخلة"&&!isValidLambSex(sex)->"اختر جنس المولود: ذكر أو أنثى."
    kind=="حمل/رخلة"&&birthDate.isBlank()->"اختر تاريخ ميلاد المولود."
    kind=="حمل/رخلة"&&(bw==null||bw<=0.0||bw>20.0)->"أدخل وزن الميلاد بالكيلوغرام."
    kind=="حمل/رخلة"&&mother.isBlank()->"اختر أم المولود من إناث القطيع."
    kind=="حمل/رخلة"&&initial!=null&&existingAnimals.none{it.id==initial.id}&&linkedMother?.birthConfirmedDate?.isNotBlank()==true&&birthDate!=linkedMother.birthConfirmedDate->"تاريخ ميلاد المولود يجب أن يطابق تاريخ الولادة الفعلية المسجل للأم."
    kind=="حمل/رخلة"&&!isValidMotherTag(mother,existingAnimals,initial?.id)&&mother!=initial?.motherTag->"الأم المختارة غير موجودة ضمن إناث القطيع النشطة."
    productionState=="نعجة حامل"&&!isValidPregnancyDates(pregnancyStartDate,expectedBirthDate)->"أدخل تاريخ بداية الحمل وموعد ولادة متوقعاً صحيحين."
    !isValidProductionState(kind,productionState)->"الحالة الإنتاجية لا تتوافق مع نوع الحيوان."
    else->null
   }
   if(error==null){
    val animalAge=if(birthDate.isNotBlank())ageMonthsFromBirthDate(birthDate,m?:0) else m!!
    save(HerdAnimal(
     id=initial?.id?:UUID.randomUUID().toString(),tag=t,kind=kind,ageMonths=animalAge,health=health,notes=notes.trim(),
     weightKg=w,birthDate=birthDate.trim(),birthWeightKg=if(kind=="حمل/رخلة")bw else initial?.birthWeightKg,
     motherTag=if(kind=="حمل/رخلة")mother else initial?.motherTag.orEmpty(),sex=if(kind=="حمل/رخلة")sex else initial?.sex.orEmpty(),productionState=productionState,
     pregnancyStage=if(productionState=="نعجة حامل")pregnancyStage else "غير حامل",
     fetusCount=if(productionState=="نعجة حامل")fetusCount.toIntOrNull()?:1 else 1,
     nursingLambs=if(productionState=="نعجة مرضعة")nursingLambs.toIntOrNull()?:1 else 1,
     lifecycleStatus=initial?.lifecycleStatus?:"نشط",exitDate=initial?.exitDate?:"",exitReason=initial?.exitReason?:"",
     acquisitionCost=initial?.acquisitionCost,breed=breed.trim().ifBlank{"غير محددة"},
     pregnancyStartDate=if(productionState=="نعجة حامل")pregnancyStartDate else "",
     expectedBirthDate=if(productionState=="نعجة حامل")expectedBirthDate else "",
     pregnancyScanDate=initial?.pregnancyScanDate?:"",
     pregnancyScanOutcome=initial?.pregnancyScanOutcome?:"",
     pregnancyOffspringSex=if(productionState=="نعجة حامل")initial?.pregnancyOffspringSex.orEmpty() else "",repeatScanDate=initial?.repeatScanDate.orEmpty(),birthConfirmedDate=initial?.birthConfirmedDate.orEmpty(),weaningConfirmedDate=initial?.weaningConfirmedDate.orEmpty(),ageRecordedDate=if(birthDate.isNotBlank()) "" else if(initial==null || initial.ageMonths!=animalAge) LocalDate.now().toString() else initial.ageRecordedDate.ifBlank{LocalDate.now().toString()}
    ))
   }
  }){Text("حفظ",color=HerdGreen)}},
  dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}}
 )
}
