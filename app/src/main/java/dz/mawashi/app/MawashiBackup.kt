package dz.mawashi.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class PreparedMawashiBackup(
    val createdAt: String,
    val isLegacyUnencrypted: Boolean,
    internal val stores: Map<String, Map<String, Any?>>
)

internal object MawashiBackup {
    private const val FORMAT = "mawashi-data-backup"
    private const val LEGACY_SCHEMA_VERSION = 1
    private const val SCHEMA_VERSION = 2
    private const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    private const val KDF_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KDF_NAME = "PBKDF2WithHmacSHA256"
    private val random = SecureRandom()
    private val storeNames = listOf(
        "mawashi_herd_local",
        "mawashi_feed_stock",
        "mawashi_finance_v24",
        "mawashi_herd_care_v27"
    )

    fun export(context: Context, output: OutputStream, passphrase: String? = null) {
        val password = passphrase?.takeIf { it.isNotBlank() }
        require(password == null || password.length >= 12) {
            "كلمة المرور يجب أن تتكون من 12 حرفاً على الأقل"
        }
        val storesJson = JSONObject()
        storeNames.forEach { name ->
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val encoded = JSONObject()
            values.forEach { (key, value) -> encoded.put(key, encodeValue(value)) }
            storesJson.put(name, encoded)
        }
        val payloadText = JSONObject()
            .put("app_family", "mawashi-v32")
            .put("stores", storesJson)
            .toString()

        val envelope = if (password == null) {
            // Password protection is optional. Keep the established v1 checksum format
            // for a portable backup that can be restored without entering a password.
            JSONObject()
                .put("format", FORMAT)
                .put("schema_version", LEGACY_SCHEMA_VERSION)
                .put("created_at", System.currentTimeMillis())
                .put("payload", payloadText)
                .put("sha256", sha256(payloadText))
        } else {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val key = deriveKey(password, salt, KDF_ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad())
            val encrypted = cipher.doFinal(payloadText.toByteArray(Charsets.UTF_8))
            JSONObject()
                .put("format", FORMAT)
                .put("schema_version", SCHEMA_VERSION)
                .put("created_at", System.currentTimeMillis())
                .put("kdf", KDF_NAME)
                .put("iterations", KDF_ITERATIONS)
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
        val backupBytes = envelope.toString(2).toByteArray(Charsets.UTF_8)
        require(backupBytes.size <= MAX_BACKUP_BYTES) {
            "حجم النسخة الاحتياطية يتجاوز الحد المسموح (25 ميغابايت)؛ لم يتم تصدير ملف"
        }
        output.write(backupBytes)
        output.flush()
    }

    fun requiresPassword(bytes: ByteArray): Boolean {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية غير صالح" }
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("format") == FORMAT) { "هذا الملف ليس نسخة احتياطية من تطبيق مواشي" }
        return when (envelope.optInt("schema_version", -1)) {
            LEGACY_SCHEMA_VERSION -> false
            SCHEMA_VERSION -> true
            else -> error("إصدار ملف النسخة الاحتياطية غير مدعوم")
        }
    }

