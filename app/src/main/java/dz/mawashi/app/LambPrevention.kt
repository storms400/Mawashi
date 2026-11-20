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

private val LambGreen = Color(0xFF526B55)
private val LambCream = Color(0xFFF7F3EA)
private val LambInk = Color(0xFF27332A)
private val LambMuted = Color(0xFF778078)
private val LambLine = Color(0xFFE8E4DA)

private data class LambAction(val id: String, val title: String, val hint: String)
private data class LambStage(val id: String, val title: String, val age: String, val goal: String, val actions: List<LambAction>)
private data class LambCareRecord(val id: String, val tag: String, val stageId: String, val actionId: String, val date: String, val value: String, val notes: String)
private data class LambVaccine(val id: String, val title: String, val disease: String, val caution: String)
private data class LambVaccinePlan(val tag: String, val vaccineId: String, val motherStatus: String, val firstAgeDays: Int?, val boosterDays: Int?, val product: String)
internal data class LambDose(val id: String, val tag: String, val vaccineId: String, val doseNumber: Int, val date: String, val product: String, val batch: String, val expiry: String, val vet: String, val notes: String)

private val LAMB_STAGES = listOf(
    LambStage("birth", "المرحلة الأولى – الولادة", "من الولادة إلى أول 6 ساعات", "التنفس والدفء واللبأ ومنع عدوى السرة.", listOf(
        LambAction("breathing", "فحص التنفس والحيوية", "سجّل النتيجة والملاحظات."),
        LambAction("navel", "تطهير السرة", "اسم المطهر وتركيزه ووقت التطبيق."),
        LambAction("drying", "تجفيف الحمل وتوفير الدفء", "حالة التجفيف والدفء."),
        LambAction("temperature", "قياس درجة الحرارة", "القياس بالدرجة المئوية."),
        LambAction("birth_weight", "وزن الحمل عند الولادة", "الوزن بالكيلوغرام."),
        LambAction("colostrum", "تأكيد تناول اللبأ", "وقت أول رضاعة وتقييم كفايتها.")
    )),
    LambStage("first_week", "المرحلة الثانية – الأيام الأولى", "من اليوم الأول إلى اليوم السابع", "متابعة الرضاعة والسرة والبراز والنشاط.", listOf(
        LambAction("navel_check", "فحص السرة", "سليمة أو احمرار أو تورم أو إفراز."),
        LambAction("suckling", "مراقبة الرضاعة", "قوة المص وتكرار الرضاعة."),
        LambAction("feces", "فحص البراز والإسهال", "المظهر وأي إسهال أو تغير."),
        LambAction("vitality", "فحص النشاط والحيوية", "الحركة والوقوف والاستجابة.")
    )),
    LambStage("early_growth", "المرحلة الثالثة – النمو المبكر", "من الأسبوع الثاني إلى نهاية الشهر الأول", "رصد النمو والمشكلات التنفسية والهضمية والطفيليات.", listOf(
        LambAction("weight_early", "متابعة الوزن والنمو", "الوزن بالكيلوغرام ومقارنته بالسابق."),
        LambAction("respiratory", "فحص التنفس", "السعال أو الإفرازات أو صعوبة التنفس."),
        LambAction("digestive", "فحص الجهاز الهضمي", "الشهية والبراز والانتفاخ."),
        LambAction("parasites", "تقييم خطر الطفيليات", "الملاحظة وخطة الطبيب عند الحاجة."),
        LambAction("dam_nutrition", "متابعة تغذية الأم والحمل", "ملاحظات الرضاعة وحالة الأم.")
    )),
    LambStage("first_vaccines", "المرحلة الرابعة – التحصينات الأولى", "تُحدد وفق العمر ونشرة اللقاح", "فحص أهلية الحمل للتحصين ومتابعة الاستجابة.", listOf(
        LambAction("fitness", "فحص الحالة قبل التحصين", "الحالة العامة وأي مرض ظاهر."),
        LambAction("vaccine_review", "مراجعة خطة اللقاحات", "تحصين الأم ونشرة المنتج وتعليمات الطبيب."),
        LambAction("post_vaccine", "متابعة ما بعد التحصين", "أي تفاعل أو أعراض بعد اللقاح.")
    )),
    LambStage("completion", "المرحلة الخامسة – استكمال الوقاية", "من الشهر الثاني حتى الانتقال للفئة التالية", "متابعة الوزن والتغذية والاستعداد للفطام.", listOf(
        LambAction("weight_late", "قياس الوزن", "الوزن بالكيلوغرام وتاريخ القياس."),
        LambAction("solid_feed", "متابعة استهلاك العلف", "تقبل العلف والماء."),
        LambAction("weaning", "تقييم الاستعداد للفطام", "الوزن والصحة واستهلاك العلف الصلب."),
        LambAction("transition", "مراجعة الانتقال للفئة التالية", "نتيجة التقييم وخطة المتابعة.")
    ))
)

