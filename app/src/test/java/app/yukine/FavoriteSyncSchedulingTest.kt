package app.yukine

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteSyncSchedulingTest {
    @Test
    fun backgroundSyncIsDisabledByDefault() {
        val spec = favoriteSyncScheduleSpec(FavoriteSyncPreferences())

        assertFalse(spec.enabled)
        assertEquals(30L, spec.intervalMinutes)
        assertEquals(NetworkType.CONNECTED, spec.networkType)
    }

    @Test
    fun backgroundSyncUsesUniquePeriodicRequestInputsAndWifiConstraint() {
        val spec = favoriteSyncScheduleSpec(
            FavoriteSyncPreferences(
                periodicSyncEnabled = true,
                wifiOnly = true,
                intervalMinutes = 30
            )
        )

        assertTrue(spec.enabled)
        assertEquals(30L, spec.intervalMinutes)
        assertEquals(NetworkType.UNMETERED, spec.networkType)
    }

    @Test
    fun requestedPeriodNeverRunsMoreOftenThanThirtyMinutes() {
        val spec = favoriteSyncScheduleSpec(
            FavoriteSyncPreferences(periodicSyncEnabled = true, intervalMinutes = 1)
        )

        assertEquals(30L, spec.intervalMinutes)
    }

    @Test
    fun foregroundSyncWaitsUntilThirtyMinuteThreshold() {
        val preferences = FavoriteSyncPreferences()
        val now = 10_000_000L

        assertTrue(shouldRunFavoriteSyncOnForeground(preferences, 0L, now))
        assertFalse(
            shouldRunFavoriteSyncOnForeground(
                preferences,
                now - FAVORITE_SYNC_FOREGROUND_STALE_MS + 1L,
                now
            )
        )
        assertTrue(
            shouldRunFavoriteSyncOnForeground(
                preferences,
                now - FAVORITE_SYNC_FOREGROUND_STALE_MS,
                now
            )
        )
    }

    @Test
    fun foregroundSyncRespectsBothMasterAndForegroundSwitches() {
        val now = 10_000_000L

        assertFalse(
            shouldRunFavoriteSyncOnForeground(
                FavoriteSyncPreferences(autoSyncEnabled = false),
                0L,
                now
            )
        )
        assertFalse(
            shouldRunFavoriteSyncOnForeground(
                FavoriteSyncPreferences(syncOnForeground = false),
                0L,
                now
            )
        )
    }
}
