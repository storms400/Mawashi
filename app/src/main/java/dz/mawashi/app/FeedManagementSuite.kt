package dz.mawashi.app

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import java.io.FileOutputStream

private const val FS_PREFS = "mawashi_feed_stock"
private const val FS_INVOICES = "purchase_invoices"
private const val FS_LEDGER = "movement_ledger"
private const val FS_PASTURE = "pasture_records"
private val FS_GREEN = Color(0xFF526B55)
private val FS_CREAM = Color(0xFFF7F3EA)
private val FS_INK = Color(0xFF27332A)
private val FS_MUTED = Color(0xFF778078)
private val FS_LINE = Color(0xFFE8E4DA)

private data class FsLine(val kind: FeedKind, val quantity: Double, val unitPrice: Double) {
    val total: Double get() = quantity * unitPrice
}
private data class FsInvoice(val id: String, val date: String, val supplier: String, val total: Double, val paid: Double, val notes: String, val lines: List<FsLine>)
private data class FsMove(val id: String, val date: String, val type: String, val kind: FeedKind, val quantity: Double, val before: Double, val after: Double, val invoice: String, val note: String)

private fun fsPrefs(c: Context) = c.getSharedPreferences(FS_PREFS, 0)
private fun fsInvoices(c: Context): List<FsInvoice> {
    val a = JSONArray(fsPrefs(c).getString(FS_INVOICES, "[]") ?: "[]")
    return (0 until a.length()).mapNotNull { i -> runCatching {
        val x = a.getJSONObject(i); val rows = x.getJSONArray("lines")
        val lines = (0 until rows.length()).mapNotNull { j -> runCatching {
            val r = rows.getJSONObject(j); FsLine(FeedKind.valueOf(r.getString("kind")), r.getDouble("quantity"), r.getDouble("unitPrice"))
        }.getOrNull() }
        FsInvoice(x.getString("id"), x.getString("date"), x.optString("supplier"), x.optDouble("total"), x.optDouble("paid"), x.optString("notes"), lines)
    }.getOrNull() }.sortedByDescending { it.date }
}
private fun fsMoves(c: Context): List<FsMove> {
    val a = JSONArray(fsPrefs(c).getString(FS_LEDGER, "[]") ?: "[]")
    return (0 until a.length()).mapNotNull { i -> runCatching {
        val x = a.getJSONObject(i); FsMove(x.getString("id"), x.getString("date"), x.getString("type"),
            FeedKind.valueOf(x.getString("kind")), x.getDouble("quantity"), x.optDouble("before"),
            x.optDouble("after"), x.optString("invoice"), x.optString("note"))
    }.getOrNull() }.sortedByDescending { it.date }
}
internal fun readFeedPurchaseCostsFromSuite(c: Context): List<FeedPurchaseCost> =
    fsInvoices(c).flatMap { inv -> inv.lines.map { FeedPurchaseCost(it.kind.label, it.quantity, it.total, inv.date) } }