private val LAMB_VACCINES = listOf(
    LambVaccine("clostridial", "التسمم المعوي والكزاز", "أمراض المطثيات والكزاز", "يتأثر الموعد بتحصين الأم واللبأ ونشرة المنتج؛ راجع الطبيب."),
    LambVaccine("pasteurella", "الباستوريلا", "الالتهاب الرئوي والتسمم الدموي الباستوريلي", "العمر والجرعات يختلفان حسب المنتج والعامل المسبب."),
    LambVaccine("pox", "جدري الأغنام", "جدري الأغنام", "يرتبط بالبرنامج البيطري المحلي والحملات الرسمية."),
    LambVaccine("ppr", "طاعون المجترات الصغيرة PPR", "طاعون المجترات الصغيرة", "اتبع توقيت الحملة الرسمية واللقاح المعتمد في الجزائر."),
    LambVaccine("fmd", "الحمى القلاعية FMD", "الحمى القلاعية", "اتبع برنامج المصالح البيطرية ونشرة اللقاح."),
    LambVaccine("orf", "الأورف", "الإكثيما المعدية", "ليس روتينياً لكل القطعان؛ يُبحث عند وجود خطر وبقرار بيطري.")
)

internal fun isDuplicateLambDoseRecord(existing: List<LambDose>, candidate: LambDose): Boolean =
    existing.any { record ->
        record.tag.equals(candidate.tag, ignoreCase = true) &&
            record.vaccineId.equals(candidate.vaccineId, ignoreCase = true) &&
            record.date == candidate.date &&
            record.doseNumber == candidate.doseNumber
    }

