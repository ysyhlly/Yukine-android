package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {
    @Test
    fun groupTitleAndModeTitleUseLanguageTable() {
        assertEquals("Unknown album", LibraryGrouping.groupTitle("", LibraryGrouping.ALBUMS, AppLanguage.MODE_ENGLISH))
        assertEquals("\u672a\u77e5\u4e13\u8f91", LibraryGrouping.groupTitle("", LibraryGrouping.ALBUMS, AppLanguage.MODE_CHINESE))
        assertEquals("Unknown folder", LibraryGrouping.groupTitle("", LibraryGrouping.FOLDERS, AppLanguage.MODE_ENGLISH))
        assertEquals("\u672a\u77e5\u6587\u4ef6\u5939", LibraryGrouping.groupTitle("", LibraryGrouping.FOLDERS, AppLanguage.MODE_CHINESE))
        assertEquals("Unknown artist", LibraryGrouping.groupTitle("\u672a\u77e5\u827a\u4eba", LibraryGrouping.ARTISTS, AppLanguage.MODE_ENGLISH))
        assertEquals("Unknown track", LibraryGrouping.groupTitle("\u672a\u77e5\u6b4c\u66f2", LibraryGrouping.SONGS, AppLanguage.MODE_ENGLISH))
        assertEquals("Albums", LibraryGrouping.modeTitle(LibraryGrouping.ALBUMS, AppLanguage.MODE_ENGLISH))
        assertEquals("\u4e13\u8f91", LibraryGrouping.modeTitle(LibraryGrouping.ALBUMS, AppLanguage.MODE_CHINESE))
    }

    @Test
    fun groupSubtitleUsesLocalizedTrackAndAlbumCounts() {
        val tracks = listOf(
            track(1L, "Alpha", "Artist", "Album A"),
            track(2L, "Beta", "Artist", "Album B")
        )

        assertEquals("2 albums - 2 tracks", LibraryGrouping.groupSubtitle(tracks, LibraryGrouping.ARTISTS, AppLanguage.MODE_ENGLISH))
        assertEquals("2 \u5f20\u4e13\u8f91 - 2 \u9996\u6b4c\u66f2", LibraryGrouping.groupSubtitle(tracks, LibraryGrouping.ARTISTS, AppLanguage.MODE_CHINESE))
    }

    private fun track(id: Long, title: String, artist: String, album: String): Track {
        return Track(id, title, artist, album, 1000L, Uri.EMPTY, "file:$id")
    }
}
