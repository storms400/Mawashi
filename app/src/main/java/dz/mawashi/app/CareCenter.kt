package dz.mawashi.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.LocalDate
import java.util.UUID

private val CareGreen = Color(0xFF526B55)
private val CareCream = Color(0xFFF7F3EA)
private val CareInk = Color(0xFF27332A)
private val CareMuted = Color(0xFF778078)
private val CareLine = Color(0xFFE8E4DA)
private val CareVaccines = listOf(
 "لقاح الكلوستريديا والتسمم المعوي والكزاز (CD&T)",
 "لقاح الباستوريلا",
 "لقاح طاعون المجترات الصغيرة (PPR)",
 "لقاح جدري الأغنام",
 "لقاح الحمى القلاعية (FMD)",
 "لقاح البروسيلا (Rev.1) — وفق البرنامج الرسمي فقط"
)

@Composable
internal fun CareCenterScreen(modifier: Modifier = Modifier) {
 var page by rememberSaveable { mutableStateOf("home") }
 when (page) {
  "vaccines" -> CareVaccineScreen(modifier) { page = "home" }
  "tasks" -> CareTasksScreen(modifier) { page = "home" }
  "health" -> HerdHealthMonitor(modifier) { page = "home" }
  else -> CareHomeScreen(modifier, { page = "vaccines" }, { page = "tasks" }, { page = "health" })
 }
}

@Composable
private fun CareHomeScreen(modifier: Modifier, vaccines: () -> Unit, tasks: () -> Unit, health: () -> Unit) {
 val c = LocalContext.current
 var animals by remember { mutableStateOf(loadHerd(c).filter { it.lifecycleStatus != "مؤرشف" }) }
 var reminders by remember { mutableStateOf(readHerdCare(c)) }
 val today = LocalDate.now()
 val active = reminders.filter { !it.completed && animals.any { a -> a.tag.equals(it.tag, true) } }
 val overdue = active.count { runCatching { LocalDate.parse(it.date).isBefore(today) }.getOrDefault(false) }
 val todayCount = active.count { runCatching { LocalDate.parse(it.date) == today }.getOrDefault(false) }
 Column(modifier.fillMaxSize().background(CareCream).padding(16.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text("مركز الرعاية والمتابعة", color = CareInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    Text("متابعة صحية مترابطة مع ملفات القطيع", color = CareMuted, fontSize = 10.sp)
   }
   EweLogo(36)
  }
  Spacer(Modifier.height(12.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   CareStat("حيوانات نشطة", animals.size.toString(), Modifier.weight(1f))
   CareStat("مواعيد مفتوحة", active.size.toString(), Modifier.weight(1f))
   CareStat("متأخرة", overdue.toString(), Modifier.weight(1f))
  }
  Spacer(Modifier.height(7.dp))
  CareStat("مواعيد اليوم", todayCount.toString(), Modifier.fillMaxWidth())
  Spacer(Modifier.height(14.dp))
  Text("أقسام المركز", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
  Spacer(Modifier.height(8.dp))
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   item { CareTile("برنامج التطعيمات", "تسجيل المواعيد والجرعات وربطها بالحيوان", Icons.Outlined.Vaccines, vaccines) }
   item { CareTile("الفحوص والمتابعة الصحية", "الملف الصحي والوزن وتقييم حالة الجسم", Icons.Outlined.MonitorHeart, health) }
   item { CareTile("متابعة النمو والوزن", "قياسات الوزن وسجل التقييمات", Icons.Outlined.MonitorWeight, health) }
   item { CareTile("جدول الرعاية والمهام", "مواعيد اليوم والقادمة والمتأخرة والمنجزة", Icons.Outlined.EventNote, tasks) }
   item { CareTile("السجل الصحي للقطيع", "الرجوع إلى التاريخ الصحي لكل حيوان", Icons.Outlined.Assignment, health) }
  }
  LaunchedEffect(Unit) {
   animals = loadHerd(c).filter { it.lifecycleStatus != "مؤرشف" }
   reminders = readHerdCare(c)
  }
 }
}

@Composable
private fun CareStat(label: String, value: String, modifier: Modifier) {
 Surface(modifier, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CareLine)) {
  Column(Modifier.padding(11.dp)) {
   Text(value, color = CareGreen, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
   Text(label, color = CareMuted, fontSize = 9.sp)
  }
 }
}

@Composable
private fun CareTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
 Surface(Modifier.fillMaxWidth().clickable { action() }, color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, CareLine)) {
  Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
   Icon(icon, null, tint = CareGreen, modifier = Modifier.size(25.dp))
   Spacer(Modifier.width(10.dp))
   Column(Modifier.weight(1f)) {
    Text(title, color = CareInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text(subtitle, color = CareMuted, fontSize = 9.sp)
   }
   Icon(Icons.Outlined.ChevronLeft, null, tint = CareMuted)
  }
 }
}