private object LambPreventionStore {
    private const val PREFS = "mawashi_lamb_prevention_v1"
    private fun read(c: Context, key: String): JSONArray {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
    private fun write(c: Context, key: String, a: JSONArray) {
        check(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, a.toString()).commit()) { "تعذر حفظ سجل وقاية الحملان" }
    }
    fun care(c: Context): List<LambCareRecord> {
        val a = read(c, "care")
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val x = a.getJSONObject(i)
            LambCareRecord(x.optString("id"), x.optString("tag"), x.optString("stage"), x.optString("action"), x.optString("date"), x.optString("value"), x.optString("notes"))
        }.getOrNull() }
    }
    fun saveCare(c: Context, values: List<LambCareRecord>) {
        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("id", it.id).put("tag", it.tag).put("stage", it.stageId).put("action", it.actionId).put("date", it.date).put("value", it.value).put("notes", it.notes)) }
        write(c, "care", a)
    }
    fun plans(c: Context): List<LambVaccinePlan> {
        val a = read(c, "plans")
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val x = a.getJSONObject(i)
            LambVaccinePlan(x.optString("tag"), x.optString("vaccine"), x.optString("mother", "مجهولة"), x.optInt("age", -1).takeIf { it >= 0 }, x.optInt("booster", -1).takeIf { it >= 0 }, x.optString("product"))
        }.getOrNull() }
    }
    fun savePlans(c: Context, values: List<LambVaccinePlan>) {
        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("tag", it.tag).put("vaccine", it.vaccineId).put("mother", it.motherStatus).put("age", it.firstAgeDays ?: -1).put("booster", it.boosterDays ?: -1).put("product", it.product)) }
        write(c, "plans", a)
    }
    fun doses(c: Context): List<LambDose> {
        val a = read(c, "doses")
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val x = a.getJSONObject(i)
            LambDose(x.optString("id"), x.optString("tag"), x.optString("vaccine"), x.optInt("number", 1), x.optString("date"), x.optString("product"), x.optString("batch"), x.optString("expiry"), x.optString("vet"), x.optString("notes"))
        }.getOrNull() }
    }
    fun saveDoses(c: Context, values: List<LambDose>) {
        values.forEach {
            require(it.id.isNotBlank() && it.tag.isNotBlank() && it.vaccineId.isNotBlank()) { "بيانات سجل الجرعة أو الحيوان أو اللقاح ناقصة" }
            require(it.doseNumber > 0) { "رقم الجرعة غير صالح" }
            require(runCatching { LocalDate.parse(it.date) }.isSuccess) { "تاريخ الجرعة غير صالح" }
        }
        val duplicateInSubmission = values.indices.any { index ->
            values.drop(index + 1).any { candidate -> isDuplicateLambDoseRecord(listOf(values[index]), candidate) }
        }
        require(!duplicateInSubmission) { "جرعة الحمل مكررة في البيانات المراد حفظها" }

        // The caller supplies the full list. Compare new/changed IDs with persisted records
        // so a second save cannot silently overwrite or duplicate an already stored dose.
        val persisted = doses(c)
        val duplicateAgainstStorage = values.any { candidate ->
            persisted.any { existing ->
                existing.id != candidate.id && isDuplicateLambDoseRecord(listOf(existing), candidate)
            }
        }
        require(!duplicateAgainstStorage) { "هذه الجرعة مسجلة مسبقاً في سجل الحمل لهذا الحيوان" }

        val a = JSONArray()
        values.forEach { a.put(JSONObject().put("id", it.id).put("tag", it.tag).put("vaccine", it.vaccineId).put("number", it.doseNumber).put("date", it.date).put("product", it.product).put("batch", it.batch).put("expiry", it.expiry).put("vet", it.vet).put("notes", it.notes)) }
        write(c, "doses", a)
    }
}

private fun syncLambVaccineReminders(context: Context, animals: List<HerdAnimal>, plans: List<LambVaccinePlan>, doses: List<LambDose>) {
    val existing = readHerdCare(context).filterNot { it.kind == "وقاية الحملان" }
    val generated = mutableListOf<HerdCareReminder>()
    animals.filter { it.lifecycleStatus != "مؤرشف" && it.birthDate.isNotBlank() }.forEach { animal ->
        LAMB_VACCINES.forEach { vaccine ->
            val plan = plans.firstOrNull { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id } ?: return@forEach
            val first = doses.firstOrNull { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id && it.doseNumber == 1 }
            val secondDone = doses.any { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id && it.doseNumber == 2 }
            val due = when {
                first == null && plan.firstAgeDays != null -> runCatching { LocalDate.parse(animal.birthDate).plusDays(plan.firstAgeDays.toLong()) }.getOrNull()
                first != null && !secondDone && plan.boosterDays != null -> runCatching { LocalDate.parse(first.date).plusDays(plan.boosterDays.toLong()) }.getOrNull()
                else -> null
            } ?: return@forEach
            val doseLabel = if (first == null) "الجرعة الأولى" else "الجرعة المنشطة"
            generated += HerdCareReminder(
                id = "lamb-vaccine-" + animal.tag + "-" + vaccine.id,
                tag = animal.tag,
                kind = "وقاية الحملان",
                title = doseLabel + " • " + vaccine.title,
                date = due.toString(),
                notes = "موعد محسوب من خطة اللقاح المعتمدة ونشرة المنتج",
                completed = false
            )
        }
    }
    saveHerdCare(context, existing + generated)
}

