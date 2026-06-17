package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.ui.CollectionMetricUiState
import app.yukine.ui.CollectionsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionsViewModelTest {
    @Test
    fun updateCollectionsBuildsSelectedPlaylistDetail() {
        val viewModel = CollectionsViewModel()
        val track = track(1L)
        val playlist = Playlist(9L, "Road Mix", 2, 0L, 0L)

        viewModel.updateCollections(
            favoriteTracks = listOf(track),
            recentRecords = listOf(TrackPlayRecord(track, 100L, 1)),
            mostPlayedRecords = listOf(TrackPlayRecord(track, 200L, 3)),
            playlists = listOf(playlist),
            selectedPlaylistTracks = listOf(track),
            selectedPlaylistId = 9L,
            fallbackPlaylistTitle = "Playlist"
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.favorites.size)
        assertEquals(1, state.recentlyPlayed.size)
        assertEquals(1, state.mostPlayed.size)
        assertEquals(1, state.playlists.size)
        assertEquals(PlaylistDetailUiState(9L, "Road Mix", 1), state.selectedPlaylist)
    }

    @Test
    fun updateCollectionsClearsSelectedPlaylistWhenNoPlaylistIsSelected() {
        val viewModel = CollectionsViewModel()

        viewModel.updateCollections(
            favoriteTracks = emptyList(),
            recentRecords = emptyList(),
            mostPlayedRecords = emptyList(),
            playlists = emptyList(),
            selectedPlaylistTracks = emptyList(),
            selectedPlaylistId = -1L,
            fallbackPlaylistTitle = "Playlist"
        )

        assertNull(viewModel.uiState.value.selectedPlaylist)
    }

    @Test
    fun updateScreenPublishesComposeState() {
        val viewModel = CollectionsViewModel()
        val screen = CollectionsUiState(
            title = "Collections",
            metrics = listOf(CollectionMetricUiState("Favorites", "1")),
            topActions = emptyList(),
            trackSections = emptyList(),
            playlistTitle = "Playlists",
            playlistEmptyText = "",
            playlistEmptyDescription = "",
            playlists = emptyList(),
            selectedPlaylistVisible = false,
            selectedPlaylistTitle = "",
            selectedPlaylistEmptyText = "",
            selectedPlaylistEmptyDescription = "",
            selectedPlaylistTopActions = emptyList(),
            selectedPlaylistTracks = emptyList()
        )

        viewModel.updateScreen(screen)

        assertEquals(screen, viewModel.screen.value)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }
}
