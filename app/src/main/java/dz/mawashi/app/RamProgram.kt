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
import androidx.compose.material.icons.outlined.Favorite
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

private val RamGreen = Color(0xFF526B55)
private val RamCream = Color(0xFFF7F3EA)
private val RamInk = Color(0xFF27332A)
private val RamMuted = Color(0xFF778078)
private val RamLine = Color(0xFFE8E4DA)
private const val RAM_PREFS = "mawashi_ram_program_v1"

private data class RamAction(val id: String, val title: String, val hint: String)
private data class RamStage(val id: String, val title: String, val period: String, val goal: String, val actions: List<RamAction>)
private data class RamRecord(
    val id: String, val tag: String, val category: String, val item: String, val date: String,
    val result: String, val dose: String, val product: String, val batch: String, val expiry: String,
    val vet: String, val nextDate: String, val notes: String
)

private val RAM_STAGES = listOf(
    RamStage("precheck", "المرحلة الأولى – الفحص الصحي قبل الموسم", "قبل موسم التلقيح", "تقييم الحالة العامة وبنية الفحل وقدرته على الحركة.", listOf(
        RamAction("body", "الحالة العامة والنشاط", "الشهية والاجترار والنشاط ودرجة الحرارة عند الحاجة."),
        RamAction("weight", "الوزن وحالة الجسم", "تسجيل الوزن وتقييم حالة الجسم BCS من 1 إلى 5."),
        RamAction("teeth", "فحص الأسنان والفم", "سلامة الأسنان وقدرة الفحل على تناول العلف."),
        RamAction("feet", "الأرجل والأظلاف", "المشي والعرج وطول الأظلاف وأي إصابات."),
        RamAction("skin", "الجلد والصوف والطفيليات", "فحص الجلد والصوف والحكة والطفيليات الخارجية والداخلية حسب الحاجة.")
    )),
    RamStage("fertility", "المرحلة الثانية – تقييم الخصوبة", "قبل الموسم بنحو 6–4 أسابيع", "فحص الجهاز التناسلي وتوثيق التقييم البيطري.", listOf(
        RamAction("testes", "فحص الخصيتين والصفن", "الحجم والتماثل والقوام وغياب الألم أو التورم."),
        RamAction("epididymis", "فحص البربخ والقضيب", "فحص تناسلي بيطري وتسجيل أي تغير."),
        RamAction("scrotal", "محيط الصفن", "قياسه عند الحاجة وتفسيره وفق العمر والسلالة."),
        RamAction("semen", "تحليل السائل المنوي", "الحركة والتركيز والشكل عند توفر المختبر."),
        RamAction("vet_clearance", "تقييم الطبيب للجاهزية", "جاهز أو يحتاج متابعة أو يؤجل مع ذكر السبب.")
    )),
    RamStage("preparation", "المرحلة الثالثة – الاستعداد لموسم التلقيح", "قبل الموسم بنحو أسبوعين", "مراجعة الصحة والتغذية والتحصينات قبل إدخال الفحل.", listOf(
        RamAction("vax_review", "مراجعة سجل التحصينات", "اللقاحات السابقة والجرعات المستحقة وفق الطبيب."),
        RamAction("nutrition_review", "مراجعة العليقة والمعادن", "الطاقة والبروتين والماء ومكمل معدني مخصص للأغنام."),
        RamAction("parasites", "مراجعة الطفيليات", "قرار العلاج وفق الفحص أو تحليل البراز وتوجيه الطبيب."),
        RamAction("final_exam", "الفحص النهائي", "توثيق قرار الجاهزية أو التأجيل.")
    )),
    RamStage("during", "المرحلة الرابعة – المتابعة أثناء الموسم", "طوال فترة التلقيح", "مراقبة الأداء والصحة وتجنب الإجهاد والإصابات.", listOf(
        RamAction("activity", "النشاط والرغبة في التلقيح", "ملاحظة النشاط والسلوك دون إجهاد الفحل."),
        RamAction("appetite", "الشهية والوزن", "متابعة الشهية والوزن وحالة الجسم."),
        RamAction("injuries", "الإصابات والعرج", "فحص الأرجل والجهاز التناسلي عند ظهور مشكلة."),
        RamAction("service_log", "توثيق التلقيحات", "تسجيل الإناث المخصصة للفحل وتواريخ التلقيح.")
    )),
    RamStage("after", "المرحلة الخامسة – التقييم بعد الموسم", "بعد انتهاء التلقيح", "تقييم التعافي ونتائج التلقيح والنسل.", listOf(
        RamAction("recovery", "تقييم الحالة والتعافي", "الوزن والحالة الجسمية والشهية."),
        RamAction("pregnancy_results", "نتائج الحمل", "ربط نتائج فحوص الحمل بالإناث التي لقحها الفحل."),
        RamAction("offspring_review", "مراجعة نتائج المواليد", "الحيوية وأوزان الميلاد والنمو والمشكلات المسجلة."),
        RamAction("next_season", "خطة الموسم القادم", "تحديد الفحوص والاحتياجات قبل الموسم التالي.")
    ))
)

