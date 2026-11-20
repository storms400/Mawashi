package dz.mawashi.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private val DryGreen = Color(0xFF526B55)
private val DryCream = Color(0xFFF7F3EA)
private val DryInk = Color(0xFF27332A)
private val DryMuted = Color(0xFF778078)
private val DryLine = Color(0xFFE8E4DA)

private data class DryAction(val id: String, val title: String, val hint: String)
private data class DryStage(val id: String, val title: String, val period: String, val goal: String, val actions: List<DryAction>)
private data class DryCareRecord(val id: String, val tag: String, val stageId: String, val actionId: String, val date: String, val result: String, val notes: String)
private data class DryVaccine(val id: String, val title: String, val disease: String, val group: String, val guidance: String)
private data class DryVaccinePlan(val tag: String, val vaccineId: String, val firstDue: String, val intervalDays: Int?, val product: String, val vetRule: String)
private data class DryDose(val id: String, val tag: String, val vaccineId: String, val number: Int, val date: String, val product: String, val batch: String, val expiry: String, val vet: String, val notes: String)

private val DRY_STAGES = listOf(
    DryStage("weaning", "المرحلة الأولى – ما بعد الفطام", "من تاريخ الفطام", "التأكد من انتهاء الرضاعة وسلامة الضرع واستقرار الحالة العامة.", listOf(
        DryAction("wean_date", "تسجيل تاريخ الفطام", "تاريخ الفطام الفعلي."),
        DryAction("udder", "فحص الضرع والحلمات", "الحجم والحرارة والألم والتورم أو الإفرازات."),
        DryAction("milk_stop", "متابعة توقف إنتاج الحليب", "ملاحظة امتلاء الضرع أو أي علامة التهاب."),
        DryAction("general", "فحص الحالة العامة والشهية", "النشاط والشهية والاجترار ودرجة الحرارة عند الحاجة.")
    )),
    DryStage("assessment", "المرحلة الثانية – الفحص الصحي وتقييم الحالة", "بعد الفطام وخلال فترة الجفاف", "تحديد الحالة الجسمية والصحية قبل إعداد التغذية.", listOf(
        DryAction("weight", "قياس الوزن", "الوزن بالكيلوغرام وتاريخ القياس."),
        DryAction("bcs", "تقييم حالة الجسم BCS", "درجة من 1 إلى 5 مع وصف حالة النعجة."),
        DryAction("teeth", "فحص الأسنان", "سلامة القواطع وقدرتها على الرعي."),
        DryAction("feet", "فحص الأظلاف والمشي", "طول الظلف والعرج وأي آفات."),
        DryAction("parasite_check", "تقييم الطفيليات", "حالة الجسم والأغشية المخاطية والبراز أو تحليل البراز عند الحاجة.")
    )),
    DryStage("nutrition", "المرحلة الثالثة – التغذية واستعادة الحالة", "طوال فترة الجفاف", "ضبط العليقة حسب الوزن وحالة الجسم والعلف المتاح والمراعي.", listOf(
        DryAction("ration", "مراجعة العليقة", "نوع العلف والكميات والمادة الجافة المتاحة."),
        DryAction("body_change", "متابعة تغير حالة الجسم", "مقارنة الوزن ودرجة BCS مع القياسات السابقة."),
        DryAction("water_minerals", "الماء والأملاح المعدنية", "توفر الماء النظيف ومعدن مخصص للأغنام.")
    )),
    DryStage("prevention", "المرحلة الرابعة – الوقاية والمتابعة الصحية", "وفق سجل القطيع والمخاطر المحلية", "مراجعة التحصينات والطفيليات والإصابات دون علاج أو تلقيح تلقائي غير مبرر.", listOf(
        DryAction("vax_history", "مراجعة سجل اللقاحات", "اللقاح والمنتج وتاريخ الجرعة والجرعة المنشطة."),
        DryAction("parasite_plan", "خطة مكافحة الطفيليات", "قرار مبني على الفحص أو تحليل البراز وتوجيه الطبيب."),
        DryAction("disease_signs", "متابعة علامات المرض", "السعال والإفرازات والإسهال والعرج وفقد الشهية."),
        DryAction("treatment_log", "توثيق العلاجات", "اسم الدواء والجرعة والمدة وفترة السحب إن وجدت.")
    )),
    DryStage("breeding", "المرحلة الخامسة – الاستعداد للتلقيح", "قبل إدخال الفحل", "التأكد من جاهزية النعجة وتوثيق بداية موسم التلقيح.", listOf(
        DryAction("breeding_bcs", "تقييم الحالة الجسمية قبل التلقيح", "الوزن ودرجة BCS ومدى ملاءمتهما لهدف القطيع."),
        DryAction("repro_health", "فحص الجاهزية التناسلية", "الحالة الصحية والتاريخ التناسلي."),
        DryAction("breeding_date", "تسجيل تاريخ إدخال الفحل", "تاريخ بداية التلقيح وطريقة التلقيح."),
        DryAction("final_review", "التقييم النهائي", "قرار الجاهزية أو تأجيل التلقيح مع السبب.")
    ))
)

