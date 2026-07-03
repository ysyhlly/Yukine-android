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
import org.junit.Assert.assertThrows
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
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val track = track(42L)

        manager.playQueue(listOf(track), 0, 0L)

        assertEquals(1, provider.queue.size)
        assertEquals(0, manager.queueStateSnapshot().currentIndex)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(1, store.savedTracks.size)
        assertEquals(42L, store.savedTracks.first().id)
    }

    @Test
    fun appendToEmptyQueueStartsPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.appendToQueue(listOf(track(7L)))

        assertTrue(provider.prepareCurrentCalled)
        assertEquals(0, manager.queueStateSnapshot().currentIndex)
    }

    @Test
    fun playQueueUsesExplicitStartPositionForImmediateRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val track = track(8L, durationMs = 10_000L)

        manager.playQueue(listOf(track), 0, 3200L)

        assertEquals(3200L, provider.positionManager.restoredPositionFor(track))
    }

    @Test
    fun queuePlaybackStartPersistsResumeRequestThroughStore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.playQueue(listOf(track(9L)), 0, 0L)

        assertTrue(store.resumeRequested)
    }

    @Test
    fun queuePlaybackStartClearsRuntimeErrorAndTransitionMarkerThroughOwners() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val markedTrack = track(1L)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(markedTrack)

        manager.playQueue(listOf(markedTrack), 0, 0L)

        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
    }

    @Test
    fun skipToNextImmediatelyReadsRepeatModeFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        manager.skipToNextImmediately()

        assertEquals(1, manager.queueStateSnapshot().currentIndex)
    }

    @Test
    fun preparePlaybackCompletionActionPreparesCurrentInternallyWhenRepeatOne() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ONE)

        assertEquals(null, manager.preparePlaybackCompletionAction())
        assertTrue(provider.queuePlaybackActions.prepareCurrentCalled)
    }

    @Test
    fun preparePlaybackCompletionActionStopsAndClearsWhenQueueIsEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR,
            manager.preparePlaybackCompletionAction()
        )
    }

    @Test
    fun preparePlaybackCompletionActionStopsAtEndWhenRepeatOff() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.STOP_AT_END,
            manager.preparePlaybackCompletionAction()
        )
    }

    @Test
    fun preparePlaybackCompletionActionAdvancesWhenQueueCanContinue() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT,
            manager.preparePlaybackCompletionAction()
        )
    }

    @Test
    fun upcomingTracksForPrecacheStopsAtQueueEndWhenRepeatOff() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L), track(3L)), 1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            listOf(3L),
            manager.upcomingTracksForPrecache(4).map { it.id }
        )
    }

    @Test
    fun upcomingTracksForPrecacheWrapsAndSkipsCurrentWhenRepeatAll() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L), track(3L)), 2)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)

        assertEquals(
            listOf(1L, 2L),
            manager.upcomingTracksForPrecache(4).map { it.id }
        )
    }

    @Test
    fun preparePlaybackCompletionActionResetsCompletedTrackAndClearsRepeatRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ONE)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        assertEquals(null, manager.preparePlaybackCompletionAction())
        assertTrue(provider.queuePlaybackActions.prepareCurrentCalled)

        assertEquals(listOf(1L to 0L), store.savedPositions)
        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun preparePlaybackCompletionActionKeepsRestoreForAdvanceAction() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current, track(2L)), 0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT,
            manager.preparePlaybackCompletionAction()
        )

        assertEquals(listOf(1L to 0L), store.savedPositions)
        assertEquals(4500L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun prepareStopAtEndOfQueueClearsQueuePlaybackStateThroughOwners() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.positionManager.setRestoredPosition(current.id, 4000L, explicit = true)
        provider.runtimeStateManager.setPreparing(true)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(current)
        store.resumeRequested = true

        manager.prepareStopAtEndOfQueue()

        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
        assertFalse(provider.runtimeStateManager.preparing())
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
        assertFalse(store.resumeRequested)
    }

    @Test
    fun prepareStopAfterAutomaticAdvancePersistsAndResetsCompletedTrackThroughOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 5300L
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(
                        track(1L, durationMs = 10_000L),
                        track(2L, durationMs = 10_000L)
                ),
                1
        )

        manager.prepareStopAfterAutomaticAdvance(completedIndex = 1)

        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(listOf(2L to 5300L, 2L to 0L), store.savedPositions)
    }

    @Test
    fun prepareStopAndClearPlaybackStateClearsQueuePositionRuntimeAndResumeState() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(2L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), current), 1)
        provider.positionManager.setRestoredPosition(current.id, 2500L, explicit = true)
        provider.runtimeStateManager.setPreparing(true)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(current)
        provider.transitionStateManager.setFadeOutAdvancing(true)
        store.resumeRequested = true

        manager.prepareStopAndClearPlaybackState()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertEquals(emptyList<Long>(), store.savedTracks.map { it.id })
        assertEquals(-1, store.savedIndex)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertFalse(provider.runtimeStateManager.preparing())
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
        assertFalse(provider.transitionStateManager.fadeOutAdvancing())
        assertFalse(store.resumeRequested)
        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun replaceQueuedTrackContinuesPreparingPlaybackFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)
        provider.runtimeStateManager.setPreparing(true)

        manager.replaceQueuedTrackById(1L, track(1L, durationMs = 2_000L))

        assertTrue(provider.prepareCurrentCalled)
    }

    @Test
    fun replaceCurrentQueueTrackPersistsCurrentSlotOnly() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
        val replacement = track(9L)

        manager.replaceCurrentQueueTrack(replacement)

        assertEquals(listOf(1L, 9L), provider.queue.map { it.id })
        assertEquals(listOf(1L, 9L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
    }

    @Test
    fun replaceCurrentQueueTrackRejectsMissingCurrentSlot() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.add(track(1L))
        val manager = queueManager(store, provider)
        setRawCurrentIndex(manager, 3)

        manager.replaceCurrentQueueTrack(track(9L))

        assertEquals(listOf(1L), provider.queue.map { it.id })
        assertTrue(store.savedTracks.isEmpty())
    }

    @Test
    fun restorePlaybackQueueCanBeDisabled() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(listOf(track(1L)), 0)
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.setPlaybackRestoreEnabled(false)
        manager.restorePlaybackQueue()
        val snapshot = manager.queueStateSnapshot()

        assertTrue(provider.queue.isEmpty())
        assertTrue(snapshot.isQueueEmpty)
        assertEquals(-1, snapshot.currentIndex)
    }

    @Test
    fun restorePlaybackQueueReadsInitialEnablementFromStore() {
        val store = FakeQueueStore()
        store.playbackRestoreEnabled = false
        store.restore = PlaybackQueueState(listOf(track(1L)), 0)
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()
        val snapshot = manager.queueStateSnapshot()

        assertTrue(provider.queue.isEmpty())
        assertTrue(snapshot.isQueueEmpty)
    }

    @Test
    fun setPlaybackRestoreEnabledPersistsThroughStore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.setPlaybackRestoreEnabled(false)
        manager.setPlaybackRestoreEnabled(true)

        assertEquals(listOf(false, true), store.savedPlaybackRestoreEnabledValues)
        assertTrue(store.playbackRestoreEnabled)
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
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()
        val snapshot = manager.queueStateSnapshot()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(listOf("streaming:netease:2", "/music/3"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(2L, snapshot.currentTrack?.id)
        assertEquals(0, snapshot.currentIndex)
        assertEquals(2, snapshot.queueSize)
        assertFalse(snapshot.isQueueEmpty)
        assertTrue(snapshot.hasCurrentTrack)
    }

    @Test
    fun restorePlaybackQueueClampsCurrentIndexAfterFilteringInvalidTracks() {
        val store = FakeQueueStore()
        val current = track(3L, android.net.Uri.parse("content://music/3"), "/music/3")
        store.restore = PlaybackQueueState(
                listOf(
                        track(-1L, android.net.Uri.parse("content://music/invalid"), "/music/invalid"),
                        track(2L, null, "streaming:netease:2"),
                        current
                ),
                2
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()
        val snapshot = manager.queueStateSnapshot()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(1, snapshot.currentIndex)
        assertEquals(3L, snapshot.currentTrack?.id)
        assertEquals(listOf(2L, 3L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
    }

    @Test
    fun restoreLastPlaybackReportsEmptyQueueWithoutServicePolicy() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val result = manager.restoreLastPlayback(playWhenRestored = true)

        assertFalse(result.shouldCreatePlayer)
        assertFalse(result.shouldPrepare)
        assertFalse(result.playWhenReady)
    }

    @Test
    fun restoreLastPlaybackPreservesCreateWithoutPrepareForMissingCurrentTrack() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(track(1L, android.net.Uri.parse("content://music/1"))),
                -1
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val result = manager.restoreLastPlayback(playWhenRestored = true)

        assertTrue(result.shouldCreatePlayer)
        assertFalse(result.shouldPrepare)
        assertFalse(result.playWhenReady)
    }

    @Test
    fun restoreLastPlaybackUsesExplicitPlayRequest() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(track(1L, android.net.Uri.parse("content://music/1"))),
                0
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val result = manager.restoreLastPlayback(playWhenRestored = true)

        assertTrue(result.shouldCreatePlayer)
        assertTrue(result.shouldPrepare)
        assertTrue(result.playWhenReady)
    }

    @Test
    fun restoreLastPlaybackUsesPersistedResumeRequest() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(track(1L, android.net.Uri.parse("content://music/1"))),
                0
        )
        store.resumeRequested = true
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val result = manager.restoreLastPlayback(playWhenRestored = false)

        assertTrue(result.shouldCreatePlayer)
        assertTrue(result.shouldPrepare)
        assertTrue(result.playWhenReady)
    }

    @Test
    fun skipToPreviousMovesCursorAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L, durationMs = 10_000L), track(2L)), 1)

        manager.skipToPrevious()

        assertEquals(0, manager.queueStateSnapshot().currentIndex)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(2L, store.savedPositions.first().first)
    }

    @Test
    fun skipToNextMovesCursorPersistsAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1300L
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(track(1L))
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(track(1L, durationMs = 10_000L), track(2L, durationMs = 10_000L)),
                0
        )

        val reused = manager.skipToNextImmediately()

        assertFalse(reused)
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(2L, manager.queueStateSnapshot().currentTrack?.id)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertEquals(listOf(1L to 1300L, 2L to 0L), store.savedPositions)
        assertTrue(store.resumeRequested)
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
    }

    @Test
    fun skipToPreviousWrapsToQueueEndAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 900L
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(
                        track(1L, durationMs = 10_000L),
                        track(2L, durationMs = 10_000L),
                        track(3L, durationMs = 10_000L)
                ),
                0
        )

        val reused = manager.skipToPrevious()

        assertFalse(reused)
        assertEquals(2, manager.queueStateSnapshot().currentIndex)
        assertEquals(3L, manager.queueStateSnapshot().currentTrack?.id)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(listOf(1L, 2L, 3L), store.savedTracks.map { it.id })
        assertEquals(2, store.savedIndex)
        assertEquals(listOf(1L to 900L, 3L to 0L), store.savedPositions)
        assertTrue(store.resumeRequested)
    }

    @Test
    fun skipToPreviousNoOpsWhenQueueIsEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val reused = manager.skipToPrevious()

        assertFalse(reused)
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertFalse(provider.prepareCurrentCalled)
        assertFalse(provider.queuePlaybackActions.published)
        assertTrue(store.savedPositions.isEmpty())
        assertEquals(-1, store.savedIndex)
    }

    @Test
    fun skipToNextNoOpsWhenQueueIsEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val reused = manager.skipToNextImmediately()

        assertFalse(reused)
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertFalse(provider.prepareCurrentCalled)
        assertFalse(provider.queuePlaybackActions.published)
        assertTrue(store.savedPositions.isEmpty())
        assertEquals(-1, store.savedIndex)
    }

    @Test
    fun skipToNextReusesMirroredQueueWithoutPreparingNewMediaSources() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L, durationMs = 10_000L), track(2L)))
        provider.playbackPositionMsValue = 2400L
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = true
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L, durationMs = 10_000L), track(2L)), 0)
        provider.streamingRestoreProvider.restoredDataPaths.clear()

        val reused = manager.skipToNextImmediately()

        assertTrue(reused)
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
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
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1200L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)
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
        val provider = FakeQueueState()
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = true
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
        provider.streamingRestoreProvider.restoredDataPaths.clear()
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
        val provider = FakeQueueState()
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = false
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)

        val reused = manager.reuseMirroredQueueIfAvailable(playWhenReady = true, startPositionMs = 0L)

        assertFalse(reused)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun queuePreparationForNewPlayerRestoresMirroredHeadersAndReturnsSnapshot() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.parse("content://music/2"))
                ),
                0
        )
        provider.streamingRestoreProvider.restoredDataPaths.clear()

        val tracks = manager.queuePreparationForNewPlayer().mirroredQueueTracks

        assertEquals(listOf(1L, 2L), tracks?.map { it.id })
        assertEquals(listOf("/music/1", "/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
    }

    @Test
    fun queuePreparationForNewPlayerRejectsUnplayableMirroredTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        manager.playQueue(listOf(track(1L, null, "")), 0, 0L)

        val tracks = manager.queuePreparationForNewPlayer().mirroredQueueTracks

        assertEquals(null, tracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun queuePreparationForNewPlayerRejectsEmptyMirroredUriWithoutPartialRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.EMPTY, "/music/2")
                )
        )
        val manager = queueManager(store, provider)
        manager.playQueue(provider.queue.toList(), 0, 0L)

        val tracks = manager.queuePreparationForNewPlayer().mirroredQueueTracks

        assertEquals(null, tracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun queuePreparationForNewPlayerReturnsCurrentTrackAndMirroredTracks() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.parse("content://music/2"))
                ),
                1
        )
        provider.streamingRestoreProvider.restoredDataPaths.clear()

        val preparation = manager.queuePreparationForNewPlayer()

        assertEquals(2L, preparation.currentTrack?.id)
        assertEquals(1, preparation.startIndex)
        assertEquals(listOf(1L, 2L), preparation.mirroredQueueTracks?.map { it.id })
        assertEquals(listOf("/music/1", "/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
    }

    @Test
    fun queuePreparationForNewPlayerFallsBackToSingleTrackWhenMirrorRejected() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.EMPTY, "/music/2")
                )
        )
        val manager = queueManager(store, provider)
        manager.playQueue(provider.queue.toList(), 0, 0L)

        val preparation = manager.queuePreparationForNewPlayer()

        assertEquals(1L, preparation.currentTrack?.id)
        assertEquals(0, preparation.startIndex)
        assertEquals(null, preparation.mirroredQueueTracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun queuePreparationForNewPlayerIsEmptyWithoutCurrentTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.add(track(1L, android.net.Uri.parse("content://music/1")))
        val manager = queueManager(store, provider)
        setRawCurrentIndex(manager, 3)

        val preparation = manager.queuePreparationForNewPlayer()

        assertEquals(null, preparation.currentTrack)
        assertEquals(0, preparation.startIndex)
        assertEquals(null, preparation.mirroredQueueTracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun currentIndexStateIsOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertTrue(manager.queueStateSnapshot().isQueueEmpty)
        assertEquals(0, manager.queueStateSnapshot().queueSize)

        setRawCurrentIndex(manager, 7)
        assertEquals(7, manager.queueStateSnapshot().currentIndex)

        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        assertFalse(manager.queueStateSnapshot().isQueueEmpty)
        assertEquals(3, manager.queueStateSnapshot().queueSize)

        manager.prepareStopAfterAutomaticAdvance(completedIndex = 9)
        assertEquals(2, manager.queueStateSnapshot().currentIndex)

        provider.queue.clear()
        manager.prepareStopAfterAutomaticAdvance(completedIndex = 2)
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
    }

    @Test
    fun currentTrackStateIsExposedThroughQueueStateSnapshot() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertEquals(null, manager.queueStateSnapshot().currentTrack)

        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
        assertEquals(2L, manager.queueStateSnapshot().currentTrack?.id)

        setRawCurrentIndex(manager, 2)
        assertEquals(null, manager.queueStateSnapshot().currentTrack)
    }

    @Test
    fun queueSnapshotIsOwnedByQueueManagerAndDefensive() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)

        val snapshot = manager.queueSnapshot()
        provider.queue.clear()

        assertEquals(listOf(1L, 2L), snapshot.map { it.id })
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot as MutableList<Track>).add(track(3L))
        }
    }

    @Test
    fun queueStateSnapshotIsOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)

        val snapshot = manager.queueStateSnapshot()

        assertEquals(2L, snapshot.currentTrack?.id)
        assertEquals(1, snapshot.currentIndex)
        assertEquals(2, snapshot.queueSize)
        assertFalse(snapshot.isQueueEmpty)
        assertTrue(snapshot.hasCurrentTrack)
        assertTrue(snapshot.hasMultipleTracks)
        assertTrue(snapshot.isAtEndOfQueue)
    }

    @Test
    fun queueStateSnapshotBooleansAreDerivedFromSourceFields() {
        val empty = PlaybackQueueManager.QueueStateSnapshot(
            currentTrack = null,
            currentIndex = -1,
            queueSize = 0
        )
        assertTrue(empty.isQueueEmpty)
        assertFalse(empty.hasCurrentTrack)
        assertFalse(empty.hasMultipleTracks)
        assertTrue(empty.isAtEndOfQueue)

        val middle = PlaybackQueueManager.QueueStateSnapshot(
            currentTrack = track(1L),
            currentIndex = 0,
            queueSize = 2
        )
        assertFalse(middle.isQueueEmpty)
        assertTrue(middle.hasCurrentTrack)
        assertTrue(middle.hasMultipleTracks)
        assertFalse(middle.isAtEndOfQueue)

        val end = middle.copy(currentIndex = 1)
        assertTrue(end.isAtEndOfQueue)
    }

    @Test
    fun emptyQueueStateSnapshotIsOwnedByQueueManager() {
        val snapshot = PlaybackQueueManager.QueueStateSnapshot.empty()

        assertEquals(null, snapshot.currentTrack)
        assertEquals(-1, snapshot.currentIndex)
        assertEquals(0, snapshot.queueSize)
        assertTrue(snapshot.isQueueEmpty)
        assertFalse(snapshot.hasCurrentTrack)
        assertFalse(snapshot.hasMultipleTracks)
        assertTrue(snapshot.isAtEndOfQueue)
    }

    @Test
    fun queueStateSnapshotHandlesMissingCurrentSlot() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.add(track(1L))
        val manager = queueManager(store, provider)
        setRawCurrentIndex(manager, 3)

        val snapshot = manager.queueStateSnapshot()

        assertEquals(null, snapshot.currentTrack)
        assertEquals(3, snapshot.currentIndex)
        assertEquals(1, snapshot.queueSize)
        assertFalse(snapshot.isQueueEmpty)
        assertFalse(snapshot.hasCurrentTrack)
        assertFalse(snapshot.hasMultipleTracks)
        assertTrue(snapshot.isAtEndOfQueue)
    }

    @Test
    fun queueStateSnapshotHandlesEmptyQueue() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.queueStateSnapshot()

        assertEquals(null, snapshot.currentTrack)
        assertEquals(-1, snapshot.currentIndex)
        assertEquals(0, snapshot.queueSize)
        assertTrue(snapshot.isQueueEmpty)
        assertFalse(snapshot.hasCurrentTrack)
        assertFalse(snapshot.hasMultipleTracks)
        assertTrue(snapshot.isAtEndOfQueue)
    }

    @Test
    fun prepareStopAndClearPlaybackStateClearsQueueAndCurrentIndexWithoutPublishing() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)

        manager.prepareStopAndClearPlaybackState()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertFalse(provider.queuePlaybackActions.published)
    }

    @Test
    fun queueMutationReturnsStopRequestWhenQueueBecomesEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)

        assertTrue(manager.removeTracksById(setOf(1L)))

        assertTrue(provider.queue.isEmpty())
        assertFalse(provider.queuePlaybackActions.prepareCurrentCalled)
        assertFalse(provider.queuePlaybackActions.published)
    }

    @Test
    fun persistQueueStateSavesCurrentSnapshotThroughQueueStore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)

        manager.persistQueueState()

        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
    }

    @Test
    fun queueAdvanceSnapshotIsOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertFalse(manager.queueStateSnapshot().hasMultipleTracks)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)

        provider.queue.add(track(1L))
        assertFalse(manager.queueStateSnapshot().hasMultipleTracks)
        assertFalse(manager.queueStateSnapshot().isAtEndOfQueue)
        restoreQueue(manager, store, listOf(track(1L)), 0)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)

        provider.queue.clear()
        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        assertTrue(manager.queueStateSnapshot().hasMultipleTracks)
        assertFalse(manager.queueStateSnapshot().isAtEndOfQueue)

        restoreQueue(manager, store, listOf(track(1L), track(2L), track(3L)), 2)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)

        restoreQueue(manager, store, listOf(track(1L), track(2L), track(3L)), 4)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)
    }

    @Test
    fun mirroredTransitionIndexValidationAndRepeatStopAreOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L), track(3L)), 1)

        assertEquals(null, manager.applyMirroredTransitionIndex(-1, automaticAdvance = false))
        assertEquals(null, manager.applyMirroredTransitionIndex(3, automaticAdvance = false))
        assertEquals(null, manager.applyMirroredTransitionIndex(1, automaticAdvance = false))
        assertEquals(1, manager.queueStateSnapshot().currentIndex)

        val manualTransition = manager.applyMirroredTransitionIndex(2, automaticAdvance = false)
        assertEquals(1, manualTransition?.completedIndex)
        assertEquals(false, manualTransition?.stopAfterAutomaticAdvance)
        assertEquals(2, manager.queueStateSnapshot().currentIndex)

        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)
        val automaticTransition = manager.applyMirroredTransitionIndex(0, automaticAdvance = true)
        assertEquals(2, automaticTransition?.completedIndex)
        assertEquals(true, automaticTransition?.stopAfterAutomaticAdvance)
        assertEquals(2, manager.queueStateSnapshot().currentIndex)
    }

    @Test
    fun mirroredTransitionPersistsAndResetsNewCurrentTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val first = track(1L, durationMs = 10_000L)
        val second = track(2L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 4200L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(first, second), 0)
        provider.streamingRestoreProvider.restoredDataPaths.clear()
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(first)
        provider.positionManager.setRestoredPosition(second.id, 1200L, explicit = true)

        manager.applyMirroredTransitionIndex(1, automaticAdvance = false)

        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(listOf(2L to 4200L, 2L to 0L), store.savedPositions)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
        assertEquals(listOf("/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0L, provider.positionManager.restoredPositionFor(second))
    }

    private fun queueManager(store: FakeQueueStore, provider: FakeQueueState): PlaybackQueueManager {
        lateinit var manager: PlaybackQueueManager
        val positionManager = PlaybackPositionManager(
                store,
                object : PlaybackPositionManager.StateProvider {
                    override fun currentTrack(): Track? {
                        val index = manager.queueStateSnapshot().currentIndex
                        return provider.queue.getOrNull(index)
                    }

                    override fun positionMs(): Long = provider.playbackPositionMsValue
                }
        )
        manager = PlaybackQueueManager(
                store,
                provider.queue,
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

    private fun restoreQueue(
            manager: PlaybackQueueManager,
            store: FakeQueueStore,
            tracks: List<Track>,
            currentIndex: Int
    ) {
        store.restore = PlaybackQueueState(tracks.map(::restorableTrack), currentIndex)
        manager.restorePlaybackQueue()
        store.savedTracks = emptyList()
        store.savedIndex = -1
        store.savedPositions.clear()
    }

    private fun setRawCurrentIndex(manager: PlaybackQueueManager, currentIndex: Int) {
        val field = PlaybackQueueManager::class.java.getDeclaredField("currentIndex")
        field.isAccessible = true
        field.setInt(manager, currentIndex)
    }

    private fun restorableTrack(track: Track): Track {
        if (track.contentUri != null && track.contentUri != android.net.Uri.EMPTY) {
            return track
        }
        return Track(
                track.id,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                android.net.Uri.parse("content://test/${track.id}"),
                track.dataPath,
                track.albumId,
                track.albumArtUri
        )
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
        var playbackRestoreEnabled = true
        val savedPlaybackRestoreEnabledValues = mutableListOf<Boolean>()

        override fun load(): PlaybackQueueState = restore
        override fun save(tracks: List<Track>, currentIndex: Int) {
            savedTracks = tracks
            savedIndex = currentIndex
        }
        override fun loadResumeRequested(): Boolean = resumeRequested
        override fun saveResumeRequested(requested: Boolean) {
            resumeRequested = requested
        }
        override fun loadPlaybackRestoreEnabled(): Boolean = playbackRestoreEnabled
        override fun savePlaybackRestoreEnabled(enabled: Boolean) {
            playbackRestoreEnabled = enabled
            savedPlaybackRestoreEnabledValues.add(enabled)
        }
        override fun loadPlaybackPositionTrackId(): Long = -1L
        override fun loadPlaybackPositionMs(): Long = 0L
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
            savedPositions.add(trackId to positionMs)
        }
    }

    private class FakeQueueState {
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
    }

    private class FakeQueuePlaybackActions : PlaybackQueueManager.QueuePlaybackActions {
        var prepareCurrentCalled = false
        var published = false
        override fun prepareCurrent(playWhenReady: Boolean) {
            prepareCurrentCalled = playWhenReady
        }
        override fun publishState() {
            published = true
        }
    }

    private class FakeStreamingRestoreProvider : PlaybackQueueManager.StreamingRestoreProvider {
        val restoredDataPaths = mutableListOf<String?>()
        override fun restoreTrackForPlayback(track: Track): Track {
            restoredDataPaths.add(track.dataPath)
            return track
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