    fun readBackupBytes(input: InputStream): ByteArray = input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية أكبر من الحد المسموح" }
            output.write(buffer, 0, count)
        }
        output.toByteArray().also { require(it.isNotEmpty()) { "ملف النسخة الاحتياطية فارغ" } }
    }

    fun prepareRestore(input: InputStream, passphrase: String? = null): PreparedMawashiBackup =
        prepareRestore(readBackupBytes(input), passphrase)

    fun prepareRestore(bytes: ByteArray, passphrase: String? = null): PreparedMawashiBackup {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "حجم ملف النسخة الاحتياطية غير صالح" }
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("format") == FORMAT) { "هذا الملف ليس نسخة احتياطية من تطبيق مواشي" }
        val version = envelope.optInt("schema_version", -1)
        val payloadText: String
        val legacy: Boolean
        when (version) {
            LEGACY_SCHEMA_VERSION -> {
                payloadText = envelope.optString("payload")
                require(payloadText.isNotBlank()) { "محتوى النسخة الاحتياطية غير موجود" }
                require(sha256(payloadText) == envelope.optString("sha256")) {
                    "فشل التحقق من سلامة النسخة الاحتياطية؛ لم يتم تغيير بياناتك"
                }
                legacy = true
            }
            SCHEMA_VERSION -> {
                require(!passphrase.isNullOrBlank()) { "أدخل كلمة مرور النسخة الاحتياطية" }
                require(envelope.optString("kdf") == KDF_NAME) { "طريقة حماية النسخة الاحتياطية غير مدعومة" }
                val iterations = envelope.optInt("iterations", -1)
                require(iterations in 100_000..500_000) { "إعدادات حماية الملف غير صالحة" }
                val salt = decodeBase64(envelope.optString("salt"), SALT_BYTES, "salt")
                val nonce = decodeBase64(envelope.optString("nonce"), NONCE_BYTES, "nonce")
                val ciphertext = try {
                    Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
                } catch (_: Exception) {
                    error("بيانات الملف المشفّر غير صالحة")
                }
                require(ciphertext.size >= 16 && ciphertext.size <= MAX_BACKUP_BYTES) { "حجم المحتوى المشفّر غير صالح" }
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
                    cipher.updateAAD(aad())
                    payloadText = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
                } catch (_: AEADBadTagException) {
                    throw IllegalArgumentException("كلمة المرور غير صحيحة أو تم العبث بملف النسخة الاحتياطية")
                } catch (failure: Exception) {
                    throw IllegalArgumentException("تعذر فك تشفير النسخة الاحتياطية", failure)
                }
                legacy = false
            }
            else -> error("إصدار ملف النسخة الاحتياطية غير مدعوم")
        }

        val payload = JSONObject(payloadText)
        require(payload.optString("app_family") == "mawashi-v32") { "النسخة الاحتياطية لا تخص إصدار مواشي v32" }
        val stores = payload.optJSONObject("stores") ?: error("بيانات الأقسام غير موجودة في النسخة الاحتياطية")
        val decoded = linkedMapOf<String, Map<String, Any?>>()
        storeNames.forEach { name ->
            val store = stores.optJSONObject(name) ?: error("النسخة الاحتياطية ناقصة: $name")
            val values = linkedMapOf<String, Any?>()
            val keys = store.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(key.isNotBlank()) { "يوجد مفتاح بيانات غير صالح" }
                val entry = store.optJSONObject(key) ?: error("قيمة غير صالحة في بيانات النسخة الاحتياطية")
                values[key] = decodeValue(entry)
            }
            decoded[name] = values
        }
        return PreparedMawashiBackup(envelope.optLong("created_at").toString(), legacy, decoded)
    }

    fun restore(context: Context, backup: PreparedMawashiBackup) {
        require(backup.stores.keys.containsAll(storeNames)) { "النسخة الاحتياطية لا تحتوي جميع أقسام التطبيق" }
        val originals = storeNames.associateWith { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
        }
        val changed = mutableListOf<String>()
        try {
            storeNames.forEach { name ->
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                backup.stores.getValue(name).forEach { (key, value) -> putValue(editor, key, value) }
                changed.add(name)
                check(editor.commit()) { "تعذر حفظ بيانات قسم $name" }
            }
        } catch (failure: Exception) {
            val rollbackFailures = mutableListOf<String>()
            changed.asReversed().forEach { name ->
                try {
                    val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                    originals.getValue(name).forEach { (key, value) -> putOriginalValue(editor, key, value) }
                    if (!editor.commit()) rollbackFailures += name
                } catch (_: Exception) {
                    rollbackFailures += name
                }
            }
            val rollbackMessage = if (rollbackFailures.isEmpty()) {
                "تمت إعادة البيانات السابقة بنجاح."
            } else {
                "تحذير مهم: تعذرت إعادة البيانات السابقة لبعض الأقسام: " +
                    rollbackFailures.joinToString("، ") +
                    ". أوقف استخدام التطبيق وأنشئ نسخة من البيانات المتاحة قبل أي محاولة أخرى."
            }
            throw IllegalStateException("تعذرت الاستعادة. $rollbackMessage " + (failure.message ?: ""), failure)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun aad(): ByteArray = "$FORMAT|$SCHEMA_VERSION".toByteArray(Charsets.UTF_8)

    private fun decodeBase64(value: String, expectedSize: Int, label: String): ByteArray {
        val decoded = try { Base64.decode(value, Base64.NO_WRAP) } catch (_: Exception) {
            error("بيانات $label غير صالحة")
        }
        require(decoded.size == expectedSize) { "حجم $label غير صالح" }
        return decoded
    }

    private fun encodeValue(value: Any?): JSONObject {
        val type: String
        val encoded: Any
        when (value) {
            null -> { type = "null"; encoded = JSONObject.NULL }
            is String -> { type = "string"; encoded = value }
            is Boolean -> { type = "boolean"; encoded = value }
            is Int -> { type = "int"; encoded = value }
            is Long -> { type = "long"; encoded = value }
            is Float -> { type = "float"; encoded = value.toDouble() }
            is Set<*> -> {
                type = "string_set"
                encoded = JSONArray(value.map { it as? String ?: error("نوع مجموعة بيانات غير مدعوم") })
            }
            else -> error("نوع بيانات غير مدعوم في النسخ الاحتياطي: " + value.javaClass.simpleName)
        }
        return JSONObject().put("type", type).put("value", encoded)
    }

    private fun decodeValue(entry: JSONObject): Any? {
        val value = entry.opt("value")
        return when (entry.optString("type")) {
            "null" -> null
            "string" -> value as? String ?: error("قيمة نصية غير صالحة")
            "boolean" -> value as? Boolean ?: error("قيمة منطقية غير صالحة")
            "int" -> (value as? Number)?.toInt() ?: error("قيمة عددية غير صالحة")
            "long" -> (value as? Number)?.toLong() ?: error("قيمة عددية غير صالحة")
            "float" -> (value as? Number)?.toFloat() ?: error("قيمة عشرية غير صالحة")
            "string_set" -> {
                val array = value as? JSONArray ?: error("مجموعة نصوص غير صالحة")
                buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
            }
            else -> error("نوع بيانات غير معروف في النسخة الاحتياطية")
        }
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> error("نوع بيانات غير مدعوم")
        }
    }

    private fun putOriginalValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
