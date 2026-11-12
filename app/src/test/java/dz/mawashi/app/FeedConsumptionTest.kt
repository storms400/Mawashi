package dz.mawashi.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FeedConsumptionTest {
    private lateinit var context: Context
    private val shares = mapOf(FeedKind.BARLEY to 100.0)

    @Before
    fun resetPreferences() {
        context = RuntimeEnvironment.getApplication()
        listOf("mawashi_feed_stock", "mawashi_herd_local").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        saveHerd(
            context,
            listOf(
                HerdAnimal(
                    id = "feed-test-animal",
                    tag = "Q-9001",
                    kind = "نعجة",
                    ageMonths = 30,
                    health = "جيدة",
                    notes = "",
                    weightKg = 50.0,
                    productionState = "نعجة جافة"
                )
            )
        )
    }

    @Test
    fun recordsDailyConsumptionAndDeductsStockOnlyOnce() {
        saveFeedStocks(context, mapOf(FeedKind.BARLEY to 5.0))

        val first = applyDailyFeedConsumption(context, loadHerd(context), "متوسط", shares)
        val remainingAfterFirst = loadFeedStocks(context).getValue(FeedKind.BARLEY)
        val second = applyDailyFeedConsumption(context, loadHerd(context), "متوسط", shares)

        assertTrue(first.startsWith("تم تسجيل الخصم اليومي"))
        assertTrue(remainingAfterFirst in 0.0..5.0 && remainingAfterFirst < 5.0)
        assertEquals("استهلاك اليوم مسجل مسبقاً؛ لم يتكرر الخصم", second)
        assertEquals(remainingAfterFirst, loadFeedStocks(context).getValue(FeedKind.BARLEY), 0.000001)
        assertEquals(1, readFeedConsumptionEntries(context).size)
    }

    @Test
    fun insufficientStockDoesNotPartiallyDeductOrRecordConsumption() {
        saveFeedStocks(context, mapOf(FeedKind.BARLEY to 0.01))

        val result = applyDailyFeedConsumption(context, loadHerd(context), "متوسط", shares)

        assertTrue(result.startsWith("المخزون غير كافٍ"))
        assertEquals(0.01, loadFeedStocks(context).getValue(FeedKind.BARLEY), 0.000001)
        assertTrue(readFeedConsumptionEntries(context).isEmpty())
        assertEquals("", context.getSharedPreferences("mawashi_feed_stock", Context.MODE_PRIVATE)
            .getString("last_consumption_date", ""))
    }

    @Test
    fun missingSelectedIngredientPreventsAnyDeduction() {
        val result = applyDailyFeedConsumption(context, loadHerd(context), "متوسط", shares)

        assertTrue(result.startsWith("لا يوجد مخزون من:"))
        assertTrue(loadFeedStocks(context).isEmpty())
        assertTrue(readFeedConsumptionEntries(context).isEmpty())
        assertEquals("", context.getSharedPreferences("mawashi_feed_stock", Context.MODE_PRIVATE)
            .getString("last_consumption_date", ""))
    }

    @Test
    fun animalsWithoutValidWeightsDoNotConsumeFeed() {
        saveHerd(context, loadHerd(context).map { it.copy(weightKg = null) })
        saveFeedStocks(context, mapOf(FeedKind.BARLEY to 5.0))

        val result = applyDailyFeedConsumption(context, loadHerd(context), "متوسط", shares)

        assertTrue(result.startsWith("لا توجد حيوانات بأوزان مسجلة صالحة"))
        assertEquals(5.0, loadFeedStocks(context).getValue(FeedKind.BARLEY), 0.000001)
        assertTrue(readFeedConsumptionEntries(context).isEmpty())
    }
}
