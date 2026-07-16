package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.streaming.StreamingProviderName
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteSyncPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun recordingIdentityAndPendingImportsSurviveProcessRecreation() {
        val repository = SharedPreferencesFavoriteSyncRepository(context)
        repository.update { state ->
            state.copy(
                favorites = listOf(
                    UnifiedFavorite(
                        unifiedId = "recording:stable-uuid",
                        localTrackId = 7L,
                        title = "Song",
                        artist = "Artist",
                        album = "Album",
                        durationMs = 120_000L,
                        recordingId = 42L,
                        canonicalUuid = "stable-uuid"
                    )
                ),
                pendingImports = listOf(
                    PendingProviderFavorite(
                        provider = StreamingProviderName.NETEASE,
                        providerTrackId = "pending-1",
                        title = "Pending",
                        artist = "Artist",
                        album = "Album",
                        durationMs = 120_000L,
                        errorMessage = "retry"
                    )
                )
            )
        }

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value

        assertEquals(42L, restored.favorites.single().recordingId)
        assertEquals("stable-uuid", restored.favorites.single().canonicalUuid)
        assertEquals("pending-1", restored.pendingImports.single().providerTrackId)
    }

    @Test
    fun versionTwoPreferencesArePreservedDuringIdentityMigration() {
        val raw = JSONObject()
            .put("version", 2)
            .put("preferences", JSONObject()
                .put("autoSyncEnabled", false)
                .put("propagateRemovals", false))
        preferences.edit().putString(KEY_STATE, raw.toString()).commit()

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value.preferences

        assertFalse(restored.autoSyncEnabled)
        assertFalse(restored.propagateRemovals)
    }

    private companion object {
        const val PREFS_NAME = "cross_provider_favorite_sync"
        const val KEY_STATE = "state_v1"
    }
}
