package dz.mawashi.app

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class MawashiBackupTest {
    private lateinit var context: Context

    private val stores = listOf(
        "mawashi_herd_local",
        "mawashi_feed_stock",
        "mawashi_finance_v24",
        "mawashi_herd_care_v27"
    )

    @Before
    fun clearStores() {
        context = RuntimeEnvironment.getApplication()
        stores.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun encryptedExportAndRestorePreservesAllSupportedPreferenceTypes() {
        stores.forEachIndexed { index, name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putString("text", "section-$index")
                .putBoolean("enabled", index % 2 == 0)
                .putInt("count", index + 4)
                .putLong("timestamp", 1_700_000_000_000L + index)
                .putFloat("ratio", 1.25f + index)
                .putStringSet("tags", setOf("alpha", "beta-$index"))
                .commit()
        }
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output, "correct horse battery")
        val bytes = output.toByteArray()
        assertTrue(MawashiBackup.requiresPassword(bytes))
        val prepared = MawashiBackup.prepareRestore(ByteArrayInputStream(bytes), "correct horse battery")
        stores.forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
        MawashiBackup.restore(context, prepared)
        stores.forEachIndexed { index, name ->
            val restored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertEquals("section-$index", restored.getString("text", null))
            assertEquals(index % 2 == 0, restored.getBoolean("enabled", false))
            assertEquals(index + 4, restored.getInt("count", -1))
            assertEquals(1_700_000_000_000L + index, restored.getLong("timestamp", -1))
            assertEquals(1.25f + index, restored.getFloat("ratio", -1f), 0.0001f)
            assertEquals(setOf("alpha", "beta-$index"), restored.getStringSet("tags", emptySet()))
        }
    }

    @Test
    fun exportWithoutPasswordCanBeRestoredDirectly() {
        val prefs = context.getSharedPreferences(stores.first(), Context.MODE_PRIVATE)
        prefs.edit().putString("farmer-note", "my flock").commit()
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output)
        val bytes = output.toByteArray()
        assertFalse(MawashiBackup.requiresPassword(bytes))
        val prepared = MawashiBackup.prepareRestore(ByteArrayInputStream(bytes))
        assertTrue(prepared.isLegacyUnencrypted)
        prefs.edit().clear().commit()
        MawashiBackup.restore(context, prepared)
        assertEquals("my flock", prefs.getString("farmer-note", null))
    }

    @Test
    fun blankPasswordExportsWithoutEncryptionAndShortPasswordIsRejected() {
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output, "")
        assertFalse(MawashiBackup.requiresPassword(output.toByteArray()))
        assertThrows(IllegalArgumentException::class.java) {
            MawashiBackup.export(context, ByteArrayOutputStream(), "short")
        }
    }

    @Test
    fun wrongPasswordIsRejectedBeforeAnyPreferenceIsChanged() {
        val prefs = context.getSharedPreferences(stores.first(), Context.MODE_PRIVATE)
        prefs.edit().putString("keep", "original").commit()
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output, "correct horse battery")
        assertThrows(IllegalArgumentException::class.java) {
            MawashiBackup.prepareRestore(ByteArrayInputStream(output.toByteArray()), "incorrect password")
        }
        assertEquals("original", prefs.getString("keep", null))
    }

    @Test
    fun ciphertextTamperingIsRejected() {
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output, "correct horse battery")
        val envelope = JSONObject(output.toString("UTF-8"))
        val ciphertext = envelope.getString("ciphertext")
        val changed = (if (ciphertext.first() == 'A') 'B' else 'A') + ciphertext.substring(1)
        envelope.put("ciphertext", changed)
        assertThrows(IllegalArgumentException::class.java) {
            MawashiBackup.prepareRestore(envelope.toString().toByteArray(Charsets.UTF_8), "correct horse battery")
        }
    }

    @Test
    fun everyExportUsesFreshSaltAndNonce() {
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()
        MawashiBackup.export(context, first, "correct horse battery")
        MawashiBackup.export(context, second, "correct horse battery")
        val a = JSONObject(first.toString("UTF-8"))
        val b = JSONObject(second.toString("UTF-8"))
        assertNotEquals(a.getString("salt"), b.getString("salt"))
        assertNotEquals(a.getString("nonce"), b.getString("nonce"))
    }

    @Test
    fun legacyUnencryptedBackupRemainsRestorable() {
        val storesObject = JSONObject()
        stores.forEach { storesObject.put(it, JSONObject()) }
        val payload = JSONObject()
            .put("app_family", "mawashi-v32")
            .put("stores", storesObject)
            .toString()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val legacy = JSONObject()
            .put("format", "mawashi-data-backup")
            .put("schema_version", 1)
            .put("created_at", 1_700_000_000_000L)
            .put("payload", payload)
            .put("sha256", digest)
            .toString()
            .toByteArray(Charsets.UTF_8)
        assertFalse(MawashiBackup.requiresPassword(legacy))
        val prepared = MawashiBackup.prepareRestore(legacy)
        assertTrue(prepared.isLegacyUnencrypted)
        MawashiBackup.restore(context, prepared)
    }

    @Test
    fun restoreReplacesOldKeysRatherThanMergingStaleData() {
        val source = context.getSharedPreferences(stores.first(), Context.MODE_PRIVATE)
        source.edit().putString("new-key", "from-backup").commit()
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output, "correct horse battery")
        val prepared = MawashiBackup.prepareRestore(ByteArrayInputStream(output.toByteArray()), "correct horse battery")
        source.edit().putString("stale-key", "must-disappear").commit()
        MawashiBackup.restore(context, prepared)
        assertEquals("from-backup", source.getString("new-key", null))
        assertTrue(!source.contains("stale-key"))
    }
}
