package dz.mawashi.app

import android.content.Context

internal data class FinanceTransactionResult(
    val committed: Boolean,
    val rollbackSucceeded: Boolean
)

/**
 * Applies a finance operation across affected preference stores.
 * If any write throws or reports failure, previous values are restored
 * on a best-effort basis before the caller reports failure.
 */
internal fun runFinanceTransaction(
    context: Context,
    storeNames: List<String>,
    action: () -> Unit
): FinanceTransactionResult {
    require(storeNames.isNotEmpty()) { "At least one preference store is required" }
    val snapshots = storeNames.distinct().associateWith { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
    }

    return try {
        action()
        FinanceTransactionResult(committed = true, rollbackSucceeded = true)
    } catch (_: Exception) {
        var restored = true
        snapshots.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            try {
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                        null -> Unit
                        else -> throw IllegalStateException("Unsupported preference value")
                    }
                }
                if (!editor.commit()) restored = false
            } catch (_: Exception) {
                restored = false
            }
        }
        FinanceTransactionResult(committed = false, rollbackSucceeded = restored)
    }
}

internal fun isValidFinanceAmount(value: Double): Boolean =
    value.isFinite() && value > 0.0

internal fun financeTransactionStoreNames(): List<String> =
    listOf("mawashi_finance_v24", "mawashi_feed_stock", "mawashi_herd_local")
