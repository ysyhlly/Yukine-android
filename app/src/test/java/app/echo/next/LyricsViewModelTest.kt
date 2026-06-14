package app.echo.next

import android.net.Uri
import app.echo.next.model.LyricsLine
import app.echo.next.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun configureStoresLyricsSettings() {
        val viewModel = LyricsViewModel()

        viewModel.configure(
            FakeLyricsLoader(),
            onlineEnabled = true,
            offsetMs = 450L
        )

        assertTrue(viewModel.onlineEnabled())
        assertEquals(450L, viewModel.offsetMs())
        assertEquals(LyricsStatusKind.NOT_LOADED, viewModel.state.value.statusKind)
    }

    @Test
    fun loadPublishesLoadedLyricsState() = runTest {
        val operations = FakeLyricsLoader().apply {
            result = listOf(LyricsLine(1000L, "hello"), LyricsLine(2000L, "world"))
        }
        val viewModel = LyricsViewModel()
        val notifications = mutableListOf<LyricsStatusKind>()
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 100L)
        viewModel.bindListener { notifications += viewModel.state.value.statusKind }

        viewModel.load(track(7L), "9988").join()

        assertEquals(listOf("load:7:true:9988"), operations.events)
        assertEquals(7L, viewModel.trackId())
        assertEquals(listOf("hello", "world"), viewModel.lines().map { it.text })
        assertEquals(LyricsStatusKind.LOADED, viewModel.state.value.statusKind)
        assertEquals(2, viewModel.state.value.loadedLineCount)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "loaded.lyrics.prefix") + "2" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "loaded.lyrics.suffix"), viewModel.status(AppLanguage.MODE_ENGLISH))
        assertTrue(notifications.contains(LyricsStatusKind.LOADING))
        assertEquals(LyricsStatusKind.LOADED, notifications.last())
    }

    @Test
    fun loadUsesLocalNotFoundWhenOfflineResultIsEmpty() = runTest {
        val viewModel = LyricsViewModel()
        viewModel.configure(FakeLyricsLoader(), onlineEnabled = false, offsetMs = 0L)

        viewModel.load(track(9L), "").join()

        assertEquals(LyricsStatusKind.LOCAL_NOT_FOUND, viewModel.state.value.statusKind)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "no.local.lyrics.found"), viewModel.status(AppLanguage.MODE_ENGLISH))
    }

    @Test
    fun missingTrackPublishesNoTrackWithoutLoading() = runTest {
        val operations = FakeLyricsLoader()
        val viewModel = LyricsViewModel()
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)

        viewModel.load(null, "123").join()

        assertEquals(-1L, viewModel.trackId())
        assertEquals(LyricsStatusKind.NO_TRACK, viewModel.state.value.statusKind)
        assertTrue(operations.events.isEmpty())
    }

    private class FakeLyricsLoader : LyricsLoader {
        val events = mutableListOf<String>()
        var result: List<LyricsLine> = emptyList()

        override fun load(
            track: Track,
            onlineEnabled: Boolean,
            neteaseProviderTrackId: String
        ): List<LyricsLine> {
            events += "load:${track.id}:$onlineEnabled:$neteaseProviderTrackId"
            return result
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 10_000L, Uri.EMPTY, "file:$id.mp3")
}