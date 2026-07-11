package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibrarySort
import app.yukine.ui.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTrackPresentationPolicyTest {
    @Test
    fun filtersSourcesAndKeepsDetailsAttachedWhileSorting() {
        val local = track(1L, "Zulu", "/music/z.mp3")
        val stream = track(2L, "Alpha", "stream:https://example.test/a.mp3")

        val localOnly = LibraryTrackPresentationPolicy.present(
            listOf(local, stream),
            listOf("local-detail", "stream-detail"),
            LibraryUiState(filter = LibraryFilter.Local, sort = LibrarySort.TitleAscending),
            emptySet()
        )
        assertEquals(listOf(local), localOnly.map { it.track })
        assertEquals(listOf("local-detail"), localOnly.map { it.detail })

        val sorted = LibraryTrackPresentationPolicy.present(
            listOf(local, stream),
            listOf("local-detail", "stream-detail"),
            LibraryUiState(sort = LibrarySort.TitleAscending),
            emptySet()
        )
        assertEquals(listOf(stream, local), sorted.map { it.track })
        assertEquals(listOf("stream-detail", "local-detail"), sorted.map { it.detail })
    }

    private fun track(id: Long, title: String, path: String): Track = Track(
        id,
        title,
        "Artist",
        "Album",
        1000L,
        Uri.parse(if (path.startsWith("stream:")) "https://example.test/$id" else "file://$path"),
        path
    )
}
