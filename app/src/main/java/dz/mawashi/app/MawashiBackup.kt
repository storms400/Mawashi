package dz.mawashi.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class PreparedMawashiBackup(
    val createdAt: String,
    internal val stores: Map<String, Map<String, Any?>>
)

internal object MawashiBackup {
    private const val FORMAT = "mawashi-data-backup"
    private const val SCHEMA_VERSION = 1
    private const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    private val storeNames = listOf(
        "mawashi_herd_local",
        "mawashi_feed_stock",
        "mawashi_finance_v24",
        "mawashi_herd_care_v27"
    )

    fun export(context: Context, output: OutputStream) {
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
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("schema_version", SCHEMA_VERSION)
            .put("created_at", System.currentTimeMillis())
            .put("payload", payloadText)
            .put("sha256", sha256(payloadText))
        val backupBytes = envelope.toString(2).toByteArray(Charsets.UTF_8)
        require(backupBytes.size <= MAX_BACKUP_BYTES) {
            "حجم النسخة الاحتياطية يتجاوز الحد المسموح (25 ميغابايت)؛ لم يتم تصدير ملف"
        }
        output.write(backupBytes)
        output.flush()
    }

    fun prepareRestore(input: InputStream): PreparedMawashiBackup {
        val bytes = input.use { stream ->
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
            val result = output.toByteArray()
            require(result.isNotEmpty()) { "ملف النسخة الاحتياطية فارغ" }
            result
        }
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("format") == FORMAT) { "هذا الملف ليس نسخة احتياطية من تطبيق مواشي" }
        require(envelope.optInt("schema_version", -1) == SCHEMA_VERSION) { "إصدار ملف النسخة الاحتياطية غير مدعوم" }
        val payloadText = envelope.optString("payload")
        require(payloadText.isNotBlank()) { "محتوى النسخة الاحتياطية غير موجود" }
        require(sha256(payloadText) == envelope.optString("sha256")) { "فشل التحقق من سلامة النسخة الاحتياطية؛ لم يتم تغيير بياناتك" }

        val payload = JSONObject(payloadText)
        require(payload.optString("app_family") == "mawashi-v32") { "النسخة الاحتياطية لا تخص إصدار مواشي v32" }
        val stores = payload.optJSONObject("stores") ?: error("بيانات الأقسام غير موجودة في النسخة الاحتياطية")
        val storeNames = listOf("mawashi_herd_local", "mawashi_feed_stock", "mawashi_finance_v24", "mawashi_herd_care_v27")
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
        return PreparedMawashiBackup(envelope.optLong("created_at").toString(), decoded)
    }

    fun restore(context: Context, backup: PreparedMawashiBackup) {
        val storeNames = listOf("mawashi_herd_local", "mawashi_feed_stock", "mawashi_finance_v24", "mawashi_herd_care_v27")
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
            changed.asReversed().forEach { name ->
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                originals.getValue(name).forEach { (key, value) -> putOriginalValue(editor, key, value) }
                editor.commit()
            }
            throw IllegalStateException("تعذرت الاستعادة؛ تمت محاولة إعادة البيانات السابقة. " + (failure.message ?: ""), failure)
        }
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
