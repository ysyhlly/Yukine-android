package app.yukine.playback

import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackQueueManager
import app.yukine.playback.manager.PlaybackQueueStore
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
        val manager = PlaybackQueueManager(store, provider)
        val track = track(42L)

        manager.playQueue(listOf(track), 0, 0L)

        assertEquals(1, provider.queue.size)
        assertEquals(0, provider.currentIndexValue)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(1, store.savedTracks.size)
        assertEquals(42L, store.savedTracks.first().id)
    }

    @Test
    fun appendToEmptyQueueStartsPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = PlaybackQueueManager(store, provider)

        manager.appendToQueue(listOf(track(7L)))

        assertTrue(provider.prepareCurrentCalled)
        assertEquals(0, provider.currentIndexValue)
    }

    @Test
    fun playQueueUsesExplicitStartPositionForImmediateRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        val manager = PlaybackQueueManager(store, provider)

        manager.playQueue(listOf(track(8L)), 0, 3200L)

        assertEquals(8L, provider.restoredTrackId)
        assertEquals(3200L, provider.restoredPositionMs)
        assertTrue(provider.restoredPositionExplicit)
    }

    @Test
    fun restorePlaybackQueueCanBeDisabled() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(listOf(track(1L)), 0)
        val provider = FakeQueueProvider()
        val manager = PlaybackQueueManager(store, provider)

        manager.setPlaybackRestoreEnabled(false)
        manager.restorePlaybackQueue()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, provider.currentIndexValue)
    }

    @Test
    fun skipToPreviousMovesCursorAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        provider.currentIndexValue = 1
        val manager = PlaybackQueueManager(store, provider)

        manager.skipToPrevious()

        assertEquals(0, provider.currentIndexValue)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(2L, store.savedPositions.first().first)
    }

    @Test
    fun replaceCurrentTrackAndResumeSchedulesRecoveryForNewUri() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L))
        provider.currentIndexValue = 0
        provider.playbackPositionMsValue = 1200L
        val manager = PlaybackQueueManager(store, provider)
        val replacement = track(2L)

        manager.replaceCurrentTrackAndResume(replacement, 800L)

        assertEquals(2L, provider.queue.first().id)
        assertEquals(2L, provider.restoredTrackId)
        assertEquals(1200L, provider.restoredPositionMs)
        assertTrue(provider.recoveryRecorded)
        assertTrue(provider.prepareCurrentScheduled)
    }

    @Test
    fun reuseMirroredQueueAppliesQueueStateWithoutPreparingNewMediaSources() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        provider.currentIndexValue = 1
        provider.mirroredQueueMatchesPlayerValue = true
        provider.seekMirroredQueueToValue = true
        val manager = PlaybackQueueManager(store, provider)

        val reused = manager.reuseMirroredQueueIfAvailable(playWhenReady = true, startPositionMs = 4500L)

        assertTrue(reused)
        assertEquals(1, provider.seekMirroredQueueIndex)
        assertEquals(4500L, provider.seekMirroredQueuePositionMs)
        assertFalse(provider.preparingValue)
        assertTrue(provider.playbackParametersApplied)
        assertTrue(provider.playbackModeApplied)
        assertTrue(provider.resumeRequested)
        assertTrue(provider.wifiLockAcquired)
        assertTrue(provider.restoredPositionCleared)
        assertTrue(provider.progressUpdatesStarted)
        assertFalse(provider.prepareCurrentCalled)
    }

    @Test
    fun reuseMirroredQueueClearsMirrorFlagWhenPlayerSeekFails() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L))
        provider.currentIndexValue = 0
        provider.mirroredQueueMatchesPlayerValue = true
        provider.seekMirroredQueueToValue = false
        val manager = PlaybackQueueManager(store, provider)

        val reused = manager.reuseMirroredQueueIfAvailable(playWhenReady = true, startPositionMs = 0L)

        assertFalse(reused)
        assertFalse(provider.playerMirrorsQueueValue)
        assertFalse(provider.resumeRequested)
        assertFalse(provider.progressUpdatesStarted)
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
        provider.currentIndexValue = 0
        val manager = PlaybackQueueManager(store, provider)

        val tracks = manager.mirroredQueueTracksForPreparation()

        assertEquals(listOf(1L, 2L), tracks?.map { it.id })
        assertEquals(listOf("/music/1", "/music/2"), provider.restoredDataPaths)
    }

    @Test
    fun mirroredQueueTracksForPreparationRejectsUnplayableTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueProvider()
        provider.queue.add(track(1L, null, ""))
        provider.currentIndexValue = 0
        val manager = PlaybackQueueManager(store, provider)

        val tracks = manager.mirroredQueueTracksForPreparation()

        assertEquals(null, tracks)
        assertTrue(provider.restoredDataPaths.isEmpty())
    }

    private fun track(id: Long, uri: android.net.Uri? = null, dataPath: String = "/music/$id"): Track {
        return Track(
                id,
                "t$id",
                "artist",
                "album",
                1000L,
                uri,
                dataPath
        )
    }

    private class FakeQueueStore : PlaybackQueueStore {
        var savedTracks: List<Track> = emptyList()
        var savedIndex: Int = -1
        val savedPositions = mutableListOf<Pair<Long, Long>>()
        var restore = PlaybackQueueState(emptyList(), -1)

        override fun load(): PlaybackQueueState = restore
        override fun save(tracks: List<Track>, currentIndex: Int) {
            savedTracks = tracks
            savedIndex = currentIndex
        }
        override fun loadResumeRequested(): Boolean = false
        override fun saveResumeRequested(requested: Boolean) {}
        override fun loadPlaybackPositionTrackId(): Long = -1L
        override fun loadPlaybackPositionMs(): Long = 0L
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
            savedPositions.add(trackId to positionMs)
        }
    }

    private class FakeQueueProvider : PlaybackQueueManager.QueueProvider {
        val queue = mutableListOf<Track>()
        var currentIndexValue = -1
        var prepareCurrentCalled = false
        var restoredTrackId = -1L
        var restoredPositionMs = 0L
        var restoredPositionExplicit = false
        val restoredDataPaths = mutableListOf<String?>()
        var playbackPositionMsValue = 0L
        var recoveryRecorded = false
        var prepareCurrentScheduled = false
        var preparingValue = true
        var mirroredQueueMatchesPlayerValue = false
        var seekMirroredQueueToValue = false
        var seekMirroredQueueIndex = -1
        var seekMirroredQueuePositionMs = -1L
        var playbackParametersApplied = false
        var playbackModeApplied = false
        var resumeRequested = false
        var wifiLockAcquired = false
        var restoredPositionCleared = false
        var progressUpdatesStarted = false
        var playerMirrorsQueueValue = true
        override fun queue(): MutableList<Track> = queue
        override fun currentIndex(): Int = currentIndexValue
        override fun repeatMode(): Int = EchoPlaybackService.REPEAT_ALL
        override fun shuffleEnabled(): Boolean = false
        override fun isPlaying(): Boolean = false
        override fun preparing(): Boolean = preparingValue
        override fun clearRestoredPosition() { restoredPositionCleared = true }
        override fun resetCurrentPlaybackPosition() {}
        override fun savePlaybackResumeRequested(requested: Boolean) { resumeRequested = requested }
        override fun prepareCurrent(playWhenReady: Boolean) { prepareCurrentCalled = playWhenReady }
        override fun publishState() {}
        override fun stopAndClear() {}
        override fun seekMirroredQueueToCurrentIndex(playWhenReady: Boolean): Boolean = false
        override fun playbackPositionMs(): Long = playbackPositionMsValue
        override fun restoredTrackFor(track: Track): Track? = track
        override fun restoreForDataPath(dataPath: String?) { restoredDataPaths.add(dataPath) }
        override fun isRestorableQueueTrack(track: Track): Boolean = true
        override fun setRestoredPosition(trackId: Long, positionMs: Long, explicit: Boolean) {
            restoredTrackId = trackId
            restoredPositionMs = positionMs
            restoredPositionExplicit = explicit
        }
        override fun setCurrentIndex(index: Int) { currentIndexValue = index }
        override fun setErrorMessage(message: String) {}
        override fun setPreparing(preparing: Boolean) { preparingValue = preparing }
        override fun setLastMarkedTrack(track: Track?) {}
        override fun setExplicitRestoredPosition(track: Track?, positionMs: Long): Long {
            restoredTrackId = track?.id ?: -1L
            restoredPositionMs = positionMs
            restoredPositionExplicit = true
            return positionMs
        }
        override fun recordStreamingRecovery(track: Track, restoredPositionMs: Long) {
            recoveryRecorded = true
        }
        override fun schedulePrepareCurrent(playWhenReady: Boolean) {
            prepareCurrentScheduled = playWhenReady
        }
        override fun mirroredQueueMatchesCurrentPlayer(): Boolean = mirroredQueueMatchesPlayerValue
        override fun resetWaveformIfTrackChanged(track: Track) {}
        override fun applyPlaybackParametersToPlayer() { playbackParametersApplied = true }
        override fun applyPlaybackModeToPlayer() { playbackModeApplied = true }
        override fun seekMirroredQueueTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean {
            seekMirroredQueueIndex = index
            seekMirroredQueuePositionMs = positionMs
            return seekMirroredQueueToValue
        }
        override fun setPlayerMirrorsQueue(enabled: Boolean) { playerMirrorsQueueValue = enabled }
        override fun acquireWifiLockIfStreaming() { wifiLockAcquired = true }
        override fun startProgressUpdates() { progressUpdatesStarted = true }
    }
}
