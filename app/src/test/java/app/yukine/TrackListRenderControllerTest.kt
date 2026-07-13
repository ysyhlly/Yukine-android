package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.TrackListLabels
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryMode
import app.yukine.ui.EchoIconKind
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderActionKind
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackListRenderControllerTest {
    @Test
    fun songsRootKeepsPlayActionsButOmitsDownloadCurrentList() {
        val viewModel = LibraryViewModel()
        val controller = TrackListRenderController(viewModel, FakeListener())
        val labels = TrackListLabels()

        controller.render(
            "Songs", listOf(track(1L)), true, listOf(""), false,
            emptyList(), emptyList(), "",
            listOf(TrackListModeAction("Songs", "songs", true, Runnable { })),
            labels, null, emptySet()
        )

        assertEquals(
            listOf(labels.playAllLabel, labels.shuffleLabel),
            viewModel.trackList.value.headerActions.map { it.label }
        )
    }

    @Test
    fun playlistDetailDoesNotAddDuplicatePlayAllAction() {
        val viewModel = LibraryViewModel()
        viewModel.presentation.onAction(LibraryAction.ModeChanged(LibraryMode.Playlists))
        val controller = TrackListRenderController(viewModel, FakeListener())
        val labels = TrackListLabels()
        val playPlaylist = TrackListHeaderAction("播放歌单", Runnable { })

        controller.render(
            "Playlist", listOf(track(1L)), true, listOf(""), false,
            emptyList(), listOf(playPlaylist), "",
            listOf(TrackListModeAction("Playlists", "playlists", true, Runnable { })),
            labels, null, emptySet()
        )

        assertEquals(
            listOf("播放歌单", labels.downloadCurrentListLabel),
            viewModel.trackList.value.headerActions.map { it.label }
        )
    }

    @Test
    fun headerActionDeduplicationUsesSemanticKindNotLabel() {
        val viewModel = LibraryViewModel()
        val controller = TrackListRenderController(viewModel, FakeListener())
        val labels = TrackListLabels()
        val existingPlayAll = TrackListHeaderAction(
            "本地化播放全部",
            Runnable { },
            icon = EchoIconKind.Play,
            kind = TrackListHeaderActionKind.PlayAll
        )

        controller.render(
            "Songs", listOf(track(1L)), true, listOf(""), false,
            emptyList(), listOf(existingPlayAll), "",
            listOf(TrackListModeAction("Songs", "songs", true, Runnable { })),
            labels, null, emptySet()
        )

        assertEquals(
            listOf(existingPlayAll.label, labels.shuffleLabel),
            viewModel.trackList.value.headerActions.map { it.label }
        )
    }

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
        assertEquals("Tracks", viewModel.trackList.value.headerMetrics.single().label)
        assertEquals("1", viewModel.trackList.value.headerMetrics.single().value)
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
    }
}
