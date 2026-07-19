package app.yukine

import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersistentMetadataGatewayRequestQuotaTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clear() {
        context.getSharedPreferences(
            PersistentMetadataGatewayRequestQuota.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun moreThanOneHundredRequestsInFifteenMinutesAreAllowed() {
        val quota = PersistentMetadataGatewayRequestQuota(
            context,
            zoneId = ZoneId.of("Asia/Shanghai"),
            maximum = PersistentMetadataGatewayRequestQuota.DAILY_REQUEST_LIMIT
        )
        val now = 1_752_915_600_000L

        repeat(101) {
            assertTrue(quota.tryAcquire(now + it * 1_000L))
        }
    }

    @Test
    fun dailyLimitPersistsAndResetsOnTheNextLocalDay() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val first = PersistentMetadataGatewayRequestQuota(context, zoneId, maximum = 2)
        val firstDay = 1_752_940_740_000L

        assertTrue(first.tryAcquire(firstDay))
        assertTrue(first.tryAcquire(firstDay + 1_000L))
        assertFalse(first.tryAcquire(firstDay + 2_000L))

        val reopened = PersistentMetadataGatewayRequestQuota(context, zoneId, maximum = 2)
        assertFalse(reopened.tryAcquire(firstDay + 3_000L))
        assertTrue(reopened.tryAcquire(firstDay + 120_000L))
    }
}
