package dz.mawashi.app
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

import java.time.LocalDate

internal data class SmartVaccinationStep(
    val id: String,
    val stage: Int,
    val title: String,
    val ageMonths: Long
)

internal val SMART_VACCINATION_STEPS = listOf(
    SmartVaccinationStep("stage1_enteric", 1, "التسمم المعوي", 2),
    SmartVaccinationStep("stage2_hemorrhagic", 2, "التسمم الدموي", 3),
    SmartVaccinationStep("stage3_sheep_pox", 3, "جدري الأغنام", 4),
    SmartVaccinationStep("stage3_ppr", 3, "طاعون المجترات الصغيرة (PPR)", 4),
    SmartVaccinationStep("stage3_fmd", 3, "الحمى القلاعية (FMD)", 4)
)

internal fun smartVaccinationDueDate(birthDate: LocalDate, step: SmartVaccinationStep): LocalDate =
    birthDate.plusMonths(step.ageMonths)

internal fun smartVaccinationDone(tag: String, stepId: String, records: List<HerdCareReminder>): Boolean =
    records.any { it.tag.equals(tag, true) && it.kind == "تحصين مرحلي" && it.notes.startsWith("SMART_VAX_DONE:$stepId") && it.completed }

internal fun smartVaccinationUnlocked(tag: String, stepIndex: Int, records: List<HerdCareReminder>): Boolean =
    stepIndex == 0 || smartVaccinationDone(tag, SMART_VACCINATION_STEPS[stepIndex - 1].id, records)

internal fun smartVaccinationCurrentStep(tag: String, records: List<HerdCareReminder>): SmartVaccinationStep? =
    SMART_VACCINATION_STEPS.firstOrNull { step -> !smartVaccinationDone(tag, step.id, records) }

internal fun reconcileSmartVaccinationReminders(
    animals: List<HerdAnimal>,
    records: List<HerdCareReminder>,
    today: LocalDate = LocalDate.now()
): List<HerdCareReminder> {
    val preserved = records.filterNot { it.kind == "تحصين مرحلي" && it.notes.startsWith("SMART_VAX_PENDING:") }
    val pending = mutableListOf<HerdCareReminder>()
    animals.filter { it.lifecycleStatus != "مؤرشف" }.forEach { animal ->
        val birth = runCatching { LocalDate.parse(animal.birthDate) }.getOrNull() ?: return@forEach
        if (birth.isAfter(today)) return@forEach
        val nextIndex = SMART_VACCINATION_STEPS.indexOfFirst { !smartVaccinationDone(animal.tag, it.id, preserved) }
        if (nextIndex < 0) return@forEach
        val step = SMART_VACCINATION_STEPS[nextIndex]
        if (!smartVaccinationUnlocked(animal.tag, nextIndex, preserved)) return@forEach
        val due = smartVaccinationDueDate(birth, step)
        pending += HerdCareReminder(
            id = "smart-vax-${animal.tag}-${step.id}",
            tag = animal.tag,
            kind = "تحصين مرحلي",
            title = step.title,
            date = due.toString(),
            notes = "SMART_VAX_PENDING:${step.id}",
            completed = false
        )
    }
    return preserved + pending
}

internal data class AnnualVaccineRecord(val id:String,val tag:String,val vaccineId:String,val date:String,val number:Int,val dose:String,val product:String,val batch:String,val expiry:String,val vet:String,val nextDate:String,val notes:String,val source:String)
internal data class AnnualVaccineTask(val id:String,val vaccineId:String,val title:String,val due:LocalDate?,val reason:String,val group:String)
internal data class AnnualVaccineDefinition(val id:String,val title:String,val group:String,val applicable:Set<String>,val guidance:String)