private data class RamCatalogItem(val id: String, val title: String, val group: String, val guidance: String)
private val RAM_VACCINES = listOf(
    RamCatalogItem("cdt", "التسمم المعوي والكزاز", "أساسي حسب سجل القطيع", "راجع تاريخ التحصين والجرعة المنشطة ونشرة المنتج."),
    RamCatalogItem("pasteurella", "الباستوريلا", "حسب الخطر الوبائي", "يراجع عند وجود تاريخ إصابات أو خطر محلي."),
    RamCatalogItem("pox", "جدري الأغنام", "حسب البرنامج المحلي", "وفق تعليمات المصالح البيطرية ونشرة اللقاح."),
    RamCatalogItem("ppr", "طاعون المجترات الصغيرة PPR", "الحملات الرسمية", "يُتبع برنامج المصالح البيطرية الجزائرية."),
    RamCatalogItem("fmd", "الحمى القلاعية FMD", "الحملات الرسمية", "يُتبع البرنامج الرسمي وتوقيت الحملة."),
    RamCatalogItem("footrot", "تعفن القدم", "إضافي حسب المخاطر", "ليس روتينياً لكل فحل؛ يعتمد على انتشار المرض."),
    RamCatalogItem("orf", "الأورف", "إضافي حسب المخاطر", "يُبحث عند وجود إصابات متكررة وبحسب النشرة."),
    RamCatalogItem("caseous", "الخراجات الجبنية", "إضافي حسب المخاطر", "الوقاية الحيوية والعزل أساسيان؛ اللقاح حسب توفره وتوصية الطبيب."),
    RamCatalogItem("other", "لقاح إضافي آخر", "إضافي", "يسجل الاسم والسبب والمرجع البيطري.")
)
private val RAM_SUPPLEMENTS = listOf(
    RamCatalogItem("e_se", "فيتامين E والسيلينيوم", "فيتامينات ومعادن", "عند وجود نقص أو حاجة مثبتة؛ السيلينيوم قد يسبب السمية عند الزيادة."),
    RamCatalogItem("vit_a", "فيتامين A", "فيتامينات ومعادن", "يراعى مجموع ما يأتي من العلف والمكملات."),
    RamCatalogItem("vit_d3", "فيتامين D3", "فيتامينات ومعادن", "يُضبط حسب التعرض للشمس والعليقة والمنتج."),
    RamCatalogItem("zinc", "الزنك", "معادن نادرة", "يدخل في وظائف التناسل والإنزيمات؛ لا تكرر المكملات."),
    RamCatalogItem("mineral", "خلطة أملاح معدنية للأغنام", "معادن نادرة", "اختيار خلطة مخصصة للأغنام مع الانتباه الشديد للنحاس."),
    RamCatalogItem("b_group", "مجموعة فيتامين B", "دعم إضافي", "تستخدم عند وجود مبرر غذائي أو صحي، لا كحقن روتينية."),
    RamCatalogItem("other", "مكمل آخر", "إضافي", "سجل المادة الفعالة والتركيز والسبب.")
)
private val RAM_FERTILITY = listOf(
    "فحص الخصيتين والصفن", "فحص البربخ والقضيب", "قياس محيط الصفن", "تحليل السائل المنوي", "تقييم الطبيب للجاهزية التناسلية"
)

