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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Vaccines
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

private val PregGreen = Color(0xFF526B55)
private val PregCream = Color(0xFFF7F3EA)
private val PregInk = Color(0xFF27332A)
private val PregMuted = Color(0xFF778078)
private val PregLine = Color(0xFFE8E4DA)
private const val PREGNANT_PREFS = "mawashi_pregnant_ewe_program_v1"

private data class PregnantRecord(
    val id: String, val tag: String, val type: String, val itemId: String, val itemName: String,
    val date: String, val dose: String, val product: String, val batch: String, val expiry: String,
    val vet: String, val nextDate: String, val assessment: String, val notes: String
)

private data class PregnantStage(val id: String, val title: String, val period: String, val goal: String, val checks: List<String>)

private val PREGNANT_STAGES = listOf(
    PregnantStage("confirm", "المرحلة الأولى – تأكيد الحمل وبداية المتابعة", "بعد التلقيح", "تثبيت بيانات الحمل وربطها بسجل النعجة.", listOf("تاريخ التلقيح وطريقته", "تاريخ فحص الحمل ونتيجته", "عدد الأجنة: مفرد أو توأم أو أكثر", "تقييم الحالة الصحية للأم")),
    PregnantStage("mid", "المرحلة الثانية – بداية ومنتصف الحمل", "من تأكيد الحمل حتى الثلث الأخير", "متابعة الأم واكتشاف المشكلات مبكراً.", listOf("الفحوص الصحية الدورية", "متابعة الوزن وحالة الجسم بالتنسيق مع قسم التغذية", "تسجيل أي تدخل بيطري أو أعراض غير طبيعية")),
    PregnantStage("late", "المرحلة الثالثة – الثلث الأخير من الحمل", "قبل الولادة", "رفع مستوى المتابعة حسب عدد الأجنة وحالة الأم.", listOf("متابعة النعاج الحاملة بتوأم بصورة أدق", "الانتباه لعلامات تسمم الحمل أو الخمول وفقد الشهية", "مراجعة السجل الوقائي والمواعيد البيطرية")),
    PregnantStage("prepare", "المرحلة الرابعة – الاستعداد للولادة", "اقتراب موعد الولادة", "التأكد من جاهزية الأم ومكان الولادة.", listOf("مراجعة الضرع والحلمات", "مراجعة التحصينات وتوقيت الجرعات", "تجهيز مكان نظيف وجاف ومراقبة علامات الولادة")),
    PregnantStage("birth", "المرحلة الخامسة – الولادة وتسليم متابعة المواليد", "عند الولادة", "إنشاء رابط موثوق بين الأم وكل حمل مولود.", listOf("تسجيل تاريخ الولادة وعدد المواليد", "ربط كل حمل بمعرف أمه", "تأكيد حصول المولود على اللبأ وتوقيت أول رضاعة", "نقل سجل الأم الوقائي إلى ملف كل حمل"))
)

private val PREGNANT_VACCINES = listOf(
    "clostridial" to "التسمم المعوي والكزاز", "pasteurella" to "الباستوريلا", "pox" to "جدري الأغنام",
    "ppr" to "طاعون المجترات الصغيرة PPR", "fmd" to "الحمى القلاعية FMD", "brucella" to "البروسيلا Rev.1",
    "chlamydia" to "الكلاميديا المسببة للإجهاض", "campylobacter" to "الكامبيلوباكتر المسبب للإجهاض",
    "footrot" to "تعفن القدم", "cl" to "الخراجات الجبنية", "orf" to "الأورف",
    "anthrax" to "الجمرة الخبيثة", "bluetongue" to "الحمى الزرقاء", "rabies" to "داء الكلب"
)
private val PREGNANT_VITAMINS = listOf(
    "ade" to "فيتامينات A وD3 وE", "selenium" to "السيلينيوم",
    "mineral" to "الأملاح والمعادن المخصصة للأغنام", "b_complex" to "مجموعة فيتامين B عند الحاجة",
    "other" to "مكمل آخر موصى به بيطرياً"
)