private val DRY_VACCINES = listOf(
    DryVaccine("clostridial", "التسمم المعوي والكزاز", "أمراض المطثيات والكزاز", "أساسي بحسب تاريخ التحصين", "راجع إكمال الجرعتين التأسيسيتين؛ المنشط غالباً سنوي حسب المنتج. قبل الولادة التالية قد يلزم توقيت خاص لحماية المواليد."),
    DryVaccine("pasteurella", "الباستوريلا", "الأمراض التنفسية الباستوريليّة", "حسب مخاطر القطيع", "يُستخدم عند وجود خطر أو تاريخ مرضي؛ التأسيس والمنشط والفاصل حسب المنتج."),
    DryVaccine("pox", "جدري الأغنام", "جدري الأغنام", "حسب الحملة الرسمية", "في الجزائر قد تأمر السلطات بالتلقيح في منطقة أو حول بؤرة؛ لا تنشئ موعداً سنوياً تلقائياً."),
    DryVaccine("ppr", "طاعون المجترات الصغيرة PPR", "PPR", "حسب البرنامج الرسمي", "يُضبط وفق حملة المصالح البيطرية واللقاح المعتمد، ولا يفترض تكراراً ثابتاً."),
    DryVaccine("fmd", "الحمى القلاعية FMD", "الحمى القلاعية", "حسب البرنامج الرسمي", "يُدرج إذا شمل البرنامج الرسمي الأغنام في المنطقة؛ اتبع نوع اللقاح وتعليماته."),
    DryVaccine("brucella", "البروسيلا Rev.1", "Brucella melitensis", "للإناث الصغيرة وفق البرنامج الرسمي", "ليس منشطاً للنعاج الجافة البالغة. لقاح حي ذو قيود خاصة؛ لا يُعطى للحامل أو عشوائياً."),
    DryVaccine("chlamydia", "الكلاميديا المسببة للإجهاض", "Chlamydia abortus", "قبل التلقيح عند وجود خطر", "يُخطط قبل التلقيح وبحسب المنتج؛ بعض اللقاحات الحية ممنوعة للحامل."),
    DryVaccine("campylobacter", "الكامبيلوباكتر المسبب للإجهاض", "Campylobacter fetus", "قبل التلقيح عند وجود خطر", "يُستخدم في القطعان ذات التاريخ أو الخطر المثبت، وفق نظام الجرعات للمنتج."),
    DryVaccine("footrot", "تعفن القدم", "Footrot", "حسب وجود المرض", "قبل موسم الخطر عند وجود المشكلة؛ لا يُعد لقاحاً روتينياً لكل القطعان."),
    DryVaccine("cl", "الخراجات الجبنية", "Caseous lymphadenitis", "حسب وجود المرض", "يُبحث عند وجود المرض أو خطره؛ تقييم الحيوان واللقاح ضروري."),
    DryVaccine("orf", "الأورف", "Contagious ecthyma", "حسب وجود المرض", "ليس روتينياً؛ يُستخدم في القطعان المتأثرة ووفق تعليمات المنتج."),
    DryVaccine("anthrax", "الجمرة الخبيثة", "Anthrax", "حسب المنطقة وتوجيه السلطات", "يقتصر على المناطق المعروفة بالخطر وبأمر بيطري أو رسمي."),
    DryVaccine("bluetongue", "الحمى الزرقاء", "Bluetongue", "حسب الوبائيات واللقاح المتاح", "يعتمد على النمط المصلي المنتشر وتوجيه الطبيب؛ لا يُحدد من العمر وحده."),
    DryVaccine("rabies", "داء الكلب", "Rabies", "حسب الخطر المحلي", "يُدرج فقط في المناطق أو الحالات التي توصي فيها السلطات أو الطبيب بذلك.")
)