private object RamProgramStore {
    private fun prefs(c: Context) = c.getSharedPreferences(RAM_PREFS, Context.MODE_PRIVATE)
    fun records(c: Context): List<RamRecord> {
        val a = runCatching { JSONArray(prefs(c).getString("records", "[]") ?: "[]") }.getOrElse { JSONArray() }
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val x = a.getJSONObject(i)
            RamRecord(x.optString("id"), x.optString("tag"), x.optString("category"), x.optString("item"), x.optString("date"),
                x.optString("result"), x.optString("dose"), x.optString("product"), x.optString("batch"), x.optString("expiry"),
                x.optString("vet"), x.optString("nextDate"), x.optString("notes"))
        }.getOrNull() }
    }
    fun saveRecords(c: Context, values: List<RamRecord>) {
        val a = JSONArray()
        values.forEach { r -> a.put(JSONObject().put("id",r.id).put("tag",r.tag).put("category",r.category).put("item",r.item)
            .put("date",r.date).put("result",r.result).put("dose",r.dose).put("product",r.product).put("batch",r.batch)
            .put("expiry",r.expiry).put("vet",r.vet).put("nextDate",r.nextDate).put("notes",r.notes)) }
        check(prefs(c).edit().putString("records", a.toString()).commit()) { "تعذر حفظ سجل برنامج الفحول" }
    }
    fun season(c: Context, tag: String): String = prefs(c).getString("season_$tag", "").orEmpty()
    fun saveSeason(c: Context, tag: String, date: String) {
        check(prefs(c).edit().putString("season_$tag", date).commit()) { "تعذر حفظ موعد موسم التلقيح" }
    }
}

private fun syncRamReminders(context: Context, tag: String, seasonDate: String, records: List<RamRecord>) {
    val current = readHerdCare(context)
    val managedPrefix = "ram-program-$tag-"
    val retained = current.filterNot { it.id.startsWith(managedPrefix) && !it.completed }
    val completed = current.filter { it.id.startsWith(managedPrefix) && it.completed }
    val generated = mutableListOf<HerdCareReminder>()
    val season = runCatching { LocalDate.parse(seasonDate) }.getOrNull()
    if (season != null) {
        listOf(
            Triple("8w", "الفحص الصحي ومراجعة التغذية قبل الموسم", 56L),
            Triple("6w", "تقييم الخصوبة والفحص التناسلي", 42L),
            Triple("2w", "مراجعة الجاهزية النهائية للفحل", 14L)
        ).forEach { (key, title, days) ->
            val date = season.minusDays(days)
            generated += HerdCareReminder(managedPrefix + key, tag, "برنامج الفحول", title, date.toString(),
                "تذكير بالمتابعة قبل موسم التلقيح؛ لا يمثل وصفة أو جرعة علاجية.", false)
        }
    }
    records.filter { it.tag.equals(tag, true) && it.nextDate.isNotBlank() }.forEach { r ->
        val valid = runCatching { LocalDate.parse(r.nextDate) }.getOrNull() ?: return@forEach
        generated += HerdCareReminder(managedPrefix + "record-" + r.id, tag, "برنامج الفحول", "مراجعة • " + r.item,
            valid.toString(), "موعد متابعة مسجل من سجل الفحل. تحقق من تعليمات الطبيب البيطري.", false)
    }
    val completedIds = completed.map { it.id }.toSet()
    saveHerdCare(context, retained + generated.filterNot { it.id in completedIds } + completed)
}

