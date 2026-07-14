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
        val values = tracks.mapIndexed { index, track ->
            LibraryPresentedTrack(track, details.getOrElse(index) { "" })
        }.filter { value ->
            when (uiState.filter) {
                LibraryFilter.All -> true
                LibraryFilter.Favorites -> favoriteIds.contains(value.track.id)
                LibraryFilter.Local -> sourceKind(value.track) in setOf(
                    LibrarySourceKind.MediaStore,
                    LibrarySourceKind.Document
                )
                LibraryFilter.Network -> sourceKind(value.track) in setOf(
                    LibrarySourceKind.Stream,
                    LibrarySourceKind.WebDav
                )
            }
        }
        val comparator = when (uiState.sort) {
            LibrarySort.TitleAscending -> compareBy<LibraryPresentedTrack> { normalized(it.track.title) }
            LibrarySort.TitleDescending -> compareByDescending<LibraryPresentedTrack> { normalized(it.track.title) }
            LibrarySort.Artist -> compareBy<LibraryPresentedTrack> { normalized(it.track.artist) }
                .thenBy { normalized(it.track.title) }
            LibrarySort.Album -> compareBy<LibraryPresentedTrack> { normalized(it.track.album) }
                .thenBy { normalized(it.track.title) }
            LibrarySort.DurationAscending -> compareBy<LibraryPresentedTrack> { it.track.durationMs }
                .thenBy { normalized(it.track.title) }
            LibrarySort.DurationDescending -> compareByDescending<LibraryPresentedTrack> { it.track.durationMs }
                .thenBy { normalized(it.track.title) }
        }
        return values.sortedWith(comparator)
    }

    fun sourceKind(track: Track): LibrarySourceKind = when {
        track.dataPath.startsWith("stream:") -> LibrarySourceKind.Stream
        track.dataPath.startsWith("webdav:") -> LibrarySourceKind.WebDav
        track.dataPath.startsWith("document:") -> LibrarySourceKind.Document
        else -> LibrarySourceKind.MediaStore
    }

    private fun normalized(value: String?): String = value.orEmpty().trim().lowercase(Locale.ROOT)
}
