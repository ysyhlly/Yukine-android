package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.ui.LibrarySourceKind
import javax.inject.Inject

internal data class LibraryDeletionResult(
    val removed: List<Track>,
    val failed: List<Track> = emptyList(),
    val skipped: List<Track> = emptyList(),
    val cancelled: Boolean = false
)

internal class LibraryDeletionUseCase @Inject constructor(
    private val repository: MusicLibraryRepository
) {
    fun removeFromLibrary(tracks: List<Track>): LibraryDeletionResult {
        val unique = tracks.filter { TrackIdentity.isUsable(it.id) }.distinctBy { it.id to it.dataPath }
        val local = unique.filter { LibraryTrackPresentationPolicy.sourceKind(it) in LOCAL_SOURCES }
        val remote = unique - local.toSet()
        val removed = ArrayList<Track>()
        local.forEach { track ->
            if (repository.hideTracks(listOf(track)) > 0) removed.add(track)
        }
        remote.forEach { track ->
            if (repository.deleteTrack(track.id) > 0) removed.add(track)
        }
        return LibraryDeletionResult(
            removed = removed,
            failed = unique.filterNot { candidate -> removed.any { it.id == candidate.id && it.dataPath == candidate.dataPath } }
        )
    }

    fun removeFromPlaylist(playlistId: Long, tracks: List<Track>): LibraryDeletionResult {
        if (playlistId < 0L) return LibraryDeletionResult(emptyList(), tracks)
        val unique = tracks.filter { TrackIdentity.isUsable(it.id) }.distinctBy { it.id }
        val removed = unique.filter { repository.removeTrackFromPlaylist(playlistId, it.id) }
        return LibraryDeletionResult(removed, unique - removed.toSet())
    }

    fun finalizeDeletedFiles(tracks: List<Track>): LibraryDeletionResult {
        val removed = tracks.filter { track -> repository.deleteTrack(track.id) > 0 }
        return LibraryDeletionResult(removed, tracks - removed.toSet())
    }

    companion object {
        private val LOCAL_SOURCES = setOf(LibrarySourceKind.MediaStore, LibrarySourceKind.Document)
    }
}
