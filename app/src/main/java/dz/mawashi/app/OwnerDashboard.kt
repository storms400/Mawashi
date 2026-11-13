package dz.mawashi.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.google.firebase.auth.FirebaseUser
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val MAWASHI_OWNER_EMAIL = "mmsmmimo16@gmail.com"
private const val OWNER_API_BASE = "https://mawashi-otp-api.mmsmimo16.workers.dev"

private data class ManagedUser(
    val uid: String,
    val email: String,
    val verified: Boolean,
    val disabled: Boolean,
    val createdAt: String
)

private data class OwnerReport(
    val total: Int,
    val verified: Int,
    val pending: Int,
    val disabled: Int,
    val active7d: Int,
    val new7d: Int,
    val new30d: Int,
    val users: List<ManagedUser>
)

private fun fetchOwnerReport(user: FirebaseUser, callback: (OwnerReport?, String?) -> Unit) {
    user.getIdToken(true).addOnSuccessListener { tokenResult ->
        val token = tokenResult.token
        if (token.isNullOrBlank()) {
            callback(null, "تعذر تأكيد هوية المالك.")
            return@addOnSuccessListener
        }
        Thread {
            try {
                val connection = (URL("$OWNER_API_BASE/v1/admin/stats").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 30000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
                    throw IllegalStateException(if (error == "forbidden") "هذا الحساب غير مخوّل للوصول إلى لوحة المالك." else "تعذر جلب التقرير (HTTP $status).")
                }
                val json = JSONObject(raw)
                val array = json.optJSONArray("users")
                val users = buildList {
                    if (array != null) for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(ManagedUser(
                            uid = item.optString("uid"),
                            email = item.optString("email"),
                            verified = item.optBoolean("emailVerified"),
                            disabled = item.optBoolean("disabled"),
                            createdAt = item.optString("createdAt")
                        ))
                    }
                }
                val report = OwnerReport(
                    total = json.optInt("total_users"),
                    verified = json.optInt("verified_users"),
                    pending = json.optInt("pending_verification"),
                    disabled = json.optInt("disabled_users"),
                    active7d = json.optInt("active_last_7_days"),
                    new7d = json.optInt("new_last_7_days"),
                    new30d = json.optInt("new_last_30_days"),
                    users = users
                )
                Handler(Looper.getMainLooper()).post { callback(report, null) }
            } catch (failure: Exception) {
                Handler(Looper.getMainLooper()).post { callback(null, failure.message ?: "تعذر تحميل الإحصائيات.") }
            }
        }.start()
    }.addOnFailureListener { callback(null, "تعذر تأكيد جلسة المالك.") }
}

private fun setManagedUserDisabled(user: FirebaseUser, uid: String, disabled: Boolean, callback: (String?) -> Unit) {
    user.getIdToken(true).addOnSuccessListener { tokenResult ->
        val token = tokenResult.token
        if (token.isNullOrBlank()) {
            callback("تعذر تأكيد هوية المالك.")
            return@addOnSuccessListener
        }
        Thread {
            try {
                val connection = (URL("$OWNER_API_BASE/v1/admin/users/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use {
                    it.write(JSONObject().put("uid", uid).put("disabled", disabled).toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
                    throw IllegalStateException(if (error == "owner_protected") "لا يمكن تعطيل حساب المالك." else "تعذر تحديث الحساب (HTTP $status).")
                }
                Handler(Looper.getMainLooper()).post { callback(null) }
            } catch (failure: Exception) {
                Handler(Looper.getMainLooper()).post { callback(failure.message ?: "تعذر تحديث الحساب.") }
            }
        }.start()
    }.addOnFailureListener { callback("تعذر تأكيد جلسة المالك.") }
}

@Composable
fun OwnerDashboardScreen(user: FirebaseUser, onContinue: () -> Unit) {
    var report by remember { mutableStateOf<OwnerReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionUid by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        fetchOwnerReport(user) { data, failure ->
            loading = false
            report = data
            error = failure
        }
    }

    LaunchedEffect(user.uid) { refresh() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("لوحة مالك مواشي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("إدارة الحسابات ومتابعة نمو التطبيق", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("حساب المالك: ${user.email ?: MAWASHI_OWNER_EMAIL}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh() }, enabled = !loading, modifier = Modifier.weight(1f)) {
                    if (loading) CircularProgressIndicator() else Text("تحديث التقرير")
                }
                OutlinedButton(onClick = { onContinue() }, modifier = Modifier.weight(1f)) { Text("دخول التطبيق") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            report?.let { data ->
                Text("الإحصائيات العامة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("إجمالي الحسابات", data.total.toString(), Modifier.weight(1f))
                    MetricCard("حسابات موثقة", data.verified.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("بانتظار التحقق", data.pending.toString(), Modifier.weight(1f))
                    MetricCard("حسابات معطلة", data.disabled.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("نشطون آخر 7 أيام", data.active7d.toString(), Modifier.weight(1f))
                    MetricCard("جدد آخر 7 أيام", data.new7d.toString(), Modifier.weight(1f))
                }
                MetricCard("حسابات جديدة خلال 30 يوماً", data.new30d.toString(), Modifier.fillMaxWidth())
                Text("قائمة المشتركين (${data.users.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                data.users.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.email, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    item.disabled -> "الحساب معطّل"
                                    item.verified -> "البريد موثّق"
                                    else -> "بانتظار التحقق"
                                },
                                color = if (item.disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Text("معرّف الحساب: ${item.uid}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!item.verified && !item.disabled) {
                                Text("لم يُكمل المستخدم تحقق البريد بعد.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = {
                                    actionUid = item.uid
                                    setManagedUserDisabled(user, item.uid, !item.disabled) { failure ->
                                        actionUid = null
                                        if (failure != null) error = failure else refresh()
                                    }
                                },
                                enabled = actionUid == null && item.email.lowercase() != MAWASHI_OWNER_EMAIL
                            ) {
                                Text(if (actionUid == item.uid) "جارٍ التنفيذ..." else if (item.disabled) "إعادة تفعيل الحساب" else "تعطيل الحساب")
                            }
                        }
                    }
                }
                Text("تعرض هذه اللوحة بيانات حسابات Firebase فقط؛ لا تجمع محتوى ملفات المزارع أو بيانات الحيوانات الخاصة بالمستخدمين.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading && report == null) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