private fun syncDryEweReminders(context: Context, animals: List<HerdAnimal>, plans: List<DryVaccinePlan>, doses: List<DryDose>) {
    val retained = readHerdCare(context).filterNot { it.kind == "برنامج النعاج الجافة" && !it.completed }
    val generated = mutableListOf<HerdCareReminder>()
    animals.forEach { animal ->
        DRY_VACCINES.forEach vaccineLoop@ { vaccine ->
            val plan = plans.firstOrNull { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id } ?: return@vaccineLoop
            val history = doses.filter { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id }.sortedBy { it.date }
            val due = if (history.isEmpty()) runCatching { LocalDate.parse(plan.firstDue) }.getOrNull()
                else plan.intervalDays?.let { days -> runCatching { LocalDate.parse(history.last().date).plusDays(days.toLong()) }.getOrNull() }
            if (due != null) generated += HerdCareReminder(
                id = "dry-ewe-vaccine-${animal.tag}-${vaccine.id}",
                tag = animal.tag,
                kind = "برنامج النعاج الجافة",
                title = (if (history.isEmpty()) "موعد اللقاح" else "موعد الجرعة المنشطة") + " • " + vaccine.title,
                date = due.toString(),
                notes = "موعد للمراجعة البيطرية وفق الخطة المحفوظة؛ لا يعني إعطاء اللقاح تلقائياً.",
                completed = false
            )
        }
    }
    saveHerdCare(context, retained + generated)
}

private object DryEweStore {
    private const val PREFS = "mawashi_dry_ewe_program_v1"
    private fun read(c: Context, key: String): JSONArray {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
    private fun write(c: Context, key: String, array: JSONArray) {
        check(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).commit()) { "تعذر حفظ برنامج النعاج الجافة" }
    }
    fun care(c: Context): List<DryCareRecord> = (0 until read(c, "care").length()).mapNotNull { i -> runCatching {
        val x = read(c, "care").getJSONObject(i)
        DryCareRecord(x.optString("id"), x.optString("tag"), x.optString("stage"), x.optString("action"), x.optString("date"), x.optString("result"), x.optString("notes"))
    }.getOrNull() }
    fun saveCare(c: Context, values: List<DryCareRecord>) {
        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("id",it.id).put("tag",it.tag).put("stage",it.stageId).put("action",it.actionId).put("date",it.date).put("result",it.result).put("notes",it.notes)) }
        write(c, "care", a)
    }
    fun plans(c: Context): List<DryVaccinePlan> = (0 until read(c, "plans").length()).mapNotNull { i -> runCatching {
        val x = read(c, "plans").getJSONObject(i)
        DryVaccinePlan(x.optString("tag"), x.optString("vaccine"), x.optString("firstDue"), x.optInt("interval",-1).takeIf { it > 0 }, x.optString("product"), x.optString("rule"))
    }.getOrNull() }
    fun savePlans(c: Context, values: List<DryVaccinePlan>) {
        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("tag",it.tag).put("vaccine",it.vaccineId).put("firstDue",it.firstDue).put("interval",it.intervalDays ?: -1).put("product",it.product).put("rule",it.vetRule)) }
        write(c, "plans", a)
    }
    fun doses(c: Context): List<DryDose> = (0 until read(c, "doses").length()).mapNotNull { i -> runCatching {
        val x = read(c, "doses").getJSONObject(i)
        DryDose(x.optString("id"),x.optString("tag"),x.optString("vaccine"),x.optInt("number",1),x.optString("date"),x.optString("product"),x.optString("batch"),x.optString("expiry"),x.optString("vet"),x.optString("notes"))
    }.getOrNull() }
    fun saveDoses(c: Context, values: List<DryDose>) {
        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("id",it.id).put("tag",it.tag).put("vaccine",it.vaccineId).put("number",it.number).put("date",it.date).put("product",it.product).put("batch",it.batch).put("expiry",it.expiry).put("vet",it.vet).put("notes",it.notes)) }
        write(c, "doses", a)
    }
}

