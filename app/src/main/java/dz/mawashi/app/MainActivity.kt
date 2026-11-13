package dz.mawashi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF526B55)
private val Emerald = Color(0xFF526B55)
private val Leaf = Color(0xFF526B55)
private val Cream = Color(0xFFF7F3EA)
private val Ivory = Color(0xFFFFFFFF)
private val Gold = Color(0xFF526B55)
private val Ink = Color(0xFF27332A)
private val Muted = Color(0xFF778078)
private val Line = Color(0xFFE8E4DA)

private data class Section(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val metric: String,
    val metricLabel: String
)

private val sections = listOf(
    Section("القطيع", "الحيوانات والمواليد", Icons.Outlined.Agriculture, Emerald, "0", "حيوان مسجل"),
    Section("الأدوية والتطعيمات", "العلاجات ومواعيد التحصين", Icons.Outlined.Vaccines, Color(0xFF526B55), "—", "لا توجد بيانات مسجلة"),
    Section("مخزون العلف", "الكميات والاستهلاك", Icons.Outlined.Inventory2, Color(0xFF526B55), "—", "لا توجد بيانات مسجلة"),
    Section("المالية", "الإيرادات والمصاريف", Icons.Outlined.AccountBalanceWallet, Color(0xFF526B55), "—", "لا توجد بيانات مسجلة")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MawashiApp() }
    }
}