internal val ANNUAL_VACCINE_CATALOG=listOf(
 AnnualVaccineDefinition("clostridial","التسمم المعوي والكزاز","أساسي بحسب سجل التحصين",setOf("lamb","dry","pregnant","lactating","ram"),"راجع الجرعات التأسيسية والمنشطة ونشرة المنتج؛ توقيت ما قبل الولادة مهم عند انطباقه."),
 AnnualVaccineDefinition("pasteurella","الباستوريلا","حسب خطر القطيع",setOf("lamb","dry","pregnant","lactating","ram"),"عند وجود خطر تنفسي أو تاريخ مرضي وبقرار بيطري؛ لا موعد سنوي موحد."),
 AnnualVaccineDefinition("pox","جدري الأغنام","حملة رسمية أو خطر محلي",setOf("lamb","dry","pregnant","lactating","ram"),"حسب الحملة الرسمية وتعليمات المصالح البيطرية."),
 AnnualVaccineDefinition("ppr","طاعون المجترات الصغيرة PPR","حملة رسمية",setOf("lamb","dry","pregnant","lactating","ram"),"حسب الحملة الرسمية والعمر والأهلية؛ لا يفترض تكرار سنوي ثابت."),
 AnnualVaccineDefinition("fmd","الحمى القلاعية FMD","حملة رسمية",setOf("lamb","dry","pregnant","lactating","ram"),"يُدرج عند شمول الأغنام في حملة المصالح البيطرية."),
 AnnualVaccineDefinition("brucella","البروسيلا Rev.1","برنامج رسمي للإناث الصغيرة",setOf("lamb"),"لقاح حي ضمن برنامج رسمي محدد؛ لا يُعطى للحامل أو خارج شروط السلطات."),
 AnnualVaccineDefinition("chlamydia","الكلاميديا المسببة للإجهاض","حسب الخطر",setOf("dry","pregnant"),"يُخطط قبل التلقيح عند وجود خطر؛ بعض المنتجات الحية لها موانع أثناء الحمل."),
 AnnualVaccineDefinition("campylobacter","الكامبيلوباكتر المسبب للإجهاض","حسب الخطر",setOf("dry","pregnant","ram"),"للقطعان ذات التاريخ المرضي وبحسب المنتج وتقييم الطبيب."),
 AnnualVaccineDefinition("footrot","تعفن القدم","حسب وجود المرض",setOf("lamb","dry","pregnant","lactating","ram"),"ليس لقاحاً روتينياً لكل القطعان؛ النظافة وفحص الأظلاف أساسيان."),
 AnnualVaccineDefinition("caseous","الخراجات الجبنية","إضافي حسب المخاطر",setOf("lamb","dry","pregnant","lactating","ram"),"يُبحث عند وجود المرض أو خطره مع تقييم الطبيب."),
 AnnualVaccineDefinition("orf","الأورف","إضافي حسب وجود المرض",setOf("lamb","dry","pregnant","lactating","ram"),"ليس روتينياً؛ للقطعان المتأثرة وفق المنتج والطبيب."),
 AnnualVaccineDefinition("anthrax","الجمرة الخبيثة","حسب المنطقة والسلطات",setOf("lamb","dry","pregnant","lactating","ram"),"يقتصر على المناطق المعروفة بالخطر وبأمر بيطري أو رسمي."),
 AnnualVaccineDefinition("bluetongue","الحمى الزرقاء","حسب الوبائيات",setOf("lamb","dry","pregnant","lactating","ram"),"يعتمد على النمط المصلي المنتشر واللقاح المتاح وتوجيه الطبيب."),
 AnnualVaccineDefinition("rabies","داء الكلب","حسب الخطر المحلي",setOf("lamb","dry","pregnant","lactating","ram"),"يُدرج فقط عند وجود خطر محلي أو توصية رسمية/بيطرية.")
)

