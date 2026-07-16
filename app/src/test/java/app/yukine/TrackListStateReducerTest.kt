package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.TrackListLabels
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryMode
import app.yukine.ui.LibraryFilter
import app.yukine.ui.EchoIconKind
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderActionKind
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class TrackListStateReducerTest {
    @Test
    fun songsRootKeepsPlayActionsButOmitsDownloadCurrentList() {
        val viewModel = LibraryViewModel()
        val controller = TrackListStateReducer(viewModel, FakeListener())
        val labels = TrackListLabels()

        controller.reduce(
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
        val controller = TrackListStateReducer(viewModel, FakeListener())
        val labels = TrackListLabels()
        val playPlaylist = TrackListHeaderAction("播放歌单", Runnable { })

        controller.reduce(
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
        val controller = TrackListStateReducer(viewModel, FakeListener())
        val labels = TrackListLabels()
        val existingPlayAll = TrackListHeaderAction(
            "本地化播放全部",
            Runnable { },
            icon = EchoIconKind.Play,
            kind = TrackListHeaderActionKind.PlayAll
        )

        controller.reduce(
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
        val controller = TrackListStateReducer(viewModel, listener)

        controller.reduceRecommendation(
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
        val controller = TrackListStateReducer(viewModel, listener)
        val tracks = listOf(track(1L), track(2L), track(3L))

        controller.reduce(
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

    @Test
    fun rowPublishesRecordingMatchManagementActionForTheExactTrack() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = TrackListStateReducer(viewModel, listener)

        controller.reduce(
            "Songs", listOf(track(7L)), true, listOf(""), false,
            emptyList(), emptyList(), "", emptyList(), TrackListLabels(), null, emptySet()
        )
        viewModel.trackList.value.actions.single().onMatchManagement?.run()

        assertEquals(listOf(7L), listener.matchTrackIds)
    }

    @Test
    fun favoriteOnlyChangeReusesUnchangedRowsAndExistingActions() {
        val viewModel = LibraryViewModel()
        val controller = TrackListStateReducer(viewModel, FakeListener())
        val tracks = listOf(track(21L), track(22L), track(23L))
        val modes = listOf(TrackListModeAction("Songs", "songs", true, Runnable { }))

        controller.reduce(
            "Songs", tracks, true, emptyList(), false,
            emptyList(), emptyList(), "", modes, TrackListLabels(), null, emptySet()
        )
        val beforeRows = viewModel.trackList.value.rows
        val beforeActions = viewModel.trackList.value.actions

        controller.reduce(
            "Songs", tracks, true, emptyList(), false,
            emptyList(), emptyList(), "", modes, TrackListLabels(), null, setOf(22L)
        )
        val after = viewModel.trackList.value

        assertSame(beforeRows[0], after.rows[0])
        assertNotSame(beforeRows[1], after.rows[1])
        assertSame(beforeRows[2], after.rows[2])
        assertSame(beforeActions, after.actions)
        assertEquals(true, after.rows[1].favorite)
    }

    @Test
    fun favoriteFilterRebuildsMembershipWhenFavoriteIdsChange() {
        val viewModel = LibraryViewModel()
        viewModel.presentation.onAction(LibraryAction.FilterChanged(LibraryFilter.Favorites))
        val controller = TrackListStateReducer(viewModel, FakeListener())
        val tracks = listOf(track(31L), track(32L))
        val modes = listOf(TrackListModeAction("Songs", "songs", true, Runnable { }))

        controller.reduce(
            "Songs", tracks, true, emptyList(), false,
            emptyList(), emptyList(), "", modes, TrackListLabels(), null, setOf(31L)
        )
        assertEquals(listOf(31L), viewModel.trackList.value.rows.map { it.id })

        controller.reduce(
            "Songs", tracks, true, emptyList(), false,
            emptyList(), emptyList(), "", modes, TrackListLabels(), null, setOf(32L)
        )

        assertEquals(listOf(32L), viewModel.trackList.value.rows.map { it.id })
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

    private class FakeListener : TrackListStateReducer.Listener {
        val playCalls = ArrayList<String>()
        val matchTrackIds = ArrayList<Long>()

        override fun playTrackList(tracks: List<Track>, index: Int) {
            playCalls.add("play:${tracks.size}:$index")
        }

        override fun toggleFavorite(track: Track) = Unit

        override fun showAddToPlaylist(track: Track) = Unit

        override fun showRecordingMatch(track: Track) {
            matchTrackIds += track.id
        }

        override fun downloadTrack(track: Track) = Unit

        override fun downloadTracks(tracks: List<Track>) = Unit

        override fun showEditStream(track: Track) = Unit

        override fun confirmDeleteTrack(track: Track) = Unit
    }
}
