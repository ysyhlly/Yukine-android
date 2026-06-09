package app.echo.next

import android.os.Handler
import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Track
import app.echo.next.model.WebDavSyncResult

internal class NetworkActionsController(
    private val repository: MusicLibraryRepository,
    private val executors: MainExecutors,
    private val mainHandler: Handler,
    private val listener: Listener
) : NetworkOperationSink {
    interface Listener {
        fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onStreamUpdated(
            oldTrackId: Long,
            updated: Track?,
            cached: List<Track>,
            favorites: Set<Long>,
            status: String
        )

        fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String)

        fun onRemoteSourceTested(status: String)

        fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String)

        fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String)
    }

    override fun addStreamUrl(title: String, url: String) {
        executors.io {
            val track = repository.addStreamUrl(title, url)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            val status = if (track == null) "Could not add stream URL" else "Library updated"
            mainHandler.post {
                listener.onStreamAdded(cached, favorites, status)
            }
        }
    }

    override fun updateStreamUrl(oldTrack: Track?, title: String, url: String) {
        if (oldTrack == null) {
            return
        }
        executors.io {
            val updated = repository.updateStreamUrl(oldTrack.id, title, url)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            val status = if (updated == null) "Could not update stream URL" else "Library updated"
            mainHandler.post {
                listener.onStreamUpdated(oldTrack.id, updated, cached, favorites, status)
            }
        }
    }

    override fun importM3uPlaylist(url: String) {
        executors.network {
            val result = repository.importM3uPlaylistWithResult(url)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            val status = if (result == null || result.isEmpty) {
                "No streams imported"
            } else {
                M3uDocumentHelper.streamImportStatus("Imported streams", result)
            }
            mainHandler.post {
                listener.onStreamPlaylistImported(cached, favorites, status)
            }
        }
    }

    override fun deleteAllStreams() {
        executors.io {
            repository.deleteAllStreams()
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            mainHandler.post {
                listener.onAllStreamsDeleted(cached, favorites, "Library updated")
            }
        }
    }

    override fun deleteTrack(trackId: Long, status: String) {
        executors.io {
            repository.deleteTrack(trackId)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            mainHandler.post {
                listener.onTrackDeleted(cached, favorites, status)
            }
        }
    }

    override fun deleteRemoteSource(sourceId: Long) {
        executors.io {
            repository.deleteRemoteSource(sourceId)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            mainHandler.post {
                listener.onRemoteSourceDeleted(cached, favorites, "Library updated")
            }
        }
    }

    override fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ) {
        executors.io {
            val savedSourceId = repository.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            val status = if (savedSourceId > 0L) {
                if (sourceId > 0L) "Updated WebDAV source" else "Added WebDAV source"
            } else {
                "Could not save WebDAV source"
            }
            mainHandler.post {
                listener.onWebDavSourceSaved(sourceId, cached, favorites, status)
            }
        }
    }

    override fun testRemoteSource(sourceId: Long) {
        executors.network {
            val status = repository.testRemoteSource(sourceId)
            mainHandler.post {
                listener.onRemoteSourceTested(status)
            }
        }
    }

    override fun syncRemoteSource(sourceId: Long, sourceName: String) {
        executors.network {
            val resultStatus = try {
                val result: WebDavSyncResult? = repository.syncRemoteSource(sourceId)
                result?.summary() ?: "WebDAV sync finished"
            } catch (error: RuntimeException) {
                "WebDAV sync failed: $sourceName ${safeMessage(error)}"
            }
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            mainHandler.post {
                listener.onRemoteSourceSynced(cached, favorites, resultStatus)
            }
        }
    }

    override fun syncAllWebDavSources(sourceIds: List<Long>) {
        executors.network {
            var total = 0
            var added = 0
            var removed = 0
            var kept = 0
            var success = 0
            var failed = 0
            for (sourceId in sourceIds) {
                try {
                    val result = repository.syncRemoteSource(sourceId)
                    if (result != null) {
                        total += result.trackCount()
                        added += result.addedCount
                        removed += result.removedCount
                        kept += result.keptCount
                    }
                    success++
                } catch (ignored: RuntimeException) {
                    failed++
                }
            }
            val cached = repository.loadCachedTracks()
            val favorites = repository.loadFavoriteIds()
            mainHandler.post {
                var status = "WebDAV sync: added $added, removed $removed, kept $kept, tracks $total, ok $success"
                if (failed > 0) {
                    status += ", failed $failed"
                }
                listener.onAllWebDavSourcesSynced(cached, favorites, status)
            }
        }
    }

    private fun safeMessage(error: RuntimeException): String {
        val message = error.message
        return if (message == null || message.isEmpty()) error.javaClass.simpleName else message
    }
}
