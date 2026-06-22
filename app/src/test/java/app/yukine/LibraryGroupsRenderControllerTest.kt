package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList

class LibraryGroupsRenderControllerTest {
    @Test
    fun rendersAlbumGroupsWithEnglishLabels() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(viewModel, listener)
        val tracks = listOf(track(1L, "Track", "Artist", ""))

        controller.render(
            AppLanguage.MODE_ENGLISH,
            tracks,
            LibraryGrouping.ALBUMS,
            "",
            "",
            emptyList()
        )

        assertEquals("Albums", viewModel.libraryGroups.value.title)
        assertEquals("Unknown album", viewModel.libraryGroups.value.rows.single().title)
        assertEquals("Artist - 1 track", viewModel.libraryGroups.value.rows.single().subtitle)
        assertEquals("No Albums available", listener.chromeState?.emptyText)
        assertEquals(true, listener.chromeState?.actions?.single()?.playEnabled)
    }

    @Test
    fun rendersPlaylistFavoritesAndGroupDetailWithLanguageLabels() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(viewModel, listener)

        controller.render(
            AppLanguage.MODE_ENGLISH,
            emptyList(),
            LibraryGrouping.PLAYLISTS,
            "",
            "",
            emptyList()
        )

        assertEquals("Favorites playlist", viewModel.libraryGroups.value.rows.single().title)
        assertEquals("Open collected tracks", viewModel.libraryGroups.value.rows.single().subtitle)

        val key = "Album\u001fArtist"
        controller.render(
            AppLanguage.MODE_ENGLISH,
            listOf(track(1L, "Track", "Artist", "Album")),
            LibraryGrouping.ALBUMS,
            key,
            "Album",
            emptyList()
        )

        assertEquals("Album", listener.trackListRequest?.title)
        assertEquals("Tracks", listener.trackListRequest?.headerMetrics?.single()?.label)
        assertEquals(listOf("Back", "Play group"), listener.trackListRequest?.headerActions?.map { it.label })
    }

    private fun track(id: Long, title: String, artist: String, album: String): Track {
        return Track(id, title, artist, album, 1000L, Uri.EMPTY, "file:$id")
    }

    private class FakeListener : LibraryGroupsRenderController.Listener {
        var chromeState: LibraryGroupsChromeState? = null
        var trackListRequest: LibraryGroupTrackListRequest? = null

        override fun selectLibraryGroup(key: String, title: String) = Unit

        override fun clearLibraryGroupSelection() = Unit

        override fun closeLibraryGroup() = Unit

        override fun openFavoritesCollection() = Unit

        override fun playTrackList(tracks: List<Track>, index: Int) = Unit

        override fun confirmDeleteGroup(title: String, tracks: List<Track>) = Unit

        override fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        ) {
            chromeState = LibraryGroupsChromeState(actions, emptyText, modeActions)
        }

        override fun renderTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>
        ) {
            trackListRequest = LibraryGroupTrackListRequest(title, tracks, headerMetrics, headerActions)
        }
    }
}
