package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryAction
import app.yukine.ui.LibraryGroupSort
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackListAlbumCardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryGroupsStateReducerTest {
    @Test
    fun rendersAlbumGroupsWithEnglishLabels() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(viewModel, listener)
        val tracks = listOf(track(1L, "Track", "Artist", ""))

        controller.reduce(
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
        assertEquals(1, viewModel.libraryGroups.value.rows.single().trackCount)
        assertTrue(viewModel.libraryGroups.value.rows.single().groupKey.endsWith("\u001fArtist"))
        assertEquals("No Albums available", listener.chromeState?.emptyText)
        assertEquals(true, listener.chromeState?.actions?.single()?.playEnabled)
    }

    @Test
    fun rendersPlaylistFavoritesAndGroupDetailWithLanguageLabels() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(viewModel, listener)

        controller.reduce(
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
        controller.reduce(
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
        assertEquals(LibraryListContext.Album, listener.trackListRequest?.context)
    }

    @Test
    fun sortsByTrackCountAndKeepsStableGroupSelection() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(viewModel, listener)
        val tracks = listOf(
            track(1L, "One", "Artist", "Small"),
            track(2L, "Two", "Artist", "Large"),
            track(3L, "Three", "Artist", "Large")
        )

        controller.reduce(
            AppLanguage.MODE_ENGLISH,
            tracks,
            LibraryGrouping.ALBUMS,
            "",
            "",
            emptyList()
        )
        val smallId = viewModel.libraryGroups.value.rows.first { it.title == "Small" }.id
        viewModel.presentation.onAction(LibraryAction.ToggleGroupSelection(smallId))
        viewModel.presentation.onAction(
            LibraryAction.GroupSortChanged(LibraryGroupSort.TrackCountDescending)
        )
        controller.reduce(
            AppLanguage.MODE_ENGLISH,
            tracks,
            LibraryGrouping.ALBUMS,
            "",
            "",
            emptyList()
        )

        assertEquals(listOf("Large", "Small"), viewModel.libraryGroups.value.rows.map { it.title })
        assertEquals(listOf(2, 1), viewModel.libraryGroups.value.rows.map { it.trackCount })
        assertEquals(setOf(smallId), viewModel.libraryUi.value.selectedGroupKeys)
    }

    @Test
    fun folderDetailPublishesFolderContext() {
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(viewModel, listener)
        val folderTrack = Track(
            1L,
            "Track",
            "Artist",
            "Album",
            1000L,
            Uri.EMPTY,
            "/storage/emulated/0/Music/Echo/track.mp3"
        )

        controller.reduce(
            AppLanguage.MODE_ENGLISH,
            listOf(folderTrack),
            LibraryGrouping.FOLDERS,
            "",
            "",
            emptyList()
        )
        val group = viewModel.libraryGroups.value.rows.single()
        controller.reduce(
            AppLanguage.MODE_ENGLISH,
            listOf(folderTrack),
            LibraryGrouping.FOLDERS,
            group.groupKey,
            group.title,
            emptyList()
        )

        assertEquals(LibraryListContext.Folder, listener.trackListRequest?.context)
    }

    @Test
    fun extractsStableArtistIdFromCanonicalGroupKey() {
        assertEquals("artist-aimer", LibraryGrouping.artistIdFromGroupKey("artist:artist-aimer\u001fAimer"))
        assertEquals(null, LibraryGrouping.artistIdFromGroupKey("Aimer"))
    }

    @Test
    fun keepsLocalArtistInfoAcrossRerendersWithoutCallingNetworkRepository() {
        var localLoadCount = 0
        val viewModel = LibraryViewModel()
        viewModel.dataOwner().bindArtistIdentityProvider {
            listOf(LibraryArtistGroupIdentity("artist-aimer", "Aimer"))
        }
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(
            viewModel,
            listener,
            LibraryGroupsUiDispatcher { it.run() },
            ArtistLocalInfoSource { _, artistId, _ ->
                localLoadCount++
                ArtistInfo(
                    "Aimer",
                    "本地身份数据库",
                    "$artistId 已关联稳定的本地艺人身份。",
                    avatarUrl = "https://commons.wikimedia.org/aimer.jpg"
                )
            }
        )
        val tracks = listOf(track(1L, "花の唄", "Aimer", "ONE"))
        val groupKey = "artist:artist-aimer\u001fAimer"

        controller.reduce(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            groupKey,
            "Aimer",
            emptyList()
        )
        waitUntil { listener.artistIntro().contains("稳定的本地艺人身份") }

        controller.reduce(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            groupKey,
            "Aimer",
            emptyList()
        )

        assertTrue(listener.artistIntro().contains("稳定的本地艺人身份"))
        assertEquals("本地身份数据库", listener.headerMetric("资料来源"))
        assertEquals(
            "https://commons.wikimedia.org/aimer.jpg",
            listener.trackListRequest?.headerMetrics
                ?.first { it.label == "歌手介绍" }
                ?.artworkUri
                ?.toString()
        )
        listener.trackListRequest?.headerActions
            ?.first { it.label == "管理艺人身份" }
            ?.onClick
            ?.run()
        assertEquals("artist-aimer", listener.managedArtistId)
        assertEquals(1, localLoadCount)
    }

    @Test
    fun plainArtistGroupDoesNotStartIdentityOrNetworkLookup() {
        var localLoadCount = 0
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(
            viewModel,
            listener,
            LibraryGroupsUiDispatcher { it.run() },
            ArtistLocalInfoSource { _, _, _ ->
                localLoadCount++
                error("Unexpected local identity lookup")
            }
        )
        val tracks = listOf(track(1L, "花の唄", "Aimer", "ONE"))

        controller.reduce(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            "Aimer",
            "Aimer",
            emptyList()
        )
        assertEquals(0, localLoadCount)
        assertTrue(listener.artistIntro().contains("后台增强"))
    }

    @Test
    fun ignoresStaleLocalArtistInfoWhenAnotherArtistIsOpened() {
        val pending = ArrayList<Runnable>()
        val viewModel = LibraryViewModel()
        viewModel.dataOwner().bindArtistIdentityProvider { track ->
            val artistId = if (track.id == 1L) "artist-aimer" else "artist-lisa"
            listOf(LibraryArtistGroupIdentity(artistId, track.artist))
        }
        val listener = FakeListener()
        val controller = LibraryGroupsStateReducer(
            viewModel,
            listener,
            LibraryGroupsUiDispatcher {
                synchronized(pending) {
                    pending += it
                }
            },
            ArtistLocalInfoSource { _, artistId, _ ->
                ArtistInfo(artistId, "local", "$artistId local")
            }
        )

        controller.reduce(AppLanguage.MODE_CHINESE, listOf(track(1L, "A", "Aimer", "ONE")), LibraryGrouping.ARTISTS, "artist:artist-aimer\u001fAimer", "Aimer", emptyList())
        controller.reduce(AppLanguage.MODE_CHINESE, listOf(track(2L, "B", "LiSA", "TWO")), LibraryGrouping.ARTISTS, "artist:artist-lisa\u001fLiSA", "LiSA", emptyList())

        waitUntil {
            val callbacks = synchronized(pending) {
                pending.toList().also { pending.clear() }
            }
            callbacks.forEach { it.run() }
            listener.trackListRequest?.title == "LiSA" && listener.artistIntro().contains("artist-lisa local")
        }

        assertEquals("LiSA", listener.trackListRequest?.title)
        assertTrue(listener.artistIntro().contains("artist-lisa local"))
        assertTrue(!listener.artistIntro().contains("artist-aimer local"))
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 1000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(10)
        }
    }

    private fun track(id: Long, title: String, artist: String, album: String): Track {
        return Track(id, title, artist, album, 1000L, Uri.EMPTY, "file:$id")
    }

    private class FakeListener : LibraryGroupsStateReducer.Listener {
        var chromeState: LibraryGroupsChromeState? = null
        var trackListRequest: LibraryGroupTrackListRequest? = null
        var playedTracks: List<Track> = emptyList()
        var managedArtistId: String = ""

        override fun selectLibraryGroup(key: String, title: String) = Unit

        override fun clearLibraryGroupSelection() = Unit

        override fun closeLibraryGroup() = Unit

        override fun openFavoritesCollection() = Unit

        override fun playTrackList(tracks: List<Track>, index: Int) {
            playedTracks = tracks
        }

        override fun confirmDeleteGroup(title: String, tracks: List<Track>) = Unit

        override fun manageArtistIdentity(artistId: String, title: String) {
            managedArtistId = artistId
        }

        override fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        ) {
            chromeState = LibraryGroupsChromeState(actions, emptyText, modeActions)
        }

        override fun publishTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>,
            footerAlbums: ArrayList<TrackListAlbumCardUiState>,
            context: LibraryListContext
        ) {
            trackListRequest = LibraryGroupTrackListRequest(
                title,
                tracks,
                headerMetrics,
                headerActions,
                footerAlbums,
                context
            )
        }

        fun artistIntro(): String {
            return trackListRequest
                ?.headerMetrics
                ?.firstOrNull { it.label == "歌手介绍" || it.label == "Artist info" }
                ?.value
                .orEmpty()
        }

        fun headerMetric(label: String): String {
            return trackListRequest
                ?.headerMetrics
                ?.firstOrNull { it.label == label }
                ?.value
                .orEmpty()
        }
    }
}
