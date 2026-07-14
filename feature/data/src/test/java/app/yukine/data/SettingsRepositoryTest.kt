package app.yukine.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "settings-room-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = SettingsRepository(database.settingsDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun typedDefaultsAndRuntimeSettingsRoundTripWithoutKeyDrift() {
        assertFalse(repository.loadSystemMediaLyricsTitleEnabled())
        assertFalse(repository.loadDebugPromptsEnabled())
        assertTrue(repository.loadOnlineLyricsEnabled())
        assertEquals(1.0f, repository.loadPlaybackSpeed())
        assertEquals(-1L, repository.loadMediaStoreGeneration())

        repository.saveSystemMediaLyricsTitleEnabled(true)
        repository.saveDebugPromptsEnabled(true)
        repository.savePlaybackSpeed(1.25f)
        repository.saveLyricsOffsetMs(-850L)
        repository.saveMediaStoreGeneration(42L)

        assertTrue(repository.loadSystemMediaLyricsTitleEnabled())
        assertTrue(repository.loadDebugPromptsEnabled())
        assertEquals(1.25f, repository.loadPlaybackSpeed())
        assertEquals(-850L, repository.loadLyricsOffsetMs())
        assertEquals(42L, repository.loadMediaStoreGeneration())
    }

    @Test
    fun invalidStoredNumbersUseLegacyDefaults() {
        database.settingsDao().put(app.yukine.data.room.SettingEntity("playback_speed", "NaN!"))
        database.settingsDao().put(app.yukine.data.room.SettingEntity("lyrics_offset_ms", "bad"))

        assertEquals(1.0f, repository.loadPlaybackSpeed())
        assertEquals(0L, repository.loadLyricsOffsetMs())
    }
}