@Composable
internal fun RamProgramScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var animals by remember { mutableStateOf(emptyList<HerdAnimal>()) }
    var selectedTag by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("home") }
    var stageId by rememberSaveable { mutableStateOf("precheck") }
    var records by remember { mutableStateOf(RamProgramStore.records(context)) }
    var seasonDate by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<Triple<String,String,String>?>(null) }

    LaunchedEffect(Unit) {
        animals = loadHerd(context).filter { it.lifecycleStatus != "مؤرشف" && (it.kind.contains("فحل") || (it.sex == "ذكر" && it.kind.contains("كبش"))) }
        if (animals.none { it.tag.equals(selectedTag, true) }) selectedTag = animals.firstOrNull()?.tag.orEmpty()
    }
    LaunchedEffect(selectedTag) {
        if (selectedTag.isNotBlank()) {
            seasonDate = RamProgramStore.season(context, selectedTag)
            records = RamProgramStore.records(context)
            syncRamReminders(context, selectedTag, seasonDate, records)
        }
    }
    val animal = animals.firstOrNull { it.tag.equals(selectedTag, true) }
    val animalRecords = records.filter { it.tag.equals(selectedTag, true) }.sortedByDescending { it.date }
    val pageTitle = when(section) {
        "care" -> "برنامج الرعاية والمتابعة"
        "vaccines" -> "التطعيمات والتحصينات"
        "supplements" -> "الفيتامينات والمعادن"
        "fertility" -> "الخصوبة والفحوصات"
        "offspring" -> "سجل النسل وجودته"
        "records" -> "السجل الصحي والتذكيرات"
        else -> "برنامج الفحول"
    }

    Column(modifier.fillMaxSize().background(RamCream).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (section == "home") onBack() else section = "home" }) {
                Icon(Icons.Outlined.ArrowBack, null, tint = RamGreen)
            }
            Column(Modifier.weight(1f)) {
                Text(pageTitle, color = RamInk, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("الرعاية والتحصين والخصوبة وجودة النسل", color = RamMuted, fontSize = 12.sp)
            }
            Text("🐏", fontSize = 27.sp)
        }
        if (animals.isEmpty()) {
            Surface(Modifier.padding(top = 14.dp), color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, RamLine)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("لا توجد فحول مسجلة", color = RamInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("أضف الفحل إلى القطيع وحدد نوعه «فحل» أو «كبش» وجنسه «ذكر» ليظهر هنا.", color = RamMuted, fontSize = 13.sp)
                }
            }
        } else {
            Text("اختر الفحل", color = RamInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                animals.forEach { a -> FilterChip(selectedTag == a.tag, { selectedTag = a.tag }, label = { Text("🐏 " + a.tag, fontSize = 12.sp) }) }
            }
            when(section) {
                "care" -> RamCareSection(stageId, { stageId = it }, animalRecords, { action -> dialog = Triple("رعاية", action.title, action.hint) })
                "vaccines" -> RamCatalogSection("اللقاحات", RAM_VACCINES, animalRecords, "لقاح") { item -> dialog = Triple("لقاح", item.title, item.guidance) }
                "supplements" -> RamCatalogSection("الفيتامينات والمعادن", RAM_SUPPLEMENTS, animalRecords, "مكمل") { item -> dialog = Triple("مكمل", item.title, item.guidance) }
                "fertility" -> RamSimpleListSection("فحوصات الخصوبة", RAM_FERTILITY, animalRecords, "خصوبة") { item -> dialog = Triple("خصوبة", item, "سجل النتيجة وفق فحص الطبيب أو المختبر.") }
                "offspring" -> RamSimpleListSection("متابعة نسل الفحل", listOf("تسجيل مولود مرتبط بالفحل", "وزن الميلاد والحيوية", "متابعة النمو والوزن", "المشكلات الصحية أو العيوب المسجلة"), animalRecords, "نسل") { item -> dialog = Triple("نسل", item, "أدخل وسم المولود والنتيجة والوزن والملاحظات. الربط هنا توثيق يدوي ولا يستنتج الأبوة تلقائياً.") }
                "records" -> RamRecordsSection(animalRecords)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, RamLine)) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("ملف الفحل", color = RamInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("🐏 ${animal?.tag ?: "—"} • ${animal?.breed ?: "سلالة غير محددة"}", color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("العمر: ${animal?.let { herdAnimalAgeLabel(it) } ?: "—"} • الوزن: ${animal?.weightKg?.let { "${"%.1f".format(java.util.Locale.US,it)} كغ" } ?: "غير مسجل"}", color = RamMuted, fontSize = 12.sp)
                                Text("الحالة الصحية: ${animal?.health ?: "غير مسجلة"}", color = RamMuted, fontSize = 12.sp)
                                MawashiDatePickerField(seasonDate, { value ->
                                    seasonDate = value
                                    runCatching { RamProgramStore.saveSeason(context, selectedTag, value); syncRamReminders(context, selectedTag, value, records) }
                                        .onFailure { Toast.makeText(context, "تعذر حفظ موعد الموسم", Toast.LENGTH_LONG).show() }
                                }, "تاريخ بداية موسم التلقيح", Modifier.fillMaxWidth())
                                Text("يحسب النظام تذكيرات قبل الموسم بـ 8 و6 وأسبوعين، ويجمعها مع المواعيد المسجلة للفحل.", color = RamMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    item { RamHomeTile("برنامج الرعاية والمتابعة", "خمس مراحل وإجراءات قابلة للتوثيق", "🩺") { section = "care" } }
                    item { RamHomeTile("التطعيمات والتحصينات", "لقاحات أساسية وإضافية وفق سجل الفحل", "💉") { section = "vaccines" } }
                    item { RamHomeTile("الفيتامينات والمعادن", "دعم مدروس مع تجنب الجرعات الزائدة", "💊") { section = "supplements" } }
                    item { RamHomeTile("الخصوبة والفحوصات", "فحص تناسلي وتقييم السائل المنوي", "🔬") { section = "fertility" } }
                    item { RamHomeTile("سجل النسل وجودته", "أوزان المواليد والحيوية والنمو والصفات", "🧬") { section = "offspring" } }
                    item { RamHomeTile("السجل الصحي والتذكيرات", "سجل تراكمي ومواعيد المتابعة", "📋") { section = "records" } }
                    item {
                        Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
                            Text("تنبيه علمي: لا توجد جرعات أو خلطة مكملات موحدة لكل الفحول. السيلينيوم والنحاس والفيتامينات قد تسبب السمية عند الزيادة. تعتمد اللقاحات والمواعيد والجرعات على الوضع الوبائي ونشرة المنتج والطبيب البيطري. قوة النسل تعتمد على الوراثة والانتخاب وسجلات الأداء، لا على المكملات وحدها.", color = RamInk, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    dialog?.let { entry ->
        RamRecordDialog(entry.second, entry.third, entry.first, { dialog = null }) { record ->
            val updated = records + record.copy(id = UUID.randomUUID().toString(), tag = selectedTag)
            runCatching {
                RamProgramStore.saveRecords(context, updated)
                syncRamReminders(context, selectedTag, seasonDate, updated)
            }.onSuccess {
                records = updated
                dialog = null
                Toast.makeText(context, "تم حفظ السجل وتحديث التذكيرات", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "تعذر حفظ السجل", Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun RamHomeTile(title: String, subtitle: String, symbol: String, action: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { action() }, color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, RamLine)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, fontSize = 25.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = RamInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = RamMuted, fontSize = 11.sp)
            }
            Text("‹", color = RamMuted, fontSize = 23.sp)
        }
    }
}

@Composable
private fun RamCareSection(selected: String, onStage: (String) -> Unit, records: List<RamRecord>, onRecord: (RamAction) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        items(RAM_STAGES) { stage ->
            val expanded = selected == stage.id
            val count = stage.actions.count { a -> records.any { it.category == "رعاية" && it.item == a.title } }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(if (expanded) 2.dp else 1.dp, if (expanded) RamGreen else RamLine)) {
                Column(Modifier.fillMaxWidth().clickable { onStage(if (expanded) "" else stage.id) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stage.title, color = RamInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(stage.period, color = RamGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${count}/${stage.actions.size}", color = RamGreen, fontWeight = FontWeight.Bold)
                    }
                    Text(stage.goal, color = RamMuted, fontSize = 12.sp)
                    if (expanded) {
                        HorizontalDivider(color = RamLine)
                        stage.actions.forEach { action ->
                            val latest = records.filter { it.category == "رعاية" && it.item == action.title }.maxByOrNull { it.date }
                            Text(action.title, color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(latest?.let { "آخر تسجيل: ${it.date} • ${it.result}" } ?: action.hint, color = RamMuted, fontSize = 11.sp)
                            TextButton(onClick = { onRecord(action) }) { Text("تسجيل الفحص أو الإجراء", color = RamGreen) }
                            HorizontalDivider(color = RamLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RamCatalogSection(title: String, catalog: List<RamCatalogItem>, records: List<RamRecord>, category: String, onRecord: (RamCatalogItem) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Text(title + " • سجل الفحل المحدد", color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        items(catalog) { item ->
            val history = records.filter { it.category == category && it.item == item.title }.sortedByDescending { it.date }
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RamLine)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, color = RamInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(item.group, color = RamGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.guidance, color = RamMuted, fontSize = 11.sp)
                    Text(if (history.isEmpty()) "لا يوجد تسجيل بعد" else "آخر تسجيل: ${history.first().date} • ${history.size} سجل", color = RamMuted, fontSize = 10.sp)
                    Button(onClick = { onRecord(item) }, colors = ButtonDefaults.buttonColors(containerColor = RamGreen), modifier = Modifier.fillMaxWidth()) { Text("إضافة تسجيل") }
                }
            }
        }
    }
}

@Composable
private fun RamSimpleListSection(title: String, itemsList: List<String>, records: List<RamRecord>, category: String, onRecord: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { Text(title, color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        items(itemsList) { item ->
            val history = records.filter { it.category == category && it.item == item }.sortedByDescending { it.date }
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RamLine)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item, color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (history.isEmpty()) "لم يسجل بعد" else "آخر نتيجة: ${history.first().result} • ${history.first().date}", color = RamMuted, fontSize = 11.sp)
                    OutlinedButton(onClick = { onRecord(item) }, modifier = Modifier.fillMaxWidth()) { Text("إضافة نتيجة") }
                }
            }
        }
    }
}

@Composable
private fun RamRecordsSection(records: List<RamRecord>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        if (records.isEmpty()) item { Text("لا توجد سجلات لهذا الفحل حتى الآن.", color = RamMuted, fontSize = 13.sp) }
        items(records, key = { it.id }) { r ->
            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, RamLine)) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(r.item, color = RamInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(r.category + " • " + r.date, color = RamGreen, fontSize = 11.sp)
                    if (r.result.isNotBlank()) Text("النتيجة: " + r.result, color = RamMuted, fontSize = 12.sp)
                    if (r.product.isNotBlank()) Text("المنتج: " + r.product + if (r.dose.isNotBlank()) " • الجرعة: " + r.dose else "", color = RamMuted, fontSize = 11.sp)
                    if (r.nextDate.isNotBlank()) Text("المراجعة القادمة: " + r.nextDate, color = RamGreen, fontSize = 11.sp)
                    if (r.notes.isNotBlank()) Text(r.notes, color = RamMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun RamRecordDialog(title: String, hint: String, category: String, onDismiss: () -> Unit, onSave: (RamRecord) -> Unit) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var result by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var vet by rememberSaveable { mutableStateOf("") }
    var nextDate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, color = RamInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(hint, color = RamMuted, fontSize = 12.sp)
                MawashiDatePickerField(date, { date = it }, "تاريخ التنفيذ أو الفحص", Modifier.fillMaxWidth())
                OutlinedTextField(result, { result = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (category == "نسل") "وسم المولود / النتيجة / الوزن" else "النتيجة أو القياس") })
                if (category == "لقاح" || category == "مكمل") {
                    OutlinedTextField(dose, { dose = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الجرعة والتركيز") })
                    OutlinedTextField(product, { product = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج التجاري") })
                    OutlinedTextField(batch, { batch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم التشغيلة") })
                    OutlinedTextField(expiry, { expiry = it }, modifier = Modifier.fillMaxWidth(), label = { Text("تاريخ الصلاحية") })
                }
                OutlinedTextField(vet, { vet = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الطبيب أو المختبر") })
                MawashiDatePickerField(nextDate, { nextDate = it }, "موعد المراجعة القادمة (اختياري)", Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات أو تفاعلات أو تفاصيل") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = runCatching { LocalDate.parse(date) }.getOrNull()
                val n = if (nextDate.isBlank()) null else runCatching { LocalDate.parse(nextDate) }.getOrNull()
                if (d == null || d.isAfter(LocalDate.now()) || (nextDate.isNotBlank() && n == null) || (category == "نسل" && result.isBlank())) {
                    Toast.makeText(context, "تحقق من التاريخ والنتيجة المطلوبة", Toast.LENGTH_LONG).show()
                } else onSave(RamRecord("", "", category, title, date, result.trim(), dose.trim(), product.trim(), batch.trim(), expiry.trim(), vet.trim(), nextDate, notes.trim()))
            }, colors = ButtonDefaults.buttonColors(containerColor = RamGreen)) { Text("حفظ السجل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = RamGreen) } },
        containerColor = RamCream)
}
