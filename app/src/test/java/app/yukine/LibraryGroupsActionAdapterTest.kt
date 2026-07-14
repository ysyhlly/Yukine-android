package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.util.ArrayList

class LibraryGroupsActionAdapterTest {
    @Test
    fun delegatesLibraryGroupActionsToInjectedOwners() {
        val calls = mutableListOf<String>()
        val listener = listener(calls)
        val track = track(1L)
        val tracks = listOf(track)

        listener.selectLibraryGroup("album:one", "Album One")
        listener.clearLibraryGroupSelection()
        listener.closeLibraryGroup()
        listener.openFavoritesCollection()
        listener.playTrackList(tracks, 0)
        listener.confirmDeleteGroup("Album One", tracks)

        assertEquals(
            listOf(
                "open:album:one:Album One",
                "clear",
                "close",
                "open:virtual:favorites:${AppLanguage.text(AppLanguage.MODE_ENGLISH, "favorite.playlist")}",
                "play:1:0",
                "delete:Album One:1"
            ),
            calls
        )
    }

    @Test
    fun publishesCopiedChromeState() {
        var chromeState: LibraryGroupsChromeState? = null
        val actions = mutableListOf(LibraryGroupActions(Runnable {}, Runnable {}, false, null))
        val modeActions = mutableListOf(TrackListModeAction("Albums", "albums", true, Runnable {}))
        val listener = listener(mutableListOf(), chromePublisher = { chromeState = it })

        listener.publishLibraryGroupsChrome(actions, "Empty", modeActions)

        val state = requireNotNull(chromeState)
        assertEquals("Empty", state.emptyText)
        assertEquals(actions, state.actions)
        assertEquals(modeActions, state.modeActions)
        assertNotSame(actions, state.actions)
        assertNotSame(modeActions, state.modeActions)
    }

    @Test
    fun rendersTrackListRequestThroughInjectedRenderer() {
        var request: LibraryGroupTrackListRequest? = null
        val tracks = arrayListOf(track(2L))
        val metrics = arrayListOf(TrackListHeaderMetric("Tracks", "1"))
        val headerActions = arrayListOf(TrackListHeaderAction("Play", Runnable {}))
        val footerAlbums = arrayListOf(
            TrackListAlbumCardUiState(
                title = "Album",
                subtitle = "Artist",
                coverUri = null,
                onClick = Runnable {}
            )
        )
        val listener = listener(mutableListOf(), trackListPublisher = { request = it })

        listener.publishTrackList("Album", tracks, metrics, headerActions, footerAlbums)

        val rendered = requireNotNull(request)
        assertEquals("Album", rendered.title)
        assertEquals(tracks, rendered.tracks)
        assertEquals(metrics, rendered.headerMetrics)
        assertEquals(headerActions, rendered.headerActions)
        assertEquals(footerAlbums, rendered.footerAlbums)
    }

    @Test
    fun directConstructionCreatesLibraryGroupsStateReducerListener() {
        val calls = mutableListOf<String>()
        val listener = LibraryGroupsActionAdapter(
            LibraryGroupsActionAdapter.GroupOpener { key, title -> calls += "open:$key:$title" },
            LibraryGroupsActionAdapter.GroupSelectionClearer { calls += "clear" },
            LibraryGroupsActionAdapter.GroupCloser { calls += "close" },
            LibraryGroupsActionAdapter.LanguageModeProvider { AppLanguage.MODE_ENGLISH },
            LibraryGroupsActionAdapter.TrackListPlayer { tracks, index -> calls += "play:${tracks.size}:$index" },
            LibraryGroupsActionAdapter.GroupDeleteConfirmer { title, tracks -> calls += "delete:$title:${tracks.size}" },
            LibraryGroupsActionAdapter.ChromePublisher { calls += "chrome:${it.emptyText}" },
            LibraryGroupsActionAdapter.TrackListPublisher { calls += "publish:${it.title}:${it.tracks.size}" }
        )

        listener.openFavoritesCollection()
        listener.publishLibraryGroupsChrome(emptyList(), "Empty", emptyList())
        listener.publishTrackList(
            "Tracks",
            ArrayList(listOf(track(3L))),
            ArrayList(),
            ArrayList(),
            ArrayList()
        )

        assertEquals(
            listOf(
                "open:virtual:favorites:${AppLanguage.text(AppLanguage.MODE_ENGLISH, "favorite.playlist")}",
                "chrome:Empty",
                "publish:Tracks:1"
            ),
            calls
        )
    }

    private fun listener(
        calls: MutableList<String>,
        chromePublisher: (LibraryGroupsChromeState) -> Unit = { calls += "chrome:${it.emptyText}" },
        trackListPublisher: (LibraryGroupTrackListRequest) -> Unit = { calls += "publish:${it.title}:${it.tracks.size}" }
    ): LibraryGroupsActionAdapter =
        LibraryGroupsActionAdapter(
            groupOpener = LibraryGroupsActionAdapter.GroupOpener { key, title -> calls += "open:$key:$title" },
            groupSelectionClearer = LibraryGroupsActionAdapter.GroupSelectionClearer { calls += "clear" },
            groupCloser = LibraryGroupsActionAdapter.GroupCloser { calls += "close" },
            languageModeProvider = LibraryGroupsActionAdapter.LanguageModeProvider { AppLanguage.MODE_ENGLISH },
            trackListPlayer = LibraryGroupsActionAdapter.TrackListPlayer { tracks, index ->
                calls += "play:${tracks.size}:$index"
            },
            groupDeleteConfirmer = LibraryGroupsActionAdapter.GroupDeleteConfirmer { title, tracks ->
                calls += "delete:$title:${tracks.size}"
            },
            chromePublisher = LibraryGroupsActionAdapter.ChromePublisher(chromePublisher),
            trackListPublisher = LibraryGroupsActionAdapter.TrackListPublisher(trackListPublisher)
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
