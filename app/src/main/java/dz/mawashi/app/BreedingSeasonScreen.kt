package dz.mawashi.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
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
import java.time.temporal.ChronoUnit

private val SeasonForest = Color(0xFF304B38)
private val SeasonCream = Color(0xFFF5F1E7)
private val SeasonMuted = Color(0xFF71806E)
private const val SEASON_PREFS = "mawashi_breeding_seasons"
private const val SEASON_KEY = "records"

private data class SeasonRecord(
    val id: String, val name: String, val ram: String, val start: String, val removal: String,
    val scan: String, val tags: List<String>, val results: Map<String, String>,
    val gestation: Map<String, Int>, val fetuses: Map<String, Int>, val repeatScan: Map<String, String>
)

private fun readSeasons(context: Context): List<SeasonRecord> {
    val raw = context.getSharedPreferences(SEASON_PREFS, 0).getString(SEASON_KEY, "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tags = o.optJSONArray("tags") ?: JSONArray()
            fun stringMap(key: String): Map<String, String> {
                val x = o.optJSONObject(key) ?: JSONObject()
                return x.keys().asSequence().associateWith { x.optString(it) }
            }
            fun intMap(key: String): Map<String, Int> {
                val x = o.optJSONObject(key) ?: JSONObject()
                return x.keys().asSequence().associateWith { x.optInt(it) }
            }
            SeasonRecord(o.optString("id"), o.optString("name"), o.optString("ram"), o.optString("start"), o.optString("removal"), o.optString("scan"),
                (0 until tags.length()).map { tags.optString(it) }, stringMap("results"), intMap("gestation"), intMap("fetuses"), stringMap("repeatScan"))
        }
    }.getOrDefault(emptyList())
}

private fun writeSeasons(context: Context, seasons: List<SeasonRecord>) {
    val array = JSONArray()
    seasons.forEach { s ->
        fun stringObject(map: Map<String, String>) = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }
        fun intObject(map: Map<String, Int>) = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }
        array.put(JSONObject().apply {
            put("id", s.id); put("name", s.name); put("ram", s.ram); put("start", s.start); put("removal", s.removal); put("scan", s.scan)
            put("tags", JSONArray(s.tags)); put("results", stringObject(s.results)); put("gestation", intObject(s.gestation))
            put("fetuses", intObject(s.fetuses)); put("repeatScan", stringObject(s.repeatScan))
        })
    }
    check(context.getSharedPreferences(SEASON_PREFS, Context.MODE_PRIVATE).edit().putString(SEASON_KEY, array.toString()).commit()) { "تعذر حفظ سجلات مواسم التلقيح" }
}

@Composable
fun BreedingSeasonHomeCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val active = remember { readSeasons(context).lastOrNull() }
    val confirmed = active?.results?.values?.count { it == "حامل" } ?: 0
    val pending = (active?.tags?.size ?: 0) - (active?.results?.size ?: 0)
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = SeasonForest, shape = RoundedCornerShape(23.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Color(0xFFF5E8C8), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("موسم التلقيح", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text("التلقيح • السونار • تأكيد الحمل • الولادات", color = Color(0xFFE5EBDD), fontSize = 10.sp)
                }
                Text("←", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            if (active == null) Text("أنشئ موسماً وتابع النعاج من الصفحة الرئيسية", color = Color(0xFFE5EBDD), fontSize = 11.sp)
            else {
                Text(active.name, color = Color(0xFFF5E8C8), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeasonMiniMetric("مشاركة", active.tags.size.toString(), Modifier.weight(1f))
                    SeasonMiniMetric("حامل", confirmed.toString(), Modifier.weight(1f))
                    SeasonMiniMetric("بانتظار الفحص", pending.coerceAtLeast(0).toString(), Modifier.weight(1f))
                }
            }
            Text("الدخول إلى موسم التلقيح  ←", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SeasonMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White.copy(alpha = .13f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFFE5EBDD), fontSize = 9.sp)
        }
    }
}

