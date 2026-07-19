package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.streaming.StreamingProviderName
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                ),
                mappings = listOf(
                    ProviderFavoriteMapping(
                        unifiedId = "recording:stable-uuid",
                        provider = StreamingProviderName.NETEASE,
                        providerTrackId = "netease-7",
                        recordingId = 42L,
                        sourceKey = "netease:account-7:liked",
                        accountId = "account-7",
                        collectionId = "liked",
                        consecutiveMissing = 1
                    )
                ),
                cursors = listOf(
                    FavoriteSyncCursor(
                        provider = StreamingProviderName.NETEASE,
                        seenProviderTrackIds = setOf("netease-7"),
                        sourceKey = "netease:account-7:liked",
                        accountId = "account-7",
                        baselineEstablished = true
                    )
                ),
                sources = listOf(
                    FavoriteSyncSourceRecord(
                        sourceKey = "netease:account-7:liked",
                        provider = StreamingProviderName.NETEASE,
                        providerName = "网易云音乐",
                        sourceName = "我喜欢",
                        accountId = "account-7",
                        collectionId = "liked",
                        selected = true,
                        supported = true,
                        loggedIn = true
                    )
                ),
                preferences = state.preferences.copy(
                    periodicSyncEnabled = true,
                    wifiOnly = true,
                    selectedSourceKeys = setOf("netease:account-7:liked")
                )
            )
        }

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value

        assertEquals(42L, restored.favorites.single().recordingId)
        assertEquals("stable-uuid", restored.favorites.single().canonicalUuid)
        assertEquals("pending-1", restored.pendingImports.single().providerTrackId)
        assertEquals("account-7", restored.mappings.single().accountId)
        assertEquals(1, restored.mappings.single().consecutiveMissing)
        assertTrue(restored.cursors.single().baselineEstablished)
        assertTrue(restored.sources.single().selected)
        assertTrue(restored.preferences.periodicSyncEnabled)
        assertTrue(restored.preferences.wifiOnly)
        assertEquals(setOf("netease:account-7:liked"), restored.preferences.selectedSourceKeys)
    }

    @Test
    fun legacyStatePreferencesArePreservedDuringV2Migration() {
        val raw = JSONObject()
            .put("version", 2)
            .put("preferences", JSONObject()
                .put("autoSyncEnabled", false)
                .put("propagateRemovals", false)
                .put("periodicSyncEnabled", true)
                .put("wifiOnly", true))
        preferences.edit().putString(LEGACY_KEY_STATE, raw.toString()).commit()

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value.preferences

        assertFalse(restored.autoSyncEnabled)
        assertFalse(restored.propagateRemovals)
        assertTrue(restored.periodicSyncEnabled)
        assertTrue(restored.wifiOnly)
        assertEquals(FavoriteSyncMode.REMOTE_TO_LOCAL, restored.mode)
    }

    @Test
    fun legacyCursorAndPendingWriteMigrateIntoSafePausedBaseline() {
        val raw = JSONObject()
            .put("version", 3)
            .put("mappings", JSONArray().put(JSONObject()
                .put("unifiedId", "legacy")
                .put("provider", "netease")
                .put("providerTrackId", "legacy-track")
                .put("recordingId", 7L)))
            .put("cursors", JSONArray().put(JSONObject()
                .put("provider", "netease")
                .put("cursor", "legacy-cursor")
                .put("seenProviderTrackIds", JSONArray().put("legacy-track"))))
            .put("operations", JSONArray().put(JSONObject()
                .put("operationId", "ADD:legacy:qqmusic")
                .put("unifiedId", "legacy")
                .put("action", "ADD")
                .put("targetProvider", "qqmusic")
                .put("batchId", "legacy")))
        preferences.edit().putString(LEGACY_KEY_STATE, raw.toString()).commit()

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value

        assertFalse(restored.cursors.single().baselineEstablished)
        assertEquals("", restored.mappings.single().accountId)
        assertEquals(FavoriteSyncStatus.PENDING, restored.operations.single().status)
        assertEquals(FavoriteSyncMode.REMOTE_TO_LOCAL, restored.preferences.mode)
    }

    @Test
    fun corruptV2StateFallsBackToSafeDefaults() {
        preferences.edit().putString(KEY_STATE, "{not-json").commit()

        val restored = SharedPreferencesFavoriteSyncRepository(context).state.value

        assertTrue(restored.favorites.isEmpty())
        assertTrue(restored.mappings.isEmpty())
        assertTrue(restored.operations.isEmpty())
        assertTrue(restored.preferences.autoSyncEnabled)
        assertTrue(restored.preferences.syncOnForeground)
        assertFalse(restored.preferences.periodicSyncEnabled)
    }

    private companion object {
        const val PREFS_NAME = "cross_provider_favorite_sync"
        const val KEY_STATE = "state_v2"
        const val LEGACY_KEY_STATE = "state_v1"
    }
}
