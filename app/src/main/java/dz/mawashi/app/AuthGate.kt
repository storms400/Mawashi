package dz.mawashi.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

@Composable
fun AuthGate(onAuthenticated: @Composable () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    if (user?.isEmailVerified == true) {
        onAuthenticated()
    } else if (user != null) {
        EmailVerificationScreen(
            user = user!!,
            onRefresh = {
                user?.reload()?.addOnCompleteListener {
                    user = auth.currentUser
                }
            },
            onResend = { onResult -> user?.sendEmailVerification()?.addOnCompleteListener { onResult(it.isSuccessful, it.exception?.localizedMessage) } },
            onSignOut = {
                auth.signOut()
                user = null
            }
        )
    } else {
        SignInScreen(
            auth = auth,
            onSignedIn = { user = auth.currentUser }
        )
    }
}

@Composable
private fun SignInScreen(
    auth: FirebaseAuth,
    onSignedIn: () -> Unit
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
                                    val task = if (isRegister) {
                                        auth.createUserWithEmailAndPassword(cleanEmail, password)
                                    } else {
                                        auth.signInWithEmailAndPassword(cleanEmail, password)
                                    }
                                    task.addOnCompleteListener { result ->
                                        busy = false
                                        if (result.isSuccessful) {
                                            if (isRegister) {
                                                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { verification ->
                                                    if (verification.isSuccessful) {
                                                        onSignedIn()
                                                    } else {
                                                        message = "تم إنشاء الحساب، لكن تعذر إرسال رسالة التحقق. يمكنك طلب إعادة الإرسال من الشاشة التالية."
                                                        onSignedIn()
                                                    }
                                                }
                                            } else {
                                                onSignedIn()
                                            }
                                        } else {
                                            message = result.exception?.localizedMessage ?: "تعذر إتمام العملية. تحقق من الاتصال والبيانات."
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        if (busy) CircularProgressIndicator() else Text(if (isRegister) "إنشاء الحساب" else "دخول")
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
private fun EmailVerificationScreen(
    user: FirebaseUser,
    onRefresh: () -> Unit,
    onResend: ((Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var message by remember { mutableStateOf("أرسلنا رابط التحقق إلى ${user.email ?: "بريدك الإلكتروني"}. افتح الرسالة واضغط رابط التحقق، ثم عد إلى هنا.") }
    var busy by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("تحقق من بريدك الإلكتروني", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(message)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    busy = true
                    user.reload().addOnCompleteListener {
                        busy = false
                        onRefresh()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("تحققت من البريد، متابعة") }
            TextButton(
                onClick = {
                    busy = true
                    onResend { success, error ->
                        busy = false
                        message = if (success) "أُعيد إرسال رابط التحقق إلى بريدك." else (error ?: "تعذر إرسال رسالة التحقق.")
                    }
                },
                enabled = !busy
            ) { Text("إعادة إرسال رسالة التحقق") }
            TextButton(onClick = onSignOut, enabled = !busy) { Text("تسجيل الخروج") }
        }
    }
}
