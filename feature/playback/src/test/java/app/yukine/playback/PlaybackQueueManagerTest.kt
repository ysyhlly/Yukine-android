package app.yukine.playback

import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackPositionManager
import app.yukine.playback.manager.PlaybackQueueManager
import app.yukine.playback.manager.PlaybackQueueStore
import app.yukine.playback.manager.PlaybackRuntimeStateManager
import app.yukine.playback.manager.PlaybackTransitionStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaybackQueueManagerTest {
    @Test
    fun playQueuePersistsAndStartsPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)
        val track = track(42L)

        manager.playQueue(listOf(track), 0, 0L)

        assertEquals(1, provider.queue.size)
        assertEquals(0, manager.currentIndex())
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(1, store.savedTracks.size)
        assertEquals(42L, store.savedTracks.first().id)
    }

    @Test
    fun appendToEmptyQueueStartsPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)

        manager.appendToQueue(listOf(track(7L)))

        assertTrue(provider.prepareCurrentCalled)
        assertEquals(0, manager.currentIndex())
    }

    @Test
    fun playQueueUsesExplicitStartPositionForImmediateRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)
        val track = track(8L, durationMs = 10_000L)

        manager.playQueue(listOf(track), 0, 3200L)

        assertEquals(3200L, provider.positionManager.restoredPositionFor(track))
    }

    @Test
    fun queuePlaybackStartPersistsResumeRequestThroughStore() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)

        manager.playQueue(listOf(track(9L)), 0, 0L)

        assertTrue(store.resumeRequested)
    }

    @Test
    fun queuePlaybackStartClearsRuntimeErrorAndTransitionMarkerThroughOwners() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)
        val markedTrack = track(1L)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(markedTrack)

        manager.playQueue(listOf(markedTrack), 0, 0L)

        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
    }

    @Test
    fun advanceQueueIndexToNextReadsRepeatModeFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        manager.advanceQueueIndexToNext()

        assertEquals(1, manager.currentIndex())
    }

    @Test
    fun replaceQueuedTrackContinuesPreparingPlaybackFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.runtimeStateManager.setPreparing(true)

        manager.replaceQueuedTrack(track(1L, durationMs = 2_000L))

        assertTrue(provider.prepareCurrentCalled)
    }

    @Test
    fun restorePlaybackQueueCanBeDisabled() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(listOf(track(1L)), 0)
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)

        manager.setPlaybackRestoreEnabled(false)
        manager.restorePlaybackQueue()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.currentIndex())
    }

    @Test
    fun restorePlaybackQueueFiltersInvalidTracksInsideManager() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(
                        track(-1L, android.net.Uri.parse("content://music/invalid"), "/music/invalid"),
                        track(1L, null, "/music/missing-uri"),
                        track(2L, null, "streaming:netease:2"),
                        track(3L, android.net.Uri.parse("content://music/3"), "/music/3")
                ),
                0
        )
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(listOf("streaming:netease:2", "/music/3"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0, manager.currentIndex())
    }

    @Test
    fun skipToPreviousMovesCursorAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L, durationMs = 10_000L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

        manager.skipToPrevious()

        assertEquals(0, manager.currentIndex())
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(2L, store.savedPositions.first().first)
    }

    @Test
    fun skipToNextReusesMirroredQueueWithoutPreparingNewMediaSources() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L, durationMs = 10_000L), track(2L)))
        provider.playbackPositionMsValue = 2400L
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = true
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        val reused = manager.skipToNextImmediately()

        assertTrue(reused)
        assertEquals(1, manager.currentIndex())
        assertEquals(1, provider.mirroredQueuePlayer.seekIndex)
        assertEquals(0L, provider.mirroredQueuePlayer.seekPositionMs)
        assertEquals(1, store.savedIndex)
        assertEquals(listOf(1L to 2400L, 2L to 0L), store.savedPositions)
        assertEquals(listOf("/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
        assertFalse(provider.prepareCurrentCalled)
    }

    @Test
    fun replaceCurrentTrackAndResumeSchedulesRecoveryForNewUri() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L))
        provider.playbackPositionMsValue = 1200L
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        val replacement = track(2L, durationMs = 10_000L)

        val recovery = requireNotNull(manager.replaceCurrentTrackAndResume(replacement, 800L))

        assertEquals(2L, provider.queue.first().id)
        assertEquals(1200L, provider.positionManager.restoredPositionFor(replacement))
        assertEquals(replacement, recovery.track)
        assertEquals(1200L, recovery.restoredPositionMs)
        assertTrue(recovery.playWhenReady)
    }

    @Test
    fun reuseMirroredQueueAppliesQueueStateWithoutPreparingNewMediaSources() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = true
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        provider.positionManager.setRestoredPosition(2L, 1200L, explicit = true)

        val reused = manager.reuseMirroredQueueIfAvailable(playWhenReady = true, startPositionMs = 4500L)

        assertTrue(reused)
        assertEquals(1, provider.mirroredQueuePlayer.seekIndex)
        assertEquals(4500L, provider.mirroredQueuePlayer.seekPositionMs)
        assertTrue(store.resumeRequested)
        assertEquals(0L, provider.positionManager.restoredPositionFor(track(2L)))
        assertFalse(provider.prepareCurrentCalled)
    }

    @Test
    fun reuseMirroredQueueClearsMirrorFlagWhenPlayerSeekFails() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L))
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = false
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        val reused = manager.reuseMirroredQueueIfAvailable(playWhenReady = true, startPositionMs = 0L)

        assertFalse(reused)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun mirroredQueueTracksForPreparationRestoresHeadersAndReturnsSnapshot() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.parse("content://music/2"))
                )
        )
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        val tracks = manager.mirroredQueueTracksForPreparation()

        assertEquals(listOf(1L, 2L), tracks?.map { it.id })
        assertEquals(listOf("/music/1", "/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
    }

    @Test
    fun mirroredQueueTracksForPreparationRejectsUnplayableTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L, null, ""))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        val tracks = manager.mirroredQueueTracksForPreparation()

        assertEquals(null, tracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun mirroredQueueTracksForPreparationRejectsEmptyUriWithoutPartialRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.EMPTY, "/music/2")
                )
        )
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        val tracks = manager.mirroredQueueTracksForPreparation()

        assertEquals(null, tracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun currentIndexStateIsOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = queueManager(store, provider)

        assertEquals(-1, manager.currentIndex())

        manager.setCurrentIndex(7)
        assertEquals(7, manager.currentIndex())
        assertEquals(2, manager.clampCurrentIndex(queueSize = 3))

        manager.setClampedCurrentIndex(index = 9, queueSize = 4)
        assertEquals(3, manager.currentIndex())

        manager.setClampedCurrentIndex(index = 2, queueSize = 0)
        assertEquals(-1, manager.currentIndex())
        assertEquals(0, manager.clampCurrentIndex(queueSize = 0))
    }

    private fun queueManager(store: FakeQueueStore, provider: FakeQueueProvider): PlaybackQueueManager {
        lateinit var manager: PlaybackQueueManager
        val positionManager = PlaybackPositionManager(
                store,
                object : PlaybackPositionManager.StateProvider {
                    override fun currentTrack(): Track? {
                        val index = manager.currentIndex()
                        return provider.queue.getOrNull(index)
                    }

                    override fun positionMs(): Long = provider.playbackPositionMsValue
                }
        )
        manager = PlaybackQueueManager(
                store,
                provider,
                provider.queuePlaybackActions,
                positionManager,
                provider.streamingRestoreProvider,
                provider.mirroredQueuePlayer,
                provider.runtimeStateManager,
                provider.transitionStateManager
        )
        provider.positionManager = positionManager
        return manager
    }

    private fun track(
            id: Long,
            uri: android.net.Uri? = null,
            dataPath: String = "/music/$id",
            durationMs: Long = 1000L
    ): Track {
        return Track(
                id,
                "t$id",
                "artist",
                "album",
                durationMs,
                uri,
                dataPath
        )
    }

    private class FakeQueueStore : PlaybackQueueStore {
        var savedTracks: List<Track> = emptyList()
        var savedIndex: Int = -1
        val savedPositions = mutableListOf<Pair<Long, Long>>()
        var restore = PlaybackQueueState(emptyList(), -1)
        var resumeRequested = false

        override fun load(): PlaybackQueueState = restore
        override fun save(tracks: List<Track>, currentIndex: Int) {
            savedTracks = tracks
            savedIndex = currentIndex
        }
        override fun loadResumeRequested(): Boolean = false
        override fun saveResumeRequested(requested: Boolean) {
            resumeRequested = requested
        }
        override fun loadPlaybackPositionTrackId(): Long = -1L
        override fun loadPlaybackPositionMs(): Long = 0L
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
            savedPositions.add(trackId to positionMs)
        }
    }

    private class FakeQueueProvider : PlaybackQueueManager.QueueProvider {
        val queue = mutableListOf<Track>()
        lateinit var positionManager: PlaybackPositionManager
        val streamingRestoreProvider = FakeStreamingRestoreProvider()
        val mirroredQueuePlayer = FakeMirroredQueuePlayer()
        val queuePlaybackActions = FakeQueuePlaybackActions()
        val runtimeStateManager = PlaybackRuntimeStateManager(
                object : PlaybackRuntimeStateManager.StateProvider {
                    override fun player(): androidx.media3.exoplayer.ExoPlayer? = null
                    override fun playerMirrorsQueue(): Boolean = false
                    override fun currentTrack(): Track? = null
                }
        )
        val transitionStateManager = PlaybackTransitionStateManager()
        val prepareCurrentCalled: Boolean
            get() = queuePlaybackActions.prepareCurrentCalled
        var playbackPositionMsValue = 0L
        override fun queue(): MutableList<Track> = queue
    }

    private class FakeQueuePlaybackActions : PlaybackQueueManager.QueuePlaybackActions {
        var isPlayingValue = false
        var prepareCurrentCalled = false
        var published = false
        var stoppedAndCleared = false
        override fun isPlaying(): Boolean = isPlayingValue
        override fun prepareCurrent(playWhenReady: Boolean) {
            prepareCurrentCalled = playWhenReady
        }
        override fun publishState() {
            published = true
        }
        override fun stopAndClear() {
            stoppedAndCleared = true
        }
    }

    private class FakeStreamingRestoreProvider : PlaybackQueueManager.StreamingRestoreProvider {
        val restoredDataPaths = mutableListOf<String?>()
        override fun restoredTrackFor(track: Track): Track? = track
        override fun restoreForDataPath(dataPath: String?) {
            restoredDataPaths.add(dataPath)
        }
    }

    private class FakeMirroredQueuePlayer : PlaybackQueueManager.MirroredQueuePlayer {
        var matchesCurrentQueueValue = false
        var seekToValue = false
        var seekIndex = -1
        var seekPositionMs = -1L
        override fun matchesCurrentQueue(): Boolean = matchesCurrentQueueValue
        override fun seekTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean {
            seekIndex = index
            seekPositionMs = positionMs
            return seekToValue
        }
    }
}
