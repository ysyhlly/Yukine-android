package app.yukine

import android.net.Uri
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

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
        val viewModel = LyricsViewModel(dispatcher)
        val notifications = mutableListOf<LyricsStatusKind>()
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 100L)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.state.map { it.statusKind }.toList(notifications)
        }

        viewModel.load(track(7L), "9988").join()
        runCurrent()
        observer.cancel()

        assertEquals(listOf("load:7:true:9988"), operations.events)
        assertEquals(7L, viewModel.trackId())
        assertEquals(listOf("hello", "world"), viewModel.lines().map { it.text })
        assertEquals(LyricsStatusKind.LOADED, viewModel.state.value.statusKind)
        assertEquals(2, viewModel.state.value.loadedLineCount)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "loaded.lyrics.prefix") + "2" + AppLanguage.text(AppLanguage.MODE_ENGLISH, "loaded.lyrics.suffix"), viewModel.status(AppLanguage.MODE_ENGLISH))
        assertEquals(LyricsStatusKind.LOADED, notifications.last())
    }

    @Test
    fun loadUsesLocalNotFoundWhenOfflineResultIsEmpty() = runTest {
        val viewModel = LyricsViewModel(dispatcher)
        viewModel.configure(FakeLyricsLoader(), onlineEnabled = false, offsetMs = 0L)

        viewModel.load(track(9L), "").join()

        assertEquals(LyricsStatusKind.LOCAL_NOT_FOUND, viewModel.state.value.statusKind)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "no.local.lyrics.found"), viewModel.status(AppLanguage.MODE_ENGLISH))
    }

    @Test
    fun missingTrackPublishesNoTrackWithoutLoading() = runTest {
        val operations = FakeLyricsLoader()
        val viewModel = LyricsViewModel(dispatcher)
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)

        viewModel.load(null, "123").join()

        assertEquals(-1L, viewModel.trackId())
        assertEquals(LyricsStatusKind.NO_TRACK, viewModel.state.value.statusKind)
        assertTrue(operations.events.isEmpty())
    }

    @Test
    fun reloadCurrentLyricsLoadsBoundCurrentTrackAndPublishesReloadingStatus() = runTest {
        val operations = FakeLyricsLoader().apply {
            result = listOf(LyricsLine(1000L, "hello"))
        }
        val viewModel = LyricsViewModel(dispatcher)
        val statuses = mutableListOf<String>()
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)
        viewModel.bindReloadGateway(
            CurrentLyricsTrackProvider { track(11L) },
            LyricsProviderTrackIdResolver { nextTrack -> "provider:${nextTrack?.id}" },
            LyricsReloadStatusSink { statuses += it }
        )

        viewModel.reloadCurrentLyrics(AppLanguage.MODE_ENGLISH).join()

        assertEquals(listOf("load:11:true:provider:11"), operations.events)
        assertEquals(listOf(AppLanguage.text(AppLanguage.MODE_ENGLISH, "reloading.lyrics")), statuses)
        assertEquals(LyricsStatusKind.LOADED, viewModel.state.value.statusKind)
    }

    @Test
    fun playbackTrackLoadUsesBoundProviderResolverWithoutActivityPolicy() = runTest {
        val operations = FakeLyricsLoader().apply {
            result = listOf(LyricsLine(1000L, "hello"))
        }
        val viewModel = LyricsViewModel(dispatcher)
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)
        viewModel.bindReloadGateway(
            CurrentLyricsTrackProvider { null },
            LyricsProviderTrackIdResolver { nextTrack -> "provider:${nextTrack?.id}" },
            null
        )

        viewModel.loadPlaybackTrack(track(13L)).join()

        assertEquals(listOf("load:13:true:provider:13"), operations.events)
    }

    @Test
    fun playbackTrackProviderResolverRunsOffMainThread() = runTest {
        val resolverExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "lyrics-provider-io")
        }
        val resolverDispatcher = resolverExecutor.asCoroutineDispatcher()
        try {
            val operations = FakeLyricsLoader().apply {
                result = listOf(LyricsLine(1000L, "hello"))
            }
            val viewModel = LyricsViewModel(resolverDispatcher)
            var resolverThread = ""
            viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)
            viewModel.bindReloadGateway(
                CurrentLyricsTrackProvider { null },
                LyricsProviderTrackIdResolver { nextTrack ->
                    resolverThread = Thread.currentThread().name
                    "provider:${nextTrack?.id}"
                },
                null
            )

            viewModel.loadPlaybackTrack(track(15L)).join()

            assertTrue(resolverThread.startsWith("lyrics-provider-io"))
            assertEquals(listOf("load:15:true:provider:15"), operations.events)
        } finally {
            resolverDispatcher.close()
            resolverExecutor.shutdownNow()
        }
    }

    @Test
    fun reloadCurrentLyricsPublishesNoTrackStatusWhenCurrentTrackIsMissing() = runTest {
        val operations = FakeLyricsLoader()
        val viewModel = LyricsViewModel(dispatcher)
        val statuses = mutableListOf<String>()
        viewModel.configure(operations, onlineEnabled = true, offsetMs = 0L)
        viewModel.bindReloadGateway(
            CurrentLyricsTrackProvider { null },
            LyricsProviderTrackIdResolver { nextTrack -> "provider:${nextTrack?.id}" },
            LyricsReloadStatusSink { statuses += it }
        )

        viewModel.reloadCurrentLyrics(AppLanguage.MODE_ENGLISH).join()

        assertTrue(operations.events.isEmpty())
        assertEquals(listOf(AppLanguage.text(AppLanguage.MODE_ENGLISH, "no.track.selected")), statuses)
        assertEquals(LyricsStatusKind.NO_TRACK, viewModel.state.value.statusKind)
    }

    private class FakeLyricsLoader : LyricsLoader {
        val events = mutableListOf<String>()
        var result: List<LyricsLine> = emptyList()

        override suspend fun load(
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