@Composable
internal fun LambPreventionScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var animals by remember { mutableStateOf(loadHerd(context).filter { it.kind == "حمل/رخلة" && it.lifecycleStatus != "مؤرشف" }) }
    var tag by rememberSaveable { mutableStateOf("") }
    var care by remember { mutableStateOf(LambPreventionStore.care(context)) }
    var plans by remember { mutableStateOf(LambPreventionStore.plans(context)) }
    var doses by remember { mutableStateOf(LambPreventionStore.doses(context)) }
    var stageId by rememberSaveable { mutableStateOf("birth") }
    var actionDialog by remember { mutableStateOf<LambAction?>(null) }
    var planDialog by remember { mutableStateOf<LambVaccine?>(null) }
    var doseDialog by remember { mutableStateOf<LambVaccine?>(null) }

    LaunchedEffect(Unit) {
        val currentAnimals = loadHerd(context).filter { it.kind == "حمل/رخلة" && it.lifecycleStatus != "مؤرشف" }
        val currentPlans = LambPreventionStore.plans(context)
        val linkedPlans = currentPlans.map { plan ->
            val lamb = currentAnimals.firstOrNull { it.tag.equals(plan.tag, true) }
            plan.copy(motherStatus = maternalVaccinationStatus(context, lamb?.motherTag.orEmpty(), plan.vaccineId))
        }
        if (linkedPlans != currentPlans) runCatching { LambPreventionStore.savePlans(context, linkedPlans) }
        val currentDoses = LambPreventionStore.doses(context)
        animals = currentAnimals
        care = LambPreventionStore.care(context); plans = linkedPlans; doses = currentDoses
        if (currentAnimals.none { it.tag == tag }) tag = currentAnimals.firstOrNull()?.tag.orEmpty()
        syncLambVaccineReminders(context, currentAnimals, currentPlans, currentDoses)
    }
    val animal = animals.firstOrNull { it.tag.equals(tag, true) }
    val today = LocalDate.now()
    val recordedCare = care.filter { it.tag.equals(tag, true) }
    val dueCount = LAMB_VACCINES.count { v ->
        val p = plans.firstOrNull { it.tag.equals(tag, true) && it.vaccineId == v.id }
        val done = doses.any { it.tag.equals(tag, true) && it.vaccineId == v.id && it.doseNumber == 1 }
        val due = if (p?.firstAgeDays != null && animal?.birthDate?.isNotBlank() == true) runCatching { LocalDate.parse(animal.birthDate).plusDays(p.firstAgeDays.toLong()) }.getOrNull() else null
        !done && due != null && !due.isAfter(today)
    }

    Column(modifier.fillMaxSize().background(LambCream).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != "home") IconButton(onClick = { page = "home" }) { Icon(Icons.Outlined.ArrowBack, null, tint = LambGreen) }
            else if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = LambGreen) }
            Column(Modifier.weight(1f)) {
                Text(if (page == "home") "برنامج وقاية الحملان" else when (page) { "care" -> "الرعاية والفحوصات"; "vaccines" -> "اللقاحات والتحصينات"; else -> "المواعيد والتنبيهات" }, color = LambInk, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("متابعة من الولادة حتى استكمال الوقاية", color = LambMuted, fontSize = 12.sp)
            }
            if (page == "home") Text("📕", fontSize = 28.sp)
        }
        if (animals.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, LambLine)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("لا توجد حملان مسجلة حالياً", color = LambInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("أضف الحمل إلى قسم القطيع مع تسجيل تاريخ الميلاد والجنس والأم حتى ينشئ مواشي سجل الوقاية الخاص به.", color = LambMuted, fontSize = 13.sp)
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("الحمل المحدد", color = LambInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                animals.forEach { a -> FilterChip(tag == a.tag, { tag = a.tag }, label = { Text("🐑 " + a.tag, fontSize = 13.sp) }) }
            }
            Spacer(Modifier.height(7.dp))
            when (page) {
                "care" -> LambCarePage(animal, stageId, { stageId = it }, care) { actionDialog = it }
                "vaccines" -> SmartAnnualVaccinationScreen("lamb", animal, Modifier.weight(1f))
                "reminders" -> LambRemindersPage(animal, plans, doses, care)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, LambLine)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("ملخص الحمل المحدد", color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    LambStat("العمر", animal?.let { herdAnimalAgeLabel(it) } ?: "—", Modifier.weight(1f))
                                    LambStat("الوزن", animal?.weightKg?.let { "%.2f كغ".format(java.util.Locale.US, it) } ?: "غير مسجل", Modifier.weight(1f))
                                }
                                Text("الأم: " + (animal?.motherTag?.ifBlank { "غير مسجلة" } ?: "—") + " • الميلاد: " + (animal?.birthDate?.ifBlank { "غير مسجل" } ?: "—"), color = LambMuted, fontSize = 12.sp)
                                Text("تحصين الأم: " + maternalVaccinationStatus(context, animal?.motherTag.orEmpty(), "clostridial"), color = LambGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(maternalVaccinationSummary(context, animal?.motherTag.orEmpty()), color = LambMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    item { LambStat("إجراءات رعاية موثقة", recordedCare.size.toString(), Modifier.fillMaxWidth()) }
                    item { LambStat("لقاحات مستحقة وفق جدول مضبوط", dueCount.toString(), Modifier.fillMaxWidth()) }
                    item { LambHomeTile("الرعاية والفحوصات", "خمس مراحل وإجراءات قابلة للقياس والتوثيق", "🩺") { page = "care" } }
                    item { LambHomeTile("اللقاحات والتحصينات", "جدول مستقل لكل لقاح والجرعة المنشطة", "💉") { page = "vaccines" } }
                    item { LambHomeTile("المواعيد والتنبيهات", "القادم والمستحق والجرعات المسجلة", "🔔") { page = "reminders" } }
                    item {
                        Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
                            Text("تنبيه علمي: لا تُحدد مواعيد اللقاحات تلقائياً قبل إدخال العمر والفاصل المعتمدين من الطبيب البيطري ونشرة المنتج. حالة تحصين الأم تُسجل لأنها تؤثر في قرار التوقيت، ولا تُستخدم وحدها لإعطاء موعد طبي ثابت.", color = LambInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    actionDialog?.let { action ->
        LambCareDialog(action, { actionDialog = null }) { date, value, notes ->
            val updated = care + LambCareRecord(UUID.randomUUID().toString(), tag, stageId, action.id, date, value, notes)
            runCatching { LambPreventionStore.saveCare(context, updated) }.onSuccess { care = updated; actionDialog = null; Toast.makeText(context, "تم حفظ إجراء الرعاية", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الإجراء", Toast.LENGTH_LONG).show() }
        }
    }
    planDialog?.let { vaccine ->
        LambPlanDialog(tag, vaccine, plans.firstOrNull { it.tag.equals(tag, true) && it.vaccineId == vaccine.id }, maternalVaccinationStatus(context, animal?.motherTag.orEmpty(), vaccine.id), { planDialog = null }) { plan ->
            val updated = plans.filterNot { it.tag.equals(tag, true) && it.vaccineId == vaccine.id } + plan
            runCatching { LambPreventionStore.savePlans(context, updated); syncLambVaccineReminders(context, animals, updated, doses) }.onSuccess { plans = updated; planDialog = null; Toast.makeText(context, "تم حفظ خطة اللقاح وتحديث التنبيهات", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الخطة", Toast.LENGTH_LONG).show() }
        }
    }
    doseDialog?.let { vaccine ->
        val firstDone = doses.any { it.tag.equals(tag, true) && it.vaccineId == vaccine.id && it.doseNumber == 1 }
        LambDoseDialog(tag, vaccine, if (firstDone) 2 else 1, { doseDialog = null }) { dose ->
            val updated = doses + dose
            runCatching { LambPreventionStore.saveDoses(context, updated); syncLambVaccineReminders(context, animals, plans, updated) }.onSuccess { doses = updated; doseDialog = null; Toast.makeText(context, "تم توثيق الجرعة وتحديث التنبيهات", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "تعذر حفظ الجرعة", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun LambStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, LambLine)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = LambGreen, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = LambMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LambHomeTile(title: String, subtitle: String, emoji: String, action: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { action() }, color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, LambLine)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 27.sp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = LambMuted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronLeft, null, tint = LambMuted)
        }
    }
}

@Composable
private fun LambCarePage(animal: HerdAnimal?, selected: String, onStage: (String) -> Unit, records: List<LambCareRecord>, onRecord: (LambAction) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("مراحل الرعاية الخمس", color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("لكل مرحلة تعريف وإجراءات عملية مستقلة، مع تاريخ وقياس وملاحظات لكل إجراء.", color = LambMuted, fontSize = 12.sp)
                }
            }
        }
        items(LAMB_STAGES) { stage ->
            val expanded = selected == stage.id
            val count = stage.actions.count { a -> records.any { it.tag == animal?.tag && it.actionId == a.id } }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(if (expanded) 2.dp else 1.dp, if (expanded) LambGreen else LambLine)) {
                Column(Modifier.fillMaxWidth().clickable { onStage(if (expanded) "" else stage.id) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stage.title, color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(stage.age, color = LambGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("$count/" + stage.actions.size, color = LambGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = LambMuted)
                    }
                    Text(stage.goal, color = LambMuted, fontSize = 12.sp)
                    if (expanded) {
                        HorizontalDivider(color = LambLine)
                        stage.actions.forEach { action ->
                            val record = records.lastOrNull { it.tag.equals(animal?.tag, true) && it.actionId == action.id }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(action.title, color = LambInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(record?.let { "آخر تسجيل: " + it.date + " • " + it.value } ?: action.hint, color = LambMuted, fontSize = 11.sp)
                                }
                                TextButton(onClick = { onRecord(action) }) { Text(if (record == null) "تسجيل" else "تحديث", color = LambGreen) }
                            }
                            HorizontalDivider(color = LambLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LambVaccinesPage(animal: HerdAnimal?, plans: List<LambVaccinePlan>, doses: List<LambDose>, onPlan: (LambVaccine) -> Unit, onDose: (LambVaccine) -> Unit) {
    val today = LocalDate.now()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("جدول اللقاحات والتحصينات", color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("اضبط العمر والفاصل وفق نشرة المنتج والطبيب البيطري؛ يحسب مواشي المواعيد من تاريخ الميلاد بعد اعتماد الإعدادات.", color = LambMuted, fontSize = 12.sp)
                }
            }
        }
        items(LAMB_VACCINES) { vaccine ->
            val plan = plans.firstOrNull { it.tag.equals(animal?.tag, true) && it.vaccineId == vaccine.id }
            val records = doses.filter { it.tag.equals(animal?.tag, true) && it.vaccineId == vaccine.id }
            val first = records.firstOrNull { it.doseNumber == 1 }
            val booster = records.firstOrNull { it.doseNumber == 2 }
            val due = if (animal?.birthDate?.isNotBlank() == true && plan?.firstAgeDays != null) runCatching { LocalDate.parse(animal.birthDate).plusDays(plan.firstAgeDays.toLong()) }.getOrNull() else null
            val boosterDue = if (first != null && plan?.boosterDays != null) runCatching { LocalDate.parse(first.date).plusDays(plan.boosterDays.toLong()) }.getOrNull() else null
            val eligible = if (first == null) due != null && !due.isAfter(today) else booster == null && boosterDue != null && !boosterDue.isAfter(today)
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, LambLine)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(vaccine.title, color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(vaccine.disease, color = LambGreen, fontSize = 12.sp)
                    Text(vaccine.caution, color = LambMuted, fontSize = 11.sp)
                    Text("حالة تحصين الأم: " + (plan?.motherStatus ?: "غير مسجلة"), color = LambInk, fontSize = 12.sp)
                    Text("الجرعة الأولى: " + (if (first != null) first.date else due?.toString() ?: "لم يُضبط العمر المعتمد"), color = LambMuted, fontSize = 12.sp)
                    if (first != null && plan?.boosterDays != null) Text("الجرعة المنشطة: " + (booster?.date ?: boosterDue?.toString() ?: "تحتاج ضبطاً"), color = LambMuted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onPlan(vaccine) }, modifier = Modifier.weight(1f)) { Text("ضبط الخطة", fontSize = 12.sp) }
                        Button(onClick = { onDose(vaccine) }, enabled = eligible, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = LambGreen)) {
                            Text(if (first == null) "تسجيل الجرعة الأولى" else "تسجيل المنشطة", fontSize = 11.sp)
                        }
                    }
                    if (first != null && booster == null && plan?.boosterDays == null) Text("لم يُحدد فاصل الجرعة المنشطة؛ راجع نشرة اللقاح.", color = Color(0xFF9A6A2E), fontSize = 11.sp)
                }
            }
        }
        item {
            Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
                Text("لا تُسجل أي جرعة تلقائياً. التسجيل يتطلب تأكيد إعطاء اللقاح فعلياً، مع توثيق المنتج والتشغيلة والصلاحية. لا يُفترض أن جميع اللقاحات تحتاج جرعتين.", color = LambInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun LambRemindersPage(animal: HerdAnimal?, plans: List<LambVaccinePlan>, doses: List<LambDose>, care: List<LambCareRecord>) {
    val today = LocalDate.now()
    val reminders = mutableListOf<Pair<LocalDate, String>>()
    if (animal != null && animal.birthDate.isNotBlank()) LAMB_VACCINES.forEach { vaccine ->
        val plan = plans.firstOrNull { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id } ?: return@forEach
        val first = doses.firstOrNull { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id && it.doseNumber == 1 }
        if (first == null && plan.firstAgeDays != null) runCatching { LocalDate.parse(animal.birthDate).plusDays(plan.firstAgeDays.toLong()) }.getOrNull()?.let { reminders += it to "الجرعة الأولى: " + vaccine.title }
        if (first != null && plan.boosterDays != null && doses.none { it.tag.equals(animal.tag, true) && it.vaccineId == vaccine.id && it.doseNumber == 2 }) runCatching { LocalDate.parse(first.date).plusDays(plan.boosterDays.toLong()) }.getOrNull()?.let { reminders += it to "الجرعة المنشطة: " + vaccine.title }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Text("مواعيد الحمل " + (animal?.tag.orEmpty()), color = LambInk, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        if (reminders.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, LambLine)) {
                Text("لا توجد مواعيد محسوبة. اضبط أعمار الجرعات والفواصل المعتمدة أولاً.", color = LambMuted, fontSize = 13.sp, modifier = Modifier.padding(13.dp))
            }
        }
        items(reminders.sortedBy { it.first }) { reminder ->
            val state = when { reminder.first.isBefore(today) -> "متأخر"; reminder.first == today -> "اليوم"; else -> "قادم" }
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, LambLine)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(reminder.second, color = LambInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(reminder.first.toString(), color = LambMuted, fontSize = 12.sp)
                    }
                    Text(state, color = if (state == "متأخر") Color(0xFFB35C43) else LambGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("إجراءات الرعاية المسجلة: " + care.count { it.tag.equals(animal?.tag, true) }, color = LambMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun LambCareDialog(action: LambAction, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var value by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(action.title, color = LambInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.hint, color = LambMuted, fontSize = 12.sp)
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ", Modifier.fillMaxWidth())
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("النتيجة أو القياس") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات إضافية") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { if (runCatching { LocalDate.parse(date) }.isSuccess && value.isNotBlank()) onSave(date, value.trim(), notes.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = LambGreen)) { Text("حفظ التسجيل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = LambGreen) } }, containerColor = LambCream)
}

@Composable
private fun LambPlanDialog(tag: String, vaccine: LambVaccine, initial: LambVaccinePlan?, inheritedMotherStatus: String, onDismiss: () -> Unit, onSave: (LambVaccinePlan) -> Unit) {
    var mother by rememberSaveable { mutableStateOf(inheritedMotherStatus) }
    var age by rememberSaveable { mutableStateOf(initial?.firstAgeDays?.toString().orEmpty()) }
    var booster by rememberSaveable { mutableStateOf(initial?.boosterDays?.toString().orEmpty()) }
    var product by rememberSaveable { mutableStateOf(initial?.product.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ضبط خطة " + vaccine.title, color = LambInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل الأعمار والفواصل من نشرة المنتج أو توجيه الطبيب البيطري، وليس كتوصية ثابتة من التطبيق.", color = LambMuted, fontSize = 12.sp)
                Text("حالة تحصين الأم (من ملف الأم المرتبط)", color = LambInk, fontWeight = FontWeight.SemiBold)
                Text("تُملأ تلقائياً من سجل النعجة. لا تعتبر الجرعة حماية مؤكدة إلا إذا سُجل تقييم بيطري مناسب لتوقيتها.", color = LambMuted, fontSize = 11.sp)
                Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(10.dp)) {
                    Text("الحالة الموروثة من سجل الأم: " + mother, color = LambInk, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
                OutlinedTextField(age, { age = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("عمر الجرعة الأولى بالأيام") })
                OutlinedTextField(booster, { booster = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("الفاصل للجرعة المنشطة بالأيام (إن لزمت)") })
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج التجاري") })
            }
        },
        confirmButton = { Button(onClick = {
            val a = age.toIntOrNull(); val b = booster.toIntOrNull()
            if ((age.isBlank() || a != null && a in 0..730) && (booster.isBlank() || b != null && b in 1..730)) onSave(LambVaccinePlan(tag, vaccine.id, mother, a, b, product.trim()))
        }, colors = ButtonDefaults.buttonColors(containerColor = LambGreen)) { Text("حفظ الخطة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = LambGreen) } }, containerColor = LambCream)
}

@Composable
private fun LambDoseDialog(tag: String, vaccine: LambVaccine, number: Int, onDismiss: () -> Unit, onSave: (LambDose) -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var product by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var vet by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("توثيق " + vaccine.title + " – الجرعة " + number, color = LambInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MawashiDatePickerField(date, { date = it }, "تاريخ إعطاء اللقاح", Modifier.fillMaxWidth())
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المنتج التجاري") })
                OutlinedTextField(batch, { batch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم التشغيلة") })
                OutlinedTextField(expiry, { expiry = it }, modifier = Modifier.fillMaxWidth(), label = { Text("تاريخ الصلاحية") })
                OutlinedTextField(vet, { vet = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الطبيب البيطري") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات أو تفاعلات") }, minLines = 2)
                Text("لا تحفظ الجرعة إلا بعد إعطائها فعلياً. تحقق من صلاحية المنتج وطريقة الإعطاء.", color = LambMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = {
            val d = runCatching { LocalDate.parse(date) }.getOrNull()
            if (d == null || d.isAfter(LocalDate.now()) || product.isBlank()) Toast.makeText(context, "أدخل تاريخاً صحيحاً واسم المنتج", Toast.LENGTH_SHORT).show()
            else onSave(LambDose(UUID.randomUUID().toString(), tag, vaccine.id, number, date, product.trim(), batch.trim(), expiry.trim(), vet.trim(), notes.trim()))
        }, colors = ButtonDefaults.buttonColors(containerColor = LambGreen)) { Text("تأكيد الجرعة المنفذة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = LambGreen) } }, containerColor = LambCream)
}
