package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackListActionAdapterTest {
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
        listener.showRecordingMatch(track)

        assertEquals(
            listOf(
                "play:1:0",
                "favorite:1",
                "playlist:1",
                "download:1",
                "download-list:1",
                "edit:1",
                "delete:1",
                "match:1"
            ),
            calls
        )
    }

    @Test
    fun directConstructionCreatesTrackListStateReducerListener() {
        val calls = mutableListOf<String>()
        val listener = TrackListActionAdapter(
            TrackListActionAdapter.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            TrackListActionAdapter.FavoriteToggler { calls += "favorite:${it.id}" },
            TrackListActionAdapter.PlaylistAdder { calls += "playlist:${it.id}" },
            TrackListActionAdapter.TrackDownloader { calls += "download:${it.id}" },
            TrackListActionAdapter.TracksDownloader { calls += "download-list:${it.size}" },
            TrackListActionAdapter.StreamEditor { calls += "edit:${it.id}" },
            TrackListActionAdapter.TrackDeleteConfirmer { calls += "delete:${it.id}" },
            TrackListActionAdapter.RecordingMatchOpener { calls += "match:${it.id}" }
        )

        listener.playTrackList(listOf(track(2L)), 0)

        assertEquals(listOf("play:1:0"), calls)
    }

    private fun listener(calls: MutableList<String>): TrackListActionAdapter =
        TrackListActionAdapter(
            trackListPlayer = TrackListActionAdapter.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            favoriteToggler = TrackListActionAdapter.FavoriteToggler { calls += "favorite:${it.id}" },
            playlistAdder = TrackListActionAdapter.PlaylistAdder { calls += "playlist:${it.id}" },
            trackDownloader = TrackListActionAdapter.TrackDownloader { calls += "download:${it.id}" },
            tracksDownloader = TrackListActionAdapter.TracksDownloader { calls += "download-list:${it.size}" },
            streamEditor = TrackListActionAdapter.StreamEditor { calls += "edit:${it.id}" },
            trackDeleteConfirmer = TrackListActionAdapter.TrackDeleteConfirmer { calls += "delete:${it.id}" },
            recordingMatchOpener = TrackListActionAdapter.RecordingMatchOpener { calls += "match:${it.id}" }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