@Composable
fun BreedingSeasonScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val animals = remember { mutableStateListOf<HerdAnimal>().apply { addAll(loadHerd(context)) } }
    val ewes = animals.filter { it.kind == "نعجة" && it.lifecycleStatus == "نشط" }
    val rams = animals.filter { it.kind == "كبش" && it.lifecycleStatus == "نشط" }
    var seasons by remember { mutableStateOf(readSeasons(context)) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("الكل") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val active = seasons.lastOrNull()

    fun save(updated: List<SeasonRecord>) {
        writeSeasons(context, updated)
        seasons = updated
    }

    Column(modifier.fillMaxSize().background(SeasonCream).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "رجوع", tint = SeasonForest) }
            Column(Modifier.weight(1f)) {
                Text("موسم التلقيح", color = SeasonForest, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("إدارة التلقيح ومتابعة الحمل والولادات", color = SeasonMuted, fontSize = 11.sp)
            }
            TextButton(onClick = { historyOpen = !historyOpen }) { Text(if (historyOpen) "الموسم الحالي" else "السجل السابق", color = SeasonForest, fontSize = 11.sp) }
        }
        if (historyOpen) {
            Text("سجل المواسم السابقة", color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons.reversed(), key = { it.id }) { season ->
                    val pregnant = season.results.values.count { it == "حامل" }
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp)) {
                            Text(season.name, color = SeasonForest, fontWeight = FontWeight.Bold)
                            Text("الكبش " + season.ram + " • " + season.start + " إلى " + season.removal, color = SeasonMuted, fontSize = 10.sp)
                            Text("النعاج: " + season.tags.size + " • حمل مؤكد: " + pregnant + " • نسبة النجاح: " + (if (season.tags.isEmpty()) 0 else pregnant * 100 / season.tags.size) + "%", color = SeasonMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else if (active == null) {
            Spacer(Modifier.height(12.dp))
            Surface(color = SeasonForest, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ابدأ بتنظيم موسم التلقيح", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("اختر الكبش والنعاج، ثم سجّل نتائج السونار لتقدير الولادات.", color = Color(0xFFE5EBDD), fontSize = 12.sp)
                    Button(onClick = { createOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SeasonForest)) { Text("إنشاء موسم تلقيح جديد") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("النعاج النشطة في القطيع: " + ewes.size, color = SeasonMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
        } else {
            val confirmed = active.results.values.count { it == "حامل" }
            val negative = active.results.values.count { it == "غير حامل" }
            val inconclusive = active.results.values.count { it == "غير حاسم" }
            val pending = (active.tags.size - active.results.size).coerceAtLeast(0)
            Surface(color = SeasonForest, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(active.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("الكبش " + active.ram + " • " + active.start + " حتى " + active.removal, color = Color(0xFFE5EBDD), fontSize = 10.sp)
                    Text("موعد السونار: " + active.scan, color = Color(0xFFF5E8C8), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeasonMiniMetric("مشاركة", active.tags.size.toString(), Modifier.weight(1f))
                        SeasonMiniMetric("حامل", confirmed.toString(), Modifier.weight(1f))
                        SeasonMiniMetric("بانتظار", pending.toString(), Modifier.weight(1f))
                    }
                    LinearProgressIndicator(progress = { if (active.tags.isEmpty()) 0f else confirmed.toFloat() / active.tags.size }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFB5D5A6), trackColor = Color.White.copy(alpha = .2f))
                    Text("نجاح مؤكد " + (if (active.tags.isEmpty()) 0 else confirmed * 100 / active.tags.size) + "%  •  غير حامل " + negative + "  •  غير حاسم " + inconclusive, color = Color.White, fontSize = 10.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("النعاج المشاركة", color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { createOpen = true }) { Text("موسم جديد", color = SeasonForest, fontSize = 11.sp) }
            }
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("البحث برقم النعجة") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("الكل", "حامل", "بانتظار", "غير حامل", "غير حاسم").forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item, fontSize = 9.sp) })
                }
            }
            val visible = active.tags.mapNotNull { tag -> ewes.firstOrNull { it.tag == tag } }.filter { ewe ->
                val result = active.results[ewe.tag]
                ewe.tag.contains(search.trim(), true) && when (filter) {
                    "حامل" -> result == "حامل"
                    "غير حامل" -> result == "غير حامل"
                    "غير حاسم" -> result == "غير حاسم"
                    "بانتظار" -> result == null
                    else -> true
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp, top = 7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { ewe ->
                    val result = active.results[ewe.tag]
                    val days = active.gestation[ewe.tag]
                    val due = if (result == "حامل" && days != null) runCatching { LocalDate.parse(active.scan).plusDays(147L - days).toString() }.getOrNull() else null
                    Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            EwePhoto(42)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("النعجة رقم " + ewe.tag, color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("الكبش " + active.ram + " • " + when (result) {
                                    "حامل" -> "حامل مؤكد"
                                    "غير حامل" -> "غير حامل"
                                    "غير حاسم" -> "نتيجة غير حاسمة"
                                    else -> "بانتظار السونار"
                                }, color = when (result) { "حامل" -> Color(0xFF438052); "غير حامل" -> Color(0xFFC75B52); else -> Color(0xFFA66B16) }, fontSize = 10.sp)
                                if (result == "حامل") Text((active.fetuses[ewe.tag]?.let { "عدد الأجنة: " + it } ?: "عدد الأجنة غير محدد") + (days?.let { " • عمر الحمل: " + it + " يوم" } ?: ""), color = SeasonMuted, fontSize = 9.sp)
                                if (due != null) Text("الولادة المتوقعة: " + due, color = SeasonForest, fontSize = 9.sp)
                                if (active.repeatScan[ewe.tag] != null) Text("إعادة الفحص: " + active.repeatScan[ewe.tag], color = SeasonMuted, fontSize = 9.sp)
                            }
                            TextButton(onClick = { selectedTag = ewe.tag }) { Text("تسجيل الفحص", color = SeasonForest, fontSize = 10.sp) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { historyOpen = true }, modifier = Modifier.weight(1f)) { Text("سجل المواسم", fontSize = 10.sp) }
                        OutlinedButton(onClick = { calendarOpen = !calendarOpen }, modifier = Modifier.weight(1f)) { Text("تقويم الولادات", fontSize = 10.sp) }
                    }
                }
                if (calendarOpen) {
                    item { Text("الولادات المتوقعة للحمل المؤكد", color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    val dueRows = active.tags.mapNotNull { tag ->
                        val age = active.gestation[tag]
                        if (active.results[tag] != "حامل" || age == null) null
                        else runCatching { tag to LocalDate.parse(active.scan).plusDays(147L - age) }.getOrNull()
                    }.sortedBy { it.second }
                    items(dueRows, key = { it.first }) { due ->
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                EwePhoto(34)
                                Spacer(Modifier.width(8.dp))
                                Text("النعجة " + due.first, color = SeasonForest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 11.sp)
                                Text(due.second.toString(), color = SeasonMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    if (dueRows.isEmpty()) item { Text("لا توجد ولادات مؤكدة التاريخ بعد؛ سجّل عمر الحمل من السونار.", color = SeasonMuted, fontSize = 10.sp) }
                }
            }
        }
    }

    if (createOpen) {
        CreateSeasonDialog(ewes, rams, onDismiss = { createOpen = false }) { record ->
            save(seasons + record)
            createOpen = false
        }
    }
    if (selectedTag != null && active != null) {
        ScanOutcomeDialog(selectedTag!!, active.repeatScan[selectedTag!!].orEmpty(), onDismiss = { selectedTag = null }) { outcome, days, fetusCount, repeat ->
            val updated = active.copy(
                results = active.results + (selectedTag!! to outcome),
                gestation = if (outcome == "حامل" && days > 0) active.gestation + (selectedTag!! to days) else active.gestation - selectedTag!!,
                fetuses = if (outcome == "حامل") active.fetuses + (selectedTag!! to fetusCount) else active.fetuses - selectedTag!!,
                repeatScan = if (outcome == "غير حاسم" && repeat.isNotBlank()) active.repeatScan + (selectedTag!! to repeat) else active.repeatScan - selectedTag!!
            )
            val profileSaved = savePregnancyScanToHerd(
                context, selectedTag!!, outcome, active.scan, days, fetusCount
            )
            save(seasons.dropLast(1) + updated)
            if (!profileSaved && outcome != "غير حاسم") {
                android.widget.Toast.makeText(context, "حُفظت نتيجة الموسم لكن تعذر تحديث ملف النعجة في القطيع", android.widget.Toast.LENGTH_LONG).show()
            }
            selectedTag = null
        }
    }
}

@Composable
private fun CreateSeasonDialog(ewes: List<HerdAnimal>, rams: List<HerdAnimal>, onDismiss: () -> Unit, onSave: (SeasonRecord) -> Unit) {
    var name by rememberSaveable { mutableStateOf("موسم التلقيح " + LocalDate.now().year) }
    var ram by rememberSaveable { mutableStateOf(rams.firstOrNull()?.tag.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var removal by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(34).toString()) }
    var scan by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(74).toString()) }
    var all by rememberSaveable { mutableStateOf(true) }
    var chosen by remember { mutableStateOf(ewes.map { it.tag }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء موسم تلقيح") },
        text = {
            Column(Modifier.heightIn(max = 530.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الموسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("الكبش المستخدم", color = SeasonForest, fontWeight = FontWeight.Bold)
                rams.forEach { animal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = ram == animal.tag, onClick = { ram = animal.tag })
                        Text("الكبش " + animal.tag, fontSize = 11.sp)
                    }
                }
                if (rams.isEmpty()) Text("لا يوجد كبش نشط مسجل في القطيع.", color = Color(0xFFC75B52), fontSize = 11.sp)
                OutlinedTextField(start, { start = it; error = null }, label = { Text("تاريخ إدخال الكبش YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(removal, { removal = it; error = null }, label = { Text("تاريخ إخراج الكبش YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(scan, { scan = it; error = null }, label = { Text("موعد السونار YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("اختيار النعاج", color = SeasonForest, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = all, onClick = { all = true; chosen = ewes.map { it.tag }.toSet() })
                    Text("كل النعاج النشطة (" + ewes.size + ")", fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !all, onClick = { all = false })
                    Text("اختيار نعاج محددة", fontSize = 11.sp)
                }
                if (!all) ewes.forEach { ewe ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ewe.tag in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + ewe.tag else chosen - ewe.tag })
                        Text("النعجة " + ewe.tag, fontSize = 11.sp)
                    }
                }
                Text("عند تلقيح مضبوط لا يتجاوز نحو 34 يوماً، يُقترح السونار بعد 40 يوماً من إخراج الكبش. الحالات الأطول أو غير المؤكدة تحتاج جدولة فردية مع الطبيب البيطري.", color = SeasonMuted, fontSize = 10.sp)
                error?.let { Text(it, color = Color(0xFFC75B52), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val from = runCatching { LocalDate.parse(start) }.getOrNull()
                val to = runCatching { LocalDate.parse(removal) }.getOrNull()
                val scanDate = runCatching { LocalDate.parse(scan) }.getOrNull()
                val participants = if (all) ewes.map { it.tag } else chosen.toList()
                error = when {
                    name.isBlank() -> "أدخل اسم الموسم."
                    ram.isBlank() -> "اختر كبشاً نشطاً."
                    participants.isEmpty() -> "اختر نعجة واحدة على الأقل."
                    from == null || to == null || !to.isAfter(from) -> "تحقق من تاريخي إدخال الكبش وإخراجه."
                    scanDate == null || scanDate.isBefore(to) -> "موعد السونار يجب أن يكون بعد إخراج الكبش."
                    else -> null
                }
                if (error == null) onSave(SeasonRecord(java.util.UUID.randomUUID().toString(), name.trim(), ram, start, removal, scan, participants, emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            }) { Text("حفظ الموسم", color = SeasonForest) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ScanOutcomeDialog(tag: String, oldRepeat: String, onDismiss: () -> Unit, onSave: (String, Int, Int, String) -> Unit) {
    var result by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("60") }
    var fetus by rememberSaveable { mutableStateOf("1") }
    var repeat by rememberSaveable { mutableStateOf(oldRepeat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نتيجة سونار النعجة " + tag) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("حامل", "غير حامل", "غير حاسم").forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = result == option, onClick = { result = option })
                        Text(option, fontSize = 12.sp)
                    }
                }
                if (result == "حامل") {
                    OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("عمر الحمل بالأيام حسب السونار") }, singleLine = true)
                    Text("عدد الأجنة", color = SeasonForest, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1", "2", "3").forEach { v -> FilterChip(selected = fetus == v, onClick = { fetus = v }, label = { Text(v) }) }
                    }
                    Text("يُحسب الموعد التقديري من عمر الحمل في تاريخ السونار وبمتوسط 147 يوماً.", color = SeasonMuted, fontSize = 10.sp)
                }
                if (result == "غير حاسم") {
                    OutlinedTextField(repeat, { repeat = it }, label = { Text("موعد إعادة الفحص YYYY-MM-DD") }, singleLine = true)
                    Text("لن تُسجل هذه النعجة على أنها غير حامل.", color = Color(0xFFA66B16), fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val age = days.toIntOrNull() ?: 0
                val valid = result.isNotBlank() && (result != "حامل" || age in 1..145)
                val validRepeat = result != "غير حاسم" || runCatching { LocalDate.parse(repeat); true }.getOrDefault(false)
                if (valid && validRepeat) onSave(result, age, fetus.toIntOrNull() ?: 1, repeat)
            }) { Text("حفظ النتيجة", color = SeasonForest) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
