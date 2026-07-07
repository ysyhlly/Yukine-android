package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackListRenderControllerTest {
    @Test
    fun renderRecommendationPublishesLanguageAwareTrackMetric() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = TrackListRenderController(viewModel, listener)

        controller.renderRecommendation(
            "Daily",
            listOf(track(1L)),
            AppLanguage.MODE_ENGLISH
        )

        assertEquals("Daily", viewModel.trackList.value.title)
        assertEquals("Tracks", listener.chromeState?.headerMetrics?.single()?.label)
        assertEquals("1", listener.chromeState?.headerMetrics?.single()?.value)
    }

    @Test
    fun renderPublishesRowsAndActionsTogetherForSecondTrackClick() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = TrackListRenderController(viewModel, listener)
        val tracks = listOf(track(1L), track(2L), track(3L))

        controller.render(
            "Playlist",
            tracks,
            true,
            listOf("", "", ""),
            false,
            emptyList(),
            emptyList(),
            "",
            emptyList(),
            TrackListLabels(),
            null,
            emptySet()
        )
        viewModel.trackList.value.actions[1].onPlay.run()

        assertEquals(3, viewModel.trackList.value.rows.size)
        assertEquals(3, viewModel.trackList.value.actions.size)
        assertEquals(listOf("play:3:1"), listener.playCalls)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

    private class FakeListener : TrackListRenderController.Listener {
        var chromeState: TrackListChromeState? = null
        val playCalls = ArrayList<String>()

        override fun playTrackList(tracks: List<Track>, index: Int) {
            playCalls.add("play:${tracks.size}:$index")
        }

        override fun toggleFavorite(track: Track) = Unit

        override fun showAddToPlaylist(track: Track) = Unit

        override fun downloadTrack(track: Track) = Unit

        override fun downloadTracks(tracks: List<Track>) = Unit

        override fun showEditStream(track: Track) = Unit

        override fun confirmDeleteTrack(track: Track) = Unit

        override fun publishTrackListChrome(
            actions: List<TrackRowActions>,
            headerMetrics: List<TrackListHeaderMetric>,
            headerActions: List<TrackListHeaderAction>,
            emptyText: String,
            modeActions: List<TrackListModeAction>,
            labels: TrackListLabels
        ) {
            chromeState = TrackListChromeState(
                actions,
                headerMetrics,
                headerActions,
                emptyText,
                modeActions,
                labels
            )
        }
    }
}
