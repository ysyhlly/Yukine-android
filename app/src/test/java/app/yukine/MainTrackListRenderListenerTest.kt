package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class MainTrackListRenderListenerTest {
    @Test
    fun delegatesTrackListActionsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val track = track(1L)
        val tracks = listOf(track)
        val listener = listener(calls)

        listener.playTrackList(tracks, 0)
        listener.toggleFavorite(track)
        listener.showAddToPlaylist(track)
        listener.downloadTrack(track)
        listener.downloadTracks(tracks)
        listener.showEditStream(track)
        listener.confirmDeleteTrack(track)

        assertEquals(
            listOf(
                "play:1:0",
                "favorite:1",
                "playlist:1",
                "download:1",
                "download-list:1",
                "edit:1",
                "delete:1"
            ),
            calls
        )
    }

    @Test
    fun publishesCopiedTrackListChromeState() {
        var chromeState: TrackListChromeState? = null
        val actions = mutableListOf(
            TrackRowActions(Runnable {}, Runnable {}, Runnable {}, Runnable {})
        )
        val metrics = mutableListOf(TrackListHeaderMetric("Tracks", "1"))
        val headerActions = mutableListOf(TrackListHeaderAction("Download", Runnable {}))
        val modeActions = mutableListOf(TrackListModeAction("Songs", "songs", true, Runnable {}))
        val labels = TrackListLabels(favoriteLabel = "Fav")
        val listener = listener(mutableListOf()) { chromeState = it }

        listener.publishTrackListChrome(
            actions,
            metrics,
            headerActions,
            "Empty",
            modeActions,
            labels
        )

        val state = requireNotNull(chromeState)
        assertEquals("Empty", state.emptyText)
        assertEquals(labels, state.labels)
        assertEquals(actions, state.actions)
        assertEquals(metrics, state.headerMetrics)
        assertEquals(headerActions, state.headerActions)
        assertEquals(modeActions, state.modeActions)
        assertNotSame(actions, state.actions)
        assertNotSame(metrics, state.headerMetrics)
        assertNotSame(headerActions, state.headerActions)
        assertNotSame(modeActions, state.modeActions)
    }

    @Test
    fun factoryCreatesTrackListRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = LibraryModule.provideMainTrackListRenderListenerFactory().create(
            MainTrackListRenderListener.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            MainTrackListRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            MainTrackListRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            MainTrackListRenderListener.TrackDownloader { calls += "download:${it.id}" },
            MainTrackListRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            MainTrackListRenderListener.StreamEditor { calls += "edit:${it.id}" },
            MainTrackListRenderListener.TrackDeleteConfirmer { calls += "delete:${it.id}" },
            MainTrackListRenderListener.ChromePublisher { calls += "chrome:${it.emptyText}" }
        )

        listener.playTrackList(listOf(track(2L)), 0)
        listener.publishTrackListChrome(
            emptyList(),
            emptyList(),
            emptyList(),
            "Empty",
            emptyList(),
            TrackListLabels()
        )

        assertEquals(listOf("play:1:0", "chrome:Empty"), calls)
    }

    private fun listener(
        calls: MutableList<String>,
        chromePublisher: (TrackListChromeState) -> Unit = { calls += "chrome:${it.emptyText}" }
    ): MainTrackListRenderListener =
        MainTrackListRenderListener(
            trackListPlayer = MainTrackListRenderListener.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = MainTrackListRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = MainTrackListRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            trackDownloader = MainTrackListRenderListener.TrackDownloader { calls += "download:${it.id}" },
            tracksDownloader = MainTrackListRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            streamEditor = MainTrackListRenderListener.StreamEditor { calls += "edit:${it.id}" },
            trackDeleteConfirmer = MainTrackListRenderListener.TrackDeleteConfirmer { calls += "delete:${it.id}" },
            chromePublisher = MainTrackListRenderListener.ChromePublisher(chromePublisher)
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