private fun fsMove(c: Context, move: FsMove) {
    val p = fsPrefs(c); val a = JSONArray(p.getString(FS_LEDGER, "[]") ?: "[]")
    a.put(JSONObject().put("id", move.id).put("date", move.date).put("type", move.type).put("kind", move.kind.name)
        .put("quantity", move.quantity).put("before", move.before).put("after", move.after).put("invoice", move.invoice).put("note", move.note))
    check(p.edit().putString(FS_LEDGER, a.toString()).commit())
}
private fun fsSavePurchase(c: Context, inv: FsInvoice) {
    require(inv.lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
    require(inv.lines.all { it.quantity.isFinite() && it.quantity > 0 && it.unitPrice.isFinite() && it.unitPrice >= 0 }) { "تحقق من الكميات والأسعار" }
    require(inv.paid in 0.0..inv.total) { "المبلغ المدفوع غير صالح" }
    val stock = loadFeedStocks(c).toMutableMap()
    val moves = inv.lines.map { line ->
        val before = stock[line.kind] ?: 0.0; val after = before + line.quantity; stock[line.kind] = after
        FsMove(UUID.randomUUID().toString(), inv.date, "إضافة شراء", line.kind, line.quantity, before, after, inv.id, "فاتورة شراء " + inv.id)
    }
    saveFeedStocks(c, stock)
    val p = fsPrefs(c); val a = JSONArray(p.getString(FS_INVOICES, "[]") ?: "[]"); val rows = JSONArray()
    inv.lines.forEach { rows.put(JSONObject().put("kind", it.kind.name).put("quantity", it.quantity).put("unitPrice", it.unitPrice)) }
    a.put(JSONObject().put("id", inv.id).put("date", inv.date).put("supplier", inv.supplier).put("total", inv.total)
        .put("paid", inv.paid).put("notes", inv.notes).put("lines", rows))
    check(p.edit().putString(FS_INVOICES, a.toString()).commit())
    moves.forEach { fsMove(c, it) }
    val fp = c.getSharedPreferences("mawashi_finance_v24", 0); val fa = JSONArray(fp.getString("invoices", "[]") ?: "[]")
    fa.put(JSONObject().put("id", inv.id).put("type", "purchase").put("category", "شراء الأعلاف")
        .put("party", inv.supplier.ifBlank { "غير محدد" }).put("item", "فاتورة أعلاف متعددة الأصناف")
        .put("quantity", inv.lines.sumOf { it.quantity }).put("unit", "متعدد").put("unitPrice", 0.0)
        .put("total", inv.total).put("cost", inv.total).put("timestamp", inv.date + "T" + LocalDateTime.now().toLocalTime())
        .put("notes", inv.notes))
    check(fp.edit().putString("invoices", fa.toString()).commit())
}
private fun fsConsume(c: Context, quantities: Map<FeedKind, Double>, note: String): String {
    val valid = quantities.filterValues { it.isFinite() && it > 0 }
    require(valid.isNotEmpty()) { "أدخل كمية استهلاك واحدة على الأقل" }
    val stock = loadFeedStocks(c).toMutableMap()
    val short = valid.filter { (k, q) -> q > (stock[k] ?: 0.0) + 1e-8 }
    require(short.isEmpty()) { "المخزون غير كافٍ: " + short.keys.joinToString("، ") { it.label } }
    val date = LocalDate.now().toString()
    val moves = valid.map { (k, q) ->
        val before = stock[k] ?: 0.0; val after = before - q; stock[k] = after
        FsMove(UUID.randomUUID().toString(), date, "استهلاك", k, q, before, after, "", note)
    }
    saveFeedStocks(c, stock); moves.forEach { fsMove(c, it) }
    // Keep the established accounting ledger in sync so finance reports value this consumption once.
    val legacy = c.getSharedPreferences("mawashi_feed_stock", 0)
    val legacyRows = JSONArray(legacy.getString("ledger", "[]") ?: "[]")
    valid.forEach { (kind, quantity) ->
        legacyRows.put(JSONObject().put("date", date).put("type", "consumption").put("kind", kind.name)
            .put("quantityKg", quantity).put("note", note))
    }
    check(legacy.edit().putString("ledger", legacyRows.toString()).commit())
    return "تم تسجيل الاستهلاك وخصم " + valid.size + " أصناف من المخزون"
}
private fun fsPasture(c: Context, kg: Double, group: String) {
    if (!kg.isFinite() || kg <= 0) return
    val p = fsPrefs(c); val a = JSONArray(p.getString(FS_PASTURE, "[]") ?: "[]")
    a.put(JSONObject().put("date", LocalDate.now().toString()).put("kg", kg).put("group", group))
    check(p.edit().putString(FS_PASTURE, a.toString()).commit())
    fsMove(c, FsMove(UUID.randomUUID().toString(), LocalDate.now().toString(), "مساهمة المرعى",
        FeedKind.ALFALFA, kg, 0.0, kg, "", group + " • لا تخصم من المخزون"))
}
private fun fsReverse(c: Context, m: FsMove): String {
    require(m.type != "تصحيح عكسي") { "هذه حركة تصحيح" }
    val stock = loadFeedStocks(c).toMutableMap(); val before = stock[m.kind] ?: 0.0
    require(m.type != "مساهمة المرعى") { "مساهمة المرعى لا تُعكس كحركة مخزون" }
    val after = if (m.type == "استهلاك") before + m.quantity else before - m.quantity
    require(after >= -1e-8) { "الرصيد الحالي لا يسمح بعكس هذه الحركة" }
    stock[m.kind] = after.coerceAtLeast(0.0); saveFeedStocks(c, stock)
    fsMove(c, FsMove(UUID.randomUUID().toString(), LocalDate.now().toString(), "تصحيح عكسي",
        m.kind, m.quantity, before, after.coerceAtLeast(0.0), m.invoice, "عكس الحركة " + m.id + " مع حفظ الأصل"))
    return "تم تسجيل تصحيح عكسي مع الاحتفاظ بالحركة الأصلية"
}

private enum class FsPage(val title: String) { STOCK("المخزون"), PURCHASE("إضافة مخزون"), INVOICES("الفواتير"), CONSUME("الاستهلاك"), CALC("الحاسبة"), LEDGER("السجل") }

@Composable
fun FeedManagementSuiteScreen(modifier: Modifier = Modifier) {
    val c = LocalContext.current
    var page by rememberSaveable { mutableStateOf(FsPage.STOCK) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val stock = remember(revision) { loadFeedStocks(c) }
    val herd = remember(revision) { activeHerdAnimals(loadHerd(c)) }
    val invoices = remember(revision) { fsInvoices(c) }
    val moves = remember(revision) { fsMoves(c) }
    Column(modifier.fillMaxSize().background(FS_CREAM).padding(horizontal = 15.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("إدارة المزرعة / الموارد", color = FS_MUTED, fontSize = 11.sp)
                Text("مخزون العلف", color = FS_INK, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("كل احتياجات القطيع في مكان واحد", color = FS_MUTED, fontSize = 11.sp)
            }
            Text("🌾", fontSize = 30.sp)
        }
        Spacer(Modifier.height(9.dp))
        Surface(color = FS_GREEN, shape = RoundedCornerShape(19.dp)) {
            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                Text("إجمالي المخزون المتوفر", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
                Text(String.format(Locale.US, "%.1f كغ / وحدة", stock.values.sum()), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("11 صنفاً • " + herd.size + " حيواناً في القطيع • " + invoices.size + " فاتورة شراء",
                    color = Color.White, fontSize = 10.sp)
            }
        }
        ScrollableTabRow(selectedTabIndex = FsPage.values().indexOf(page), containerColor = FS_CREAM,
            contentColor = FS_GREEN, edgePadding = 0.dp, divider = {}) {
            FsPage.values().forEach { tab -> Tab(page == tab, { page = tab }, text = { Text(tab.title, fontSize = 10.sp) }) }
        }
        when (page) {
            FsPage.STOCK -> FsStock(c, stock, moves, herd.size, { page = it })
            FsPage.PURCHASE -> FsPurchase(c) { message = it; revision++; page = FsPage.STOCK }
            FsPage.INVOICES -> FsInvoiceArchive(c, invoices)
            FsPage.CONSUME -> FsConsume(c, herd, stock) { message = it; revision++ }
            FsPage.CALC -> FsCalculator(stock, { page = it })
            FsPage.LEDGER -> FsLedger(c, moves) { message = it; revision++ }
        }
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("نتيجة العملية") },
        text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("حسناً") } }) }
}