@Composable
private fun CareVaccineScreen(modifier: Modifier, back: () -> Unit) {
 val c = LocalContext.current
 var animals by remember { mutableStateOf(loadHerd(c).filter { it.lifecycleStatus != "مؤرشف" }) }
 var records by remember { mutableStateOf(readHerdCare(c)) }
 var vaccine by rememberSaveable { mutableStateOf(CareVaccines.first()) }
 var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
 var notes by rememberSaveable { mutableStateOf("") }
 var search by rememberSaveable { mutableStateOf("") }
 var selected by remember { mutableStateOf(setOf<String>()) }
 val vaccineRecords = records.filter { it.kind == "تطعيم" }
 Column(modifier.fillMaxSize().background(CareCream).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null, tint = CareGreen) }
   Text("برنامج التطعيمات", color = CareInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
  }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   item {
    Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, CareLine)) {
     Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("إضافة موعد تطعيم", color = CareInk, fontWeight = FontWeight.Bold)
      Text("اختر اللقاح والحيوانات من القطيع. يحدد الطبيب البيطري توقيت الجرعات وفق نشرة المنتج والبرنامج المحلي.", color = CareMuted, fontSize = 10.sp)
      CareVaccines.forEach { name ->
       Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = vaccine == name, onClick = { vaccine = name })
        Text(name, color = CareInk, fontSize = 10.sp)
       }
      }
      MawashiDatePickerField(date, { date = it }, "تاريخ الموعد", Modifier.fillMaxWidth())
      OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("بحث برقم الحيوان") })
      Text("الحيوانات النشطة", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
      animals.filter { it.tag.contains(search.trim(), true) }.forEach { animal ->
       Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected.contains(animal.tag), { checked ->
         selected = if (checked) selected + animal.tag else selected - animal.tag
        })
        Column {
         Text(animal.tag + " • " + animal.kind, color = CareInk, fontSize = 11.sp)
         Text("العمر: " + herdAnimalAgeLabel(animal), color = CareMuted, fontSize = 9.sp)
        }
       }
      }
      OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات الطبيب / المنتج / رقم الدفعة") }, minLines = 2)
      Button(onClick = {
       val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
       val targets = animals.filter { selected.contains(it.tag) }
       if (!validDate || targets.isEmpty()) {
        Toast.makeText(c, "اختر حيواناً واحداً على الأقل وتاريخاً صحيحاً", Toast.LENGTH_LONG).show()
       } else {
        val additions = targets.map { a -> HerdCareReminder(UUID.randomUUID().toString(), a.tag, "تطعيم", vaccine, date, notes.trim(), false) }
        val merged = records + additions
        runCatching { saveHerdCare(c, merged) }.onSuccess {
         records = merged
         selected = emptySet()
         notes = ""
         Toast.makeText(c, "تم حفظ المواعيد وربطها بملفات الحيوانات", Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(c, "تعذر حفظ المواعيد", Toast.LENGTH_LONG).show() }
       }
      }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CareGreen)) {
       Text("حفظ مواعيد الحيوانات المختارة")
      }
     }
    }
   }
   item { Text("سجل التطعيمات", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
   if (vaccineRecords.isEmpty()) item { Text("لا توجد سجلات تطعيم بعد.", color = CareMuted, fontSize = 11.sp) }
   items(vaccineRecords.sortedBy { it.date }, key = { it.id }) { record ->
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CareLine)) {
     Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text(record.tag + " • " + record.title, color = CareInk, fontWeight = FontWeight.Bold, fontSize = 10.sp)
       Text(record.date + if (record.notes.isBlank()) "" else " • " + record.notes, color = CareMuted, fontSize = 9.sp)
      }
      Checkbox(record.completed, { done ->
       val changed = records.map { if (it.id == record.id) it.copy(completed = done) else it }
       runCatching { saveHerdCare(c, changed) }.onSuccess { records = changed }
      })
     }
    }
   }
  }
 }
}

@Composable
private fun CareTasksScreen(modifier: Modifier, back: () -> Unit) {
 val c = LocalContext.current
 var records by remember { mutableStateOf(readHerdCare(c)) }
 val activeAnimals = loadHerd(c).filter { it.lifecycleStatus != "مؤرشف" }
 val today = LocalDate.now()
 val tasks = records.filter { r -> activeAnimals.any { it.tag.equals(r.tag, true) } }
  .sortedWith(compareBy<HerdCareReminder> { it.completed }.thenBy { it.date })
 Column(modifier.fillMaxSize().background(CareCream).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null, tint = CareGreen) }
   Text("جدول الرعاية والمهام", color = CareInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
  }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   if (tasks.isEmpty()) item { Text("لا توجد مهام مسجلة حالياً.", color = CareMuted, fontSize = 11.sp) }
   items(tasks, key = { it.id }) { task ->
    val state = runCatching {
     val d = LocalDate.parse(task.date)
     when { task.completed -> "مكتمل"; d.isBefore(today) -> "متأخر"; d == today -> "اليوم"; else -> "قادم" }
    }.getOrDefault("تاريخ غير صالح")
    Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, CareLine)) {
     Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(task.completed, { done ->
       val changed = records.map { if (it.id == task.id) it.copy(completed = done) else it }
       runCatching { saveHerdCare(c, changed) }.onSuccess { records = changed }
      })
      Column(Modifier.weight(1f)) {
       Text(task.tag + " • " + task.title, color = CareInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
       Text(task.kind + " • " + task.date, color = CareMuted, fontSize = 9.sp)
       Text(state, color = if (state == "متأخر") Color(0xFFB54D42) else CareGreen, fontSize = 9.sp)
      }
     }
    }
   }
  }
 }
}
