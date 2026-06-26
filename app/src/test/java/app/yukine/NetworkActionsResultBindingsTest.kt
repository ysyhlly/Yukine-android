package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkActionsResultBindingsTest {
    @Test
    fun forwardsNetworkActionResultsToBoundActivityEdges() {
        val calls = mutableListOf<String>()
        val cached = listOf(track(1L), track(2L))
        val favorites = setOf(2L)
        val bindings = bindings(calls)

        bindings.onStreamAdded(cached, favorites, "Added")
        bindings.onStreamUpdated(1L, track(3L), cached, favorites, "Updated")
        bindings.onStreamUpdated(1L, null, cached, favorites, "Skipped")
        bindings.onStreamPlaylistImported(cached, favorites, "Imported")
        bindings.onAllStreamsDeleted(cached, favorites, "Deleted all")
        bindings.onTrackDeleted(cached, favorites, "Deleted")
        bindings.onRemoteSourceDeleted(cached, favorites, "Source deleted")
        bindings.onWebDavSourceSaved(8L, cached, favorites, "Source updated")
        bindings.onWebDavSourceSaved(-1L, cached, favorites, "Source added")
        bindings.onRemoteSourceTested("OK")
        bindings.onRemoteSourceSynced(cached, favorites, "Synced")
        bindings.onAllWebDavSourcesSynced(cached, favorites, "Synced all")

        assertEquals(
            listOf(
                "replace:2:1:Added",
                "syncQueue:1:3",
                "replace:2:1:Updated",
                "navigate:${MainRoutes.NETWORK_STREAM_LIST}",
                "replace:2:1:Skipped",
                "navigate:${MainRoutes.NETWORK_STREAM_LIST}",
                "replace:2:1:Imported",
                "navigate:${MainRoutes.NETWORK_STREAMING}",
                "retain:2",
                "replace:2:1:Deleted all",
                "navigate:${MainRoutes.NETWORK_STREAMING}",
                "retain:2",
                "replace:2:1:Deleted",
                "navigate:${MainRoutes.NETWORK_STREAM_LIST}",
                "retain:2",
                "replace:2:1:Source deleted",
                "navigate:${MainRoutes.NETWORK_SOURCES}",
                "collections",
                "retain:2",
                "replace:2:1:Source updated",
                "navigate:${MainRoutes.NETWORK_SOURCES}",
                "collections",
                "retain:2",
                "replace:2:1:Source added",
                "navigate:${MainRoutes.NETWORK_WEBDAV}",
                "collections",
                "status:OK",
                "collections",
                "replace:2:1:Synced",
                "navigate:${MainRoutes.NETWORK_SOURCES}",
                "replace:2:1:Synced all",
                "navigate:${MainRoutes.NETWORK_WEBDAV}"
            ),
            calls
        )
    }

    private fun bindings(calls: MutableList<String>): NetworkActionsResultBindings =
        NetworkActionsResultBindings(
            replaceLibraryAction = LibraryReplacementAction { tracks, favorites, status ->
                calls += "replace:${tracks.size}:${favorites.size}:$status"
            },
            playbackTracksRetainer = PlaybackTracksRetainer { tracks ->
                calls += "retain:${tracks.size}"
            },
            streamQueueSynchronizer = StreamQueueSynchronizer { oldTrackId, updated ->
                calls += "syncQueue:$oldTrackId:${updated.id}"
            },
            navigateNetworkPageAction = NetworkPageAction { page ->
                calls += "navigate:$page"
            },
            statusSink = SettingsStatusSink { status ->
                calls += "status:$status"
            },
            collectionsLoader = CollectionsLoader {
                calls += "collections"
            }
        )

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