@Composable
private fun MawashiApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var dashboardRefresh by remember { mutableIntStateOf(0) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Emerald,
            onPrimary = Color.White,
            secondary = Gold,
            background = Cream,
            surface = Ivory,
            onSurface = Ink
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Cream,
                bottomBar = {
                    NavigationBar(
                        containerColor = Ivory,
                        contentColor = Forest,
                        tonalElevation = 8.dp,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        val navItems = listOf(
                            Triple("الرئيسية", Icons.Outlined.Home, 0),
                            Triple("القطيع", Icons.Outlined.Agriculture, 1),
                            Triple("الأدوية", Icons.Outlined.MedicalServices, 2),
                            Triple("مخزون العلف", Icons.Outlined.Inventory2, 3),
                            Triple("المالية", Icons.Outlined.AccountBalanceWallet, 4)
                        )
                        navItems.forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index; dashboardRefresh++ },
                                icon = { if (index == 1) EweLogo(28) else Icon(icon, contentDescription = label, tint = if (index == 3) Forest else if (selected == index) Forest else Muted) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Forest,
                                    selectedTextColor = Forest,
                                    indicatorColor = Color.White,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                when (selected) {
                    0 -> Dashboard(Modifier.padding(padding), refreshKey = dashboardRefresh, onSection = { selected = it; dashboardRefresh++ })
                    1 -> HerdScreen(Modifier.padding(padding))
                    2 -> MedicineScreen(Modifier.padding(padding))
                    3 -> FeedScreen(Modifier.padding(padding))
                    else -> FinanceScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier, refreshKey: Int, onSection: (Int) -> Unit) {
    val context = LocalContext.current
    var herd by remember { mutableStateOf(activeHerdAnimals(loadHerd(context))) }
    LaunchedEffect(refreshKey) { herd = activeHerdAnimals(loadHerd(context)) }
    val totalAnimals = herd.size
    val ewes = herd.count { it.kind == "نعجة" }
    val rams = herd.count { it.kind == "كبش" }
    val lambs = herd.count { it.kind == "حمل/رخلة" }
    val weighedAnimals = herd.mapNotNull { it.weightKg }.filter { it > 0.0 }
    var pendingBackup by remember { mutableStateOf<PreparedMawashiBackup?>(null) }
    var restoreDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var backupAction by remember { mutableStateOf("export") }
    var backupPassword by remember { mutableStateOf("") }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف الحفظ")
                val password = backupPassword.takeIf { it.isNotBlank() }
                output.use { MawashiBackup.export(context, it, password) }
                Toast.makeText(
                    context,
                    if (password == null) "تم إنشاء نسخة احتياطية بدون كلمة مرور" else "تم إنشاء نسخة احتياطية محمية بكلمة مرور",
                    Toast.LENGTH_LONG
                ).show()
                backupPassword = ""
            } catch (failure: Exception) {
                Toast.makeText(context, "تعذر إنشاء النسخة: ${failure.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("تعذر قراءة الملف")
                val bytes = MawashiBackup.readBackupBytes(input)
                if (MawashiBackup.requiresPassword(bytes)) {
                    pendingRestoreBytes = bytes
                    backupAction = "restore"
                    backupPassword = ""
                    showBackupPasswordDialog = true
                } else {
                    pendingBackup = MawashiBackup.prepareRestore(bytes)
                    restoreDialog = true
                    Toast.makeText(context, "هذه نسخة بدون كلمة مرور. يمكنك استعادتها مباشرة.", Toast.LENGTH_LONG).show()
                }
            } catch (failure: Exception) {
                Toast.makeText(context, "لم تتم الاستعادة: ${failure.message ?: "ملف غير صالح"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val averageWeight = if (weighedAnimals.isEmpty()) "—" else String.format(java.util.Locale.US, "%.1f", weighedAnimals.average())
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مَرْحَباً بك في", color = Muted, fontSize = 13.sp)
                    Text("مواشي", color = Forest, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    Text("رفيقك الذكي لإدارة المزرعة", color = Muted, fontSize = 12.sp)
                }
                Surface(shape = CircleShape, color = Ivory, shadowElevation = 3.dp) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.NotificationsNone, contentDescription = "التنبيهات", tint = Forest)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Surface(shape = CircleShape, color = Forest) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        EweLogo(40)
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Forest, Emerald, Color(0xFF526B55))))
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("إدارة أذكى، قطيع أقوى", color = Color(0xFFF7F3EA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        Text("كل تفاصيل مزرعتك\nفي مكان واحد", color = Color.White, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(13.dp))
                        Surface(color = Color(0xFFFFFCF5).copy(alpha = .15f), shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("ابدأ بتنظيم مزرعتك", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Outlined.ArrowBack, null, tint = Color(0xFFEAD7A8), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        EweLogo(76)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نظرة عامة", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("ملخص المزرعة", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("إجمالي القطيع", totalAnimals.toString(), "حيوان مسجل", { EweLogo(25) }, Modifier.weight(1f))
                SummaryCard("الحملان", lambs.toString(), "حمل / رخلة", { EweLogo(25) }, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("النعاج", ewes.toString(), "نعجة مسجلة", { EweLogo(25) }, Modifier.weight(1f))
                SummaryCard("الكباش", rams.toString(), "كبش مسجل", { EweLogo(25) }, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("متوسط الوزن", averageWeight, "كغ للحيوانات الموزونة", { Icon(Icons.Outlined.Assessment, null, tint = Emerald, modifier = Modifier.size(19.dp)) }, Modifier.weight(1f))
                SummaryCard("أوزان مسجلة", weighedAnimals.size.toString(), "حيوان له وزن", { Icon(Icons.Outlined.Assessment, null, tint = Emerald, modifier = Modifier.size(19.dp)) }, Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("أقسام المزرعة", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("4 أقسام", color = Muted, fontSize = 11.sp)
            }
        }
        itemsIndexed(sections.chunked(2)) { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                pair.forEachIndexed { colIndex, section ->
                    val index = rowIndex * 2 + colIndex
                    SectionTile(if (index == 0) section.copy(metric = totalAnimals.toString(), metricLabel = "حيوان مسجل") else section, Modifier.weight(1f)) { onSection(index + 1) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Surface(color = Ivory, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFE8EDE5), shape = RoundedCornerShape(13.dp)) {
                            Icon(Icons.Outlined.Security, null, tint = Emerald, modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("النسخ الاحتياطي وحماية بياناتك", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("احفظ القطيع والعلف والمالية والرعاية الصحية في ملف واحد خارج التطبيق", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { backupAction = "export"; backupPassword = ""; showBackupPasswordDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.SaveAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("إنشاء نسخة", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("استعادة نسخة", fontSize = 11.sp)
                        }
                    }
                    Text("الاستعادة تستبدل بيانات التطبيق الحالية بعد التحقق من الملف. احتفظ بنسخة حديثة قبل الاستعادة.", color = Muted, fontSize = 9.sp)
                    Text("تُشفّر النسخ الجديدة بكلمة مرور تختارها. احتفظ بها؛ لا يمكن استعادة الملف من دونها.", color = Emerald, fontSize = 9.sp)
                }
            }
        }
    }
    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showBackupPasswordDialog = false
                backupPassword = ""
                pendingRestoreBytes = null
            },
            title = { Text(if (backupAction == "export") "حماية النسخة الاحتياطية" else "فتح النسخة الاحتياطية", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (backupAction == "export")
                            "كلمة المرور اختيارية. اترك الحقل فارغاً لحفظ نسخة عادية، أو أضف كلمة مرور لحماية الملف. ستحتاج إليها عند الاستعادة."
                        else
                            "أدخل كلمة المرور التي استُخدمت عند إنشاء هذه النسخة."
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        singleLine = true,
                        label = { Text(if (backupAction == "export") "كلمة المرور (اختياري)" else "كلمة مرور النسخة") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (backupAction == "export") {
                        Text("بدون كلمة مرور: يمكن فتح الملف مباشرة، لكنه غير مشفّر. مع كلمة مرور: يصبح الملف مشفّراً ولا يُفتح دونها.", color = Muted, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        if (backupAction == "export") {
                            require(backupPassword.isBlank() || backupPassword.length >= 12) {
                                "إذا اخترت كلمة مرور، فيجب أن تتكون من 12 حرفاً على الأقل"
                            }
                            showBackupPasswordDialog = false
                            createBackupLauncher.launch(
                                if (backupPassword.isBlank()) "Mawashi-Backup-v32.json"
                                else "Mawashi-Backup-v32-encrypted.json"
                            )
                        } else {
                            val bytes = pendingRestoreBytes ?: error("ملف الاستعادة غير متاح")
                            pendingBackup = MawashiBackup.prepareRestore(bytes, backupPassword)
                            pendingRestoreBytes = null
                            showBackupPasswordDialog = false
                            restoreDialog = true
                        }
                    } catch (failure: Exception) {
                        Toast.makeText(context, failure.message ?: "تعذر تنفيذ العملية", Toast.LENGTH_LONG).show()
                    }
                }) { Text(if (backupAction == "export") "متابعة الحفظ" else "فتح النسخة", color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupPasswordDialog = false
                    backupPassword = ""
                    pendingRestoreBytes = null
                }) { Text("إلغاء") }
            }
        )
    }
    if (restoreDialog && pendingBackup != null) {
        AlertDialog(
            onDismissRequest = { restoreDialog = false; pendingBackup = null; backupPassword = "" },
            title = { Text("تأكيد استعادة البيانات", fontWeight = FontWeight.Bold) },
            text = { Text("سيتم استبدال بيانات القطيع والعلف والمالية والرعاية الصحية بالبيانات الموجودة في النسخة الاحتياطية. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    val backup = pendingBackup
                    if (backup != null) {
                        try {
                            MawashiBackup.restore(context, backup)
                            herd = activeHerdAnimals(loadHerd(context))
                            Toast.makeText(context, "تمت استعادة بيانات مواشي بنجاح", Toast.LENGTH_LONG).show()
                        } catch (failure: Exception) {
                            Toast.makeText(context, "فشلت الاستعادة: ${failure.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                        }
                    }
                    restoreDialog = false
                    pendingBackup = null
                    backupPassword = ""
                }) { Text("استعادة البيانات", color = Forest) }
            },
            dismissButton = {
                TextButton(onClick = { restoreDialog = false; pendingBackup = null; backupPassword = "" }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, unit: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Ivory,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, color = Forest, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(unit, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTile(section: Section, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(145.dp),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Ivory),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = section.tint.copy(alpha = .10f), shape = RoundedCornerShape(13.dp)) {
                    if (section.title == "القطيع") EweLogo(42) else Icon(section.icon, null, tint = section.tint, modifier = Modifier.padding(9.dp).size(23.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowOutward, null, tint = Gold, modifier = Modifier.size(17.dp))
            }
            Column {
                Text(section.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(section.subtitle, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(section.metric + " " + section.metricLabel, color = section.tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionScreen(modifier: Modifier = Modifier, section: Section) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = section.tint.copy(alpha = .10f), shape = RoundedCornerShape(16.dp)) {
                Icon(section.icon, null, tint = section.tint, modifier = Modifier.padding(13.dp).size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(section.title, color = Forest, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(section.subtitle, color = Muted, fontSize = 12.sp)
            }
        }
        Surface(
            color = Forest,
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ملخص القسم", color = Color(0xFFEAD7A8), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("لا توجد بيانات مسجلة", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("ستظهر الإحصائيات هنا بعد تسجيل بيانات القسم", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
        }
        Surface(color = Ivory, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(18.dp)) {
                Text("مساحة العمل", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(7.dp))
                Text("لم تُسجّل بيانات في هذا القسم بعد. ستُحتسب الإحصائيات تلقائياً عند إضافة السجلات الفعلية.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
    }
}
