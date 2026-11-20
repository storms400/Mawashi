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

private val LactGreen = Color(0xFF526B55)
private val LactCream = Color(0xFFF7F3EA)
private val LactInk = Color(0xFF27332A)
private val LactMuted = Color(0xFF778078)
private val LactLine = Color(0xFFE8E4DA)
private const val LACT_PREFS = "mawashi_lactating_ewe_program_v1"

private data class LactAction(val id: String, val title: String, val hint: String)
private data class LactStage(val id: String, val title: String, val period: String, val goal: String, val actions: List<LactAction>)
private data class LactCareRecord(val id: String, val tag: String, val stageId: String, val actionId: String, val date: String, val result: String, val notes: String)
private data class LactPreventiveRecord(val id: String, val tag: String, val type: String, val item: String, val date: String, val dose: String, val product: String, val batch: String, val expiry: String, val vet: String, val nextDate: String, val notes: String)

private val LACT_STAGES = listOf(
    LactStage("post_birth", "المرحلة الأولى – ما بعد الولادة", "الساعات الأولى واليوم الأول", "التأكد من تعافي الأم وسلامة بداية الرضاعة.", listOf(
        LactAction("dam", "فحص الحالة العامة للأم", "النشاط والشهية والاجترار والقدرة على الوقوف."),
        LactAction("placenta", "متابعة خروج المشيمة", "سجل وقت خروجها وأي تأخر أو إفراز غير طبيعي؛ لا تسحبها يدوياً."),
        LactAction("udder", "فحص الضرع والحلمات", "الحرارة والألم والتورم والإفرازات."),
        LactAction("colostrum", "تأكيد حصول الحملان على اللبأ", "سجل وقت أول رضاعة لكل حمل وملاحظات كفايتها."),
        LactAction("mother_link", "مراجعة ربط المواليد بالأم", "تحقق من رقم الأم وعدد المواليد في ملفات الحملان.")
    )),
    LactStage("early", "المرحلة الثانية – بداية الرضاعة", "الأيام الأولى بعد الولادة", "اكتشاف التهاب الضرع أو ضعف الرضاعة مبكراً.", listOf(
        LactAction("udder_check", "فحص الضرع بصورة دورية", "الحرارة والتصلب والألم والتغير في شكل الحليب."),
        LactAction("milk", "متابعة تدفق الحليب", "لاحظ صعوبة الرضاعة أو اختلاف نصفي الضرع."),
        LactAction("suckling", "مراقبة رضاعة الحملان", "قوة المص وتكرار الرضاعة والنشاط."),
        LactAction("appetite", "متابعة شهية الأم وحالتها", "الشهية والاجترار والنشاط وأي أعراض غير طبيعية.")
    )),
    LactStage("peak", "المرحلة الثالثة – ذروة الرضاعة", "بحسب مرحلة الإدرار وحالة الأم", "متابعة الأم والحملان خلال فترة الطلب المرتفع على الحليب.", listOf(
        LactAction("udder_peak", "فحص الضرع والحليب", "راقب الألم والحرارة والتكتلات أو الإفراز غير الطبيعي."),
        LactAction("bcs", "تقييم حالة الجسم BCS", "سجل الدرجة من 1 إلى 5 وقارنها بالقياسات السابقة."),
        LactAction("twins", "متابعة الأمهات ذات التوائم", "تأكد من حصول كل حمل على فرصته في الرضاعة."),
        LactAction("lamb_growth", "مراجعة نمو الحملان", "راجع أوزان الحملان من ملفاتها دون تكرار الإدخال.")
    )),
    LactStage("continued", "المرحلة الرابعة – استمرار الرضاعة", "حتى الاستعداد للفطام", "الحفاظ على سلامة الأم ومتابعة نمو المواليد.", listOf(
        LactAction("health", "مراجعة صحة الأم", "سجل الأعراض والفحوص والتدخلات البيطرية."),
        LactAction("udder_follow", "استمرار متابعة الضرع", "راقب الالتهاب والإصابات وتغير الحليب."),
        LactAction("lambs", "متابعة حالة الحملان", "اربط الملاحظات بالحمل المعني في ملفه الصحي."),
        LactAction("wean_ready", "تقييم الاستعداد للفطام", "القرار حسب النمو والصحة واستهلاك الغذاء الصلب.")
    )),
    LactStage("weaning", "المرحلة الخامسة – الفطام وتجفيف الضرع", "عند الفطام الفعلي", "توثيق الفطام ومراقبة الضرع والانتقال لبرنامج النعاج الجافة.", listOf(
        LactAction("wean_date", "تسجيل تاريخ الفطام الفعلي", "لا يفترض التطبيق عمراً ثابتاً؛ سجل التاريخ الفعلي."),
        LactAction("udder_dry", "مراقبة الضرع بعد الفطام", "راقب الامتلاء والحرارة والألم والتورم."),
        LactAction("milk_stop", "تأكيد توقف الرضاعة", "وثق حالة الأم وأي متابعة مطلوبة."),
        LactAction("handover", "تسليم الملف لبرنامج النعاج الجافة", "راجع اكتمال السجل الصحي قبل الانتقال.")
    ))
)

