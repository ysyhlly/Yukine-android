package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode
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

    @Test
    fun progressTicksDoNotResyncQueueInputsWhenQueueIdentityIsUnchanged() {
        val listener = FakeListener(
            snapshot = snapshot(positionMs = 1_000L, queueSize = 500)
        )
        val controller = NowPlayingStateController(NowPlayingViewModel(), listener)

        controller.renderNowBar()
        listener.snapshot = snapshot(positionMs = 2_000L, queueSize = 500)
        controller.renderNowBar()
        listener.snapshot = snapshot(positionMs = 3_000L, queueSize = 500)
        controller.renderNowBar()

        assertEquals(
            listOf(
                "snapshot",
                "floating:Song",
                "queue",
                "snapshot",
                "floating:Song",
                "snapshot",
                "floating:Song"
            ),
            listener.calls
        )
    }

    @Test
    fun queueIdentityChangesResyncQueueInputs() {
        val listener = FakeListener(
            snapshot = snapshot(positionMs = 1_000L, queueSize = 2)
        )
        val controller = NowPlayingStateController(NowPlayingViewModel(), listener)

        controller.renderNowBar()
        listener.snapshot = snapshot(positionMs = 2_000L, queueSize = 3)
        controller.renderNowBar()
        listener.snapshot = snapshot(trackId = 8L, positionMs = 0L, queueSize = 3)
        controller.renderNowBar()

        assertEquals(3, listener.calls.count { it == "queue" })
    }

    @Test
    fun queueIdentityChangesDoNotSyncQueueInputsWhenQueueIsHidden() {
        val listener = FakeListener(
            queueVisible = false,
            snapshot = snapshot(positionMs = 1_000L, queueSize = 500)
        )
        val controller = NowPlayingStateController(NowPlayingViewModel(), listener)

        controller.renderNowBar()
        listener.snapshot = snapshot(trackId = 8L, positionMs = 0L, queueSize = 500)
        controller.renderNowBar()
        listener.snapshot = snapshot(trackId = 9L, positionMs = 0L, queueSize = 500)
        controller.renderNowBar()

        assertEquals(0, listener.calls.count { it == "queue" })
    }

    private class FakeListener(
        var storesReady: Boolean = true,
        var snapshot: PlaybackStateSnapshot = snapshot(),
        var queueVisible: Boolean = true
    ) : NowPlayingStateController.Listener {
        val calls = mutableListOf<String>()

        override fun storesReady(): Boolean = storesReady

        override fun playbackSnapshot(): PlaybackStateSnapshot {
            calls += "snapshot"
            return snapshot
        }

        override fun favoriteIds(): Set<Long> = setOf(7L)

        override fun lyricsState(): LyricsState? = null

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun queueVisible(): Boolean = queueVisible

        override fun publishFloatingLyrics(state: NowPlayingUiState) {
            calls += "floating:${state.trackTitle}"
        }

        override fun syncQueueInputs() {
            calls += "queue"
        }
    }

    companion object {
        private fun snapshot(
            trackId: Long = 7L,
            positionMs: Long = 0L,
            queueSize: Int = 1
        ): PlaybackStateSnapshot =
            PlaybackStateSnapshot(
                Track(trackId, "Song", "Artist", "Album", 180_000L, Uri.EMPTY, "file:song.mp3"),
                0,
                queueSize,
                positionMs,
                180_000L,
                true,
                false,
                "",
                false,
                PlaybackRepeatMode.REPEAT_ALL,
                1.0f,
                1.0f,
                0L
            )
    }
}
