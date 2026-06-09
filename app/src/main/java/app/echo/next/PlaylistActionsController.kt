package app.echo.next

import android.os.Handler
import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Track

internal class PlaylistActionsController(
    private val repository: MusicLibraryRepository,
    private val executors: MainExecutors,
    private val mainHandler: Handler,
    private val listener: Listener
) {
    interface Listener {
        fun onDefaultPlaylistTrackAdded(playlistId: Long, added: Boolean)

        fun onPlaylistCreated(playlistId: Long)

        fun onPlaylistRenamed(playlistId: Long, renamed: Boolean)

        fun onPlaylistDeleted(playlistId: Long, name: String, deleted: Boolean)

        fun onSelectedPlaylistTrackRemoved(playlistId: Long, track: Track)

        fun onSelectedPlaylistTrackMoved(playlistId: Long, track: Track, direction: Int, moved: Boolean)

        fun onTrackAddedToPlaylist(playlistId: Long, added: Boolean)
    }

    fun addToDefaultPlaylist(track: Track?) {
        if (track == null) {
            return
        }
        executors.io {
            val playlistId = repository.ensureDefaultPlaylist()
            val added = playlistId >= 0L && repository.addTrackToPlaylist(playlistId, track.id)
            mainHandler.post {
                listener.onDefaultPlaylistTrackAdded(playlistId, added)
            }
        }
    }

    fun createPlaylist(name: String) {
        executors.io {
            val playlistId = repository.createPlaylist(name)
            mainHandler.post {
                listener.onPlaylistCreated(playlistId)
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        executors.io {
            val renamed = repository.renamePlaylist(playlistId, name)
            mainHandler.post {
                listener.onPlaylistRenamed(playlistId, renamed)
            }
        }
    }

    fun deletePlaylist(playlistId: Long, name: String) {
        executors.io {
            val deleted = repository.deletePlaylist(playlistId)
            mainHandler.post {
                listener.onPlaylistDeleted(playlistId, name, deleted)
            }
        }
    }

    fun removeSelectedPlaylistTrack(playlistId: Long, track: Track?) {
        if (playlistId < 0L || track == null) {
            return
        }
        executors.io {
            repository.removeTrackFromPlaylist(playlistId, track.id)
            mainHandler.post {
                listener.onSelectedPlaylistTrackRemoved(playlistId, track)
            }
        }
    }

    fun moveSelectedPlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int) {
        if (playlistId < 0L || track == null) {
            return
        }
        executors.io {
            val moved = repository.movePlaylistTrackAt(playlistId, trackIndex, direction)
            mainHandler.post {
                listener.onSelectedPlaylistTrackMoved(playlistId, track, direction, moved)
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        executors.io {
            val added = repository.addTrackToPlaylist(playlistId, trackId)
            mainHandler.post {
                listener.onTrackAddedToPlaylist(playlistId, added)
            }
        }
    }
}