@Composable
internal fun DryEweProgramScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var animals by remember { mutableStateOf(emptyList<HerdAnimal>()) }
    var selectedTag by rememberSaveable { mutableStateOf("") }
    var care by remember { mutableStateOf(DryEweStore.care(context)) }
    var plans by remember { mutableStateOf(DryEweStore.plans(context)) }
    var doses by remember { mutableStateOf(DryEweStore.doses(context)) }
    var stageId by rememberSaveable { mutableStateOf("weaning") }
    var careDialog by remember { mutableStateOf<DryAction?>(null) }
    var planDialog by remember { mutableStateOf<DryVaccine?>(null) }
    var doseDialog by remember { mutableStateOf<DryVaccine?>(null) }

    LaunchedEffect(Unit) {
        animals = loadHerd(context).filter { it.kind.trim() == "نعجة" && it.lifecycleStatus != "مؤرشف" && it.productionState == "نعجة جافة" && it.pregnancyStage != "حامل" }
        if (animals.none { it.tag.equals(selectedTag, true) }) selectedTag = animals.firstOrNull()?.tag.orEmpty()
        syncDryEweReminders(context, animals, DryEweStore.plans(context), DryEweStore.doses(context))
    }
    val animal = animals.firstOrNull { it.tag.equals(selectedTag, true) }
    val animalCare = care.filter { it.tag.equals(selectedTag, true) }
    val animalPlans = plans.filter { it.tag.equals(selectedTag, true) }
    val animalDoses = doses.filter { it.tag.equals(selectedTag, true) }
    val today = LocalDate.now()
    val reminders = DRY_VACCINES.mapNotNull { vaccine ->
        val plan = animalPlans.firstOrNull { it.vaccineId == vaccine.id } ?: return@mapNotNull null
        val previous = animalDoses.filter { it.vaccineId == vaccine.id }.maxByOrNull { it.date }
        val due = if (previous == null) runCatching { LocalDate.parse(plan.firstDue) }.getOrNull() else plan.intervalDays?.let { runCatching { LocalDate.parse(previous.date).plusDays(it.toLong()) }.getOrNull() }
        due?.let { Triple(it, vaccine, previous == null) }
    }

    Column(modifier.fillMaxSize().background(DryCream).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != "home" || onBack != null) IconButton(onClick = { if (page == "home") onBack?.invoke() else page = "home" }) { Icon(Icons.Outlined.ArrowBack, null, tint = DryGreen) }
            Column(Modifier.weight(1f)) {
                Text(if (page == "home") "برنامج النعاج الجافة" else when(page) { "care" -> "الرعاية والمتابعة"; "vaccines" -> "التطعيمات والتحصينات"; else -> "المواعيد القادمة" }, color = DryInk, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("متابعة النعجة من الفطام حتى الاستعداد للتلقيح", color = DryMuted, fontSize = 12.sp)
            }
            if (page == "home") Text("🐑", fontSize = 27.sp)
        }
        if (animals.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, DryLine)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("لا توجد نعاج جافة مسجلة", color = DryInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("تظهر هنا النعاج المسجلة في القطيع بحالة «نعجة جافة» وغير المؤكّد حملها. حدّث حالة الإنتاج في بطاقة النعجة لتظهر في البرنامج.", color = DryMuted, fontSize = 13.sp)
                }
            }
        } else {
            if (page != "home") {
            Text("النعجة المحددة", color = DryInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                animals.forEach { a -> FilterChip(selectedTag == a.tag, { selectedTag = a.tag }, label = { Text("🐑 " + a.tag, fontSize = 12.sp) }) }
            }
            Spacer(Modifier.height(8.dp))
            }
            when (page) {
                "care" -> DryCarePage(animal, stageId, { stageId = it }, animalCare) { careDialog = it }
                "vaccines" -> SmartAnnualVaccinationScreen("dry", animal, Modifier.weight(1f))
                "reminders" -> DryRemindersPage(animal, reminders)
                else -> EweProgramOverview(
                    title = "برنامج النعاج الجافة",
                    subtitle = "الرعاية الوقائية والاستعداد لموسم التلقيح",
                    animalCount = animals.size,
                    dueCount = reminders.count { !it.first.isAfter(today) },
                    completedCount = animalCare.size,
                    stages = DRY_STAGES.map { it.title to it.period },
                    animalLines = animals.map { it.tag + " • " + (it.weightKg?.let { w -> "%.1f كغ".format(java.util.Locale.US, w) } ?: "الوزن غير مسجل") },
                    onStageClick = { index -> stageId = DRY_STAGES.getOrNull(index)?.id ?: "weaning"; page = "care" },
                    onVaccinesClick = { page = "vaccines" },
                    onRemindersClick = { page = "reminders" }
                )
            }
        }
    }

    careDialog?.let { action ->
        DryCareDialog(action, { careDialog = null }) { date, result, notes ->
            val updated = care + DryCareRecord(UUID.randomUUID().toString(), selectedTag, stageId, action.id, date, result, notes)
            runCatching { DryEweStore.saveCare(context, updated) }.onSuccess { care = updated; careDialog = null; Toast.makeText(context, "تم حفظ الإجراء", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الإجراء", Toast.LENGTH_LONG).show() }
        }
    }
    planDialog?.let { vaccine ->
        DryPlanDialog(selectedTag, vaccine, animalPlans.firstOrNull { it.vaccineId == vaccine.id }, { planDialog = null }) { plan ->
            val updated = plans.filterNot { it.tag.equals(selectedTag,true) && it.vaccineId == vaccine.id } + plan
            runCatching { DryEweStore.savePlans(context, updated); syncDryEweReminders(context, animals, updated, doses) }.onSuccess { plans = updated; planDialog = null; Toast.makeText(context, "تم حفظ الخطة", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الخطة", Toast.LENGTH_LONG).show() }
        }
    }
    doseDialog?.let { vaccine ->
        val number = (animalDoses.filter { it.vaccineId == vaccine.id }.maxOfOrNull { it.number } ?: 0) + 1
        DryDoseDialog(selectedTag, vaccine, number, { doseDialog = null }) { dose ->
            val updated = doses + dose
            runCatching { DryEweStore.saveDoses(context, updated); syncDryEweReminders(context, animals, plans, updated) }.onSuccess { doses = updated; doseDialog = null; Toast.makeText(context, "تم توثيق الجرعة", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الجرعة", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun DryStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, DryLine)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = DryGreen, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = DryMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DryHomeTile(title: String, subtitle: String, emoji: String, action: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { action() }, color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, DryLine)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 27.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DryMuted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronLeft, null, tint = DryMuted)
        }
    }
}

@Composable
private fun DryCarePage(animal: HerdAnimal?, selected: String, onStage: (String) -> Unit, records: List<DryCareRecord>, onRecord: (DryAction) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("مراحل الرعاية الخمس", color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("تُسجل كل معاينة بتاريخها ونتيجتها؛ ويظل تاريخ الفحوصات محفوظاً بدلاً من استبدال التسجيل السابق.", color = DryMuted, fontSize = 12.sp)
                }
            }
        }
        items(DRY_STAGES) { stage ->
            val expanded = selected == stage.id
            val count = stage.actions.count { a -> records.any { it.stageId == stage.id && it.actionId == a.id } }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(if (expanded) 2.dp else 1.dp, if (expanded) DryGreen else DryLine)) {
                Column(Modifier.fillMaxWidth().clickable { onStage(if (expanded) "" else stage.id) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stage.title, color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(stage.period, color = DryGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${count}/${stage.actions.size}", color = DryGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = DryMuted)
                    }
                    Text(stage.goal, color = DryMuted, fontSize = 12.sp)
                    if (expanded) {
                        HorizontalDivider(color = DryLine)
                        stage.actions.forEach { action ->
                            val latest = records.filter { it.actionId == action.id }.maxByOrNull { it.date }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(action.title, color = DryInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(latest?.let { "آخر تسجيل: ${it.date} • ${it.result}" } ?: action.hint, color = DryMuted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onRecord(action) }) { Text("إضافة فحص", color = DryGreen) }
                                }
                            }
                            HorizontalDivider(color = DryLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DryVaccinesPage(animal: HerdAnimal?, vaccines: List<DryVaccine>, plans: List<DryVaccinePlan>, doses: List<DryDose>, onPlan: (DryVaccine) -> Unit, onDose: (DryVaccine) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("جدول التطعيمات والتحصينات", color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("يشمل اللقاحات الأساسية والإضافية. أدخل الموعد المعتمد لكل نعجة من سجلها وتوجيه الطبيب؛ لا تفترض أن جميع اللقاحات مطلوبة.", color = DryMuted, fontSize = 12.sp)
                }
            }
        }
        items(vaccines) { vaccine ->
            val plan = plans.firstOrNull { it.vaccineId == vaccine.id }
            val history = doses.filter { it.vaccineId == vaccine.id }.sortedBy { it.date }
            val due = if (history.isEmpty()) plan?.firstDue else plan?.intervalDays?.let { interval -> runCatching { LocalDate.parse(history.last().date).plusDays(interval.toLong()).toString() }.getOrNull() }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, DryLine)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(vaccine.title, color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(vaccine.disease, color = DryGreen, fontSize = 12.sp)
                        }
                        Text("${history.size} جرعة", color = DryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(vaccine.group, color = DryInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(vaccine.guidance, color = DryMuted, fontSize = 11.sp)
                    Text("الموعد المسجل: ${due ?: "لم يحدد"}", color = DryMuted, fontSize = 11.sp)
                    if (plan != null) Text("المنتج: ${plan.product.ifBlank { "غير محدد" }} • الفاصل: ${plan.intervalDays?.let { "$it يوم" } ?: "غير محدد"}", color = DryMuted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onPlan(vaccine) }, modifier = Modifier.weight(1f)) { Text("ضبط الموعد والخطة", fontSize = 11.sp) }
                        Button(onClick = { onDose(vaccine) }, enabled = plan != null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DryGreen)) { Text("تسجيل جرعة", fontSize = 11.sp) }
                    }
                    if (history.isNotEmpty()) Text("آخر جرعة مسجلة: ${history.last().date}", color = DryGreen, fontSize = 10.sp)
                }
            }
        }
        item {
            Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
                Text("تنبيه: لقاح Rev.1 مخصص لبرنامج الإناث الصغيرة وفق التنظيم الرسمي، وليس جرعة للنعاج الجافة البالغة. لقاحات الإجهاض الحية قد تكون ممنوعة أثناء الحمل. لقاحات الجدري وPPR والحمى القلاعية تتبع تعليمات المصالح البيطرية الجزائرية.", color = DryInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun DryRemindersPage(animal: HerdAnimal?, reminders: List<Triple<LocalDate, DryVaccine, Boolean>>) {
    val today = LocalDate.now()
    val sorted = reminders.sortedBy { it.first }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Text("المواعيد المحسوبة ${animal?.tag.orEmpty()}", color = DryInk, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        if (sorted.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, DryLine)) {
                Text("لا توجد مواعيد مضبوطة. أدخل تاريخ الاستحقاق المعتمد في خطة اللقاح.", color = DryMuted, fontSize = 13.sp, modifier = Modifier.padding(13.dp))
            }
        }
        items(sorted) { item ->
            val state = when { item.first.isBefore(today) -> "متأخر"; item.first == today -> "اليوم"; else -> "قادم" }
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, DryLine)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.second.title, color = DryInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(item.first.toString() + if (item.third) " • الموعد الأول" else " • منشط محسوب", color = DryMuted, fontSize = 12.sp)
                    }
                    Text(state, color = if (state == "متأخر") Color(0xFFB35C43) else DryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DryCareDialog(action: DryAction, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var result by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(action.title, color = DryInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.hint, color = DryMuted, fontSize = 12.sp)
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ", Modifier.fillMaxWidth())
                OutlinedTextField(result, { result = it }, modifier = Modifier.fillMaxWidth(), label = { Text("النتيجة أو القياس") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { val d = runCatching { LocalDate.parse(date) }.getOrNull(); if (d != null && !d.isAfter(LocalDate.now()) && result.isNotBlank()) onSave(date, result.trim(), notes.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = DryGreen)) { Text("حفظ الفحص") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = DryGreen) } }, containerColor = DryCream)
}

@Composable
private fun DryPlanDialog(tag: String, vaccine: DryVaccine, initial: DryVaccinePlan?, onDismiss: () -> Unit, onSave: (DryVaccinePlan) -> Unit) {
    var firstDue by rememberSaveable { mutableStateOf(initial?.firstDue ?: "") }
    var interval by rememberSaveable { mutableStateOf(initial?.intervalDays?.toString().orEmpty()) }
    var product by rememberSaveable { mutableStateOf(initial?.product.orEmpty()) }
    var rule by rememberSaveable { mutableStateOf(initial?.vetRule.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ضبط خطة ${vaccine.title}", color = DryInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vaccine.guidance, color = DryMuted, fontSize = 12.sp)
                MawashiDatePickerField(firstDue, { firstDue = it }, "تاريخ الاستحقاق الأول", Modifier.fillMaxWidth())
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("فاصل المنشط بالأيام (اختياري)") })
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج") })
                OutlinedTextField(rule, { rule = it }, modifier = Modifier.fillMaxWidth(), label = { Text("مرجع الطبيب أو نشرة المنتج") }, minLines = 2)
                Text("اترك الموعد فارغاً إذا كان اللقاح مرتبطاً بحملة رسمية أو لم يتم تحديده بيطرياً.", color = DryMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = {
            val due = runCatching { LocalDate.parse(firstDue) }.getOrNull()
            val days = interval.toIntOrNull()
            if ((firstDue.isBlank() || due != null) && (interval.isBlank() || days != null && days in 1..3650) && product.isNotBlank()) onSave(DryVaccinePlan(tag, vaccine.id, firstDue, days, product.trim(), rule.trim()))
        }, colors = ButtonDefaults.buttonColors(containerColor = DryGreen)) { Text("حفظ الخطة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = DryGreen) } }, containerColor = DryCream)
}

@Composable
private fun DryDoseDialog(tag: String, vaccine: DryVaccine, number: Int, onDismiss: () -> Unit, onSave: (DryDose) -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var product by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var vet by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("توثيق ${vaccine.title} – الجرعة ${number}", color = DryInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MawashiDatePickerField(date, { date = it }, "تاريخ إعطاء اللقاح", Modifier.fillMaxWidth())
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المنتج التجاري") })
                OutlinedTextField(batch, { batch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم التشغيلة") })
                OutlinedTextField(expiry, { expiry = it }, modifier = Modifier.fillMaxWidth(), label = { Text("تاريخ الصلاحية") })
                OutlinedTextField(vet, { vet = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الطبيب البيطري") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات أو تفاعلات") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = {
            val d = runCatching { LocalDate.parse(date) }.getOrNull()
            if (d == null || d.isAfter(LocalDate.now()) || product.isBlank()) Toast.makeText(context, "أدخل تاريخاً صحيحاً واسم المنتج", Toast.LENGTH_SHORT).show()
            else onSave(DryDose(UUID.randomUUID().toString(), tag, vaccine.id, number, date, product.trim(), batch.trim(), expiry.trim(), vet.trim(), notes.trim()))
        }, colors = ButtonDefaults.buttonColors(containerColor = DryGreen)) { Text("تأكيد الجرعة المنفذة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = DryGreen) } }, containerColor = DryCream)
}