private object AnnualVaxStore {
 private const val PREF="mawashi_annual_vaccination_v1"
 private fun arr(c:Context,p:String,k:String)=runCatching{JSONArray(c.getSharedPreferences(p,0).getString(k,"[]")?:"[]")}.getOrElse{JSONArray()}
 private fun s(x:JSONObject,vararg keys:String)=keys.firstNotNullOfOrNull{ x.optString(it).takeIf{v->v.isNotBlank()&&v!="null"} }.orEmpty()
 private fun canonical(id:String,name:String):String { val v=(id+" "+name).lowercase(); return when {
  "clostr" in v||"cdt" in v||"تسمم" in v||"كزاز" in v->"clostridial"; "pasteur" in v||"باستور" in v->"pasteurella"
  "pox" in v||"جدري" in v->"pox"; "ppr" in v||"طاعون" in v->"ppr"; "fmd" in v||"قلاعية" in v->"fmd"
  "rev.1" in v||"bruc" in v||"بروسيلا" in v->"brucella"; "chlam" in v||"كلاميديا" in v->"chlamydia"
  "campyl" in v||"كامبيلوباكتر" in v->"campylobacter"; "footrot" in v||"تعفن القدم" in v->"footrot"
  "caseous" in v||"جبنية" in v->"caseous"; "orf" in v||"أورف" in v->"orf"; "anthrax" in v||"جمرة" in v->"anthrax"
  "blue" in v||"زرقاء" in v->"bluetongue"; "rabies" in v||"كلب" in v->"rabies"; else->id.ifBlank{name}
 } }
 private fun legacy(c:Context,p:String,k:String,tag:String,source:String):List<AnnualVaccineRecord> {
  val a=arr(c,p,k); return (0 until a.length()).mapNotNull{i->runCatching{
   val x=a.getJSONObject(i); val t=s(x,"tag"); val type=s(x,"type","category")
   if(!t.equals(tag,true)||(type.isNotBlank()&&type!="لقاح"&&type!="تحصين")) return@runCatching null
   val name=s(x,"itemName","item","title","vaccine"); val id=canonical(s(x,"itemId","vaccine","vaccineId","item"),name); val date=s(x,"date")
   if(date.isBlank()||runCatching{LocalDate.parse(date)}.isFailure)return@runCatching null
   AnnualVaccineRecord(s(x,"id").ifBlank{"$source-$tag-$id-$date-$i"},t,id,date,s(x,"number","doseNumber").toIntOrNull()?:1,s(x,"dose"),s(x,"product"),s(x,"batch"),s(x,"expiry"),s(x,"vet"),s(x,"nextDate"),s(x,"notes"),source)
  }.getOrNull()}
 }
 fun load(c:Context,tag:String):List<AnnualVaccineRecord>{
  val a=arr(c,PREF,"records"); val own=(0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);AnnualVaccineRecord(x.getString("id"),x.getString("tag"),x.getString("vaccine"),x.getString("date"),x.optInt("number",1),x.optString("dose"),x.optString("product"),x.optString("batch"),x.optString("expiry"),x.optString("vet"),x.optString("nextDate"),x.optString("notes"),"مواشي")}.getOrNull()}.filter{it.tag.equals(tag,true)}
  val old=listOf(legacy(c,"mawashi_lamb_prevention_v1","doses",tag,"الحملان"),legacy(c,"mawashi_dry_ewe_program_v1","doses",tag,"الجافة"),legacy(c,"mawashi_pregnant_ewe_program_v1","records",tag,"الحوامل"),legacy(c,"mawashi_lactating_ewe_program_v1","preventive",tag,"المرضعات"),legacy(c,"mawashi_ram_program_v1","records",tag,"الفحول")).flatten()
  return (own+old).distinctBy{"${it.tag.lowercase()}|${it.vaccineId}|${it.date}|${it.product}|${it.number}"}.sortedBy{it.date}
 }
 fun save(c:Context,r:AnnualVaccineRecord){val a=arr(c,PREF,"records");a.put(JSONObject().put("id",r.id).put("tag",r.tag).put("vaccine",r.vaccineId).put("date",r.date).put("number",r.number).put("dose",r.dose).put("product",r.product).put("batch",r.batch).put("expiry",r.expiry).put("vet",r.vet).put("nextDate",r.nextDate).put("notes",r.notes));check(c.getSharedPreferences(PREF,0).edit().putString("records",a.toString()).commit())}
}

internal fun smartLambClostridialFirstDoseDate(birth:LocalDate,damVaccinated:Boolean)=birth.plusDays(if(damVaccinated)42 else 14)
internal fun smartLambClostridialBoosterDate(first:LocalDate,days:Int=28)=first.plusDays(days.coerceIn(21,28).toLong())
internal fun smartAnnualWindowContains(date:LocalDate,today:LocalDate)=!date.isBefore(today.minusDays(30))&&!date.isAfter(today.plusYears(1))

/**
 * Lactating ewes need an explicit review path even when no dose date is justified.
 * Missing history creates a review due now, never an automatic injection order.
 * A documented annual vaccine without a product-specific nextDate gets a review
 * at the one-year point, not an assumed administration date.
 */
