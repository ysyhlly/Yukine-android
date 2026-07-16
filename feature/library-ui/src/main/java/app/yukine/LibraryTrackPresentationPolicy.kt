package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryFilter
import app.yukine.ui.LibrarySort
import app.yukine.ui.LibrarySourceKind
import app.yukine.ui.LibraryUiState
import java.util.Locale

data class LibraryPresentedTrack(val track: Track, val detail: String)

object LibraryTrackPresentationPolicy {
    fun present(
        tracks: List<Track>,
        details: List<String>,
        uiState: LibraryUiState,
        favoriteIds: Set<Long>
    ): List<LibraryPresentedTrack> {
        val values = ArrayList<SortablePresentedTrack>(tracks.size)
        tracks.forEachIndexed { index, track ->
            if (!included(track, uiState.filter, favoriteIds)) return@forEachIndexed
            val value = LibraryPresentedTrack(track, details.getOrElse(index) { "" })
            values += sortable(value, uiState.sort)
        }
        val comparator = when (uiState.sort) {
            LibrarySort.TitleAscending -> compareBy<SortablePresentedTrack> { it.primaryText }
            LibrarySort.TitleDescending -> compareByDescending<SortablePresentedTrack> { it.primaryText }
            LibrarySort.Artist,
            LibrarySort.Album -> compareBy<SortablePresentedTrack> { it.primaryText }
                .thenBy { it.secondaryText }
            LibrarySort.DurationAscending -> compareBy<SortablePresentedTrack> { it.durationMs }
                .thenBy { it.secondaryText }
            LibrarySort.DurationDescending -> compareByDescending<SortablePresentedTrack> { it.durationMs }
                .thenBy { it.secondaryText }
        }
        values.sortWith(comparator)
        return values.map { it.value }
    }

    private fun included(track: Track, filter: LibraryFilter, favoriteIds: Set<Long>): Boolean =
        when (filter) {
            LibraryFilter.All -> true
            LibraryFilter.Favorites -> track.id in favoriteIds
            LibraryFilter.Local -> when (sourceKind(track)) {
                LibrarySourceKind.MediaStore, LibrarySourceKind.Document -> true
                else -> false
            }
            LibraryFilter.Network -> when (sourceKind(track)) {
                LibrarySourceKind.Stream, LibrarySourceKind.WebDav -> true
                else -> false
            }
        }

    private fun sortable(
        value: LibraryPresentedTrack,
        sort: LibrarySort
    ): SortablePresentedTrack = when (sort) {
        LibrarySort.TitleAscending,
        LibrarySort.TitleDescending -> SortablePresentedTrack(
            value = value,
            primaryText = normalized(value.track.title)
        )
        LibrarySort.Artist -> SortablePresentedTrack(
            value = value,
            primaryText = normalized(value.track.artist),
            secondaryText = normalized(value.track.title)
        )
        LibrarySort.Album -> SortablePresentedTrack(
            value = value,
            primaryText = normalized(value.track.album),
            secondaryText = normalized(value.track.title)
        )
        LibrarySort.DurationAscending,
        LibrarySort.DurationDescending -> SortablePresentedTrack(
            value = value,
            secondaryText = normalized(value.track.title),
            durationMs = value.track.durationMs
        )
    }

    fun sourceKind(track: Track): LibrarySourceKind = when {
        track.dataPath.startsWith("stream:") -> LibrarySourceKind.Stream
        track.dataPath.startsWith("webdav:") -> LibrarySourceKind.WebDav
        track.dataPath.startsWith("document:") -> LibrarySourceKind.Document
        else -> LibrarySourceKind.MediaStore
    }

    private fun normalized(value: String?): String = value.orEmpty().trim().lowercase(Locale.ROOT)

    private data class SortablePresentedTrack(
        val value: LibraryPresentedTrack,
        val primaryText: String = "",
        val secondaryText: String = "",
        val durationMs: Long = 0L
    )
}
