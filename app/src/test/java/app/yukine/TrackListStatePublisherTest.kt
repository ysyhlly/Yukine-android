package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.MutablePlaybackReadModel
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackListStatePublisherTest {
    @Test
    fun libraryRequestUsesCurrentLanguageFavoritesAndPlaybackProjection() {
        val track = Track(7L, "Song", "Artist", "Album", 1_000L, Uri.EMPTY, "file:7")
        val library = MutableStateFlow(LibraryStoreState(favoriteTrackIds = setOf(track.id)))
        val settings = MutableStateFlow(
            SettingsState(
                preferences = SettingsPreferencesSnapshot(languageMode = AppLanguage.MODE_ENGLISH)
            )
        )
        val viewModel = LibraryViewModel()
        val publisher = TrackListStatePublisher(
            TrackListStateReducer(viewModel, FakeListener()),
            library,
            settings,
            MutablePlaybackReadModel()
        )

        publisher.publishLibraryGroup(
            LibraryGroupTrackListRequest(
                title = "Album",
                tracks = arrayListOf(track),
                headerMetrics = arrayListOf(TrackListHeaderMetric("Tracks", "1")),
                headerActions = arrayListOf(TrackListHeaderAction("Action", Runnable {}))
            )
        )

        val state = viewModel.trackList.value
        assertEquals("Album", state.title)
        assertTrue(state.rows.single().favorite)
        assertEquals(AppLanguage.text(AppLanguage.MODE_ENGLISH, "remove.favorite"), state.labels.removeFavoriteLabel)
    }

    private class FakeListener : TrackListStateReducer.Listener {
        override fun playTrackList(tracks: List<Track>, index: Int) = Unit
        override fun toggleFavorite(track: Track) = Unit
        override fun showAddToPlaylist(track: Track) = Unit
        override fun downloadTrack(track: Track) = Unit
        override fun downloadTracks(tracks: List<Track>) = Unit
        override fun showEditStream(track: Track) = Unit
        override fun confirmDeleteTrack(track: Track) = Unit
    }
}