internal fun smartAdultAnnualCoreTasks(
    category: String,
    history: List<AnnualVaccineRecord>,
    today: LocalDate,
    expectedBirthDate: String = ""
): List<AnnualVaccineTask> {
    if (category !in setOf("dry", "pregnant", "lactating", "ram")) return emptyList()
    val out = mutableListOf<AnnualVaccineTask>()
    val core = listOf(
        "clostridial" to "التسمم المعوي والكزاز",
        "pasteurella" to "الباستوريلا"
    )
    core.forEach { (id, title) ->
        val doses = history.filter { it.vaccineId == id }
            .sortedBy { runCatching { LocalDate.parse(it.date) }.getOrDefault(LocalDate.MIN) }
        val latest = doses.lastOrNull()
        val productNext = latest?.nextDate?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (productNext != null) return@forEach

        val due = if (id == "clostridial" && category == "pregnant") {
            val lambing = runCatching { LocalDate.parse(expectedBirthDate) }.getOrNull()
            lambing?.minusDays(21)
                ?.takeIf { doses.none { r -> runCatching { LocalDate.parse(r.date).isAfter(it.minusDays(300)) }.getOrDefault(false) } }
        } else if (latest != null) {
            runCatching { LocalDate.parse(latest.date).plusYears(1) }.getOrNull()
        } else today

        if (due != null && smartAnnualWindowContains(due, today)) {
            val noHistory = latest == null
            val isPreLambing = id == "clostridial" && category == "pregnant" && expectedBirthDate.isNotBlank()
            out += AnnualVaccineTask(
                "annual-core-$category-$id",
                id,
                when {
                    isPreLambing -> "مراجعة تحصين ما قبل الولادة • $title"
                    noHistory -> "مراجعة تأسيس التحصين • $title"
                    else -> "مراجعة الاستحقاق السنوي • $title"
                },
                due,
                when {
                    isPreLambing -> "موعد مراجعة قبل الولادة المتوقعة بـ21 يوماً. افحص سجل الجرعات ونشرة المنتج وحالة الحمل مع الطبيب؛ ليس أمراً تلقائياً بإعطاء جرعة."
                    noHistory -> "لا توجد جرعة موثقة. راجع سجل الحيوان وحدد الحاجة إلى الجرعات التأسيسية والمنتج والفاصل مع الطبيب؛ هذا موعد مراجعة وليس أمراً بإعطاء جرعة."
                    else -> "موعد مراجعة بعد مرور سنة على آخر جرعة مسجلة. تحقق من مدة المناعة ونشرة المنتج والبرنامج المحلي قبل تحديد الجرعة."
                },
                if (noHistory) "مراجعة تأسيسية" else if (isPreLambing) "قبل الولادة" else "مراجعة سنوية"
            )
        }
    }
    return out
}

private fun annualJsonArray(c:Context,pref:String,key:String):JSONArray =
 runCatching { JSONArray(c.getSharedPreferences(pref,Context.MODE_PRIVATE).getString(key,"[]") ?: "[]") }.getOrElse { JSONArray() }

