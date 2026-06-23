package app.yukine

import android.net.Uri
import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.ui.CollectionsActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CollectionsRenderBindingsTest {
    @Test
    fun forwardsCollectionActionsToBoundOperations() {
        val calls = mutableListOf<String>()
        val events = mutableListOf<LibraryEvent>()
        val tracks = listOf(track(1L), track(2L))
        val playlist = Playlist(9L, "Mix", 2, 0L, 0L)
        val actions = emptyActions()
        var publishedActions: CollectionsActions? = null
        val bindings = CollectionsRenderBindings(
            showCreatePlaylistAction = Runnable { calls += "create" },
            openPlaylistM3uFilePickerAction = Runnable { calls += "m3u" },
            confirmClearPlayHistoryAction = Runnable { calls += "clearHistory" },
            requestBackAction = Runnable { calls += "back" },
            playTrackListAction = TrackListPlaybackAction { nextTracks, index ->
                calls += "play:${nextTracks.size}:$index"
            },
            libraryEventSink = LibraryEventSink { events += it },
            addToPlaylistAction = QueueTrackAction { calls += "add:${it.id}" },
            downloadTrackAction = QueueTrackAction { calls += "download:${it.id}" },
            downloadTracksAction = TrackListDownloadAction { calls += "downloadList:${it.size}" },
            selectPlaylistAction = PlaylistIdAction { calls += "select:$it" },
            showRenamePlaylistAction = PlaylistAction { calls += "rename:${it.id}" },
            confirmDeletePlaylistAction = PlaylistAction { calls += "delete:${it.id}" },
            selectedPlaylistExportOpener = SelectedPlaylistExportOpener { calls += "export" },
            importSelectedPlaylistToStreamingAction = Runnable { calls += "importPlaylist" },
            importFavoritesToStreamingAction = Runnable { calls += "importFavorites" },
            importStreamingFavoritesAction = Runnable { calls += "importStreamingFavorites" },
            syncSelectedPlaylistFromStreamingAction = Runnable { calls += "sync" },
            selectedPlaylistTrackMover = SelectedPlaylistTrackMover { playlistId, track, index, direction ->
                calls += "move:$playlistId:${track.id}:$index:$direction"
            },
            selectedPlaylistTrackRemover = SelectedPlaylistTrackRemover { playlistId, track ->
                calls += "remove:$playlistId:${track.id}"
            },
            collectionsActionsSink = CollectionsActionsSink { publishedActions = it }
        )

        bindings.showCreatePlaylist()
        bindings.openPlaylistM3uFilePicker()
        bindings.confirmClearPlayHistory()
        bindings.requestBack()
        bindings.playTrackList(tracks, 1)
        bindings.toggleFavorite(tracks[0])
        bindings.showAddToPlaylist(tracks[1])
        bindings.downloadTrack(tracks[0])
        bindings.downloadTracks(tracks)
        bindings.selectPlaylist(playlist.id)
        bindings.showRenamePlaylist(playlist)
        bindings.confirmDeletePlaylist(playlist)
        bindings.openSelectedPlaylistExportDocument()
        bindings.importSelectedPlaylistToStreaming()
        bindings.importFavoritesToStreaming()
        bindings.importStreamingFavorites()
        bindings.syncSelectedPlaylistFromStreaming()
        bindings.moveSelectedPlaylistTrack(playlist.id, tracks[0], 0, 1)
        bindings.removeSelectedPlaylistTrack(playlist.id, tracks[1])
        bindings.publishCollectionsActions(actions)

        assertEquals(
            listOf(
                "create",
                "m3u",
                "clearHistory",
                "back",
                "play:2:1",
                "add:2",
                "download:1",
                "downloadList:2",
                "select:9",
                "rename:9",
                "delete:9",
                "export",
                "importPlaylist",
                "importFavorites",
                "importStreamingFavorites",
                "sync",
                "move:9:1:0:1",
                "remove:9:2"
            ),
            calls
        )
        assertEquals(listOf(LibraryEvent.ToggleFavorite(tracks[0])), events)
        assertSame(actions, publishedActions)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

    private fun emptyActions(): CollectionsActions {
        return CollectionsActions(
            onBack = Runnable { },
            topActions = emptyList(),
            trackSections = emptyList(),
            playlistActions = emptyList(),
            selectedPlaylistTopActions = emptyList(),
            selectedPlaylistTrackActions = emptyList()
        )
    }
}
