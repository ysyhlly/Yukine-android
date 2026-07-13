package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
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
    fun directConstructionCreatesTrackListRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainTrackListRenderListener(
            MainTrackListRenderListener.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            MainTrackListRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            MainTrackListRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            MainTrackListRenderListener.TrackDownloader { calls += "download:${it.id}" },
            MainTrackListRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            MainTrackListRenderListener.StreamEditor { calls += "edit:${it.id}" },
            MainTrackListRenderListener.TrackDeleteConfirmer { calls += "delete:${it.id}" }
        )

        listener.playTrackList(listOf(track(2L)), 0)

        assertEquals(listOf("play:1:0"), calls)
    }

    private fun listener(calls: MutableList<String>): MainTrackListRenderListener =
        MainTrackListRenderListener(
            trackListPlayer = MainTrackListRenderListener.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = MainTrackListRenderListener.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = MainTrackListRenderListener.PlaylistAdder { calls += "playlist:${it.id}" },
            trackDownloader = MainTrackListRenderListener.TrackDownloader { calls += "download:${it.id}" },
            tracksDownloader = MainTrackListRenderListener.TracksDownloader { calls += "download-list:${it.size}" },
            streamEditor = MainTrackListRenderListener.StreamEditor { calls += "edit:${it.id}" },
            trackDeleteConfirmer = MainTrackListRenderListener.TrackDeleteConfirmer { calls += "delete:${it.id}" }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
