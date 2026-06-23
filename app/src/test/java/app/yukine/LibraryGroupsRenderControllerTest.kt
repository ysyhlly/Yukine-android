package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackListAlbumCardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun keepsLoadedArtistInfoAcrossArtistGroupRerenders() {
        var fetchCount = 0
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                when {
                    url.contains("api/cloudsearch/pc") -> """{"result":{"artists":[{"id":16152,"name":"Aimer"}]}}"""
                    url.contains("artist/head/info/get?id=16152") -> {
                        fetchCount++
                        """{"code":200,"data":{"artist":{"id":16152,"name":"Aimer","briefDesc":"Aimer 是一名日本女歌手。","musicSize":531,"albumSize":108}}}"""
                    }
                    url.contains("artist/introduction?id=16152") -> """{"introduction":[]}"""
                    else -> error("Unexpected URL $url")
                }
            }
        )
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(
            viewModel,
            listener,
            repository,
            LibraryGroupsUiDispatcher { it.run() }
        )
        val tracks = listOf(track(1L, "花の唄", "Aimer", "ONE"))

        controller.render(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            "Aimer",
            "Aimer",
            emptyList()
        )
        waitUntil { listener.artistIntro().contains("Aimer 是一名日本女歌手") }
        assertTrue(listener.artistIntro().contains("Aimer 是一名日本女歌手"))

        controller.render(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            "Aimer",
            "Aimer",
            emptyList()
        )

        assertTrue(listener.artistIntro().contains("Aimer 是一名日本女歌手"))
        assertEquals(2, fetchCount)
    }

    @Test
    fun artistGroupFooterShowsOnlineAlbumsAsPlayableCards() {
        var albumTrackFetchCount = 0
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                when {
                    url.contains("api/cloudsearch/pc") -> """{"result":{"artists":[{"id":16152,"name":"Aimer"}]}}"""
                    url.contains("artist/head/info/get?id=16152") -> """{"code":200,"data":{"artist":{"id":16152,"name":"Aimer","briefDesc":"Aimer 是一名日本女歌手。","albumSize":1}}}"""
                    url.contains("artist/introduction?id=16152") -> """{"introduction":[]}"""
                    url.contains("api/artist/albums/16152") -> """{"hotAlbums":[{"id":9001,"name":"Walpurgis","picUrl":"https://img.example/a.jpg","size":2,"artists":[{"name":"Aimer"}]}]}"""
                    url.contains("api/album/9001") -> {
                        albumTrackFetchCount++
                        """{"songs":[{"id":11,"name":"春はゆく","duration":300000,"artists":[{"name":"Aimer"}],"album":{"picUrl":"https://img.example/a.jpg"}},{"id":12,"name":"残響散歌","duration":240000,"artists":[{"name":"Aimer"}],"album":{"picUrl":"https://img.example/a.jpg"}}]}"""
                    }
                    else -> error("Unexpected URL $url")
                }
            }
        )
        val viewModel = LibraryViewModel()
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(
            viewModel,
            listener,
            repository,
            LibraryGroupsUiDispatcher { it.run() }
        )
        val tracks = listOf(track(1L, "花の唄", "Aimer", "ONE"))

        controller.render(
            AppLanguage.MODE_CHINESE,
            tracks,
            LibraryGrouping.ARTISTS,
            "Aimer",
            "Aimer",
            emptyList()
        )
        waitUntil { listener.trackListRequest?.footerAlbums?.isNotEmpty() == true }

        val album = listener.trackListRequest?.footerAlbums?.single()
        assertEquals("Walpurgis", album?.title)
        assertTrue(album?.subtitle.orEmpty().contains("2 首"))
        assertEquals(0, albumTrackFetchCount)
        album?.onClick?.run()
        waitUntil { listener.playedTracks.isNotEmpty() }
        assertEquals(listOf("春はゆく", "残響散歌"), listener.playedTracks.map { it.title })
        assertEquals(1, albumTrackFetchCount)
    }

    @Test
    fun artistPreviewRendersBeforeSlowIntroductionCompletes() {
        val pendingIntro = Object()
        var releaseIntro = false
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                when {
                    url.contains("api/cloudsearch/pc") -> """{"result":{"artists":[{"id":16152,"name":"Aimer"}]}}"""
                    url.contains("artist/head/info/get?id=16152") -> """{"code":200,"data":{"artist":{"id":16152,"name":"Aimer","briefDesc":"Preview bio","albumSize":1}}}"""
                    url.contains("api/artist/albums/16152") -> """{"hotAlbums":[{"id":9001,"name":"Walpurgis","picUrl":"https://img.example/a.jpg","size":2,"artists":[{"name":"Aimer"}]}]}"""
                    url.contains("artist/introduction?id=16152") -> {
                        synchronized(pendingIntro) {
                            while (!releaseIntro) {
                                pendingIntro.wait(20)
                            }
                        }
                        """{"introduction":[{"ti":"详细介绍","txt":"Full bio"}]}"""
                    }
                    else -> error("Unexpected URL $url")
                }
            }
        )
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(
            LibraryViewModel(),
            listener,
            repository,
            LibraryGroupsUiDispatcher { it.run() }
        )

        controller.render(AppLanguage.MODE_CHINESE, listOf(track(1L, "花の唄", "Aimer", "ONE")), LibraryGrouping.ARTISTS, "Aimer", "Aimer", emptyList())

        waitUntil { listener.trackListRequest?.footerAlbums?.isNotEmpty() == true }
        assertTrue(listener.artistIntro().contains("Preview bio"))
        assertTrue(!listener.artistIntro().contains("Full bio"))

        synchronized(pendingIntro) {
            releaseIntro = true
            pendingIntro.notifyAll()
        }
        waitUntil { listener.artistIntro().contains("Full bio") }
        assertTrue(listener.artistIntro().contains("Full bio"))
    }

    @Test
    fun ignoresStaleArtistInfoWhenAnotherArtistIsOpened() {
        val pending = ArrayList<Runnable>()
        val repository = ArtistInfoRepository(
            textFetcher = { url ->
                when {
                    url.contains("api/cloudsearch/pc") && url.contains("Aimer") -> """{"result":{"artists":[{"id":1,"name":"Aimer"}]}}"""
                    url.contains("api/cloudsearch/pc") && url.contains("LiSA") -> """{"result":{"artists":[{"id":2,"name":"LiSA"}]}}"""
                    url.contains("artist/head/info/get?id=1") -> """{"code":200,"data":{"artist":{"id":1,"name":"Aimer","briefDesc":"Aimer online"}}}"""
                    url.contains("artist/head/info/get?id=2") -> """{"code":200,"data":{"artist":{"id":2,"name":"LiSA","briefDesc":"LiSA online"}}}"""
                    url.contains("artist/introduction") -> """{"introduction":[]}"""
                    url.contains("api/artist/albums/") -> """{"hotAlbums":[]}"""
                    else -> error("Unexpected URL $url")
                }
            }
        )
        val listener = FakeListener()
        val controller = LibraryGroupsRenderController(
            LibraryViewModel(),
            listener,
            repository,
            LibraryGroupsUiDispatcher { pending += it }
        )

        controller.render(AppLanguage.MODE_CHINESE, listOf(track(1L, "A", "Aimer", "ONE")), LibraryGrouping.ARTISTS, "Aimer", "Aimer", emptyList())
        controller.render(AppLanguage.MODE_CHINESE, listOf(track(2L, "B", "LiSA", "TWO")), LibraryGrouping.ARTISTS, "LiSA", "LiSA", emptyList())

        waitUntil { pending.size >= 2 }
        pending.toList().forEach { it.run() }

        assertEquals("LiSA", listener.trackListRequest?.title)
        assertTrue(listener.artistIntro().contains("LiSA online"))
        assertTrue(!listener.artistIntro().contains("Aimer online"))
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

    private class FakeListener : LibraryGroupsRenderController.Listener {
        var chromeState: LibraryGroupsChromeState? = null
        var trackListRequest: LibraryGroupTrackListRequest? = null
        var playedTracks: List<Track> = emptyList()

        override fun selectLibraryGroup(key: String, title: String) = Unit

        override fun clearLibraryGroupSelection() = Unit

        override fun closeLibraryGroup() = Unit

        override fun openFavoritesCollection() = Unit

        override fun playTrackList(tracks: List<Track>, index: Int) {
            playedTracks = tracks
        }

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
            headerActions: ArrayList<TrackListHeaderAction>,
            footerAlbums: ArrayList<TrackListAlbumCardUiState>
        ) {
            trackListRequest = LibraryGroupTrackListRequest(title, tracks, headerMetrics, headerActions, footerAlbums)
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