private fun annualTasks(c:Context,category:String,a:HerdAnimal,history:List<AnnualVaccineRecord>,today:LocalDate):List<AnnualVaccineTask>{
 val out=mutableListOf<AnnualVaccineTask>()
 history.filter{it.nextDate.isNotBlank()}.forEach{r->val d=runCatching{LocalDate.parse(r.nextDate)}.getOrNull()?:return@forEach;if(smartAnnualWindowContains(d,today)){val v=ANNUAL_VACCINE_CATALOG.firstOrNull{it.id==r.vaccineId};out+=AnnualVaccineTask("record-${r.id}",r.vaccineId,"موعد المراجعة/الجرعة التالية • ${v?.title?:r.vaccineId}",d,"موعد محفوظ في السجل السابق؛ تحقق من نشرة المنتج قبل التنفيذ.","سجل الحيوان")}}
 if(category=="lamb"){val birth=runCatching{LocalDate.parse(a.birthDate)}.getOrNull();if(birth!=null){val ds=history.filter{it.vaccineId=="clostridial"}.sortedBy{it.date};if(ds.isEmpty()){
  val dam=maternalVaccinationStatus(c,a.motherTag.orEmpty(),"clostridial")
  val plan=runCatching{val rows=annualJsonArray(c,"mawashi_lamb_prevention_v1","plans");(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.optString("tag").equals(a.tag,true)&&it.optString("vaccine")=="clostridial"}}.getOrNull()
  val age=plan?.optInt("age",-1)?.takeIf{it>=0};val date=when{age!=null->birth.plusDays(age.toLong());dam=="محصنة"->smartLambClostridialFirstDoseDate(birth,true);dam=="غير محصنة"->smartLambClostridialFirstDoseDate(birth,false);else->null}
  out+=AnnualVaccineTask("lamb-first","clostridial","الجرعة الأولى • التسمم المعوي والكزاز",date,when{age!=null->"حُسب من الخطة المحفوظة.";dam=="محصنة"->"موعد إرشادي عند 6 أسابيع مع أم مسجلة محصنة؛ راجع نشرة المنتج والطبيب.";dam=="غير محصنة"->"موعد إرشادي عند أسبوعين مع أم مسجلة غير محصنة؛ راجع نشرة المنتج والطبيب.";else->"حالة تحصين الأم مجهولة؛ تحقق منها قبل تحديد الموعد."},"تحصين أساسي مشروط")
 }else if(ds.size==1){val first=LocalDate.parse(ds.first().date);val days=runCatching{val rows=annualJsonArray(c,"mawashi_lamb_prevention_v1","plans");(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.optString("tag").equals(a.tag,true)&&it.optString("vaccine")=="clostridial"}?.optInt("booster",-1)?.takeIf{it>0}}.getOrNull()?:28;out+=AnnualVaccineTask("lamb-booster","clostridial","الجرعة المنشطة • التسمم المعوي والكزاز",smartLambClostridialBoosterDate(first,days),"فاصل إرشادي 3–4 أسابيع أو الفاصل المحفوظ في خطة المنتج؛ راجع النشرة.","تاريخ الجرعة الأولى")}}}
 if(category=="dry"){
  val rows=runCatching{JSONArray(c.getSharedPreferences("mawashi_dry_ewe_program_v1",Context.MODE_PRIVATE).getString("plans","[]")?:"[]")}.getOrElse{JSONArray()}
  for(i in 0 until rows.length()){
   val p=runCatching{rows.getJSONObject(i)}.getOrNull()?:continue
   if(!p.optString("tag").equals(a.tag,true))continue
   val id=p.optString("vaccine");if(out.any{it.vaccineId==id&&it.due!=null})continue
   val doses=history.filter{it.vaccineId==id}.sortedBy{it.date}
   val interval=p.optInt("interval",-1).takeIf{it>0}
   val due=if(doses.isEmpty())runCatching{LocalDate.parse(p.optString("firstDue"))}.getOrNull()
           else interval?.let{runCatching{LocalDate.parse(doses.last().date).plusDays(it.toLong())}.getOrNull()}
   if(due!=null&&smartAnnualWindowContains(due,today)){
    val title=ANNUAL_VACCINE_CATALOG.firstOrNull{it.id==id}?.title?:id
    out+=AnnualVaccineTask("dry-plan-$id",id,"موعد الخطة المحفوظة • $title",due,
      "حُسب من تاريخ الخطة أو آخر جرعة والفاصل الذي سبق حفظه؛ راجع نشرة المنتج قبل التنفيذ.","خطة المنتج المحفوظة")
   }
  }
 }
 if(category in setOf("dry","pregnant","lactating","ram")) out += smartAdultAnnualCoreTasks(category,history,today,a.expectedBirthDate).filterNot { task -> out.any { it.vaccineId == task.vaccineId && it.due != null } }
 val dated=out.map{it.vaccineId}.toSet()
 ANNUAL_VACCINE_CATALOG.filter{category in it.applicable&&it.id !in dated}.forEach{v->out+=AnnualVaccineTask("conditional-${v.id}",v.id,v.title,null,v.guidance,when(v.id){"ppr","fmd","pox","anthrax"->"حملة رسمية";"pasteurella","footrot","caseous","orf","chlamydia","campylobacter","bluetongue","rabies"->"حسب الخطر";else->"مراجعة السجل"})}
 return out.sortedWith(compareBy<AnnualVaccineTask>({it.due==null},{it.due?:LocalDate.MAX},{it.title}))
}