private object PregnantEweStore {
    private fun read(c: Context): JSONArray {
        val raw = c.getSharedPreferences(PREGNANT_PREFS, Context.MODE_PRIVATE).getString("records", "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
    fun records(c: Context): List<PregnantRecord> {
        val a = read(c)
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val x = a.getJSONObject(i)
            PregnantRecord(x.optString("id"), x.optString("tag"), x.optString("type"), x.optString("itemId"), x.optString("itemName"),
                x.optString("date"), x.optString("dose"), x.optString("product"), x.optString("batch"), x.optString("expiry"),
                x.optString("vet"), x.optString("nextDate"), x.optString("assessment"), x.optString("notes"))
        }.getOrNull() }
    }
    fun save(c: Context, values: List<PregnantRecord>) {
        val a = JSONArray()
        values.forEach { r -> a.put(JSONObject().put("id", r.id).put("tag", r.tag).put("type", r.type)
            .put("itemId", r.itemId).put("itemName", r.itemName).put("date", r.date).put("dose", r.dose)
            .put("product", r.product).put("batch", r.batch).put("expiry", r.expiry).put("vet", r.vet)
            .put("nextDate", r.nextDate).put("assessment", r.assessment).put("notes", r.notes)) }
        check(c.getSharedPreferences(PREGNANT_PREFS, Context.MODE_PRIVATE).edit().putString("records", a.toString()).commit()) {
            "تعذر حفظ السجل الوقائي للنعجة"
        }
    }
}

/** A lamb is marked maternally protected only after a veterinarian-confirmed suitable dam vaccination timing. */
internal fun maternalVaccinationStatus(context: Context, motherTag: String, vaccineId: String): String {
    if (motherTag.isBlank() || vaccineId != "clostridial") return "مجهولة"
    val latest = PregnantEweStore.records(context)
        .filter { it.tag.equals(motherTag, true) && it.type == "لقاح" && it.itemId == vaccineId }
        .maxByOrNull { it.date } ?: return "مجهولة"
    return when (latest.assessment) {
        "مناسب وفق تقييم الطبيب" -> "محصنة"
        "غير محصنة" -> "غير محصنة"
        else -> "مجهولة"
    }
}

internal fun maternalVaccinationSummary(context: Context, motherTag: String): String {
    if (motherTag.isBlank()) return "الأم غير مرتبطة بملف الحمل"
    val records = PregnantEweStore.records(context).filter { it.tag.equals(motherTag, true) && it.type == "لقاح" }
    if (records.isEmpty()) return "لا يوجد سجل تحصين موثق للأم؛ الحالة غير معروفة"
    val clostridial = records.filter { it.itemId == "clostridial" }.maxByOrNull { it.date }
    val statusText = when (clostridial?.assessment) {
        "مناسب وفق تقييم الطبيب" -> "التسمم المعوي والكزاز: توقيت مناسب وفق تقييم الطبيب"
        "غير محصنة" -> "التسمم المعوي والكزاز: الأم مسجلة كغير محصنة"
        null -> "التسمم المعوي والكزاز: لا يوجد سجل"
        else -> "التسمم المعوي والكزاز: جرعة مسجلة دون تأكيد ملاءمة التوقيت"
    }
    val vaccineSummary = records.sortedByDescending { it.date }.take(4).joinToString("، ") { it.itemName + " (" + it.date + ")" }
    val vitaminRecords = PregnantEweStore.records(context).filter { it.tag.equals(motherTag, true) && it.type == "فيتامين" }.sortedByDescending { it.date }
    val vitaminSummary = if (vitaminRecords.isEmpty()) "لا توجد فيتامينات موثقة" else vitaminRecords.take(4).joinToString("، ") { it.itemName + " (" + it.date + ")" }
    return statusText + " • التحصينات المسجلة: " + (vaccineSummary.ifBlank { "لا يوجد" }) + " • فيتامينات الأم: " + vitaminSummary
}