private val LACT_VACCINES = listOf("التسمم المعوي والكزاز", "الباستوريلا", "جدري الأغنام", "طاعون المجترات الصغيرة PPR", "الحمى القلاعية FMD", "الكلاميديا", "الكامبيلوباكتر", "تعفن القدم", "الخراجات الجبنية", "الأورف", "الجمرة الخبيثة", "الحمى الزرقاء", "داء الكلب", "لقاح آخر موصى به")
private val LACT_SUPPLEMENTS = listOf("فيتامينات A وD3 وE", "السيلينيوم", "الأملاح والمعادن المخصصة للأغنام", "مجموعة فيتامين B عند الحاجة", "مكمل آخر موصى به بيطرياً")

private fun lactatingLinkedLambs(context: Context, motherTag: String): List<HerdAnimal> {
    return loadHerd(context).filter { lamb ->
        lamb.kind == "حمل/رخلة" && lamb.lifecycleStatus != "مؤرشف" &&
            lamb.motherTag.equals(motherTag, true)
    }
}

private object LactatingEweStore {
    private fun read(c: Context, key: String): JSONArray {
        val raw = c.getSharedPreferences(LACT_PREFS, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
    fun care(c: Context): List<LactCareRecord> = (0 until read(c, "care").length()).mapNotNull { i -> runCatching {
        val x = read(c, "care").getJSONObject(i)
        LactCareRecord(x.optString("id"), x.optString("tag"), x.optString("stage"), x.optString("action"), x.optString("date"), x.optString("result"), x.optString("notes"))
    }.getOrNull() }
    fun preventive(c: Context): List<LactPreventiveRecord> = (0 until read(c, "preventive").length()).mapNotNull { i -> runCatching {
        val x = read(c, "preventive").getJSONObject(i)
        LactPreventiveRecord(x.optString("id"), x.optString("tag"), x.optString("type"), x.optString("item"), x.optString("date"), x.optString("dose"), x.optString("product"), x.optString("batch"), x.optString("expiry"), x.optString("vet"), x.optString("nextDate"), x.optString("notes"))
    }.getOrNull() }
    fun saveCare(c: Context, values: List<LactCareRecord>) {
        val a = JSONArray()
        values.forEach { r -> a.put(JSONObject().put("id",r.id).put("tag",r.tag).put("stage",r.stageId).put("action",r.actionId).put("date",r.date).put("result",r.result).put("notes",r.notes)) }
        check(c.getSharedPreferences(LACT_PREFS, Context.MODE_PRIVATE).edit().putString("care",a.toString()).commit()) { "تعذر حفظ سجل الرعاية" }
    }
    fun savePreventive(c: Context, values: List<LactPreventiveRecord>) {
        val a = JSONArray()
        values.forEach { r -> a.put(JSONObject().put("id",r.id).put("tag",r.tag).put("type",r.type).put("item",r.item).put("date",r.date).put("dose",r.dose).put("product",r.product).put("batch",r.batch).put("expiry",r.expiry).put("vet",r.vet).put("nextDate",r.nextDate).put("notes",r.notes)) }
        check(c.getSharedPreferences(LACT_PREFS, Context.MODE_PRIVATE).edit().putString("preventive",a.toString()).commit()) { "تعذر حفظ السجل الوقائي" }
    }
}

@Composable
internal fun LactatingEweProgramScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var animals by remember { mutableStateOf(emptyList<HerdAnimal>()) }
    var selectedTag by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("overview") }
    var stageId by rememberSaveable { mutableStateOf("post_birth") }
    var care by remember { mutableStateOf(LactatingEweStore.care(context)) }
    var preventive by remember { mutableStateOf(LactatingEweStore.preventive(context)) }
    var actionDialog by remember { mutableStateOf<LactAction?>(null) }
    var recordDialog by rememberSaveable { mutableStateOf(false) }
    var recordType by rememberSaveable { mutableStateOf("لقاح") }

