package app.yukine

import app.yukine.model.Track

internal class PlaylistActionResultController(
    private val viewModel: LibraryViewModel,
    private val languageModeProvider: PlaylistActionLanguageModeProvider,
    private val selectedPlaylistIdProvider: SelectedPlaylistIdProvider,
    private val selectedPlaylistSink: SelectedPlaylistSink,
    private val statusSink: PlaylistActionStatusSink,
    private val collectionsReloader: CollectionsReloader
) {
    fun onDefaultPlaylistTrackAdded(playlistId: Long, added: Boolean) {
        publishStatus(viewModel.defaultPlaylistAddPresentation(added, languageMode()).status)
        selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        collectionsReloader.reload()
    }

    fun onPlaylistCreated(playlistId: Long) {
        if (playlistId >= 0L) {
            selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        }
        publishStatus(viewModel.playlistCreatedPresentation(languageMode()).status)
        collectionsReloader.reload()
    }

    fun onPlaylistRenamed(playlistId: Long, renamed: Boolean) {
        if (renamed) {
            selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        }
        publishStatus(viewModel.playlistRenamedPresentation(renamed, languageMode()).status)
        collectionsReloader.reload()
    }

    fun onPlaylistDeleted(playlistId: Long, name: String, deleted: Boolean) {
        if (deleted && selectedPlaylistIdProvider.selectedPlaylistId() == playlistId) {
            selectedPlaylistSink.setSelectedPlaylistId(-1L)
        }
        publishStatus(viewModel.playlistDeletedPresentation(name, deleted, languageMode()).status)
        collectionsReloader.reload()
    }

    fun onSelectedPlaylistTrackRemoved(playlistId: Long, track: Track) {
        selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        publishStatus(viewModel.selectedPlaylistTrackRemovedPresentation(track, languageMode()).status)
        collectionsReloader.reload()
    }

    fun onSelectedPlaylistTrackMoved(playlistId: Long, track: Track, direction: Int, moved: Boolean) {
        selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        publishStatus(viewModel.selectedPlaylistTrackMovedPresentation(track, direction, moved, languageMode()).status)
        collectionsReloader.reload()
    }

    fun onTrackAddedToPlaylist(playlistId: Long, added: Boolean) {
        selectedPlaylistSink.setSelectedPlaylistId(playlistId)
        publishStatus(viewModel.trackAddedToPlaylistPresentation(added, languageMode()).status)
        collectionsReloader.reload()
    }

    private fun languageMode(): String = languageModeProvider.languageMode()

    private fun publishStatus(status: String) {
        statusSink.setStatus(status)
    }
}

internal fun interface PlaylistActionLanguageModeProvider {
    fun languageMode(): String
}

internal fun interface SelectedPlaylistIdProvider {
    fun selectedPlaylistId(): Long
}

internal fun interface SelectedPlaylistSink {
    fun setSelectedPlaylistId(playlistId: Long)
}

internal fun interface PlaylistActionStatusSink {
    fun setStatus(status: String)
}

internal fun interface CollectionsReloader {
    fun reload()
}