@Composable
internal fun SmartAnnualVaccinationScreen(category:String,animal:HerdAnimal?,modifier:Modifier=Modifier){
 val c=LocalContext.current
 var records by remember(animal?.tag){mutableStateOf(animal?.let{AnnualVaxStore.load(c,it.tag)}.orEmpty())}
 var chosen by rememberSaveable(animal?.tag){mutableStateOf("clostridial")}
 var dialog by rememberSaveable(animal?.tag){mutableStateOf(false)}
 var date by rememberSaveable(animal?.tag){mutableStateOf(LocalDate.now().toString())}
 var dose by rememberSaveable(animal?.tag){mutableStateOf("")};var product by rememberSaveable(animal?.tag){mutableStateOf("")}
 var batch by rememberSaveable(animal?.tag){mutableStateOf("")};var expiry by rememberSaveable(animal?.tag){mutableStateOf("")}
 var vet by rememberSaveable(animal?.tag){mutableStateOf("")};var next by rememberSaveable(animal?.tag){mutableStateOf("")};var notes by rememberSaveable(animal?.tag){mutableStateOf("")}
 val today=LocalDate.now();val catalog=ANNUAL_VACCINE_CATALOG.filter{category in it.applicable};val tasks=if(animal==null)emptyList()else annualTasks(c,category,animal,records,today)
 val dated=tasks.filter{it.due!=null&&smartAnnualWindowContains(it.due,today)}
 Column(modifier.fillMaxWidth().padding(top=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  if(animal==null) Text("لا يوجد حيوان محدد لعرض برنامجه السنوي.",color=Color(0xFF778078),fontSize=13.sp)
  else{
   Surface(color=Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Color(0xFFE8E4DA))){Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
    Text("البرنامج السنوي الذكي • ${animal.tag}",color=Color(0xFF27332A),fontSize=16.sp,fontWeight=FontWeight.Bold)
    Text("العمر: ${herdAnimalAgeLabel(animal)} • الوزن: ${animal.weightKg?.let{"%.1f كغ".format(java.util.Locale.US,it)}?:"غير مسجل"}",color=Color(0xFF778078),fontSize=12.sp)
    Text("الفترة: $today إلى ${today.plusYears(1)}",color=Color(0xFF526B55),fontSize=12.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){AnnualMetric("مواعيد ومراجعات",dated.size.toString(),Modifier.weight(1f));AnnualMetric("متأخرة",dated.count{it.due!!.isBefore(today)}.toString(),Modifier.weight(1f));AnnualMetric("جرعات موثقة",records.size.toString(),Modifier.weight(1f))}
   }}
   Text("برنامج الاثني عشر شهراً",color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=15.sp)
   if(dated.isEmpty())Text("لا توجد جرعات أو مراجعات مؤرخة ضمن هذه الفترة. اللقاحات المرتبطة بالحملات أو المخاطر تظهر للمراجعة دون تحديد موعد أو إعطاء تلقائي.",color=Color(0xFF778078),fontSize=12.sp)
   dated.forEach{t->val d=t.due!!;val state=when{d.isBefore(today)->"متأخر";d==today->"مستحق اليوم";else->"قادم"};Surface(color=Color.White,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFE8E4DA))){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(t.title,Modifier.weight(1f),color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=13.sp);Text(state,color=if(state=="متأخر")Color(0xFFB35C43)else Color(0xFF526B55),fontSize=11.sp,fontWeight=FontWeight.Bold)};Text("$d • ${t.group}",color=Color(0xFF526B55),fontSize=11.sp);Text(t.reason,color=Color(0xFF778078),fontSize=11.sp)}}}
   Text("لقاحات مرتبطة بالحملات أو تقييم الخطر",color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=15.sp)
   tasks.filter{it.due==null}.forEach{t->Surface(color=Color.White,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFE8E4DA))){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(t.title,color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=13.sp);Text(t.group,color=Color(0xFF526B55),fontSize=11.sp);Text(t.reason,color=Color(0xFF778078),fontSize=11.sp)}}}
   Button(onClick={dialog=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF526B55))){Text("تسجيل جرعة منفذة فعلياً")}
   Text("المواعيد تُحسب من الميلاد والسجل وخطة المنتج والحالة الإنتاجية. الحملات تتبع تعليمات المصالح البيطرية الجزائرية. لا تُسجّل جرعة تلقائياً، ولا تُحدد جرعة الدواء أو المكمل من الوزن وحده.",color=Color(0xFF778078),fontSize=11.sp)
   Text("السجل السابق",color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=15.sp)
   records.sortedByDescending{it.date}.forEach{r->Surface(color=Color.White,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Color(0xFFE8E4DA))){Column(Modifier.padding(9.dp)){Text(catalog.firstOrNull{it.id==r.vaccineId}?.title?:r.vaccineId,color=Color(0xFF27332A),fontWeight=FontWeight.Bold,fontSize=12.sp);Text("الجرعة ${r.number} • ${r.date} • ${r.product}",color=Color(0xFF526B55),fontSize=11.sp);if(r.nextDate.isNotBlank())Text("المراجعة التالية: ${r.nextDate}",color=Color(0xFF778078),fontSize=11.sp);if(r.notes.isNotBlank())Text(r.notes,color=Color(0xFF778078),fontSize=11.sp)} }}
  }
 }
 if(dialog&&animal!=null)AlertDialog(onDismissRequest={dialog=false},containerColor=Color(0xFFF7F3EA),title={Text("توثيق جرعة منفذة",color=Color(0xFF27332A),fontWeight=FontWeight.Bold)},text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
  catalog.forEach{v->FilterChip(selected=chosen==v.id,onClick={chosen=v.id},label={Text(v.title,fontSize=11.sp)})}
  MawashiDatePickerField(date,{date=it},"تاريخ إعطاء اللقاح",Modifier.fillMaxWidth())
  OutlinedTextField(dose,{dose=it},modifier=Modifier.fillMaxWidth(),label={Text("الجرعة والتركيز حسب النشرة")})
  OutlinedTextField(product,{product=it},modifier=Modifier.fillMaxWidth(),label={Text("اسم المنتج")})
  OutlinedTextField(batch,{batch=it},modifier=Modifier.fillMaxWidth(),label={Text("رقم التشغيلة (اختياري)")})
  OutlinedTextField(expiry,{expiry=it},modifier=Modifier.fillMaxWidth(),label={Text("تاريخ الصلاحية (اختياري)")})
  OutlinedTextField(vet,{vet=it},modifier=Modifier.fillMaxWidth(),label={Text("الطبيب البيطري")})
  MawashiDatePickerField(next,{next=it},"موعد الجرعة التالية/المراجعة إن حُدد",Modifier.fillMaxWidth())
  OutlinedTextField(notes,{notes=it},modifier=Modifier.fillMaxWidth(),label={Text("ملاحظات أو تفاعلات")},minLines=2)
  Text("أدخل الموعد التالي فقط إذا حددته نشرة المنتج أو الطبيب. لا يفترض أن كل لقاح يحتاج منشطاً سنوياً.",color=Color(0xFF778078),fontSize=11.sp)
 }},confirmButton={Button(onClick={val d=runCatching{LocalDate.parse(date)}.getOrNull();val n=if(next.isBlank())null else runCatching{LocalDate.parse(next)}.getOrNull();if(d==null||d.isAfter(today)||product.isBlank()||(next.isNotBlank()&&(n==null||n.isBefore(d))))Toast.makeText(c,"تحقق من التاريخ والمنتج وموعد المراجعة",Toast.LENGTH_LONG).show()else{val v=ANNUAL_VACCINE_CATALOG.first{it.id==chosen};val num=(records.filter{it.vaccineId==chosen}.maxOfOrNull{it.number}?:0)+1;val r=AnnualVaccineRecord(UUID.randomUUID().toString(),animal.tag,chosen,d.toString(),num,dose.trim(),product.trim(),batch.trim(),expiry.trim(),vet.trim(),n?.toString().orEmpty(),notes.trim(),"مواشي");runCatching{AnnualVaxStore.save(c,r);if(n!=null){val id="annual-vax-next-${r.id}";saveHerdCare(c,readHerdCare(c).filterNot{it.id==id}+HerdCareReminder(id,animal.tag,"التطعيمات السنوية","مراجعة • ${v.title}",n.toString(),"موعد من نشرة المنتج أو الطبيب؛ تحقق من الملاءمة قبل التنفيذ.",false))}}.onSuccess{records=AnnualVaxStore.load(c,animal.tag);dialog=false;dose="";product="";batch="";expiry="";vet="";next="";notes="";Toast.makeText(c,"تم توثيق الجرعة وتحديث البرنامج السنوي",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(c,"تعذر حفظ الجرعة: ${it.message}",Toast.LENGTH_LONG).show()}}},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF526B55))){Text("حفظ الجرعة")}},dismissButton={TextButton(onClick={dialog=false}){Text("إلغاء",color=Color(0xFF526B55))}})
}

@Composable private fun AnnualMetric(label:String,value:String,modifier:Modifier){Surface(modifier,color=Color(0xFFF7F3EA),shape=RoundedCornerShape(9.dp)){Column(Modifier.padding(7.dp)){Text(value,color=Color(0xFF526B55),fontSize=17.sp,fontWeight=FontWeight.ExtraBold);Text(label,color=Color(0xFF778078),fontSize=10.sp)}}}
