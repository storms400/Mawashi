package dz.mawashi.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val OTP_API_BASE = "https://mawashi-otp-api.mmsmimo16.workers.dev"

private data class OtpResult(val ok: Boolean, val message: String)

private fun callOtpApi(user: FirebaseUser, path: String, code: String? = null, callback: (OtpResult) -> Unit) {
    user.getIdToken(true)
        .addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            if (idToken.isNullOrBlank()) {
                callback(OtpResult(false, "تعذر الحصول على جلسة تسجيل الدخول. أعد المحاولة."))
                return@addOnSuccessListener
            }
            Thread {
                val result = try {
                    val connection = (URL(OTP_API_BASE + path).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15000
                        readTimeout = 15000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val payload = if (code == null) JSONObject() else JSONObject().put("code", code)
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    connection.disconnect()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (status in 200..299) {
                        when (json?.optString("status")) {
                            "sent" -> OtpResult(true, "أرسلنا رمز التحقق إلى بريدك. تحقق من صندوق الوارد والرسائل غير المرغوب فيها.")
                            "verified", "already_verified" -> OtpResult(true, "تم التحقق من الحساب بنجاح.")
                            else -> OtpResult(true, "تم تنفيذ الطلب بنجاح.")
                        }
                    } else {
                        val error = json?.optString("error").orEmpty()
                        val message = when (error) {
                            "unauthorized" -> "انتهت جلسة الدخول. سجّل الدخول مجدداً."
                            "test_recipient_only" -> "الإرسال متاح حالياً للبريد التجريبي المحدد فقط."
                            "too_many_requests" -> "انتظر قليلاً قبل طلب رمز جديد."
                            "incorrect_code" -> "الرمز غير صحيح. حاول مرة أخرى."
                            "code_expired" -> "انتهت صلاحية الرمز. اطلب رمزاً جديداً."
                            "code_not_found" -> "لا يوجد رمز صالح. اطلب إرسال رمز جديد."
                            "too_many_attempts" -> "تجاوزت عدد المحاولات. اطلب رمزاً جديداً."
                            "email_delivery_failed" -> "تعذر إرسال البريد حالياً. حاول لاحقاً."
                            else -> "تعذر إكمال الطلب (HTTP $status). حاول مرة أخرى."
                        }
                        OtpResult(false, message)
                    }
                } catch (_: Exception) {
                    OtpResult(false, "تعذر الاتصال بخدمة التحقق. تحقق من الإنترنت ثم أعد المحاولة.")
                }
                Handler(Looper.getMainLooper()).post { callback(result) }
            }.start()
        }
        .addOnFailureListener {
            callback(OtpResult(false, "تعذر تأكيد جلسة الحساب. سجّل الدخول مجدداً."))
        }
}

@Composable
fun AuthGate(onAuthenticated: @Composable () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var isVerified by remember { mutableStateOf(auth.currentUser?.isEmailVerified == true) }

    if (isVerified) {
        onAuthenticated()
    } else if (user != null) {
        OtpVerificationScreen(
            user = user!!,
            onVerified = {
                val current = auth.currentUser
                current?.reload()?.addOnCompleteListener { result ->
                    if (result.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                        user = auth.currentUser
                        isVerified = true
                    }
                }
            },
            onSignOut = {
                auth.signOut()
                user = null
                isVerified = false
            }
        )
    } else {
        SignInScreen(
            auth = auth,
            onSignedIn = { signedInUser ->
                user = signedInUser
                isVerified = signedInUser.isEmailVerified
            },
            onOtpRequired = { signedInUser, callback ->
                callOtpApi(signedInUser, "/v1/otp/send") { result ->
                    user = signedInUser
                    isVerified = false
                    callback(result)
                }
            }
        )
    }
}

