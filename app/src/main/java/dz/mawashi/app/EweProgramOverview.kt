package dz.mawashi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EweProgramOverview(animalCount: Int, dueCount: Int, completedCount: Int, stages: List<Pair<String, String>>, animalLines: List<String>, onStageClick: (Int) -> Unit, onVaccinesClick: () -> Unit, onRemindersClick: () -> Unit) {
    val cream = Color(0xFFF7F3EA); val green = Color(0xFF526B55); val ink = Color(0xFF27332A); val muted = Color(0xFF778078); val line = Color(0xFFE8E4DA)
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp).background(cream).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OverviewStat("عدد النعاج", animalCount.toString(), Modifier.weight(1f), green, muted, line); OverviewStat("مهام مستحقة", dueCount.toString(), Modifier.weight(1f), Color(0xFFAA7040), muted, line); OverviewStat("إجراءات موثقة", completedCount.toString(), Modifier.weight(1f), green, muted, line) }
        Text("مراحل الرعاية", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        stages.forEachIndexed { index, stage -> Surface(Modifier.fillMaxWidth().clickable { onStageClick(index) }, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, line)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = Color(0xFFE8F0E5), shape = RoundedCornerShape(10.dp)) { Text((index + 1).toString(), Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(stage.first, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(stage.second, color = muted, fontSize = 11.sp) }; Icon(Icons.Outlined.ChevronLeft, null, tint = muted) } } }
        Text("المتابعة والتحصينات", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OverviewAction("التطعيمات والتحصينات", Icons.Outlined.Vaccines, Modifier.weight(1f), green, ink, line, onVaccinesClick); OverviewAction("المواعيد والتنبيهات", Icons.Outlined.EventNote, Modifier.weight(1f), green, ink, line, onRemindersClick) }
        Text("النعاج المسجلة في هذا البرنامج", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, line)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (animalLines.isEmpty()) Text("لا توجد نعاج في هذه الفئة حالياً. ستظهر تلقائياً عند تحديث حالتها في القطيع.", color = muted, fontSize = 12.sp) else animalLines.take(12).forEach { lineText -> Text("🐑  " + lineText, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }; if (animalLines.size > 12) Text("و " + (animalLines.size - 12) + " نعجة أخرى…", color = muted, fontSize = 11.sp) } }
        Surface(color = Color(0xFFEDE8DE), shape = RoundedCornerShape(12.dp)) { Text("تُحدّث هذه القائمة من القطيع مباشرة. لا تُنشأ بطاقات جديدة عند انتقال النعجة بين الحالات، وتبقى سجلاتها مرتبطة برقمها.", Modifier.padding(12.dp), color = ink, fontSize = 11.sp) }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, modifier: Modifier, valueColor: Color, muted: Color, line: Color) { Surface(modifier, color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, line)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(value, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = muted, fontSize = 10.sp) } } }

@Composable
private fun OverviewAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, green: Color, ink: Color, line: Color, action: () -> Unit) { Surface(modifier.clickable { action() }, color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, line)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Icon(icon, null, tint = green); Text(title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } }