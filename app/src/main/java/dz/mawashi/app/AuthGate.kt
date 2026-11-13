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
    var isVerified by remember { mutableStateOf(auth.currentUser?.isEmailVerified == true) }
    var showOwnerDashboard by rememberSaveable { mutableStateOf(true) }

    if (isVerified) {
        val signedIn = auth.currentUser
        if (signedIn != null && signedIn.email.equals(MAWASHI_OWNER_EMAIL, ignoreCase = true) && showOwnerDashboard) {
            OwnerDashboardScreen(user = signedIn, onContinue = { showOwnerDashboard = false })
        } else {
            onAuthenticated()
        }
    } else if (user != null) {
        EmailVerificationScreen(
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
                showOwnerDashboard = true
            }
        )
    } else {
        SignInScreen(
            auth = auth,
            onSignedIn = { signedInUser ->
                user = signedInUser
                isVerified = signedInUser.isEmailVerified
            }
        )
    }
}

@Composable
private fun SignInScreen(
    auth: FirebaseAuth,
    onSignedIn: (FirebaseUser) -> Unit
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
                    Text(if (isRegister) "بعد إنشاء الحساب اضغط زر إرسال رابط التأكيد ليصلك بريد إلكتروني." else "سجّل الدخول باستخدام بريدك الإلكتروني وكلمة المرور.")
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
                                                busy = false
                                                onSignedIn(current)
                                            }
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
    onVerified: () -> Unit,
    onSignOut: () -> Unit
) {
    var message by remember(user.uid) {
        mutableStateOf("اضغط «إرسال رابط التأكيد» لإرسال رسالة إلى ${user.email ?: "بريدك الإلكتروني"}، ثم افتح الرابط في الرسالة. بعد التأكيد ارجع إلى التطبيق واضغط «تحققت من بريدي».")
    }
    var busy by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("تأكيد البريد الإلكتروني", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(user.email ?: "بريدك الإلكتروني")
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    busy = true
                    message = ""
                    user.sendEmailVerification().addOnCompleteListener { result ->
                        busy = false
                        message = if (result.isSuccessful) {
                            "تم إرسال رابط التأكيد. افحص صندوق الوارد والرسائل غير المرغوب فيها، ثم اضغط الرابط وارجع إلى التطبيق."
                        } else {
                            result.exception?.localizedMessage ?: "تعذر إرسال رابط التأكيد. تحقق من اتصال الإنترنت ثم أعد المحاولة."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) CircularProgressIndicator() else Text("إرسال رابط التأكيد / إعادة الإرسال")
            }
            Button(
                onClick = {
                    busy = true
                    user.reload().addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful && FirebaseAuth.getInstance().currentUser?.isEmailVerified == true) {
                            onVerified()
                        } else {
                            message = if (result.isSuccessful) {
                                "لم يتم تأكيد البريد بعد. افتح رابط التأكيد الذي وصلك ثم حاول مجدداً."
                            } else {
                                "تعذر تحديث حالة الحساب. تحقق من الإنترنت وحاول مجدداً."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) CircularProgressIndicator() else Text("تحققت من بريدي")
            }
            TextButton(onClick = onSignOut, enabled = !busy) { Text("تسجيل الخروج") }
        }
    }
}