@Composable
private fun FsStock(c: Context, stock: Map<FeedKind, Double>, moves: List<FsMove>, herdCount: Int, go: (FsPage) -> Unit) {
    val visibleKinds = FeedKind.values().filter { it.name != "MINERALS" }
    val low = visibleKinds.filter { (stock[it] ?: 0.0) in 0.000001..5.0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("أصناف المخزون", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        items(visibleKinds, key = { it.name }) { k ->
            val icon = when { k == FeedKind.BARLEY -> "🌾"; k == FeedKind.BRAN -> "🌿"; k == FeedKind.MAIZE -> "🌽"; k == FeedKind.WHEAT -> "🌾"; k == FeedKind.SOY -> "🫘"; k == FeedKind.STRAW -> "🌾"; k == FeedKind.ALFALFA -> "🍀"; k == FeedKind.OAT_HAY -> "🌾"; k == FeedKind.OATS -> "🌱"; k == FeedKind.MINERALS_YELLOW -> "🟡"; k == FeedKind.MINERALS_RED -> "🔴"; else -> "🌾" }
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 22.sp); Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.label, color = FS_INK, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("الرصيد: " + String.format(Locale.US, "%.2f", stock[k] ?: 0.0) + if (k.isMineral) " قطعة" else " كغ",
                            color = FS_MUTED, fontSize = 10.sp)
                    }
                    Text(if ((stock[k] ?: 0.0) <= 0.0001) "نفد" else if ((stock[k] ?: 0.0) <= 5.0) "منخفض" else "متوفر",
                        color = if ((stock[k] ?: 0.0) <= 5.0) Color(0xFF9A6720) else FS_GREEN, fontSize = 10.sp)
                }
            }
        }
        if (low.isNotEmpty()) item { Surface(color = Color(0xFFFFF1D9), shape = RoundedCornerShape(12.dp)) {
            Text("تنبيه انخفاض المخزون: " + low.joinToString("، ") { it.label }, Modifier.padding(11.dp), color = Color(0xFF81551A), fontSize = 10.sp)
        } }
        item { Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, FS_LINE)) {
            Column(Modifier.padding(12.dp)) {
                Text("🌱 مساهمة المرعى", color = FS_GREEN, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("تُسجل كغذاء طبيعي منفصل وتقلل المكمل المحسوب، ولا تخصم من المخزون ولا تحتسب مرتين.",
                    color = FS_MUTED, fontSize = 10.sp, lineHeight = 15.sp)
                Text("القطيع الحالي: " + herdCount + " حيواناً", color = FS_GREEN, fontSize = 10.sp)
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ go(FsPage.PURCHASE) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = FS_GREEN)) { Icon(Icons.Outlined.Add, null); Text("إضافة مخزون", fontSize = 10.sp) }
            OutlinedButton({ go(FsPage.CONSUME) }, Modifier.weight(1f)) { Icon(Icons.Outlined.Restore, null); Text("تسجيل استهلاك", fontSize = 10.sp) }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ go(FsPage.CALC) }, Modifier.weight(1f)) { Icon(Icons.Outlined.Calculate, null); Text("حساب العلف", fontSize = 10.sp) }
            OutlinedButton({ go(FsPage.LEDGER) }, Modifier.weight(1f)) { Icon(Icons.Outlined.ReceiptLong, null); Text("سجل العمليات", fontSize = 10.sp) }
        } }
        item { Text("آخر الحركات", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        items(moves.take(4), key = { it.id }) { FsMoveCard(it) }
    }
}

