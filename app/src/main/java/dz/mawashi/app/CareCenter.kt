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

@Composable
internal fun CareCenterScreen(modifier: Modifier = Modifier) {
 var page by rememberSaveable { mutableStateOf("home") }
 when (page) {
  "vaccines" -> CareVaccineScreen(modifier) { page = "home" }
  "tasks" -> CareTasksScreen(modifier) { page = "home" }
  "health" -> HerdHealthMonitor(modifier) { page = "home" }
  "lambs" -> LambPreventionScreen(modifier) { page = "home" }
  "dry_ewes" -> DryEweProgramScreen(modifier) { page = "home" }
  "pregnant_ewes" -> PregnantEweProgramScreen(modifier) { page = "home" }
  "lactating_ewes" -> LactatingEweProgramScreen(modifier) { page = "home" }
  "rams" -> RamProgramScreen(modifier) { page = "home" }
  else -> CareHomeScreen(modifier, { page = "vaccines" }, { page = "tasks" }, { page = "health" }, { page = "lambs" }, { page = "dry_ewes" }, { page = "pregnant_ewes" }, { page = "lactating_ewes" }, { page = "rams" })
 }
}

@Composable
private fun CareHomeScreen(modifier: Modifier, vaccines: () -> Unit, tasks: () -> Unit, health: () -> Unit, lambs: () -> Unit, dryEwes: () -> Unit, pregnantEwes: () -> Unit, lactatingEwes: () -> Unit, rams: () -> Unit) {
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
   item { CareTile("برنامج وقاية الحملان", "الرعاية والفحوصات واللقاحات من الولادة حتى الفطام", Icons.Outlined.Vaccines, lambs) }
   item { CareTile("برنامج النعاج الجافة", "الرعاية والتحصينات من الفطام حتى الاستعداد للتلقيح", Icons.Outlined.Vaccines, dryEwes) }
   item { CareTile("برنامج النعاج الحوامل", "المراحل الخمس والسجل الوقائي وربط المواليد بالأم", Icons.Outlined.Favorite, pregnantEwes) }
   item { CareTile("برنامج النعاج المرضعة", "الرعاية بعد الولادة ومتابعة الضرع والرضاعة والفطام", Icons.Outlined.FavoriteBorder, lactatingEwes) }
   item { CareTile("برنامج الفحول", "الرعاية والتحصينات والخصوبة ومتابعة جودة النسل", Icons.Outlined.Favorite, rams) }
   item { CareTile("جدول الرعاية والمهام", "مواعيد اليوم والقادمة والمتأخرة والمنجزة", Icons.Outlined.EventNote, tasks) }
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
 var selectedTags by remember { mutableStateOf(setOf<String>()) }
 var dialogOpen by rememberSaveable { mutableStateOf(false) }
 var dialogStepId by rememberSaveable { mutableStateOf("") }
 var dialogTags by rememberSaveable { mutableStateOf(listOf<String>()) }
 var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
 var batchNotes by rememberSaveable { mutableStateOf("") }
 val today = LocalDate.now()
 val birthDates = animals.associate { animal -> animal.tag to runCatching { LocalDate.parse(animal.birthDate) }.getOrNull() }
 val completed = animals.sumOf { animal -> SMART_VACCINATION_STEPS.count { step -> smartVaccinationDone(animal.tag, step.id, records) } }
 val eligible = animals.filter { animal ->
  val birth = birthDates[animal.tag] ?: return@filter false
  val idx = SMART_VACCINATION_STEPS.indexOfFirst { !smartVaccinationDone(animal.tag, it.id, records) }
  idx >= 0 && smartVaccinationUnlocked(animal.tag, idx, records) && !smartVaccinationDueDate(birth, SMART_VACCINATION_STEPS[idx]).isAfter(today)
 }
 val currentStepsByTag = animals.associate { animal ->
  animal.tag to SMART_VACCINATION_STEPS.firstOrNull { step -> !smartVaccinationDone(animal.tag, step.id, records) }
 }
 val selectedEligible = eligible.filter { it.tag in selectedTags }
 val commonStepId = selectedEligible.mapNotNull { currentStepsByTag[it.tag]?.id }.distinct().singleOrNull()
 val commonStep = SMART_VACCINATION_STEPS.firstOrNull { it.id == commonStepId }
 val dueStepsCount = eligible.size
 val totalHeads = animals.count { birthDates[it.tag] != null }
 LaunchedEffect(Unit) {
  animals = loadHerd(c).filter { it.lifecycleStatus != "مؤرشف" }
  val fresh = readHerdCare(c)
  val cleaned = fresh.filterNot { it.kind == "تطعيم" && !it.completed }
  records = reconcileSmartVaccinationReminders(animals, cleaned, LocalDate.now())
  if (records != fresh) runCatching { saveHerdCare(c, records) }
 }
 fun openRecord(tags: List<String>, step: SmartVaccinationStep) {
  dialogTags = tags
  dialogStepId = step.id
  date = LocalDate.now().toString()
  batchNotes = ""
  dialogOpen = true
 }
 Column(modifier.fillMaxSize().background(CareCream).padding(14.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null, tint = CareGreen) }
   Column(Modifier.weight(1f)) {
    Text("برنامج التحصين المرحلي", color = CareInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    Text("مواشي تقود مراحل التحصين منذ الولادة", color = CareMuted, fontSize = 10.sp)
   }
  }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
   item {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CareLine)) {
     Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("ملخص برنامج القطيع", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
       CareStat("مواليد ببيانات ميلاد", totalHeads.toString(), Modifier.weight(1f))
       CareStat("مستحقون الآن", dueStepsCount.toString(), Modifier.weight(1f))
       CareStat("جرعات موثقة", completed.toString(), Modifier.weight(1f))
      }
      Text("يحسب مواشي الاستحقاق من تاريخ ميلاد كل رأس، ويعرض الخطوة التالية فقط بعد إكمال الخطوة السابقة.", color = CareMuted, fontSize = 10.sp)
     }
    }
   }
   item {
    Surface(color = CareGreen, shape = RoundedCornerShape(15.dp)) {
     Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text("مراحل البرنامج المعتمدة داخل التطبيق", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Text("① شهران • التسمم المعوي", color = Color.White, fontSize = 11.sp)
      Text("② ثلاثة أشهر • التسمم الدموي", color = Color.White, fontSize = 11.sp)
      Text("③ أربعة أشهر • الجدري ثم PPR ثم الحمى القلاعية", color = Color.White, fontSize = 11.sp)
      Text("توقيت هذه المراحل يحتاج اعتماداً بيطرياً محلياً قبل اعتباره برنامجاً طبياً نهائياً.", color = Color(0xFFE9EEE8), fontSize = 9.sp)
     }
    }
   }
   item {
    Row(verticalAlignment = Alignment.CenterVertically) {
     Text("الخطوة المطلوبة الآن", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
     Text("حدد الرؤوس المستحقة للتحصين الجماعي", color = CareMuted, fontSize = 9.sp)
    }
   }
   if (eligible.isEmpty()) item {
    Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, CareLine)) {
     Column(Modifier.padding(12.dp)) {
      Text("لا توجد خطوات تحصين مستحقة الآن", color = CareGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      Text("ستظهر الحيوانات تلقائياً عند حلول موعد المرحلة التالية واستيفاء شروطها.", color = CareMuted, fontSize = 10.sp)
     }
    }
   }
   items(animals, key = { it.tag }) { animal ->
    val birth = birthDates[animal.tag]
    val doneCount = SMART_VACCINATION_STEPS.count { smartVaccinationDone(animal.tag, it.id, records) }
    val idx = SMART_VACCINATION_STEPS.indexOfFirst { !smartVaccinationDone(animal.tag, it.id, records) }
    val step = if (idx >= 0) SMART_VACCINATION_STEPS[idx] else null
    val unlocked = step != null && smartVaccinationUnlocked(animal.tag, idx, records)
    val dueDate = if (birth != null && step != null) smartVaccinationDueDate(birth, step) else null
    val dueNow = unlocked && dueDate != null && !dueDate.isAfter(today)
    val isEligible = animal in eligible
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CareLine)) {
     Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text("🐑 ${animal.tag} • ${animal.kind}", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(if (birth != null) "تاريخ الميلاد: $birth • ${herdAnimalAgeLabel(animal)}" else "تاريخ الميلاد غير مسجل؛ لا يمكن حساب البرنامج", color = CareMuted, fontSize = 9.sp)
       }
       if (isEligible) Checkbox(selectedTags.contains(animal.tag), { checked -> selectedTags = if (checked) selectedTags + animal.tag else selectedTags - animal.tag })
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
       Text("التقدم", color = CareMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
       Text("$doneCount / ${SMART_VACCINATION_STEPS.size}", color = CareGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
      }
      LinearProgressIndicator(progress = { doneCount.toFloat() / SMART_VACCINATION_STEPS.size }, modifier = Modifier.fillMaxWidth(), color = CareGreen, trackColor = CareLine)
      if (step == null) {
       Text("اكتمل المسار الأساسي المسجل لهذا الحيوان", color = CareGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
      } else {
       Text("المرحلة ${step.stage} • ${step.title}", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
       if (!unlocked) {
        Text("🔒 تفتح بعد إكمال الخطوة السابقة", color = CareMuted, fontSize = 9.sp)
       } else if (dueDate != null && dueDate.isAfter(today)) {
        Text("📅 موعد الاستحقاق: $dueDate", color = CareMuted, fontSize = 10.sp)
       } else if (dueNow) {
        Text("مستحق الآن • موعد الاستحقاق $dueDate", color = Color(0xFFB88743), fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Button(onClick = { openRecord(listOf(animal.tag), step) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CareGreen)) {
         Text("تسجيل التطعيم")
        }
       }
      }
     }
    }
   }
   item {
    if (selectedEligible.isNotEmpty()) {
     Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CareLine)) {
      Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
       Text("التحصين الجماعي", color = CareInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
       Text("المحدد: ${selectedEligible.size} • ${commonStep?.title ?: "اختر رؤوساً لها نفس الخطوة الحالية"}", color = CareMuted, fontSize = 10.sp)
       Button(onClick = { if (commonStep != null) openRecord(selectedEligible.map { it.tag }, commonStep) }, enabled = commonStep != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CareGreen)) {
        Text("تسجيل الخطوة للحيوانات المحددة")
       }
       TextButton(onClick = { selectedTags = emptySet() }) { Text("إلغاء التحديد", color = CareGreen) }
      }
     }
    }
   }
   item {
    Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(13.dp)) {
     Text("ملاحظة علمية: هذا المسار نموذج تنظيمي أولي. الأعمار والتوافق والجرعات والفواصل يجب اعتمادها من الطبيب البيطري ونشرة كل منتج والبرنامج المحلي. لا يسجل التطبيق جرعة على أنها مكتملة إلا بعد تأكيد المربي إعطاءها فعلياً.", color = CareInk, fontSize = 9.sp, modifier = Modifier.padding(11.dp))
    }
   }
  }
 }
 if (dialogOpen) {
  val step = SMART_VACCINATION_STEPS.firstOrNull { it.id == dialogStepId }
  AlertDialog(
   onDismissRequest = { dialogOpen = false },
   title = { Text("تسجيل تنفيذ التحصين", color = CareInk, fontWeight = FontWeight.Bold) },
   text = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
     Text(step?.title.orEmpty(), color = CareInk, fontWeight = FontWeight.Bold)
     Text("الحيوانات: ${dialogTags.joinToString("، ")}", color = CareMuted, fontSize = 10.sp)
     MawashiDatePickerField(date, { date = it }, "تاريخ إعطاء اللقاح", Modifier.fillMaxWidth())
     OutlinedTextField(batchNotes, { batchNotes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم الدفعة أو ملاحظات (اختياري)") }, minLines = 2)
     Text("التسجيل يؤكد أن التطعيم أعطي فعلياً. لا تستخدم تاريخاً مستقبلياً.", color = CareMuted, fontSize = 9.sp)
    }
   },
   confirmButton = {
    Button(onClick = {
     val actualDate = runCatching { LocalDate.parse(date) }.getOrNull()
     val selectedStep = SMART_VACCINATION_STEPS.firstOrNull { it.id == dialogStepId }
     if (actualDate == null || actualDate.isAfter(LocalDate.now()) || selectedStep == null) {
      Toast.makeText(c, "أدخل تاريخاً صحيحاً غير مستقبلي", Toast.LENGTH_LONG).show()
     } else {
      val additions = dialogTags.map { tag -> HerdCareReminder(
       id = UUID.randomUUID().toString(), tag = tag, kind = "تحصين مرحلي",
       title = selectedStep.title, date = actualDate.toString(),
       notes = "SMART_VAX_DONE:${selectedStep.id}" + if (batchNotes.isBlank()) "" else "\n" + batchNotes.trim(),
       completed = true
      ) }
      val base = records.filterNot { it.kind == "تحصين مرحلي" && it.notes.startsWith("SMART_VAX_PENDING:") && it.tag in dialogTags }
      val reconciled = reconcileSmartVaccinationReminders(animals, base + additions, LocalDate.now())
      runCatching { saveHerdCare(c, reconciled) }.onSuccess {
       records = reconciled
       selectedTags = selectedTags - dialogTags.toSet()
       dialogOpen = false
       Toast.makeText(c, "تم توثيق التحصين وفتح الخطوة التالية عند استيفاء شروطها", Toast.LENGTH_LONG).show()
      }.onFailure { Toast.makeText(c, "تعذر حفظ سجل التحصين", Toast.LENGTH_LONG).show() }
     }
    }, colors = ButtonDefaults.buttonColors(containerColor = CareGreen)) { Text("تأكيد وحفظ") }
   },
   dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("إلغاء", color = CareGreen) } },
   containerColor = CareCream
  )
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