    LaunchedEffect(Unit) {
        val herd = loadHerd(context).filter { it.lifecycleStatus != "مؤرشف" }
        animals = herd.filter { (it.kind.trim() == "نعجة" || it.sex == "أنثى") && it.productionState == "نعجة مرضعة" && it.lifecycleStatus == "نشط" }
        if (animals.none { it.tag.equals(selectedTag, true) }) selectedTag = animals.firstOrNull()?.tag.orEmpty()
        care = LactatingEweStore.care(context)
        preventive = LactatingEweStore.preventive(context)
    }
    val eweCare = care.filter { it.tag.equals(selectedTag, true) }.sortedByDescending { it.date }
    val ewePreventive = preventive.filter { it.tag.equals(selectedTag, true) }.sortedByDescending { it.date }
    val stage = LACT_STAGES.firstOrNull { it.id == stageId } ?: LACT_STAGES.first()
    val done = stage.actions.count { a -> eweCare.any { it.stageId == stage.id && it.actionId == a.id } }
    val reviewCount = ewePreventive.count { it.nextDate.isNotBlank() && runCatching { !LocalDate.parse(it.nextDate).isBefore(LocalDate.now()) }.getOrDefault(false) }

    Column(modifier.fillMaxSize().background(LactCream).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = LactGreen) }
            Column(Modifier.weight(1f)) {
                Text("برنامج النعاج المرضعة", color = LactInk, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("الرعاية الصحية ومتابعة الرضاعة والتحصينات", color = LactMuted, fontSize = 12.sp)
            }
            Text("🐑", fontSize = 27.sp)
        }
        if (animals.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, LactLine)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("لا توجد نعاج مرضعة محددة حالياً", color = LactInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("تظهر النعجة هنا عندما تكون حالتها الإنتاجية «نعجة مرضعة». حدّث حالة الأم في ملف القطيع بعد الولادة.", color = LactMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (section != "overview") {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, LactLine)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("اختيار النعجة", color = LactInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            animals.forEach { a -> FilterChip(selectedTag.equals(a.tag,true), { selectedTag = a.tag }, label = { Text("🐑 " + a.tag, fontSize = 12.sp) }) }
                            Text("عدد الأمهات: " + animals.size + " • حملان مرتبطة: " + lactatingLinkedLambs(context, selectedTag).size + " • مواعيد مراجعة: " + reviewCount, color = LactMuted, fontSize = 11.sp)
                        }
                    }
                }
                }
                if (section != "overview") item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(section == "care", { section = "care" }, label = { Text("الرعاية والفحوصات", fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                        FilterChip(section == "preventive", { section = "preventive" }, label = { Text("اللقاحات والتحصينات", fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                        FilterChip(section == "reminders", { section = "reminders" }, label = { Text("المواعيد والتنبيهات", fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                    }
                }
                if (section == "overview") {
                    item { EweProgramOverview(
                        title = "برنامج النعاج المرضعة",
                        subtitle = "رعاية الأم ومتابعة صحة المواليد حتى الفطام",
                        animalCount = animals.size,
                        dueCount = reviewCount,
                        completedCount = eweCare.size + ewePreventive.size,
                        stages = LACT_STAGES.map { it.title to it.period },
                        animalLines = animals.map { a -> a.tag + " • مواليد مرتبطون: " + lactatingLinkedLambs(context, a.tag).size },
                        onStageClick = { index -> stageId = LACT_STAGES.getOrNull(index)?.id ?: "post_birth"; section = "care" },
                        onVaccinesClick = { section = "preventive" },
                        onRemindersClick = { section = "reminders" }
                    ) }
                } else if (section == "preventive") {
                    item { SmartAnnualVaccinationScreen("lactating", animals.firstOrNull { it.tag.equals(selectedTag, true) }) }
                } else if (section == "reminders") {
                    item { CareRemindersPanel(selectedTag) }
                } else if (section == "care") {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, LactLine)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("مراحل الرعاية الخمس", color = LactInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("المرحلة " + (LACT_STAGES.indexOf(stage)+1) + " من 5 • " + stage.period, color = LactGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(stage.goal, color = LactMuted, fontSize = 12.sp)
                                LinearProgressIndicator(progress = { done.toFloat()/stage.actions.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = LactGreen, trackColor = LactLine)
                                Text(done.toString() + " من " + stage.actions.size + " إجراءات موثقة", color = LactGreen, fontSize = 11.sp)
                            }
                        }
                    }
                    items(LACT_STAGES) { s ->
                        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, LactLine), modifier = Modifier.fillMaxWidth().clickable { stageId = s.id }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.title, color = LactInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Icon(if (stageId == s.id) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronLeft, null, tint = LactGreen)
                                }
                                Text(s.period, color = LactGreen, fontSize = 11.sp)
                                Text(s.goal, color = LactMuted, fontSize = 11.sp)
                                if (stageId == s.id) s.actions.forEach { a ->
                                    Surface(color = LactCream, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().clickable { actionDialog = a }) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(a.title, color = LactInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(a.hint, color = LactMuted, fontSize = 11.sp)
                                            Text(if (eweCare.any { it.stageId == s.id && it.actionId == a.id }) "تم تسجيل الإجراء • اضغط لإضافة متابعة جديدة" else "اضغط لتسجيل الإجراء", color = LactGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("السجل التاريخي للمتابعة", color = LactInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (eweCare.isEmpty()) Text("لا توجد فحوصات مسجلة لهذه النعجة.", color = LactMuted, fontSize = 12.sp)
                    }
                    items(eweCare, key = { it.id }) { r ->
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LactLine)) {
                            Column(Modifier.padding(11.dp)) {
                                Text(LACT_STAGES.firstOrNull { it.id == r.stageId }?.actions?.firstOrNull { it.id == r.actionId }?.title ?: r.actionId, color = LactInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(r.date + " • " + r.result, color = LactGreen, fontSize = 11.sp)
                                if (r.notes.isNotBlank()) Text(r.notes, color = LactMuted, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(onClick = { recordType = "لقاح"; recordDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = LactGreen)) { Text("تسجيل لقاح") }
                            OutlinedButton(onClick = { recordType = "فيتامين/مكمل"; recordDialog = true }) { Text("تسجيل فيتامين") }
                        }
                        Text("السجل الوقائي للأم", color = LactInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("توثق الجرعات الفعلية فقط. لا يفرض التطبيق لقاحاً أو جرعة أثناء الرضاعة دون مراجعة النشرة والطبيب.", color = LactMuted, fontSize = 11.sp)
                    }
                    items(ewePreventive, key = { it.id }) { r ->
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LactLine)) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(r.type + " • " + r.item, color = LactInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("التاريخ: " + r.date + " • الجرعة: " + r.dose, color = LactMuted, fontSize = 11.sp)
                                Text("المنتج: " + r.product + " • التشغيلة: " + r.batch.ifBlank { "—" }, color = LactMuted, fontSize = 11.sp)
                                if (r.nextDate.isNotBlank()) Text("المراجعة القادمة: " + r.nextDate, color = LactGreen, fontSize = 11.sp)
                                if (r.notes.isNotBlank()) Text(r.notes, color = LactMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(12.dp)) {
                            Text("تنبيه بيطري: لا يُفترض أن كل اللقاحات أو الأدوية مناسبة أثناء الإدرار. تحقق من نشرة المنتج وفترة سحب الحليب واللحم والطبيب البيطري. لا تستخدم السيلينيوم أو النحاس أو الفيتامينات بجرعات عشوائية.", color = LactInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    actionDialog?.let { a ->
        LactCareDialog(a, { actionDialog = null }) { date, result, notes ->
            val updated = care + LactCareRecord(UUID.randomUUID().toString(), selectedTag, stageId, a.id, date, result, notes)
            runCatching { LactatingEweStore.saveCare(context, updated) }.onSuccess {
                care = updated; actionDialog = null
                Toast.makeText(context, "تم حفظ المتابعة في السجل التاريخي", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "تعذر حفظ المتابعة", Toast.LENGTH_LONG).show() }
        }
    }
    if (recordDialog) {
        LactPreventiveDialog(selectedTag, recordType, { recordDialog = false }) { record ->
            val updated = preventive + record
            runCatching {
                LactatingEweStore.savePreventive(context, updated)
                if (record.nextDate.isNotBlank()) {
                    val reminders = readHerdCare(context).filterNot { it.id == "lactating-" + record.id }
                    saveHerdCare(context, reminders + HerdCareReminder(id = "lactating-" + record.id, tag = record.tag, kind = "برنامج النعاج المرضعة", title = record.type + " • " + record.item, date = record.nextDate, notes = "موعد مراجعة مسجل؛ تحقق من فترة السحب وملاءمة المنتج للرضاعة.", completed = false))
                }
            }.onSuccess {
                preventive = updated; recordDialog = false
                Toast.makeText(context, "تم حفظ السجل الوقائي وتحديث التذكير", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "تعذر حفظ السجل الوقائي", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun LactCareDialog(action: LactAction, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var result by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(action.title, color = LactInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.hint, color = LactMuted, fontSize = 12.sp)
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ", Modifier.fillMaxWidth())
                OutlinedTextField(result, { result = it }, modifier = Modifier.fillMaxWidth(), label = { Text("النتيجة أو القياس") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = {
            val d = runCatching { LocalDate.parse(date) }.getOrNull()
            if (d == null || d.isAfter(LocalDate.now()) || result.isBlank()) Toast.makeText(context, "أدخل تاريخاً صحيحاً ونتيجة الفحص", Toast.LENGTH_SHORT).show()
            else onSave(date, result.trim(), notes.trim())
        }, colors = ButtonDefaults.buttonColors(containerColor = LactGreen)) { Text("حفظ المتابعة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = LactGreen) } }, containerColor = LactCream)
}

@Composable
private fun LactPreventiveDialog(tag: String, type: String, onDismiss: () -> Unit, onSave: (LactPreventiveRecord) -> Unit) {
    val context = LocalContext.current
    val options = if (type == "لقاح") LACT_VACCINES else LACT_SUPPLEMENTS
    var item by rememberSaveable { mutableStateOf(options.first()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dose by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var vet by rememberSaveable { mutableStateOf("") }
    var nextDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (type == "لقاح") "توثيق لقاح الأم" else "توثيق فيتامين أو مكمل", color = LactInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { option -> FilterChip(item == option, { item = option }, label = { Text(option, fontSize = 12.sp) }) }
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ الفعلي", Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الجرعة والتركيز") })
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج التجاري") })
                OutlinedTextField(batch, { batch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم التشغيلة (اختياري)") })
                OutlinedTextField(expiry, { expiry = it }, modifier = Modifier.fillMaxWidth(), label = { Text("تاريخ الصلاحية (اختياري)") })
                OutlinedTextField(vet, { vet = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الطبيب البيطري") })
                MawashiDatePickerField(nextDate, { nextDate = it }, "موعد المراجعة أو الجرعة التالية (اختياري)", Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات أو تفاعلات وفترة السحب") }, minLines = 2)
                Text("لا يحدد التطبيق جرعات تلقائية. راجع ملاءمة المنتج أثناء الرضاعة وفترة سحب الحليب واللحم.", color = LactMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = {
            val d = runCatching { LocalDate.parse(date) }.getOrNull()
            val next = if (nextDate.isBlank()) null else runCatching { LocalDate.parse(nextDate) }.getOrNull()
            if (d == null || d.isAfter(LocalDate.now()) || dose.isBlank() || product.isBlank() || (nextDate.isNotBlank() && (next == null || next.isBefore(LocalDate.now())))) Toast.makeText(context, "تحقق من التاريخ والجرعة والمنتج وموعد المراجعة", Toast.LENGTH_LONG).show()
            else onSave(LactPreventiveRecord(UUID.randomUUID().toString(), tag, type, item, d.toString(), dose.trim(), product.trim(), batch.trim(), expiry.trim(), vet.trim(), next?.toString().orEmpty(), notes.trim()))
        }, colors = ButtonDefaults.buttonColors(containerColor = LactGreen)) { Text("تأكيد وحفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = LactGreen) } }, containerColor = LactCream)
}