@Composable
private fun FsPurchase(c: Context, saved: (String) -> Unit) {
    val kinds = remember { FeedKind.values().filter { it.name != "MINERALS" } }
    var kind by rememberSaveable { mutableStateOf(FeedKind.BARLEY) }
    var qty by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var supplier by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val lines = remember { mutableStateListOf<FsLine>() }
    val total = lines.sumOf { it.total }
    val paidValue = paid.replace(',', '.').toDoubleOrNull() ?: 0.0
    val validPaid = paidValue.isFinite() && paidValue >= 0.0 && paidValue <= total

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("إضافة مخزون", color = FS_INK, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("اختر المواد المشتراة وسجّل كمياتها وأسعارها", color = FS_MUTED, fontSize = 10.sp)
                    }
                    Icon(Icons.Outlined.Inventory2, null, tint = FS_GREEN, modifier = Modifier.size(30.dp))
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختيار أصناف المخزون", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("11 صنفاً", color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    kinds.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { item ->
                                val icon = when {
                                    item == FeedKind.BARLEY || item == FeedKind.WHEAT || item == FeedKind.STRAW || item == FeedKind.OAT_HAY -> "🌾"
                                    item == FeedKind.BRAN -> "🌿"
                                    item == FeedKind.MAIZE -> "🌽"
                                    item == FeedKind.SOY -> "🫘"
                                    item == FeedKind.ALFALFA -> "🍀"
                                    item == FeedKind.OATS -> "🌱"
                                    item == FeedKind.MINERALS_YELLOW -> "🟡"
                                    item == FeedKind.MINERALS_RED -> "🔴"
                                    else -> "🌾"
                                }
                                Surface(Modifier.weight(1f).clickable { kind = item },
                                    color = if (kind == item) Color(0xFFE8F0E6) else Color(0xFFF8F7F2),
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, if (kind == item) FS_GREEN else FS_LINE)) {
                                    Column(Modifier.padding(vertical = 9.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(icon, fontSize = 21.sp)
                                        Text(item.label, color = FS_INK, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تفاصيل الصنف المحدد: " + kind.label, color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(qty, { qty = it }, Modifier.weight(1f), label = { Text(if (kind.isMineral) "الكمية (قطعة)" else "الكمية (كغ)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("سعر الوحدة دج") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    OutlinedButton(onClick = {
                        val q = qty.replace(',', '.').toDoubleOrNull()
                        val p = price.replace(',', '.').toDoubleOrNull()
                        if (q != null && q.isFinite() && q > 0 && p != null && p.isFinite() && p >= 0) {
                            lines.add(FsLine(kind, q, p)); qty = ""; price = ""
                        } else android.widget.Toast.makeText(c, "أدخل كمية وسعراً صحيحين", android.widget.Toast.LENGTH_SHORT).show()
                    }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddCircleOutline, null); Text("إضافة الصنف إلى المشتريات") }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المشتريات المضافة", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(lines.size.toString() + " بند", color = FS_GREEN, fontSize = 10.sp)
                    }
                    if (lines.isEmpty()) Text("لم تضف أصنافاً بعد.", color = FS_MUTED, fontSize = 10.sp)
                    lines.toList().forEachIndexed { index, line ->
                        Surface(color = Color(0xFFF8F8F4), shape = RoundedCornerShape(11.dp)) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.kind.label, color = FS_INK, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(line.quantity.toString() + (if (line.kind.isMineral) " قطعة" else " كغ") + " × " + line.unitPrice + " دج", color = FS_MUTED, fontSize = 9.sp)
                                }
                                Text(String.format(Locale.US, "%.0f دج", line.total), color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { lines.removeAt(index) }) { Icon(Icons.Outlined.DeleteOutline, "حذف") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معلومات الشراء", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(supplier, { supplier = it }, Modifier.fillMaxWidth(), label = { Text("المورّد (اختياري)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات اختيارية") }, minLines = 2)
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("فاتورة الشراء الناتجة", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("تُنشأ تلقائياً", color = FS_GREEN, fontSize = 9.sp)
                    }
                    Surface(color = Color(0xFFEFF4EC), shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("إجمالي قيمة المشتريات", color = FS_MUTED, fontSize = 10.sp)
                            Text(String.format(Locale.US, "%.0f دج", total), color = FS_GREEN, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(paid, { paid = it }, Modifier.fillMaxWidth(), label = { Text("المبلغ المدفوع (دج)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = paid.isNotBlank() && !validPaid)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المبلغ المتبقي", color = FS_MUTED, fontSize = 11.sp)
                                Text(String.format(Locale.US, "%.0f دج", (total - paidValue.coerceAtLeast(0.0)).coerceAtLeast(0.0)), color = Color(0xFF9A6B2F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("رقم الفاتورة وتاريخها ينشآن عند اعتماد الشراء.", color = FS_MUTED, fontSize = 9.sp)
                        }
                    }
                    Button(onClick = {
                        runCatching {
                            require(lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
                            require(validPaid) { "المدفوع يجب أن يكون بين صفر وإجمالي الفاتورة" }
                            val inv = FsInvoice("MF-" + System.currentTimeMillis(), LocalDate.now().toString(), supplier.trim(), total, paidValue, note.trim(), lines.toList())
                            fsSavePurchase(c, inv)
                            lines.clear(); qty = ""; price = ""; paid = ""; supplier = ""; note = ""
                            saved("تم اعتماد الشراء وتحديث المخزون وحفظ الفاتورة. رقمها: " + inv.id)
                        }.onFailure { android.widget.Toast.makeText(c, it.message ?: "تعذر حفظ الشراء", android.widget.Toast.LENGTH_LONG).show() }
                    }, Modifier.fillMaxWidth(), enabled = lines.isNotEmpty() && validPaid, colors = ButtonDefaults.buttonColors(containerColor = FS_GREEN)) {
                        Icon(Icons.Outlined.ReceiptLong, null); Spacer(Modifier.width(6.dp)); Text("تأكيد الشراء وحفظ الفاتورة")
                    }
                }
            }
        }
    }
}

@Composable
private fun FsConsume(c: Context, herd: List<HerdAnimal>, stock: Map<FeedKind, Double>, saved: (String) -> Unit) {
    val groups = listOf(
        "النعاج الجافة" to "🐑", "النعاج الحوامل" to "🐑",
        "النعاج المرضعة" to "🐑", "الكباش" to "🐏",
        "الحملان" to "🐑", "القطيع المختلط" to "🐏🐑"
    )
    var group by rememberSaveable { mutableStateOf("القطيع المختلط") }
    var pasture by rememberSaveable { mutableStateOf("متوسط") }
    var note by rememberSaveable { mutableStateOf("") }
    var expandedTag by rememberSaveable { mutableStateOf("") }
    val amounts = remember { mutableStateMapOf<FeedKind, String>() }
    val groupAnimals = when (group) {
        "النعاج الجافة" -> herd.filter { it.productionState == "نعجة جافة" }
        "النعاج الحوامل" -> herd.filter { it.productionState == "نعجة حامل" }
        "النعاج المرضعة" -> herd.filter { it.productionState == "نعجة مرضعة" }
        "الكباش" -> herd.filter { it.productionState == "كبش" }
        "الحملان" -> herd.filter { it.productionState == "حمل يرضع" || it.productionState == "خروف" }
        else -> herd
    }
    val inputs = feedInputsForAnimals(groupAnimals)
    val needsByAnimal = inputs.map { input -> input to SheepNutrition.calculate(input, pasture) }
    val needs = needsByAnimal.map { it.second }
    val ratios = linkedMapOf(
        FeedKind.BARLEY to 40.0, FeedKind.BRAN to 20.0,
        FeedKind.ALFALFA to 25.0, FeedKind.STRAW to 10.0, FeedKind.SOY to 5.0
    )
    val feedKinds = FeedKind.values().filter { it != FeedKind.MINERALS }
    val suggested = calculateSupplementUses(
        needs, ratios.map { (kind, share) -> FeedStock(kind, stock[kind] ?: 0.0, share) }
    ).associateBy { it.kind }
    val selectedIds = groupAnimals.map { it.id }
    LaunchedEffect(group, pasture, selectedIds) {
        amounts.clear()
        feedKinds.forEach { kind ->
            amounts[kind] = if (kind.isMineral) "0" else String.format(Locale.US, "%.3f", suggested[kind]?.asFedKg ?: 0.0)
        }
    }
    val totalDryMatter = needs.sumOf { it.dryMatterKg }
    val totalSupplement = needs.sumOf { it.supplementDryMatterKg }
    val pastureKg = needs.sumOf { it.dryMatterKg * it.pastureShare }
    val missingWeight = groupAnimals.size - inputs.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 1.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("تسجيل الاستهلاك", color = FS_INK, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("خلطة كل رأس أولاً، ثم تجميع احتياجات الفئة", color = FS_MUTED, fontSize = 10.sp)
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. اختيار فئة القطيع", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    groups.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { pair ->
                                FilterChip(selected = group == pair.first, onClick = { group = pair.first; expandedTag = "" },
                                    label = { Text(pair.second + " " + pair.first, fontSize = 9.sp) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الرؤوس في الفئة", color = FS_MUTED, fontSize = 10.sp)
                        Text(groupAnimals.size.toString() + " رأس", color = FS_GREEN, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    if (groupAnimals.isEmpty()) Text("لا توجد حيوانات مسجلة ضمن هذه الفئة حالياً.", color = Color(0xFF9A6720), fontSize = 10.sp)
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("2. الخلطة الغذائية المحسوبة", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("يحسب النظام الاحتياج لكل حيوان له وزن مسجل، مع مراعاة حالته ومرحلة الحمل وعدد الأجنة أو الحملان.", color = FS_MUTED, fontSize = 10.sp, lineHeight = 15.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الحيوانات المحسوبة", color = FS_MUTED, fontSize = 10.sp)
                        Text(inputs.size.toString() + " من " + groupAnimals.size, color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المادة الجافة المطلوبة", color = FS_MUTED, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%.2f كغ/يوم", totalDryMatter), color = FS_INK, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المكمل التقديري", color = FS_MUTED, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%.2f كغ مادة جافة/يوم", totalSupplement), color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (missingWeight > 0) Text("تنبيه: " + missingWeight + " حيواناً لا يملك وزناً صالحاً؛ لن يدخل في الحساب حتى تسجيل وزنه.", color = Color(0xFF9A6720), fontSize = 10.sp)
                }
            }
        }
        item {
            Text("تفاصيل الخلطات الفردية", color = FS_INK, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("افتح بطاقة الحيوان لمراجعة احتياجه وكمية كل مكوّن تقديرية.", color = FS_MUTED, fontSize = 9.sp)
        }
        items(needsByAnimal, key = { it.first.tag }) { pair ->
            val input = pair.first
            val need = pair.second
            val expanded = expandedTag == input.tag
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, FS_LINE),
                modifier = Modifier.fillMaxWidth().clickable { expandedTag = if (expanded) "" else input.tag }) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (input.state == "كبش") "🐏" else "🐑", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(input.tag, color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(input.weightKg.toString() + " كغ • " + input.state, color = FS_MUTED, fontSize = 9.sp)
                        }
                        Text(if (expanded) "إخفاء" else "الخلطة", color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("المادة الجافة: " + String.format(Locale.US, "%.0f غ/يوم", need.dryMatterKg * 1000) +
                        " • المكمل: " + String.format(Locale.US, "%.0f غ/يوم", need.supplementDryMatterKg * 1000), color = FS_GREEN, fontSize = 9.sp)
                    if (expanded) {
                        Divider(color = FS_LINE)
                        Text("الطاقة التقديرية: " + String.format(Locale.US, "%.1f ميغاجول", need.meMj) +
                            " • البروتين المستهدف: " + String.format(Locale.US, "%.0f غ", need.crudeProteinKg * 1000), color = FS_MUTED, fontSize = 9.sp)
                        Text("مساهمة المرعى التقديرية: " + (need.pastureShare * 100).toInt() + "%", color = FS_MUTED, fontSize = 9.sp)
                        calculateSupplementUses(listOf(need), ratios.map { (kind, share) ->
                            FeedStock(kind, stock[kind] ?: 0.0, share)
                        }).forEach { use ->
                            val icon = when (use.kind) {
                                FeedKind.BRAN, FeedKind.ALFALFA -> "🌿"
                                FeedKind.SOY -> "🫘"
                                FeedKind.MAIZE -> "🌽"
                                else -> "🌾"
                            }
                            Text(icon + " " + use.kind.label + ": " +
                                String.format(Locale.US, "%.0f غ/يوم", use.asFedKg * 1000), color = FS_GREEN, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("3. أصناف العلف وكميات الاستهلاك", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("الكميات المقترحة تُجمع من الحسابات الفردية، ويمكن تعديلها لتطابق ما قُدّم فعلياً.", color = FS_MUTED, fontSize = 9.sp)
                    feedKinds.forEach { kind ->
                        val icon = when (kind) {
                            FeedKind.BARLEY, FeedKind.STRAW, FeedKind.WHEAT, FeedKind.OAT_HAY, FeedKind.OATS -> "🌾"
                            FeedKind.BRAN, FeedKind.ALFALFA -> "🌿"
                            FeedKind.MAIZE -> "🌽"
                            FeedKind.SOY -> "🫘"
                            FeedKind.MINERALS_YELLOW, FeedKind.MINERALS_RED -> "🧂"
                            else -> "🌿"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 20.sp)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(kind.label, color = FS_INK, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                Text("المتوفر: " + String.format(Locale.US, "%.2f", stock[kind] ?: 0.0) +
                                    if (kind.isMineral) " قطعة" else " كغ", color = FS_MUTED, fontSize = 8.sp)
                            }
                            OutlinedTextField(value = amounts[kind] ?: "0",
                                onValueChange = { value -> amounts[kind] = value.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                modifier = Modifier.width(112.dp),
                                label = { Text(if (kind.isMineral) "قطعة" else "كمية كغ", fontSize = 8.sp) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        if (kind != feedKinds.last()) Divider(color = FS_LINE)
                    }
                }
            }
        }
        item {
            Surface(color = Color(0xFFEFF4EC), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("🌿 مساهمة المرعى", color = FS_GREEN, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("تُحتسب مساهمة المرعى ضمن احتياجات التغذية وتُسجل مستقلة؛ لا تُخصم من مخزون الفصة أو أي صنف.", color = FS_MUTED, fontSize = 9.sp, lineHeight = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("ضعيف", "متوسط", "جيد").forEach { value ->
                            FilterChip(pasture == value, { pasture = value }, label = { Text(value, fontSize = 9.sp) })
                        }
                    }
                    Text("الكمية المقدرة من المرعى: " + String.format(Locale.US, "%.2f كغ مادة جافة/يوم", pastureKg),
                        color = FS_GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات العملية") }, minLines = 2) }
        item {
            Surface(color = Color(0xFFF7F2E8), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("4. مراجعة الاستهلاك", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("سيُخصم فقط ما أدخلته وأكدت تقديمه فعلياً. راجع الأرصدة قبل الحفظ.", color = FS_MUTED, fontSize = 9.sp)
                    Text("الحساب الحالي تقديري وتوزيعي، وليس تركيبة متوازنة مثبتة مخبرياً. يجب التحقق من قيم الأعلاف والمرعى وحدود الحبوب والأملاح قبل اعتماد الخلطة غذائياً.",
                        color = Color(0xFF81551A), fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
        }
        item {
            Button(onClick = {
                runCatching {
                    val values = feedKinds.associateWith { kind ->
                        val raw = amounts[kind] ?: "0"
                        val value = raw.replace(',', '.').toDoubleOrNull() ?: error("تحقق من كمية " + kind.label)
                        require(value.isFinite() && value >= 0.0) { "الكمية غير صالحة: " + kind.label }
                        value
                    }
                    val result = fsConsume(c, values, note.ifBlank { "استهلاك " + groupAnimals.size + " حيواناً • " + group })
                    fsPasture(c, pastureKg, groupAnimals.size.toString() + " حيواناً • " + group)
                    saved(result + ". سُجلت مساهمة المرعى منفصلة دون خصم من المخزون.")
                }.onFailure { android.widget.Toast.makeText(c, it.message ?: "تعذر التسجيل", android.widget.Toast.LENGTH_LONG).show() }
            }, Modifier.fillMaxWidth(), enabled = groupAnimals.isNotEmpty() && inputs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FS_GREEN)) {
                Text("تأكيد الاستهلاك وحفظ العملية", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FsCalculator(stock: Map<FeedKind, Double>, go: (FsPage) -> Unit) {
    var category by rememberSaveable { mutableStateOf("نعاج جافة") }; var count by rememberSaveable { mutableStateOf("10") }
    var weight by rememberSaveable { mutableStateOf("45") }; var pasture by rememberSaveable { mutableStateOf("متوسط") }
    var stage by rememberSaveable { mutableStateOf("الأربعة أسابيع الأخيرة") }
    val n = count.toIntOrNull()?.coerceIn(1, 500) ?: 1
    val w = weight.replace(',', '.').toDoubleOrNull()?.coerceIn(2.0, 180.0) ?: 45.0
    val state = when (category) { "حامل بجنين واحد", "حامل بتوأم" -> "نعجة حامل"; "نعاج مرضعة" -> "نعجة مرضعة"; "الكباش" -> "كبش"; "حملان رضيعة" -> "حمل يرضع"; "حملان نامية" -> "خروف"; else -> "نعجة جافة" }
    val need = SheepNutrition.calculate(FeedAnimalInput("تقدير", w, state, stage, if (category == "حامل بتوأم") 2 else 1), pasture)
    val totalSupplement = need.supplementDryMatterKg * n
    val ration = listOf(FeedKind.BARLEY to 40.0, FeedKind.BRAN to 20.0, FeedKind.ALFALFA to 25.0, FeedKind.STRAW to 10.0, FeedKind.SOY to 5.0)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("الحاسبة الغذائية الذكية", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        item { Column { listOf("نعاج جافة", "حامل بجنين واحد", "حامل بتوأم", "نعاج مرضعة", "الكباش", "حملان رضيعة", "حملان نامية").chunked(2).forEach { row ->
            Row { row.forEach { v -> FilterChip(category == v, { category = v }, label = { Text(v, fontSize = 9.sp) }) } }
        } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(count, { count = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("عدد الرؤوس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(weight, { weight = it }, Modifier.weight(1f), label = { Text("متوسط الوزن كغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        } }
        if (state == "نعجة حامل") item { Row { listOf("بداية الحمل", "منتصف الحمل", "الأربعة أسابيع الأخيرة").forEach { v ->
            FilterChip(stage == v, { stage = v }, label = { Text(v, fontSize = 8.sp) })
        } } }
        item { Row { listOf("ضعيف", "متوسط", "جيد").forEach { v -> FilterChip(pasture == v, { pasture = v }, label = { Text(v, fontSize = 9.sp) }) } } }
        item { Surface(color = FS_GREEN, shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(12.dp)) {
            Text("الاحتياج اليومي التقديري • " + n + " رأس", color = Color.White, fontSize = 10.sp)
            Text(String.format(Locale.US, "%.0f غ مادة جافة/رأس", need.dryMatterKg * 1000), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("إجمالي المادة الجافة: " + String.format(Locale.US, "%.2f كغ", need.dryMatterKg * n) +
                " • المكمل: " + String.format(Locale.US, "%.2f كغ", totalSupplement), color = Color.White, fontSize = 10.sp)
            Text("الطاقة التقديرية: " + String.format(Locale.US, "%.1f ميغاجول/رأس", need.meMj) +
                " • البروتين المستهدف: " + String.format(Locale.US, "%.0f غ/رأس", need.crudeProteinKg * 1000), color = Color.White, fontSize = 10.sp)
        } } }
        item { Text("توزيع أولي للمكمل — ليس عليقة متوازنة معتمدة", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        items(ration) { (kind, share) ->
            val asFed = (totalSupplement * share / 100.0) / kind.dryMatter
            Row { Text(kind.label, Modifier.weight(1f), color = FS_INK, fontSize = 10.sp)
                Text(String.format(Locale.US, "%.0f غ/رأس • متاح %.1f كغ", asFed * 1000 / n, stock[kind] ?: 0.0),
                    color = if ((stock[kind] ?: 0.0) < asFed) Color(0xFF9A6720) else FS_GREEN, fontSize = 9.sp) }
        }
        item { Surface(color = Color(0xFFFFF1D9), shape = RoundedCornerShape(12.dp)) {
            Text("الحساب تقديري بنموذج مبسط وقيم عامة، وليس بديلاً عن تحليل الأعلاف والمرعى أو جداول السلالة والإنتاج. لا تغيّر العليقة فجأة، واطلب مراجعة مختص للحمل المتعدد والرضاعة.",
                Modifier.padding(10.dp), color = Color(0xFF81551A), fontSize = 9.sp, lineHeight = 14.sp)
        } }
        item { OutlinedButton({ go(FsPage.CONSUME) }, Modifier.fillMaxWidth()) { Text("الانتقال لتسجيل الاستهلاك") } }
    }
}

@Composable
private fun FsLedger(c: Context, moves: List<FsMove>, corrected: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("الكل") }
    val options = listOf("الكل", "إضافة شراء", "استهلاك", "مساهمة المرعى", "تصحيح عكسي")
    val visible = moves.filter { filter == "الكل" || it.type == filter }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("سجل عمليات المخزون", color = FS_INK, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        item { Row { options.forEach { v -> FilterChip(filter == v, { filter = v }, label = { Text(v, fontSize = 8.sp) }) } } }
        items(visible, key = { it.id }) { m ->
            FsMoveCard(m)
            if (m.type != "تصحيح عكسي" && m.type != "مساهمة المرعى") TextButton(onClick = {
                runCatching { fsReverse(c, m) }.onSuccess { corrected(it) }
                    .onFailure { android.widget.Toast.makeText(c, it.message ?: "تعذر التصحيح", android.widget.Toast.LENGTH_LONG).show() }
            }) { Icon(Icons.Outlined.Replay, null); Text("تصحيح عكسي", fontSize = 9.sp) }
        }
        if (visible.isEmpty()) item { Text("لا توجد حركات مسجلة.", color = FS_MUTED, fontSize = 10.sp) }
    }
}

@Composable
private fun FsMoveCard(m: FsMove) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, FS_LINE)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row { Text(m.kind.label, Modifier.weight(1f), color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 10.sp); Text(m.type, color = FS_GREEN, fontSize = 9.sp) }
            Text(m.date + " • " + String.format(Locale.US, "%.2f", m.quantity) + if (m.kind.isMineral) " قطعة" else " كغ", color = FS_MUTED, fontSize = 9.sp)
            Text("قبل: " + String.format(Locale.US, "%.2f", m.before) + " • بعد: " + String.format(Locale.US, "%.2f", m.after), color = FS_MUTED, fontSize = 9.sp)
            if (m.invoice.isNotBlank()) Text("فاتورة: " + m.invoice, color = FS_GREEN, fontSize = 9.sp)
            if (m.note.isNotBlank()) Text(m.note, color = FS_MUTED, fontSize = 9.sp)
        }
    }
}


@Composable
private fun FsInvoiceArchive(c: Context, invoices: List<FsInvoice>) {
    var selected by remember { mutableStateOf<FsInvoice?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val invoice = selected
        if (uri != null && invoice != null) runCatching {
            val output = c.contentResolver.openOutputStream(uri) ?: error("تعذر إنشاء ملف PDF")
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = android.graphics.Color.rgb(39, 51, 42); textAlign = Paint.Align.RIGHT }
            var y = 55f
            paint.textSize = 25f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(82, 107, 85)
            canvas.drawText("MAWASHI | مواشي", 545f, y, paint); y += 42f
            paint.textSize = 20f; canvas.drawText("فاتورة شراء أعلاف", 545f, y, paint); y += 38f
            paint.textSize = 13f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.BLACK
            listOf("رقم الفاتورة: " + invoice.id, "التاريخ: " + invoice.date,
                "المورد: " + invoice.supplier.ifBlank { "غير محدد" }).forEach { canvas.drawText(it, 545f, y, paint); y += 25f }
            y += 12f
            paint.isFakeBoldText = true
            canvas.drawText("الصنف", 545f, y, paint); canvas.drawText("الكمية", 390f, y, paint)
            canvas.drawText("سعر الوحدة", 250f, y, paint); canvas.drawText("الإجمالي", 100f, y, paint); y += 22f
            paint.isFakeBoldText = false
            invoice.lines.forEach { line ->
                canvas.drawText(line.kind.label, 545f, y, paint)
                canvas.drawText(String.format(Locale.US, "%.2f", line.quantity), 390f, y, paint)
                canvas.drawText(String.format(Locale.US, "%.0f دج", line.unitPrice), 250f, y, paint)
                canvas.drawText(String.format(Locale.US, "%.0f دج", line.total), 100f, y, paint)
                y += 23f
            }
            y += 18f; paint.isFakeBoldText = true
            canvas.drawText("الإجمالي: " + String.format(Locale.US, "%.0f دج", invoice.total), 545f, y, paint); y += 26f
            paint.isFakeBoldText = false
            canvas.drawText("المدفوع: " + String.format(Locale.US, "%.0f دج", invoice.paid), 545f, y, paint); y += 24f
            canvas.drawText("المتبقي: " + String.format(Locale.US, "%.0f دج", (invoice.total - invoice.paid).coerceAtLeast(0.0)), 545f, y, paint); y += 28f
            if (invoice.notes.isNotBlank()) canvas.drawText("ملاحظات: " + invoice.notes.take(65), 545f, y, paint)
            pdf.finishPage(page)
            output.use { pdf.writeTo(it) }
            pdf.close()
            android.widget.Toast.makeText(c, "تم حفظ الفاتورة بصيغة PDF", android.widget.Toast.LENGTH_LONG).show()
        }.onFailure { android.widget.Toast.makeText(c, it.message ?: "تعذر إنشاء PDF", android.widget.Toast.LENGTH_LONG).show() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("أرشيف فواتير العلف", color = FS_INK, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        items(invoices, key = { it.id }) { invoice ->
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, FS_LINE)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(invoice.id, Modifier.weight(1f), color = FS_GREEN, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(invoice.date, color = FS_MUTED, fontSize = 9.sp)
                    }
                    Text("المورد: " + invoice.supplier.ifBlank { "غير محدد" }, color = FS_INK, fontSize = 10.sp)
                    Text(invoice.lines.joinToString("، ") { it.kind.label }, color = FS_MUTED, fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الإجمالي " + String.format(Locale.US, "%.0f دج", invoice.total), color = FS_GREEN, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("مدفوع " + String.format(Locale.US, "%.0f", invoice.paid) + " • متبقٍ " +
                                String.format(Locale.US, "%.0f دج", (invoice.total - invoice.paid).coerceAtLeast(0.0)),
                                color = FS_MUTED, fontSize = 9.sp)
                        }
                        TextButton(onClick = { printFsInvoice(c, invoice) }) { Icon(Icons.Outlined.Print, null); Text("طباعة", fontSize = 9.sp) }
                        TextButton(onClick = { selected = invoice; savePdf.launch(invoice.id + ".pdf") }) {
                            Icon(Icons.Outlined.PictureAsPdf, null); Text("حفظ PDF", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        if (invoices.isEmpty()) item { Text("لا توجد فواتير محفوظة بعد.", color = FS_MUTED, fontSize = 10.sp) }
    }
}


private class FsInvoicePrintAdapter(private val invoice: FsInvoice) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal, callback: LayoutResultCallback, metadata: android.os.Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(invoice.id + ".pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
    }
    override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; textAlign = Paint.Align.RIGHT }
            var y = 58f
            paint.textSize = 25f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(82, 107, 85)
            canvas.drawText("MAWASHI | مواشي", 545f, y, paint); y += 42f
            paint.textSize = 20f; canvas.drawText("فاتورة شراء أعلاف", 545f, y, paint); y += 38f
            paint.textSize = 13f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.BLACK
            listOf("رقم الفاتورة: " + invoice.id, "التاريخ: " + invoice.date,
                "المورد: " + invoice.supplier.ifBlank { "غير محدد" }).forEach { canvas.drawText(it, 545f, y, paint); y += 24f }
            y += 12f
            invoice.lines.forEach { line ->
                canvas.drawText(line.kind.label + " • " + String.format(Locale.US, "%.2f", line.quantity) +
                    " • " + String.format(Locale.US, "%.0f دج", line.unitPrice) + " • " +
                    String.format(Locale.US, "%.0f دج", line.total), 545f, y, paint)
                y += 25f
            }
            y += 20f; paint.isFakeBoldText = true
            canvas.drawText("الإجمالي: " + String.format(Locale.US, "%.0f دج", invoice.total), 545f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("المدفوع: " + String.format(Locale.US, "%.0f دج", invoice.paid), 545f, y, paint); y += 24f
            canvas.drawText("المتبقي: " + String.format(Locale.US, "%.0f دج", (invoice.total - invoice.paid).coerceAtLeast(0.0)), 545f, y, paint)
            pdf.finishPage(page)
            if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange(0, 0)))
        } catch (failure: Exception) {
            callback.onWriteFailed(failure.message)
        } finally { pdf.close() }
    }
}

private fun printFsInvoice(context: Context, invoice: FsInvoice) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("mawashi", "invoice", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
    manager.print(invoice.id, FsInvoicePrintAdapter(invoice), attributes)
}