@Composable
private fun SignInScreen(
    auth: FirebaseAuth,
    onSignedIn: (FirebaseUser) -> Unit,
    onOtpRequired: (FirebaseUser, (OtpResult) -> Unit) -> Unit
) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("مواشي", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(if (isRegister) "أنشئ حسابك للبدء" else "سجّل الدخول إلى حسابك")
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isRegister) "حساب جديد" else "تسجيل الدخول", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("سنرسل رمز تحقق من 6 أرقام إلى بريدك الإلكتروني.")
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("البريد الإلكتروني") }
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("كلمة المرور") }
                    )
                    if (isRegister) {
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("تأكيد كلمة المرور") }
                        )
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            val cleanEmail = email.trim()
                            when {
                                cleanEmail.isBlank() || !cleanEmail.contains("@") -> message = "أدخل بريدًا إلكترونيًا صحيحًا."
                                password.length < 6 -> message = "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل."
                                isRegister && password != confirmation -> message = "كلمتا المرور غير متطابقتين."
                                else -> {
                                    busy = true
                                    message = null
                                    val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password)
                                               else auth.signInWithEmailAndPassword(cleanEmail, password)
                                    task.addOnCompleteListener { result ->
                                        if (!result.isSuccessful) {
                                            busy = false
                                            message = result.exception?.localizedMessage ?: "تعذر إتمام العملية. تحقق من البيانات والاتصال."
                                        } else {
                                            val current = auth.currentUser
                                            if (current == null) {
                                                busy = false
                                                message = "تعذر تحميل الحساب بعد تسجيل الدخول."
                                            } else if (current.isEmailVerified) {
                                                busy = false
                                                onSignedIn(current)
                                            } else {
                                                onOtpRequired(current) { otpResult ->
                                                    busy = false
                                                    onSignedIn(current)
                                                    if (!otpResult.ok) message = otpResult.message
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        if (busy) CircularProgressIndicator() else Text(if (isRegister) "إنشاء الحساب وإرسال الرمز" else "دخول")
                    }
                    if (!isRegister) {
                        TextButton(
                            onClick = {
                                val cleanEmail = email.trim()
                                if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                    message = "أدخل بريدك الإلكتروني أولاً لإرسال رابط إعادة تعيين كلمة المرور."
                                } else {
                                    busy = true
                                    auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { result ->
                                        busy = false
                                        message = if (result.isSuccessful) "أرسلنا رابط إعادة تعيين كلمة المرور إلى بريدك." else (result.exception?.localizedMessage ?: "تعذر إرسال الرابط.")
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            enabled = !busy
                        ) { Text("نسيت كلمة المرور؟") }
                    }
                    TextButton(
                        onClick = { isRegister = !isRegister; message = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = !busy
                    ) {
                        Text(if (isRegister) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب")
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpVerificationScreen(
    user: FirebaseUser,
    onVerified: () -> Unit,
    onSignOut: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("أدخل رمز التحقق المكوّن من 6 أرقام المرسل إلى ${user.email ?: "بريدك الإلكتروني"}.") }
    var busy by remember { mutableStateOf(false) }
    var sendEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(user.uid) {
        // On app restart, send a fresh code so the user can continue the pending registration.
        busy = true
        callOtpApi(user, "/v1/otp/send") { result ->
            busy = false
            sendEnabled = !result.ok
            message = result.message
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("التحقق من البريد الإلكتروني", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("أرسلنا رمزاً من 6 أرقام إلى ${user.email ?: "بريدك الإلكتروني"}. تنتهي صلاحيته خلال 10 دقائق.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { value -> code = value.filter { it.isDigit() }.take(6) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text("رمز التحقق") }
            )
            message?.let { Text(it, color = if (it.contains("بنجاح")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (code.length != 6) {
                        message = "أدخل الرمز المكوّن من 6 أرقام."
                    } else {
                        busy = true
                        callOtpApi(user, "/v1/otp/verify", code) { result ->
                            busy = false
                            message = result.message
                            if (result.ok) onVerified()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) CircularProgressIndicator() else Text("تأكيد الرمز")
            }
            TextButton(
                onClick = {
                    busy = true
                    callOtpApi(user, "/v1/otp/send") { result ->
                        busy = false
                        sendEnabled = !result.ok
                        message = result.message
                    }
                },
                enabled = !busy && sendEnabled
            ) { Text("إعادة إرسال الرمز") }
            TextButton(onClick = onSignOut, enabled = !busy) { Text("تسجيل الخروج") }
        }
    }
}
