package dz.mawashi.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private const val FIN_PREFS = "mawashi_finance_v24"
private const val FIN_INVOICES = "invoices"
private const val FIN_MEDICINES = "medicine_stock"
private const val FIN_WOOL = "wool_stock"
private const val FIN_OTHER = "other_entries"
private val FinGreen = Color(0xFF526B55)
private val FinCream = Color(0xFFF7F3EA)
private val FinInk = Color(0xFF27332A)
private val FinMuted = Color(0xFF778078)
private val FinLine = Color(0xFFE8E4DA)

internal data class FinanceInvoice(val id:String,val type:String,val category:String,val party:String,val item:String,val quantity:Double,val unit:String,val unitPrice:Double,val total:Double,val cost:Double,val timestamp:String,val notes:String)

private fun readInvoices(c:Context):List<FinanceInvoice>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(FIN_INVOICES,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);FinanceInvoice(x.getString("id"),x.getString("type"),x.optString("category"),x.optString("party"),x.optString("item"),x.optDouble("quantity"),x.optString("unit"),x.optDouble("unitPrice"),x.optDouble("total"),x.optDouble("cost"),x.optString("timestamp"),x.optString("notes"))}.getOrNull()}.sortedByDescending{it.timestamp}
}
private fun writeInvoice(c:Context,v:FinanceInvoice){
 val p=c.getSharedPreferences(FIN_PREFS,0);val a=JSONArray(p.getString(FIN_INVOICES,"[]")?:"[]")
 a.put(JSONObject().apply{put("id",v.id);put("type",v.type);put("category",v.category);put("party",v.party);put("item",v.item);put("quantity",v.quantity);put("unit",v.unit);put("unitPrice",v.unitPrice);put("total",v.total);put("cost",v.cost);put("timestamp",v.timestamp);put("notes",v.notes)})
 check(p.edit().putString(FIN_INVOICES,a.toString()).commit())
}
internal data class MedicineLot(val item:String,val batch:String,val expiry:String,val quantity:Double,val supplier:String,val receivedAt:String)

internal fun isMedicineLotUsable(expiry:String,today:LocalDate):Boolean {
 val expiryDate=runCatching{LocalDate.parse(expiry)}.getOrNull()?:return false
 return !expiryDate.isBefore(today)
}

internal fun usableMedicineStock(item:String,legacyStock:Double,lots:List<MedicineLot>,today:String):Double {
 val todayDate=runCatching{LocalDate.parse(today)}.getOrNull()?:return 0.0
 val matching=lots.filter{it.item==item}
 if(matching.isEmpty()) return if(legacyStock.isFinite()) legacyStock.coerceAtLeast(0.0) else 0.0
 return matching.filter{isMedicineLotUsable(it.expiry,todayDate)&&it.quantity.isFinite()&&it.quantity>0.0}.sumOf{it.quantity}
}
private const val MEDICINE_LOTS_KEY="medicine_stock_lots_v29"
private fun readMedicineLots(c:Context):List<MedicineLot>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(MEDICINE_LOTS_KEY,"[]")?:"[]")
 return (0 until a.length()).mapNotNull{i->runCatching{val x=a.getJSONObject(i);MedicineLot(x.optString("item"),x.optString("batch"),x.optString("expiry"),x.optDouble("quantity").coerceAtLeast(0.0),x.optString("supplier"),x.optString("receivedAt"))}.getOrNull()}
}
private fun saveMedicineLots(c:Context,lots:List<MedicineLot>){
 val a=JSONArray();lots.forEach{x->a.put(JSONObject().put("item",x.item).put("batch",x.batch).put("expiry",x.expiry).put("quantity",x.quantity.coerceAtLeast(0.0).takeIf{it.isFinite()}?:0.0).put("supplier",x.supplier).put("receivedAt",x.receivedAt))}
 check(c.getSharedPreferences(FIN_PREFS,0).edit().putString(MEDICINE_LOTS_KEY,a.toString()).commit())
}
private fun consumeMedicineLots(c:Context,item:String,quantity:Double){
 if(!quantity.isFinite()||quantity<=0.0) return
 var remaining=quantity
 val lots=readMedicineLots(c).toMutableList()
 val today=LocalDate.now()
 val indices=lots.indices.filter{lots[it].item==item&&lots[it].quantity.isFinite()&&lots[it].quantity>0.0&&isMedicineLotUsable(lots[it].expiry,today)}
  .sortedBy{LocalDate.parse(lots[it].expiry)}
 for(i in indices){if(remaining<=1e-8)break;val old=lots[i];val used=minOf(old.quantity,remaining);lots[i]=old.copy(quantity=old.quantity-used);remaining-=used}
 saveMedicineLots(c,lots)
}
private fun readMedicineStock(c:Context):MutableMap<String,Double>{
 val a=JSONArray(c.getSharedPreferences(FIN_PREFS,0).getString(FIN_MEDICINES,"[]")?:"[]");val m=mutableMapOf<String,Double>()