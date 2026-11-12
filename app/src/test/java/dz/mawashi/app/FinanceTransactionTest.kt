package dz.mawashi.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FinanceTransactionTest {
    private lateinit var context: Context

    @Before
    fun clearStores() {
        context = RuntimeEnvironment.getApplication()
        financeTransactionStoreNames().forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun failedOperationRestoresEveryAffectedStore() {
        val finance = context.getSharedPreferences("mawashi_finance_v24", Context.MODE_PRIVATE)
        val feed = context.getSharedPreferences("mawashi_feed_stock", Context.MODE_PRIVATE)
        val herd = context.getSharedPreferences("mawashi_herd_local", Context.MODE_PRIVATE)
        finance.edit().putString("invoices", "old-invoices").commit()
        feed.edit().putString("stock", "old-stock").commit()
        herd.edit().putString("animals", "old-herd").commit()

        val result = runFinanceTransaction(context, financeTransactionStoreNames()) {
            check(finance.edit().putString("invoices", "new-invoice").commit())
            check(feed.edit().putString("stock", "new-stock").commit())
            check(herd.edit().putString("animals", "new-herd").commit())
            error("Simulated invoice persistence failure")
        }

        assertFalse(result.committed)
        assertTrue(result.rollbackSucceeded)
        assertEquals("old-invoices", finance.getString("invoices", null))
        assertEquals("old-stock", feed.getString("stock", null))
        assertEquals("old-herd", herd.getString("animals", null))
    }

    @Test
    fun successfulOperationKeepsChangesAcrossStores() {
        val finance = context.getSharedPreferences("mawashi_finance_v24", Context.MODE_PRIVATE)
        val feed = context.getSharedPreferences("mawashi_feed_stock", Context.MODE_PRIVATE)

        val result = runFinanceTransaction(context, financeTransactionStoreNames()) {
            check(feed.edit().putString("stock", "updated").commit())
            check(finance.edit().putString("invoices", "invoice-1").commit())
        }

        assertTrue(result.committed)
        assertTrue(result.rollbackSucceeded)
        assertEquals("updated", feed.getString("stock", null))
        assertEquals("invoice-1", finance.getString("invoices", null))
    }

    @Test
    fun rejectsNonFiniteAndNonPositiveMoneyValues() {
        assertTrue(isValidFinanceAmount(0.01))
        assertFalse(isValidFinanceAmount(0.0))
        assertFalse(isValidFinanceAmount(-1.0))
        assertFalse(isValidFinanceAmount(Double.NaN))
        assertFalse(isValidFinanceAmount(Double.POSITIVE_INFINITY))
    }
}
