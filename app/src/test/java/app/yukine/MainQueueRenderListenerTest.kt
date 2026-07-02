package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackActions
import org.junit.Assert.assertEquals
import org.junit.Test

class MainQueueRenderListenerTest {
    @Test
    fun delegatesQueueRenderActionsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val track = track(1L)
        val tracks = listOf(track)
        val listener = listener(calls)

        listener.playTrackList(tracks, 0)
        listener.toggleFavorite(track)
        listener.showAddToPlaylist(track)
        listener.removeQueueTrack(track)
        listener.confirmClearQueue()
        listener.requestBack()

        assertEquals(
            listOf(
                "play:1:0",
                "favorite:1",
                "playlist:1",
                "remove:1",
                "clear",
                "back"
            ),
            calls
        )
    }

    @Test
    fun publishQueueChromeKeepsLegacyNoOpBehavior() {
        val calls = mutableListOf<String>()
        val listener = listener(calls)

        listener.publishQueueChrome(
            listOf(QueueTrackActions(Runnable {}, Runnable {}, Runnable {}, Runnable {})),
            Runnable { calls += "clear-action" },
            QueueScreenLabels(title = "Queue"),
            Runnable { calls += "back-action" }
        )

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun factoryCreatesQueueRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = PlaybackUiModule.provideMainQueueRenderListenerFactory().create(
            MainQueueRenderListener.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            MainQueueRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            MainQueueRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            MainQueueRenderListener.QueueTrackRemover { calls += "remove:${it.id}" },
            MainQueueRenderListener.ClearQueueConfirmer { calls += "clear" },
            MainQueueRenderListener.BackRequester { calls += "back" }
        )

        listener.playTrackList(listOf(track(2L)), 0)
        listener.confirmClearQueue()
        listener.requestBack()

        assertEquals(listOf("play:1:0", "clear", "back"), calls)
    }

    private fun listener(calls: MutableList<String>): MainQueueRenderListener =
        MainQueueRenderListener(
            trackListPlayer = MainQueueRenderListener.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = MainQueueRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = MainQueueRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            queueTrackRemover = MainQueueRenderListener.QueueTrackRemover { calls += "remove:${it.id}" },
            clearQueueConfirmer = MainQueueRenderListener.ClearQueueConfirmer { calls += "clear" },
            backRequester = MainQueueRenderListener.BackRequester { calls += "back" }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
