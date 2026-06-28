package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingStateControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun publishUpdatesViewModelAndFloatingLyrics() {
        val viewModel = NowPlayingViewModel()
        val listener = FakeListener()
        val controller = NowPlayingStateController(viewModel, listener)
        val snapshot = snapshot()

        val state = controller.publish(snapshot)

        assertEquals("Song", state.trackTitle)
        assertEquals("Song", viewModel.uiState.value.trackTitle)
        assertEquals(listOf("floating:Song"), listener.calls)
    }

    @Test
    fun renderNowBarPublishesAndSyncsQueueOnlyWhenStoresAreReady() {
        val listener = FakeListener(storesReady = false)
        val controller = NowPlayingStateController(NowPlayingViewModel(), listener)

        controller.renderNowBar()
        assertEquals(emptyList<String>(), listener.calls)

        listener.storesReady = true
        controller.renderNowBar()

        assertEquals(listOf("snapshot", "floating:Song", "queue"), listener.calls)
    }

    private class FakeListener(
        var storesReady: Boolean = true
    ) : NowPlayingStateController.Listener {
        val calls = mutableListOf<String>()

        override fun storesReady(): Boolean = storesReady

        override fun playbackSnapshot(): PlaybackStateSnapshot {
            calls += "snapshot"
            return snapshot()
        }

        override fun favoriteIds(): Set<Long> = setOf(7L)

        override fun lyricsState(): LyricsState? = null

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun publishFloatingLyrics(state: NowPlayingUiState) {
            calls += "floating:${state.trackTitle}"
        }

        override fun syncQueueInputs() {
            calls += "queue"
        }
    }

    companion object {
        private fun snapshot(): PlaybackStateSnapshot =
            PlaybackStateSnapshot(
                Track(7L, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3"),
                0,
                1,
                0L,
                180_000L,
                true,
                false,
                "",
                false,
                EchoPlaybackService.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
    }
}
