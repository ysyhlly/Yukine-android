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

    @Test
    fun precomputedSortKeysPreserveEverySortOrder() {
        val first = track(1L, "Beta", "/music/1.mp3", "Zulu", "One", 3_000L)
        val second = track(2L, "alpha", "/music/2.mp3", "Artist", "Two", 2_000L)
        val third = track(3L, "Gamma", "/music/3.mp3", "Artist", "One", 2_000L)
        val tracks = listOf(first, second, third)

        fun ids(sort: LibrarySort): List<Long> = LibraryTrackPresentationPolicy.present(
            tracks,
            emptyList(),
            LibraryUiState(sort = sort),
            emptySet()
        ).map { it.track.id }

        assertEquals(listOf(2L, 1L, 3L), ids(LibrarySort.TitleAscending))
        assertEquals(listOf(3L, 1L, 2L), ids(LibrarySort.TitleDescending))
        assertEquals(listOf(2L, 3L, 1L), ids(LibrarySort.Artist))
        assertEquals(listOf(1L, 3L, 2L), ids(LibrarySort.Album))
        assertEquals(listOf(2L, 3L, 1L), ids(LibrarySort.DurationAscending))
        assertEquals(listOf(1L, 2L, 3L), ids(LibrarySort.DurationDescending))
    }

    @Test
    fun dateAddedSortUsesUpdatedAtField() {
        val oldest = trackWithUpdatedAt(1L, "Old", 100L)
        val newest = trackWithUpdatedAt(2L, "New", 300L)
        val middle = trackWithUpdatedAt(3L, "Mid", 200L)
        val tracks = listOf(oldest, newest, middle)

        val descending = LibraryTrackPresentationPolicy.present(
            tracks, emptyList(), LibraryUiState(sort = LibrarySort.DateAddedDescending), emptySet()
        ).map { it.track.id }
        assertEquals(listOf(2L, 3L, 1L), descending)

        val ascending = LibraryTrackPresentationPolicy.present(
            tracks, emptyList(), LibraryUiState(sort = LibrarySort.DateAddedAscending), emptySet()
        ).map { it.track.id }
        assertEquals(listOf(1L, 3L, 2L), ascending)
    }

    @Test
    fun playCountSortUsesProvidedMap() {
        val low = track(1L, "Low", "/music/1.mp3")
        val high = track(2L, "High", "/music/2.mp3")
        val mid = track(3L, "Mid", "/music/3.mp3")
        val tracks = listOf(low, high, mid)
        val playCounts = mapOf(1L to 2, 2L to 10, 3L to 5)

        val sorted = LibraryTrackPresentationPolicy.present(
            tracks, emptyList(), LibraryUiState(sort = LibrarySort.PlayCountDescending), emptySet(), playCounts
        ).map { it.track.id }
        assertEquals(listOf(2L, 3L, 1L), sorted)
    }

    private fun track(
        id: Long,
        title: String,
        path: String,
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 1_000L
    ): Track = Track(
        id,
        title,
        artist,
        album,
        durationMs,
        Uri.parse(if (path.startsWith("stream:")) "https://example.test/$id" else "file://$path"),
        path
    )

    @Test
    fun streamingPrefixTracksClassifiedAsNetworkSource() {
        val local = track(1L, "Local", "/music/local.mp3")
        val bilibili = track(2L, "Bilibili Video", "streaming:bilibili:video:BV1TEST:cid:42")
        val legacyStream = track(3L, "Legacy Stream", "stream:https://example.test/a.mp3")

        val networkOnly = LibraryTrackPresentationPolicy.present(
            listOf(local, bilibili, legacyStream),
            emptyList(),
            LibraryUiState(filter = LibraryFilter.Network, sort = LibrarySort.TitleAscending),
            emptySet()
        )
        assertEquals(listOf(2L, 3L), networkOnly.map { it.track.id })

        val localOnly = LibraryTrackPresentationPolicy.present(
            listOf(local, bilibili, legacyStream),
            emptyList(),
            LibraryUiState(filter = LibraryFilter.Local, sort = LibrarySort.TitleAscending),
            emptySet()
        )
        assertEquals(listOf(1L), localOnly.map { it.track.id })
    }

    private fun trackWithUpdatedAt(
        id: Long,
        title: String,
        updatedAt: Long
    ): Track = Track(
        id, title, "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id", 0L, null,
        "", 0, 0, 0, 0, 0.0f, 0.0f,
        app.yukine.model.TrackIdentityTags.EMPTY, "", "", "", 0, updatedAt
    )
}
