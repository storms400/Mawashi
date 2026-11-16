package dz.mawashi.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
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

private val SeasonForest = Color(0xFF304B38)
private val SeasonCream = Color(0xFFF5F1E7)
private val SeasonMuted = Color(0xFF71806E)
private const val SEASON_PREFS = "mawashi_breeding_seasons"
private const val SEASON_KEY = "active_season"

@Composable
fun BreedingSeasonHomeCard(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = SeasonForest, shape = RoundedCornerShape(23.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = Color(0xFFF5E8C8), modifier = Modifier.padding(9.dp).size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("موسم التلقيح", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text("التلقيح • السونار • تأكيد الحمل • الولادات", color = Color(0xFFE5EBDD), fontSize = 10.sp)
                }
                Text("←", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Text("متابعة المواسم ونتائج فحص الحمل مباشرة من الصفحة الرئيسية", color = Color(0xFFE5EBDD), fontSize = 11.sp)
            Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("الدخول إلى موسم التلقيح", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("عرض التفاصيل", color = Color(0xFFE5EBDD), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun BreedingSeasonScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val ewes = remember { loadHerd(context).filter { it.kind == "نعجة" && it.lifecycleStatus == "نشط" } }
    val rams = remember { loadHerd(context).filter { it.kind == "كبش" && it.lifecycleStatus == "نشط" } }
    var seasonName by remember { mutableStateOf(context.getSharedPreferences(SEASON_PREFS, 0).getString("name", "") ?: "") }
    var ramTag by remember { mutableStateOf(context.getSharedPreferences(SEASON_PREFS, 0).getString("ram", "") ?: "") }
    var startDate by remember { mutableStateOf(context.getSharedPreferences(SEASON_PREFS, 0).getString("start", "") ?: "") }
    var removalDate by remember { mutableStateOf(context.getSharedPreferences(SEASON_PREFS, 0).getString("removal", "") ?: "") }
    var scanDate by remember { mutableStateOf(context.getSharedPreferences(SEASON_PREFS, 0).getString("scan", "") ?: "") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val prefs = context.getSharedPreferences(SEASON_PREFS, Context.MODE_PRIVATE)

    Column(modifier.fillMaxSize().background(SeasonCream).padding(horizontal = 17.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "رجوع", tint = SeasonForest) }
            Column(Modifier.weight(1f)) {
                Text("موسم التلقيح", color = SeasonForest, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("إدارة التلقيح ومتابعة الحمل والولادات", color = SeasonMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = SeasonForest, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (seasonName.isBlank()) "لا يوجد موسم مسجل" else seasonName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (seasonName.isBlank()) "ابدأ بإنشاء موسم، وحدد الكبش والنعاج ومواعيد التلقيح والسونار." else "الكبش: " + ramTag + " • إدخال: " + startDate + " • إخراج: " + removalDate, color = Color(0xFFE5EBDD), fontSize = 11.sp)
                if (scanDate.isNotBlank()) Text("موعد فحص السونار: " + scanDate, color = Color(0xFFF5E8C8), fontSize = 12.sp)
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SeasonForest)) {
                    Text(if (seasonName.isBlank()) "إنشاء موسم تلقيح جديد" else "تعديل بيانات الموسم")
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Text("النعاج المسجلة في القطيع", color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("تُعرض هنا النعاج النشطة؛ ربط نتائج السونار واحتساب الولادات سيكون ضمن سجل الموسم.", color = SeasonMuted, fontSize = 10.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ewes, key = { it.id }) { ewe ->
                Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        EweLogo(42)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("النعجة رقم " + ewe.tag, color = SeasonForest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("الحالة الحالية: " + ewe.productionState, color = SeasonMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("إعداد موسم التلقيح") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(seasonName, { seasonName = it; error = null }, label = { Text("اسم الموسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("اختر الكبش", color = SeasonForest, fontWeight = FontWeight.Bold)
                    rams.forEach { ram ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = ramTag == ram.tag, onClick = { ramTag = ram.tag; error = null })
                            Text("الكبش " + ram.tag, fontSize = 12.sp)
                        }
                    }
                    if (rams.isEmpty()) Text("لا يوجد كبش نشط مسجل. أضف الكبش في قسم القطيع أولاً.", color = Color(0xFFC75B52), fontSize = 11.sp)
                    OutlinedTextField(startDate, { startDate = it; error = null }, label = { Text("تاريخ إدخال الكبش (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(removalDate, { removalDate = it; error = null }, label = { Text("تاريخ إخراج الكبش (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(scanDate, { scanDate = it; error = null }, label = { Text("موعد فحص السونار (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("للتلقيح المضبوط الذي لا يتجاوز نحو 34 يوماً، يمكن اقتراح السونار بعد 40 يوماً من إخراج الكبش. الموعد تقديري ويُراجع مع الطبيب البيطري.", color = SeasonMuted, fontSize = 10.sp)
                    error?.let { Text(it, color = Color(0xFFC75B52), fontSize = 11.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val from = runCatching { LocalDate.parse(startDate) }.getOrNull()
                    val to = runCatching { LocalDate.parse(removalDate) }.getOrNull()
                    val scan = runCatching { LocalDate.parse(scanDate) }.getOrNull()
                    error = when {
                        seasonName.isBlank() -> "أدخل اسم الموسم."
                        ramTag.isBlank() -> "اختر كبشاً مسجلاً ونشطاً."
                        ewes.isEmpty() -> "لا توجد نعاج نشطة في القطيع."
                        from == null || to == null || !to.isAfter(from) -> "تحقق من تاريخ إدخال الكبش وإخراجه."
                        scan == null || scan.isBefore(to) -> "حدد موعد فحص بعد إخراج الكبش."
                        else -> null
                    }
                    if (error == null) {
                        prefs.edit().putString("name", seasonName.trim()).putString("ram", ramTag).putString("start", startDate).putString("removal", removalDate).putString("scan", scanDate).putString("eweTags", JSONArray(ewes.map { it.tag }).toString()).apply()
                        showCreate = false
                    }
                }) { Text("حفظ الموسم", color = SeasonForest) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("إلغاء") } }
        )
    }
}
