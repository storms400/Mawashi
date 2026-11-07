package dz.mawashi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val Cream = Color(0xFFF7F3EA)
private val Forest = Color(0xFF526B55)
private val Ink = Color(0xFF27332A)
private val Muted = Color(0xFF7C8279)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MawashiApp() }
    }
}

private enum class FarmSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    HERD("القطيع", "إدارة الحيوانات والمواليد", Icons.Outlined.Pets),
    CARE("الأدوية والتطعيمات", "العلاجات ومواعيد التحصين", Icons.Outlined.Vaccines),
    FEED("مخزون العلف", "الكميات والاستهلاك والتنبيهات", Icons.Outlined.Inventory2),
    FINANCE("المالية", "المداخيل والمصاريف والأرباح", Icons.Outlined.AccountBalanceWallet)
}

@Composable
private fun MawashiApp() {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    MaterialTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Scaffold(
                containerColor = Cream,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        val items = listOf(
                            FarmSection.HERD,
                            FarmSection.CARE,
                            FarmSection.FEED,
                            FarmSection.FINANCE
                        )
                        items.forEachIndexed { index, section ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index },
                                icon = { Icon(section.icon, contentDescription = section.title) },
                                label = { Text(section.title) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "مواشي",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        text = "إدارة مزرعتك بكل سهولة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    Spacer(Modifier.height(22.dp))
                    if (selected == -1) {
                        WelcomePanel()
                        Spacer(Modifier.height(20.dp))
                        Text("أقسام المزرعة", fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(FarmSection.entries.size) { index ->
                                SectionCard(FarmSection.entries[index])
                            }
                        }
                    } else {
                        val section = FarmSection.entries[selected]
                        Text(section.title, style = MaterialTheme.typography.titleLarge, color = Ink)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Icon(section.icon, contentDescription = null, tint = Forest, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(section.title, fontWeight = FontWeight.Bold, color = Ink)
                                Text(section.subtitle, color = Muted)
                                Spacer(Modifier.height(12.dp))
                                Text("سيتم تجهيز أدوات هذا القسم في مراحل التطوير التالية.", color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel() {
    Surface(
        color = Forest,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("مرحباً بك في مواشي", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("كل ما تحتاجه لمتابعة قطيعك ومزرعتك في مكان واحد.",
                color = Color(0xFFE7EDE5))
        }
    }
}

@Composable
private fun SectionCard(section: FarmSection) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(142.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Cream, shape = RoundedCornerShape(12.dp)) {
                    Icon(section.icon, contentDescription = null, tint = Forest,
                        modifier = Modifier.padding(9.dp).size(24.dp))
                }
            }
            Column {
                Text(section.title, color = Ink, fontWeight = FontWeight.Bold)
                Text(section.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
