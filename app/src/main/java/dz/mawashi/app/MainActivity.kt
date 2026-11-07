package dz.mawashi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val Cream = Color(0xFFF7F3EA)
private val Green = Color(0xFF526B55)
private val Ink = Color(0xFF27332A)
private val Muted = Color(0xFF7C8279)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MawashiApp() } }
}
private data class Section(val title:String,val subtitle:String,val icon:ImageVector)
private val sections = listOf(
    Section("القطيع","إدارة الحيوانات والمواليد",Icons.Outlined.Pets),
    Section("الأدوية والتطعيمات","العلاجات ومواعيد التحصين",Icons.Outlined.Vaccines),
    Section("مخزون العلف","الكميات والاستهلاك والتنبيهات",Icons.Outlined.Inventory2),
    Section("المالية","المداخيل والمصاريف والأرباح",Icons.Outlined.AccountBalanceWallet)
)
@Composable private fun MawashiApp() {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    MaterialTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor= Cream, bottomBar={ NavigationBar(containerColor=Color.White) {
                sections.forEachIndexed { i,s -> NavigationBarItem(selected==i,{selected=i},{Icon(s.icon,null)},label={Text(s.title)}) }
            } }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp, vertical=18.dp)) {
                    Text("مواشي",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Ink)
                    Text("إدارة مزرعتك بكل سهولة",color=Muted)
                    Spacer(Modifier.height(22.dp))
                    if(selected<0) {
                        Surface(color=Green,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) { Text("مرحباً بك في مواشي",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge); Spacer(Modifier.height(6.dp)); Text("كل ما تحتاجه لمتابعة قطيعك ومزرعتك في مكان واحد.",color=Color(0xFFE7EDE5)) }
                        }
                        Spacer(Modifier.height(20.dp)); Text("أقسام المزرعة",fontWeight=FontWeight.Bold,color=Ink); Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(columns=GridCells.Fixed(2),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            items(sections.size) { i -> SectionCard(sections[i]) }
                        }
                    } else {
                        val s=sections[selected]; Text(s.title,style=MaterialTheme.typography.titleLarge,color=Ink); Spacer(Modifier.height(12.dp))
                        Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) { Icon(s.icon,null,tint=Green,modifier=Modifier.size(32.dp)); Spacer(Modifier.height(10.dp)); Text(s.title,fontWeight=FontWeight.Bold,color=Ink); Text(s.subtitle,color=Muted); Spacer(Modifier.height(12.dp)); Text("سيتم تجهيز أدوات هذا القسم في مراحل التطوير التالية.",color=Muted) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun SectionCard(s:Section) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp),modifier=Modifier.height(142.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Surface(color=Cream,shape=RoundedCornerShape(12.dp)) { Icon(s.icon,null,tint=Green,modifier=Modifier.padding(9.dp).size(24.dp)) }
            Column { Text(s.title,color=Ink,fontWeight=FontWeight.Bold); Text(s.subtitle,color=Muted,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
