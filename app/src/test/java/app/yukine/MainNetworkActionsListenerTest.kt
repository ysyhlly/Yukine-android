package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainNetworkActionsListenerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun networkActionResultsUpdateLibraryQueueNavigationAndCollections() {
        val nowPlayingViewModel = NowPlayingViewModel()
        val playbackGateway = FakePlaybackGateway()
        val replacements = mutableListOf<String>()
        val navigation = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        var reloads = 0
        nowPlayingViewModel.bindPlaybackGateway(playbackGateway)
        val listener = LibraryModule.provideMainNetworkActionsListenerFactory().create(
            nowPlayingViewModel,
            { cached, favorites, status -> replacements += "replace:${cached.ids()}:${favorites.sorted()}:$status" },
            { page -> navigation += page },
            { reloads += 1 },
            { status -> statuses += status }
        )

        val cached = listOf(track(1L), track(2L))
        val favorites = setOf(2L)
        listener.onStreamAdded(cached, favorites, "Added")
        listener.onStreamUpdated(1L, track(3L), cached, favorites, "Updated")
        listener.onStreamPlaylistImported(cached, favorites, "Imported")
        listener.onAllStreamsDeleted(cached, favorites, "Deleted all")
        listener.onTrackDeleted(cached, favorites, "Deleted track")
        listener.onRemoteSourceDeleted(cached, favorites, "Deleted source")
        listener.onWebDavSourceSaved(8L, cached, favorites, "Saved source")
        listener.onWebDavSourceSaved(-1L, cached, favorites, "Added source")
        listener.onRemoteSourceTested("Test OK")
        listener.onRemoteSourceSynced(cached, favorites, "Synced source")
        listener.onAllWebDavSourcesSynced(cached, favorites, "Synced all")

        assertEquals(
            listOf(
                "replace:1,2:[2]:Added",
                "replace:1,2:[2]:Updated",
                "replace:1,2:[2]:Imported",
                "replace:1,2:[2]:Deleted all",
                "replace:1,2:[2]:Deleted track",
                "replace:1,2:[2]:Deleted source",
                "replace:1,2:[2]:Saved source",
                "replace:1,2:[2]:Added source",
                "replace:1,2:[2]:Synced source",
                "replace:1,2:[2]:Synced all"
            ),
            replacements
        )
        assertEquals(
            listOf(
                "replaceById:1:3",
                "retain:1,2",
                "retain:1,2",
                "retain:1,2",
                "retain:1,2",
                "retain:1,2"
            ),
            playbackGateway.calls
        )
        assertEquals(
            listOf(
                MainRoutes.NETWORK_STREAM_LIST,
                MainRoutes.NETWORK_STREAMING,
                MainRoutes.NETWORK_STREAMING,
                MainRoutes.NETWORK_STREAM_LIST,
                MainRoutes.NETWORK_SOURCES,
                MainRoutes.NETWORK_SOURCES,
                MainRoutes.NETWORK_WEBDAV,
                MainRoutes.NETWORK_SOURCES,
                MainRoutes.NETWORK_WEBDAV
            ),
            navigation
        )
        assertEquals(listOf("Test OK"), statuses)
        assertEquals(4, reloads)
    }

    private class FakePlaybackGateway : NowPlayingPlaybackGateway {
        val calls = mutableListOf<String>()

        override fun snapshot(): PlaybackStateSnapshot? = null
        override fun skipToPrevious() = Unit
        override fun skipToNext() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun removeTracksById(trackIds: Set<Long>) = Unit
        override fun clearQueue() = Unit
        override fun moveQueueTrack(fromIndex: Int, toIndex: Int) = Unit
        override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
            calls += "replaceById:$oldTrackId:${updated.id}"
        }

        override fun retainTracksById(trackIds: Set<Long>) {
            calls += "retain:${trackIds.sorted().joinToString(",")}"
        }

        override fun warmPlaybackTrack(track: Track) = Unit
        override fun appendToQueue(tracks: List<Track>) = Unit
        override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) = Unit
        override fun startSleepTimerMinutes(minutes: Int) = Unit
        override fun cancelSleepTimer() = Unit
        override fun playQueue(tracks: List<Track>, index: Int) = Unit
        override fun pause() = Unit
        override fun play() = Unit
        override fun setShuffleEnabled(enabled: Boolean) = Unit
        override fun cycleRepeatMode() = Unit
        override fun setRepeatMode(repeatMode: Int) = Unit
    }

    private fun List<Track>.ids(): String = joinToString(",") { it.id.toString() }

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, Uri.EMPTY, "file:$id.mp3")
}
