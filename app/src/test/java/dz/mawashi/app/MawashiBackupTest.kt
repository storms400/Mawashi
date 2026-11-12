package dz.mawashi.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
    fun exportAndRestorePreservesSupportedPreferenceTypesAcrossAllStores() {
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
        MawashiBackup.export(context, output)
        val prepared = MawashiBackup.prepareRestore(ByteArrayInputStream(output.toByteArray()))

        stores.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
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
    fun checksumFailureIsRejectedBeforeAnyPreferenceIsChanged() {
        val prefs = context.getSharedPreferences(stores.first(), Context.MODE_PRIVATE)
        prefs.edit().putString("keep", "original").commit()

        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output)
        val corrupted = output.toString("UTF-8").replace("sha256", "sha256-corrupt")
        assertFalse("Test fixture must alter the checksum", corrupted == output.toString("UTF-8"))

        assertThrows(IllegalArgumentException::class.java) {
            MawashiBackup.prepareRestore(ByteArrayInputStream(corrupted.toByteArray(Charsets.UTF_8)))
        }
        assertEquals("original", prefs.getString("keep", null))
    }

    @Test
    fun restoreReplacesOldKeysRatherThanMergingStaleData() {
        val source = context.getSharedPreferences(stores.first(), Context.MODE_PRIVATE)
        source.edit().putString("new-key", "from-backup").commit()
        val output = ByteArrayOutputStream()
        MawashiBackup.export(context, output)
        val prepared = MawashiBackup.prepareRestore(ByteArrayInputStream(output.toByteArray()))

        source.edit().putString("stale-key", "must-disappear").commit()
        MawashiBackup.restore(context, prepared)

        assertEquals("from-backup", source.getString("new-key", null))
        assertTrue(!source.contains("stale-key"))
    }
}
