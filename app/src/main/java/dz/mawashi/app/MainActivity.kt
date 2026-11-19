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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
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
    Section("الصحة والوقاية", "الرعاية الصحية الفردية للقطيع", Icons.Outlined.HealthAndSafety, Color(0xFF526B55), "—", "متابعة صحة القطيع"),
    Section("مخزون العلف", "الكميات والاستهلاك", Icons.Outlined.Inventory2, Color(0xFF526B55), "—", "لا توجد بيانات مسجلة"),
    Section("المالية", "الإيرادات والمصاريف", Icons.Outlined.AccountBalanceWallet, Color(0xFF526B55), "—", "لا توجد بيانات مسجلة"),
    Section("الموسوعة العلمية", "دليل تربية الأغنام والمعرفة العملية", Icons.Outlined.MenuBook, Color(0xFF526B55), "10", "أبواب علمية")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { reconcileHerdLifecycle(this) }
        HerdCareNotifications.rescheduleFromStorage(this)
        setContent { MawashiApp() } // Local-first entry
    }

    override fun onResume() {
        super.onResume()
        runCatching { reconcileHerdLifecycle(this) }
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
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * 1.20f)
        ) {
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
                            Triple("الصحة والوقاية", Icons.Outlined.HealthAndSafety, 2),
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
                    1 -> HerdScreen(Modifier.padding(padding), refreshKey = dashboardRefresh)
                    2 -> HealthPreventionScreen(Modifier.padding(padding))
                    3 -> FeedManagementSuiteScreen(Modifier.padding(padding))
                    5 -> BreedingSeasonScreen(Modifier.padding(padding), onBack = { selected = 0; dashboardRefresh++ })
                    6 -> EncyclopediaScreen(Modifier.padding(padding), onBack = { selected = 0; dashboardRefresh++ })
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
                    IconButton(onClick = { onSection(2) }) {
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
        item { BreedingSeasonHomeCard(refreshKey = refreshKey) { onSection(5) } }
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
                Text("5 أقسام", color = Muted, fontSize = 11.sp)
            }
        }
        itemsIndexed(sections.chunked(2)) { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                pair.forEachIndexed { colIndex, section ->
                    val index = rowIndex * 2 + colIndex
                    SectionTile(if (index == 0) section.copy(metric = totalAnimals.toString(), metricLabel = "حيوان مسجل") else section, Modifier.weight(1f)) {
                        onSection(if (index == 4) 6 else index + 1)
                    }
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
                    when (section.title) {
                        "القطيع" -> EweLogo(42)
                        "الموسوعة العلمية" -> Text("📕", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        else -> Icon(section.icon, null, tint = section.tint, modifier = Modifier.padding(9.dp).size(23.dp))
                    }
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

private data class EncyclopediaLesson(val heading: String, val body: String)
private data class EncyclopediaChapter(val title: String, val summary: String, val lessons: List<EncyclopediaLesson>)

private val encyclopediaChapters = listOf(
    EncyclopediaChapter("أساسيات تربية الأغنام", "اختيار القطيع والسلالات والسجلات والحجر الصحي.", listOf(
        EncyclopediaLesson("اختيار السلالة والهدف", "حدد هدف المزرعة أولاً: إنتاج الحملان أو الحليب أو الصوف أو نظام مختلط. اختر سلالة متأقلمة مع المناخ المحلي وتوفر المرعى والأعلاف والخدمات البيطرية. افحص الأسنان والعمر والحركة والقوائم والضرع والخصيتين والحالة الجسمية، واطلب سجل الولادات والصحة عند توفره."),
        EncyclopediaLesson("تقييم الحالة الجسمية", "يُستخدم تقدير حالة الجسم (BCS) على مقياس شائع من 1 إلى 5 عبر جس الفقرات القطنية وتغطية العضلات والدهن، لا بالنظر إلى الصوف وحده. النحافة أو السمنة الشديدة قد تخفض الخصوبة وترفع مخاطر الحمل والولادة. سجّل التقييم دورياً وقارن الحيوان بنفسه عبر الزمن."),
        EncyclopediaLesson("التعريف والسجلات", "امنح كل رأس رقماً ثابتاً، وسجّل الميلاد أو الشراء، والأم والأب إن عُرفا، والوزن، والتلقيح، والولادة، والعلاجات والتحصينات. السجل الجيد يكشف ضعف النمو وتكرر الأمراض والنعاج قليلة الإنتاج."),
        EncyclopediaLesson("العزل والأمن الحيوي", "اعزل الحيوانات الجديدة أو العائدة من الأسواق للمدة التي يحددها الطبيب البيطري وفق مخاطر المنطقة. راقب الشهية والبراز والتنفس والجلد، وخصص أدوات للعزل ونظف المشارب والمعالف، وتخلص من الجثث والمخلفات بطريقة صحية.")
    )),
    EncyclopediaChapter("التغذية والعليقة", "المادة الجافة والطاقة والبروتين والألياف والماء وتغيير العليقة.", listOf(
        EncyclopediaLesson("الاحتياجات الغذائية", "تتغير الاحتياجات بحسب الوزن والعمر والنمو والحمل وعدد الأجنة والرضاعة وإنتاج الحليب والنشاط والطقس. لا توجد خلطة واحدة مناسبة لكل الرؤوس. قدّر الاحتياجات ثم قارنها بما توفره الأعلاف والمرعى، واستعن بتحليل مخبري عند توفره."),
        EncyclopediaLesson("المادة الجافة والطاقة والبروتين", "المادة الجافة هي ما يتبقى من العلف بعد طرح الماء؛ لذلك لا يصح مقارنة كيلوغرام علف أخضر رطب بكيلوغرام حبوب جافة دون تحويل. الطاقة قد تُعبّر عنها ME أو TDN ولا يجوز خلط الوحدات. البروتين الخام مؤشر تقريبي وليس حكماً كاملاً على جودة البروتين."),
        EncyclopediaLesson("الألياف والتدرج", "الأعلاف الخشنة الفعالة تدعم الاجترار ووظيفة الكرش. الإفراط المفاجئ في الحبوب والنشا قد يسبب حموضة الكرش. غيّر العليقة تدريجياً على عدة أيام إلى أسبوع أو أكثر بحسب حجم التغيير، وراقب الاجترار والبراز والشهية."),
        EncyclopediaLesson("الماء والأملاح", "وفّر ماءً نظيفاً باستمرار، وزد الانتباه للحرارة والرضاعة والأعلاف الجافة والملوحة. تختلف المعادن حسب التربة والعلف والمنطقة، والأغنام حساسة لزيادة النحاس. لا تستخدم مخاليط الأبقار أو الماعز دون التأكد من ملاءمتها للأغنام وقراءة الملصق.")
    )),
    EncyclopediaChapter("التكاثر والحمل", "تجهيز الكباش والتلقيح وتشخيص الحمل وإدارة النعاج.", listOf(
        EncyclopediaLesson("تجهيز موسم التلقيح", "قيّم حالة النعاج والكباش قبل الموسم بوقت كافٍ، وافحص القوائم والأسنان والحالة الجسمية والخصوبة السابقة. يحتاج الكبش إلى لياقة جيدة وقدرة على الحركة، ويراعى العمر والتضاريس وحجم المجموعة."),
        EncyclopediaLesson("الشبق والتلقيح", "قد تظهر علامات الشبق في تقبل الكبش وكثرة الحركة وتحريك الذيل، وقد تكون خفية عند غيابه. تختلف النشاطات التناسلية حسب السلالة والموسم والتغذية وخط العرض. سجّل تاريخ إدخال الكبش وإخراجه لتقدير نافذة الولادة، مع اعتبار مدة الحمل تقريبية."),
        EncyclopediaLesson("التأكد من الحمل", "يمكن للطبيب البيطري استخدام الموجات فوق الصوتية في التوقيت المناسب لتأكيد الحمل وتقدير عدد الأجنة. لا تعتمد على شكل البطن وحده. يساعد التشخيص على تقسيم النعاج غذائياً ومراقبة الحوامل بتوأم بعناية."),
        EncyclopediaLesson("تغذية الحوامل", "تزداد أهمية التغذية المتوازنة في أواخر الحمل مع نمو الأجنة. الإفراط في الطاقة قد يسبب السمنة ومشاكل الولادة، والنقص قد يرفع خطر تسمم الحمل وضعف المواليد. قسّم النعاج حسب الحالة الجسمية وعدد الأجنة.")
    )),
    EncyclopediaChapter("الولادة والمواليد", "الاستعداد للولادة والسرسوب والدفء ومتابعة الحملان.", listOf(
        EncyclopediaLesson("الاستعداد", "جهّز مكاناً نظيفاً وجافاً جيد التهوية وخالياً من تيارات الهواء المباشرة، مع فرشة نظيفة وماء وأدوات نظيفة. راقب النعاج قرب الولادة دون إزعاج، وتأكد من ارتباط الأم بمولودها بعد الولادة."),
        EncyclopediaLesson("الولادة الطبيعية والتعسر", "قد تظهر علامات مثل الانعزال والقلق وامتلاء الضرع وارتخاء أربطة الحوض وظهور كيس الماء. غالباً تتقدم الولادة الطبيعية دون تدخل. لا تسحب الجنين بعنف أو تدخل اليد بأدوات غير معقمة؛ تعسر الولادة أو الوضعية غير الطبيعية يحتاجان إلى مساعدة بيطرية مدربة."),
        EncyclopediaLesson("السرسوب والدفء", "السرسوب هو الغذاء الأول الغني بالطاقة والأجسام المناعية، ويجب أن يحصل المولود عليه مبكراً وبكمية مناسبة لوزنه وفق الإرشاد البيطري. جفف المولود وحافظ على دفئه وتأكد من التنفس والوقوف والرضاعة. ضعف المص أو البرودة أو الخمول يستدعي تدخلاً سريعاً."),
        EncyclopediaLesson("السرة والتسجيل", "اتبع بروتوكول الطبيب البيطري لتنظيف أو تطهير السرة بالمنتج المناسب. سجّل وقت الولادة والجنس والوزن ورقم الأم وحالة المولود، وراقب الرضاعة والبراز والنشاط في الأيام الأولى.")
    )),
    EncyclopediaChapter("الصحة والوقاية", "المراقبة اليومية والتحصين والطفيليات والعزل.", listOf(
        EncyclopediaLesson("الملاحظة اليومية", "راقب الشهية والاجترار والحركة والتنفس والعينين والأنف والبراز والمشية والعزلة عن القطيع. اكتشاف التغير مبكراً أهم من انتظار المرض الشديد. قِس الحرارة عند الاشتباه بميزان مناسب وسجّل القراءة والأعراض."),
        EncyclopediaLesson("برنامج التحصين", "لا يوجد جدول لقاحات عالمي واحد يصلح لكل المناطق؛ يعتمد البرنامج على الأمراض المحلية وسجل المزرعة والعمر والحمل ونوع اللقاح وتعليمات الشركة والسلطات البيطرية. ضع البرنامج مع الطبيب، واحفظ اللقاحات حسب الملصق وسجّل التشغيلة والتاريخ."),
        EncyclopediaLesson("الطفيليات", "التجريع العشوائي والمتكرر يسرّع مقاومة الديدان للأدوية. استخدم تقييم الحالة وفحص البراز عند الحاجة ومؤشرات مثل فقر الدم وفقدان الوزن. اختر العلاج والجرعة وفترة السحب وفق الطبيب والملصق، وراقب القمل والجرب والقراد."),
        EncyclopediaLesson("متى تطلب الطبيب", "اعزل الحيوان المشتبه به واطلب الطبيب سريعاً عند صعوبة التنفس أو الانتفاخ الشديد أو عدم القدرة على الوقوف أو التشنجات أو النزيف أو تعسر الولادة أو النفوق المفاجئ أو انتشار أعراض بين عدة رؤوس. لا تعط أدوية بشرية ولا تخلط علاجات دون توجيه.")
    )),
    EncyclopediaChapter("الحظائر والرعاية اليومية", "التهوية والنظافة والمعالف وتقسيم المجموعات.", listOf(
        EncyclopediaLesson("الموقع والتهوية", "اختر موقعاً جيد التصريف بعيداً عن تجمع المياه، ووفّر تهوية متجددة دون تيارات باردة مباشرة على الحملان. قلل الرطوبة والأمونيا والغبار ونظف الفرشة. تتغير المساحة المناسبة بحسب حجم الحيوان والعمر ونظام الإيواء والمناخ."),
        EncyclopediaLesson("المعالف والمشارب", "اجعل المعالف والمشارب سهلة التنظيف وتمنع دخول الروث إليها، ووزعها لتقليل التنافس. افصل الأعلاف عن الأرض الرطبة وخزن الحبوب في مكان جاف محكم ضد القوارض والحشرات."),
        EncyclopediaLesson("تقسيم المجموعات", "افصل الحملان عن البالغات عند الحاجة، وخصص مجموعات للحوامل والمرضعات والنعاج الجافة والكباش والحيوانات المعزولة. التقسيم يسهل تقديم العليقة الملائمة ومراقبة الضعيفة."),
        EncyclopediaLesson("تقليل الإجهاد", "تعامل بهدوء مع الأغنام وتجنب المطاردة الطويلة والحرارة الشديدة والنقل غير الضروري. خطط للنقل والقص والفرز في أوقات مناسبة، ووفّر الظل والماء والراحة.")
    )),
    EncyclopediaChapter("النمو والإنتاج", "قياس الأوزان ومعدل النمو والفطام ومؤشرات الأداء.", listOf(
        EncyclopediaLesson("الوزن ومعدل النمو", "زن الحيوانات بميزان مناسب وفي ظروف متقاربة وسجّل العمر والوزن والتاريخ. يحسب متوسط الزيادة اليومية بقسمة فرق الوزن على عدد الأيام. انتبه لاختلاف امتلاء الكرش والترطيب، ولا تقارن أعماراً وسلالات مختلفة دون مراعاة ذلك."),
        EncyclopediaLesson("متابعة الحملان", "راقب الرضاعة والنشاط والنمو والبراز، وتأكد من توفر علف بادئ مناسب وماء نظيف عند العمر الملائم. افحص أسباب ضعف النمو مثل نقص الحليب أو الطفيليات أو المرض أو الازدحام أو رداءة العلف قبل زيادة المركزات."),
        EncyclopediaLesson("الفطام", "يُحدد الفطام وفق العمر والوزن واستهلاك العلف الصلب وصحة الحمل ونظام المزرعة، وليس بتاريخ ثابت وحده. نفّذه تدريجياً قدر الإمكان وقلل عوامل الإجهاد المتزامنة وراقب الوزن والشهية."),
        EncyclopediaLesson("مؤشرات الأداء", "تابع نسبة الولادات وعدد المواليد لكل نعجة ونفوق المواليد ومعدل النمو والعمر عند التسويق وتكاليف التغذية والعلاج. وحّد طريقة التسجيل والفترة قبل مقارنة النتائج.")
    )),
    EncyclopediaChapter("الاقتصاد والتسويق", "التكلفة الحقيقية وسجلات الشراء والبيع وتحليل الربحية.", listOf(
        EncyclopediaLesson("تكلفة الإنتاج", "اجمع تكاليف الأعلاف والأدوية واللقاحات والعمالة والنقل والصيانة والماء والطاقة ووزع التكاليف المشتركة بطريقة ثابتة. فرّق بين المصروف النقدي وتكلفة الأصول، ولا تعتبر سعر البيع ربحاً صافياً قبل خصم التكاليف."),
        EncyclopediaLesson("سجلات الشراء والبيع", "سجّل التاريخ والعدد والوزن والسعر والمورد أو المشتري والرسوم والملاحظات الصحية. اربط الحركة بسجل الحيوان والمخزون حتى لا تتكرر الأعداد أو تحسب المبيعات مرتين."),
        EncyclopediaLesson("تقييم الحيوان", "قيّم الوزن والحالة الجسمية والعمر والأسنان والصحة وملاءمة الحيوان للسوق. اتبع قواعد الرفق بالحيوان ومتطلبات السوق والنقل، ولا تستخدم التجويع أو العطش قبل الوزن أو النقل."),
        EncyclopediaLesson("تحليل الربحية", "احسب هامش الربح لكل دورة أو مجموعة، وقارن تكلفة الكيلوغرام المكتسب بأسعار الأعلاف والبيع. اختبر تغير الأسعار والنفوق والإنتاج قبل التوسع واحتفظ باحتياطي للطوارئ.")
    )),
    EncyclopediaChapter("التربية الموسمية وإدارة المرعى", "التخطيط للجفاف والحرارة وتغير توفر العلف.", listOf(
        EncyclopediaLesson("تخطيط المرعى", "قدّر مساحة المرعى وكمية الكتلة العلفية ومرحلة نمو النبات قبل إدخال القطيع. اترك للنبات فرصة للتجدد وبدّل القطع عند الإمكان وتجنب الرعي الجائر الذي يضعف الغطاء النباتي."),
        EncyclopediaLesson("مساهمة المرعى", "تختلف مساهمة المرعى حسب نوع النبات ومرحلة نموه والرطوبة وكثافة القطيع ومدة الرعي. أي نسبة تقديرية في برنامج التغذية افتراض حسابي وليست قياساً للاستهلاك الفعلي؛ عدّلها بالملاحظة أو التحليل."),
        EncyclopediaLesson("الحرارة والجفاف", "في الحر وفّر الظل والماء والتهوية وقلل النقل والفرز في أوقات الذروة. خطط لمخزون التبن والأعلاف، وراقب جودة التخزين والعفن ولا تقدم علفاً متعفناً أو متغير الرائحة."),
        EncyclopediaLesson("التخطيط السنوي", "اربط مواسم التلقيح والولادة والفطام والتسويق بمواعيد توفر المرعى والأعلاف والعمالة. ضع خطة مشتريات ومخزون أمان ومواعيد لمراجعة الميزانية والحالة الجسمية والصحة.")
    )),
    EncyclopediaChapter("إدارة المزرعة والتحسين المستمر", "تنظيم العمليات وتحليل السجلات ورفع كفاءة القطيع.", listOf(
        EncyclopediaLesson("خطة تشغيل", "ضع تقويماً سنوياً للتلقيح والولادة والفطام والتحصين والفحوصات وتنظيف الحظائر وصيانة المعدات. عيّن مسؤولية لكل مهمة وسجّل ما تم فعلياً وأسباب التأخير."),
        EncyclopediaLesson("مراجعة البيانات", "راجع أسبوعياً الحيوانات المريضة والولادات والنفوق والأوزان والمخزون، وشهرياً التكاليف والإيرادات ومؤشرات الأداء. صحح السجلات مع الاحتفاظ بتاريخ التعديل."),
        EncyclopediaLesson("إدارة المخزون", "استخدم الوارد والمنصرف والرصيد الفعلي وتاريخ الشراء والصلاحية ورقم التشغيلة. افصل المواد غير الصالحة وخزّن الأدوية وفق درجات الحرارة والضوء المحددين على العبوة."),
        EncyclopediaLesson("التحسين المبني على الدليل", "غيّر عاملاً واحداً قدر الإمكان ثم قِس أثره، مثل تعديل العليقة أو الرعي أو الفطام. دوّن الظروف المصاحبة حتى لا تنسب التحسن إلى سبب غير صحيح، واستعن بالمختصين في القرارات عالية الأثر.")
    ))
)

@Composable
private fun EncyclopediaScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    val filtered = encyclopediaChapters.mapIndexedNotNull { index, chapter ->
        val matches = search.isBlank() || chapter.title.contains(search, true) ||
            chapter.summary.contains(search, true) ||
            chapter.lessons.any { it.heading.contains(search, true) || it.body.contains(search, true) }
        if (matches) index to chapter else null
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Forest, shape = RoundedCornerShape(14.dp)) {
                    Text("📕", fontSize = 27.sp, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("موسوعة مواشي العلمية", color = Forest, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("مرجع عملي متدرج لتربية الأغنام", color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = onBack) { Text("الرئيسية", color = Forest, fontSize = 11.sp) }
            }
        }
        item {
            Surface(color = Forest, shape = RoundedCornerShape(21.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("المعرفة الصحيحة تصنع قطيعاً أقوى", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("10 أبواب تعليمية تشمل التغذية والتكاثر والولادة والصحة والإدارة والاقتصاد، مع خطوات عملية وتنبيهات واضحة.", color = Color.White.copy(alpha = .9f), fontSize = 11.sp, lineHeight = 19.sp)
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("ابحث في أبواب الموسوعة") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Forest) },
                shape = RoundedCornerShape(15.dp)
            )
        }
        itemsIndexed(filtered) { _, item ->
            val (index, chapter) = item
            val isExpanded = expanded == index
            Card(
                onClick = { expanded = if (isExpanded) -1 else index },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp),
                colors = CardDefaults.cardColors(containerColor = Ivory),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Forest.copy(alpha = .09f), shape = RoundedCornerShape(11.dp)) {
                            Text(String.format("%02d", index + 1), color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(chapter.summary, color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
                        }
                        Icon(if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = Forest)
                    }
                    if (isExpanded) {
                        Divider(color = Line)
                        chapter.lessons.forEach { lesson ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(lesson.heading, color = Forest, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(lesson.body, color = Ink, fontSize = 11.sp, lineHeight = 19.sp)
                            }
                        }
                        Surface(color = Cream, shape = RoundedCornerShape(13.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("تنبيه مهني", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("المعلومات تعليمية عامة وليست تشخيصاً أو وصفة علاجية. تختلف القرارات حسب السلالة والوزن والعمر والحالة الإنتاجية والأمراض المحلية وتحليل العلف. في المرض أو الولادة المتعسرة أو اختيار دواء وجرعته، ارجع إلى الطبيب البيطري والملصق المعتمد.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Ivory, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("المراجع العلمية للتحرير", color = Forest, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("• FAO – مراجع إدارة وإنتاج الأغنام وصحة الحيوان والأمن الحيوي.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                    Text("• National Research Council (NRC), Nutrient Requirements of Small Ruminants, 2007.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                    Text("• Feedipedia – INRAE, CIRAD, AFZ and FAO: بيانات تركيب المواد العلفية وتباينها.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                    Text("• Merck Veterinary Manual – مبادئ الأمراض والتشخيص والرعاية البيطرية للأغنام.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                    Text("• إرشادات الجامعات والخدمات الإرشادية البيطرية والزراعية، مع أولوية البروتوكولات المحلية.", color = Ink, fontSize = 10.sp, lineHeight = 17.sp)
                    Text("يجب مراجعة الإرشادات المحلية وتحديث المحتوى دورياً؛ هذه الصفحة ليست بديلاً عن الطبيب البيطري أو تحليل المختبر.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}