@Composable
internal fun PregnantEweProgramScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var animals by remember { mutableStateOf(herdAnimalsForCareCategory(context, "pregnant")) }
    var records by remember { mutableStateOf(PregnantEweStore.records(context)) }
    var selectedTag by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("overview") }
    var expandedStage by rememberSaveable { mutableStateOf("confirm") }
    var recordType by rememberSaveable { mutableStateOf("لقاح") }
    var showRecordDialog by remember { mutableStateOf(false) }
    var showStageDialog by remember { mutableStateOf<PregnantStage?>(null) }

    LaunchedEffect(Unit) {
        animals = loadHerd(context).filter { it.productionState == "نعجة حامل" && it.lifecycleStatus == "نشط" && (it.kind.trim() == "نعجة" || it.sex == "أنثى") }
        records = PregnantEweStore.records(context)
        if (animals.none { it.tag.equals(selectedTag, true) }) selectedTag = animals.firstOrNull()?.tag.orEmpty()
    }
    val ewe = animals.firstOrNull { it.tag.equals(selectedTag, true) }
    val eweRecords = records.filter { it.tag.equals(selectedTag, true) }.sortedByDescending { it.date }
    val vaccineRecords = eweRecords.filter { it.type == "لقاح" }
    val vitaminRecords = eweRecords.filter { it.type == "فيتامين" }
    val today = LocalDate.now()
    val upcoming = eweRecords.count { runCatching { LocalDate.parse(it.nextDate).isAfter(today.minusDays(1)) }.getOrDefault(false) }

    Column(modifier.fillMaxSize().background(PregCream).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = PregGreen) }
            Column(Modifier.weight(1f)) {
                Text("برنامج النعاج الحوامل", color = PregInk, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("متابعة الأم وربط سجلها الوقائي بمواليدها", color = PregMuted, fontSize = 12.sp)
            }
            Text("🐑", fontSize = 27.sp)
        }
        if (animals.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PregLine)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("لا توجد نعاج مسجلة", color = PregInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("أضف النعجة إلى القطيع وحدد جنسها أنثى حتى يظهر ملفها هنا.", color = PregMuted, fontSize = 13.sp)
                }
            }
        } else {
            if (section != "overview") {
            Spacer(Modifier.height(5.dp))
            Text("اختيار النعجة", color = PregInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                animals.forEach { a -> FilterChip(selectedTag == a.tag, { selectedTag = a.tag }, label = { Text("🐑 " + a.tag, fontSize = 13.sp) }) }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, PregLine)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ملف الأم: " + ewe?.tag.orEmpty(), color = PregInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("حالة الحمل: " + (ewe?.pregnancyStage?.takeIf { it.isNotBlank() } ?: ewe?.productionState ?: "غير محددة"), color = PregMuted, fontSize = 12.sp)
                    Text("بداية الحمل: " + (ewe?.pregnancyStartDate?.ifBlank { "غير مسجل" } ?: "غير مسجل") + " • الولادة المتوقعة: " + (ewe?.expectedBirthDate?.ifBlank { "غير محددة" } ?: "غير محددة"), color = PregMuted, fontSize = 12.sp)
                    Text("التحصينات الموثقة: " + vaccineRecords.size + " • الفيتامينات والمكملات: " + vitaminRecords.size + " • المواعيد القادمة: " + upcoming, color = PregGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FilterChip(section == "care", { section = "care" }, label = { Text("الرعاية والفحوصات", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                FilterChip(section == "prevention", { section = "prevention" }, label = { Text("اللقاحات والتحصينات", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                FilterChip(section == "reminders", { section = "reminders" }, label = { Text("المواعيد والتنبيهات", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
            }
            }
            if (section == "overview") {
                EweProgramOverview(
                    animalCount = animals.size,
                    dueCount = upcoming,
                    completedCount = eweRecords.size,
                    stages = PREGNANT_STAGES.map { it.title to it.period },
                    animalLines = animals.map { a -> a.tag + " • " + (a.pregnancyStage.ifBlank { a.productionState }) + " • ولادة متوقعة: " + a.expectedBirthDate.ifBlank { "غير محددة" } },
                    onStageClick = { index -> expandedStage = PREGNANT_STAGES.getOrNull(index)?.id ?: "confirm"; section = "care" },
                    onVaccinesClick = { section = "prevention" },
                    onRemindersClick = { section = "reminders" }
                )
            } else if (section == "prevention") {
                SmartAnnualVaccinationScreen("pregnant", ewe, Modifier.weight(1f))
            } else if (section == "reminders") {
                CareRemindersPanel(selectedTag)
            } else if (section == "care") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    item {
                        Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("المراحل الخمس للرعاية", color = PregInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("تسجل كل متابعة بتاريخها ونتيجتها؛ أما التغذية فتدار في قسمها المستقل.", color = PregMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    items(PREGNANT_STAGES) { stage ->
                        val count = eweRecords.filter { it.type == "فحص" && it.itemId == stage.id }.map { it.itemName }.distinct().size
                        val expanded = expandedStage == stage.id
                        Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(if (expanded) 2.dp else 1.dp, if (expanded) PregGreen else PregLine)) {
                            Column(Modifier.fillMaxWidth().clickable { expandedStage = if (expanded) "" else stage.id }.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stage.title, color = PregInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(stage.period, color = PregGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(count.toString() + "/" + stage.checks.size, color = PregGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Outlined.ChevronLeft, null, tint = PregMuted)
                                }
                                Text(stage.goal, color = PregMuted, fontSize = 12.sp)
                                if (expanded) {
                                    HorizontalDivider(color = PregLine)
                                    stage.checks.forEachIndexed { index, check ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text((index + 1).toString() + ". " + check, color = PregInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            val last = eweRecords.firstOrNull { it.type == "فحص" && it.itemId == stage.id && it.itemName == check }
                                            Text(last?.let { "آخر متابعة: " + it.date + " • " + it.notes } ?: "لم تسجل بعد", color = PregMuted, fontSize = 11.sp)
                                        }
                                    }
                                    Button(onClick = { showStageDialog = stage }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PregGreen)) {
                                        Text("تسجيل متابعة لهذه المرحلة")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    item {
                        Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("السجل الوقائي للأم", color = PregInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("سجل اللقاح أو الفيتامين بعد إعطائه فعلياً. لا تُنشأ جرعة أو موعد تلقائياً دون اعتماد بيطري.", color = PregMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(onClick = { recordType = "لقاح"; showRecordDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PregGreen)) {
                                Icon(Icons.Outlined.Vaccines, null); Spacer(Modifier.width(5.dp)); Text("تسجيل لقاح")
                            }
                            OutlinedButton(onClick = { recordType = "فيتامين"; showRecordDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.MedicalServices, null); Spacer(Modifier.width(5.dp)); Text("تسجيل فيتامين")
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            PregnantMetric("لقاحات", vaccineRecords.size.toString(), Modifier.weight(1f))
                            PregnantMetric("فيتامينات", vitaminRecords.size.toString(), Modifier.weight(1f))
                        }
                    }
                    items(eweRecords.filter { it.type == "لقاح" || it.type == "فيتامين" }, key = { it.id }) { record ->
                        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, PregLine)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(record.itemName, color = PregInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(record.type, color = PregGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("التاريخ: " + record.date + " • الجرعة: " + record.dose.ifBlank { "غير مدونة" }, color = PregMuted, fontSize = 12.sp)
                                Text("المنتج: " + record.product.ifBlank { "غير مدون" } + " • التشغيلة: " + record.batch.ifBlank { "—" }, color = PregMuted, fontSize = 11.sp)
                                if (record.assessment.isNotBlank()) Text("تقييم الحماية: " + record.assessment, color = PregGreen, fontSize = 11.sp)
                                if (record.nextDate.isNotBlank()) Text("المراجعة القادمة: " + record.nextDate, color = PregGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (record.notes.isNotBlank()) Text(record.notes, color = PregMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (eweRecords.none { it.type == "لقاح" || it.type == "فيتامين" }) item {
                        Text("لا توجد سجلات وقائية لهذه النعجة بعد.", color = PregMuted, fontSize = 13.sp)
                    }
                    item {
                        Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
                            Text("تنبيه: السيلينيوم قد يسبب السمية عند زيادة الجرعة، والأغنام حساسة للنحاس. لا تستخدم الحقن أو المكملات روتينياً دون تحديد الحاجة والجرعة من الطبيب البيطري ونشرة المنتج.", color = PregInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    showStageDialog?.let { stage ->
        PregnantStageDialog(stage, { showStageDialog = null }) { date, checkName, notes ->
            val addition = PregnantRecord(UUID.randomUUID().toString(), selectedTag, "فحص", stage.id, checkName, date, "", "", "", "", "", "", "", notes)
            val updated = records + addition
            runCatching { PregnantEweStore.save(context, updated) }.onSuccess {
                records = updated; showStageDialog = null
                Toast.makeText(context, "تم حفظ المتابعة في السجل التاريخي", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "تعذر حفظ المتابعة", Toast.LENGTH_LONG).show() }
        }
    }
    if (showRecordDialog) {
        PregnantPreventiveDialog(selectedTag, recordType, { showRecordDialog = false }) { record ->
            val updated = records + record
            runCatching {
                PregnantEweStore.save(context, updated)
                if (record.nextDate.isNotBlank()) {
                    val reminderDate = LocalDate.parse(record.nextDate)
                    val reminders = readHerdCare(context).filterNot { it.id == "pregnant-" + record.id }
                    saveHerdCare(context, reminders + HerdCareReminder(
                        id = "pregnant-" + record.id, tag = record.tag, kind = "برنامج النعاج الحوامل",
                        title = record.type + " • " + record.itemName, date = reminderDate.toString(),
                        notes = "موعد مراجعة مسجل من ملف الأم؛ تحقق من ملاءمة المنتج والتوقيت بيطرياً.", completed = false
                    ))
                }
            }.onSuccess {
                records = updated; showRecordDialog = false
                Toast.makeText(context, "تم حفظ السجل الوقائي وتحديث التذكير", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "تعذر حفظ السجل الوقائي", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun PregnantMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, PregLine)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = PregGreen, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = PregMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PregnantStageDialog(stage: PregnantStage, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var check by rememberSaveable { mutableStateOf(stage.checks.first()) }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تسجيل متابعة المرحلة", color = PregInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stage.title, color = PregGreen, fontWeight = FontWeight.SemiBold)
                stage.checks.forEach { item -> FilterChip(check == item, { check = item }, label = { Text(item, fontSize = 12.sp) }) }
                MawashiDatePickerField(date, { date = it }, "تاريخ الفحص", Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("النتيجة والملاحظات") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { if (runCatching { LocalDate.parse(date) }.isSuccess && notes.isNotBlank()) onSave(date, check, notes.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = PregGreen)) { Text("حفظ المتابعة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = PregGreen) } }, containerColor = PregCream)
}

@Composable
private fun PregnantPreventiveDialog(tag: String, type: String, onDismiss: () -> Unit, onSave: (PregnantRecord) -> Unit) {
    val context = LocalContext.current
    val options = if (type == "لقاح") PREGNANT_VACCINES else PREGNANT_VITAMINS
    var itemId by rememberSaveable { mutableStateOf(options.first().first) }
    var itemName by rememberSaveable { mutableStateOf(options.first().second) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dose by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var vet by rememberSaveable { mutableStateOf("") }
    var nextDate by rememberSaveable { mutableStateOf("") }
    var assessment by rememberSaveable { mutableStateOf("جرعة مسجلة فقط") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (type == "لقاح") "توثيق لقاح الأم" else "توثيق فيتامين أو مكمل", color = PregInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("اختر الصنف", color = PregInk, fontWeight = FontWeight.SemiBold)
                options.forEach { option -> FilterChip(itemId == option.first, { itemId = option.first; itemName = option.second }, label = { Text(option.second, fontSize = 12.sp) }) }
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ الفعلي", Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الجرعة والتركيز") })
                OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج التجاري") })
                if (type == "لقاح") {
                    OutlinedTextField(batch, { batch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم التشغيلة") })
                    OutlinedTextField(expiry, { expiry = it }, modifier = Modifier.fillMaxWidth(), label = { Text("تاريخ الصلاحية") })
                    Text("تقييم التحصين", color = PregInk, fontWeight = FontWeight.SemiBold)
                    listOf("جرعة مسجلة فقط", "مناسب وفق تقييم الطبيب", "غير محصنة", "غير معروف").forEach {
                        FilterChip(assessment == it, { assessment = it }, label = { Text(it, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(vet, { vet = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الطبيب البيطري") })
                OutlinedTextField(nextDate, { nextDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("موعد المراجعة أو الجرعة التالية (اختياري: YYYY-MM-DD)") })
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات أو تفاعلات") }, minLines = 2)
                Text("لا يحسب التطبيق جرعة تلقائية؛ أدخل موعد المراجعة فقط إذا كان محدداً من الطبيب أو نشرة المنتج.", color = PregMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = {
            val actual = runCatching { LocalDate.parse(date) }.getOrNull()
            val next = if (nextDate.isBlank()) null else runCatching { LocalDate.parse(nextDate) }.getOrNull()
            if (actual == null || actual.isAfter(LocalDate.now()) || dose.isBlank() || product.isBlank() || (nextDate.isNotBlank() && (next == null || next.isBefore(LocalDate.now())))) {
                Toast.makeText(context, "تحقق من التاريخ والجرعة والمنتج وموعد المراجعة", Toast.LENGTH_LONG).show()
            } else {
                onSave(PregnantRecord(UUID.randomUUID().toString(), tag, type, itemId, itemName, actual.toString(), dose.trim(), product.trim(), batch.trim(), expiry.trim(), vet.trim(), next?.toString().orEmpty(), if (type == "لقاح") assessment else "", notes.trim()))
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = PregGreen)) { Text("تأكيد وحفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = PregGreen) } }, containerColor = PregCream)
}
