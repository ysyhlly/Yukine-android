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
        assertEquals(0, manager.currentIndex())
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
        assertEquals(0, manager.currentIndex())
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
    fun advanceQueueIndexToNextReadsRepeatModeFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        manager.advanceQueueIndexToNext()

        assertEquals(1, manager.currentIndex())
    }

    @Test
    fun completionActionRepeatsCurrentWhenRepeatOne() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ONE)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.REPEAT_CURRENT,
            manager.playbackCompletionAction()
        )
    }

    @Test
    fun completionActionStopsAndClearsWhenQueueIsEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR,
            manager.playbackCompletionAction()
        )
    }

    @Test
    fun completionActionStopsAtEndWhenRepeatOff() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.STOP_AT_END,
            manager.playbackCompletionAction()
        )
    }

    @Test
    fun completionActionAdvancesWhenQueueCanContinue() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT,
            manager.playbackCompletionAction()
        )
    }

    @Test
    fun canCrossfadeAdvanceRequiresMultipleTracksAndAllowsRepeatAllAtEnd() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertFalse(manager.canCrossfadeAdvance(PlaybackRepeatMode.REPEAT_ALL))

        provider.queue.add(track(1L))
        manager.setCurrentIndex(0)
        assertFalse(manager.canCrossfadeAdvance(PlaybackRepeatMode.REPEAT_ALL))

        provider.queue.add(track(2L))
        assertTrue(manager.canCrossfadeAdvance(PlaybackRepeatMode.REPEAT_OFF))

        manager.setCurrentIndex(1)
        assertFalse(manager.canCrossfadeAdvance(PlaybackRepeatMode.REPEAT_OFF))
        assertTrue(manager.canCrossfadeAdvance(PlaybackRepeatMode.REPEAT_ALL))
    }

    @Test
    fun canSkipFailedTrackRequiresIdentifiedTrackAndMultipleQueueTracks() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val failed = track(1L)
        val manager = queueManager(store, provider)

        assertFalse(manager.canSkipFailedTrack(null))
        assertFalse(manager.canSkipFailedTrack(track(-1L)))
        assertFalse(manager.canSkipFailedTrack(failed))

        provider.queue.add(failed)
        assertFalse(manager.canSkipFailedTrack(failed))

        provider.queue.add(track(2L))
        assertTrue(manager.canSkipFailedTrack(failed))
    }

    @Test
    fun upcomingTracksForPrecacheStopsAtQueueEndWhenRepeatOff() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
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
        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(2)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)

        assertEquals(
            listOf(1L, 2L),
            manager.upcomingTracksForPrecache(4).map { it.id }
        )
    }

    @Test
    fun preparePlaybackCompletionResetsCompletedTrackAndClearsRepeatRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.queue.add(current)
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        manager.preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction.REPEAT_CURRENT)

        assertEquals(listOf(1L to 0L), store.savedPositions)
        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun preparePlaybackCompletionKeepsRestoreForAdvanceAction() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.queue.add(current)
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        manager.preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT)

        assertEquals(listOf(1L to 0L), store.savedPositions)
        assertEquals(4500L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun prepareStopAtEndOfQueueClearsQueuePlaybackStateThroughOwners() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.queue.add(current)
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
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
        provider.queue.addAll(
            listOf(
                track(1L, durationMs = 10_000L),
                track(2L, durationMs = 10_000L)
            )
        )
        provider.playbackPositionMsValue = 5300L
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

        manager.prepareStopAfterAutomaticAdvance(completedIndex = 1)

        assertEquals(1, manager.currentIndex())
        assertEquals(listOf(2L to 5300L, 2L to 0L), store.savedPositions)
    }

    @Test
    fun prepareStopAndClearPlaybackStateClearsQueuePositionRuntimeAndResumeState() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(2L, durationMs = 10_000L)
        provider.queue.addAll(listOf(track(1L), current))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        provider.positionManager.setRestoredPosition(current.id, 2500L, explicit = true)
        provider.runtimeStateManager.setPreparing(true)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(current)
        provider.transitionStateManager.setFadeOutAdvancing(true)
        store.resumeRequested = true

        manager.prepareStopAndClearPlaybackState()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.currentIndex())
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
    fun consumeRestoredPositionAfterPrepareClearsOnlyWhenStartPositionWasUsed() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.queue.add(current)
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.positionManager.setRestoredPosition(current.id, 3200L, explicit = true)

        manager.consumeRestoredPositionAfterPrepare(0L)

        assertEquals(3200L, provider.positionManager.restoredPositionFor(current))

        manager.consumeRestoredPositionAfterPrepare(3200L)

        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun restoredPositionForDelegatesThroughQueueOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val streaming = track(2L, dataPath = "streaming:test:2", durationMs = 10_000L)
        provider.queue.addAll(listOf(current, streaming))
        val manager = queueManager(store, provider)

        provider.positionManager.setRestoredPosition(current.id, 9900L, explicit = true)
        assertEquals(8000L, manager.restoredPositionFor(current))

        provider.positionManager.setRestoredPosition(streaming.id, 4000L, explicit = false)
        assertEquals(0L, manager.restoredPositionFor(streaming))

        provider.positionManager.setRestoredPosition(streaming.id, 4000L, explicit = true)
        assertEquals(4000L, manager.restoredPositionFor(streaming))
    }

    @Test
    fun persistCurrentPlaybackPositionUsesPositionOwnerThrottleAndForce() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.queue.add(current)
        provider.playbackPositionMsValue = 2300L
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)

        manager.persistCurrentPlaybackPosition(force = false)
        manager.persistCurrentPlaybackPosition(force = false)
        manager.persistCurrentPlaybackPosition(force = true)

        assertEquals(listOf(1L to 2300L, 1L to 2300L), store.savedPositions)
    }

    @Test
    fun replaceQueuedTrackContinuesPreparingPlaybackFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.add(track(1L))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.runtimeStateManager.setPreparing(true)

        manager.replaceQueuedTrack(track(1L, durationMs = 2_000L))

        assertTrue(provider.prepareCurrentCalled)
    }

    @Test
    fun replaceCurrentQueueTrackPersistsCurrentSlotOnly() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)
        val replacement = track(9L)

        assertTrue(manager.replaceCurrentQueueTrack(replacement))

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
        manager.setCurrentIndex(3)

        assertFalse(manager.replaceCurrentQueueTrack(track(9L)))

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
        val snapshot = manager.restorePlaybackQueue()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.currentIndex())
        assertTrue(snapshot.isQueueEmpty)
        assertEquals(-1, snapshot.currentIndex)
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

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(listOf("streaming:netease:2", "/music/3"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0, manager.currentIndex())
        assertEquals(2L, snapshot.currentTrack?.id)
        assertEquals(0, snapshot.currentIndex)
        assertEquals(2, snapshot.queueSize)
        assertFalse(snapshot.isQueueEmpty)
        assertTrue(snapshot.hasCurrentTrack)
    }

    @Test
    fun skipToPreviousMovesCursorAndPreparesPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L, durationMs = 10_000L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

        manager.skipToPrevious()

        assertEquals(0, manager.currentIndex())
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(2L, store.savedPositions.first().first)
    }

    @Test
    fun skipToPreviousNoOpsWhenQueueIsEmpty() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val reused = manager.skipToPrevious()

        assertFalse(reused)
        assertEquals(-1, manager.currentIndex())
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
        assertEquals(-1, manager.currentIndex())
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
        val provider = FakeQueueState()
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
        val provider = FakeQueueState()
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
        val provider = FakeQueueState()
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
        val provider = FakeQueueState()
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
        val provider = FakeQueueState()
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
        val provider = FakeQueueState()
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
    fun queuePreparationForNewPlayerReturnsCurrentTrackAndMirroredTracks() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1")),
                        track(2L, android.net.Uri.parse("content://music/2"))
                )
        )
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

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
        manager.setCurrentIndex(0)

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
        manager.setCurrentIndex(3)

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

        assertEquals(-1, manager.currentIndex())
        assertTrue(manager.queueStateSnapshot().isQueueEmpty)
        assertEquals(0, manager.queueStateSnapshot().queueSize)
        assertEquals(0, manager.safeCurrentIndex())

        manager.setCurrentIndex(7)
        assertEquals(7, manager.currentIndex())
        assertEquals(0, manager.safeCurrentIndex())

        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        assertFalse(manager.queueStateSnapshot().isQueueEmpty)
        assertEquals(3, manager.queueStateSnapshot().queueSize)
        assertEquals(2, manager.clampCurrentIndex())
        assertEquals(2, manager.safeCurrentIndex())

        manager.setClampedCurrentIndex(index = 9)
        assertEquals(2, manager.currentIndex())

        provider.queue.clear()
        manager.setClampedCurrentIndex(index = 2)
        assertEquals(-1, manager.currentIndex())
        assertEquals(0, manager.clampCurrentIndex())
    }

    @Test
    fun currentTrackStateIsOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)

        assertEquals(null, manager.currentTrack())

        manager.setCurrentIndex(1)
        assertEquals(2L, manager.currentTrack()?.id)

        manager.setCurrentIndex(2)
        assertEquals(null, manager.currentTrack())
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
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

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
        manager.setCurrentIndex(3)

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
    fun clearQueueStateClearsQueueAndCurrentIndexWithoutPublishing() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

        manager.clearQueueState()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.currentIndex())
        assertFalse(provider.queuePlaybackActions.published)
        assertFalse(provider.queuePlaybackActions.stoppedAndCleared)
    }

    @Test
    fun persistQueueStateSavesCurrentSnapshotThroughQueueStore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

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
        manager.setCurrentIndex(0)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)

        provider.queue.clear()
        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        assertTrue(manager.queueStateSnapshot().hasMultipleTracks)
        manager.setCurrentIndex(0)
        assertFalse(manager.queueStateSnapshot().isAtEndOfQueue)

        manager.setCurrentIndex(2)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)

        manager.setCurrentIndex(4)
        assertTrue(manager.queueStateSnapshot().isAtEndOfQueue)
    }

    @Test
    fun mirroredTransitionIndexValidationAndRepeatStopAreOwnedByQueueManager() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L), track(3L)))
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(1)

        assertEquals(null, manager.applyMirroredTransitionIndex(-1, automaticAdvance = false))
        assertEquals(null, manager.applyMirroredTransitionIndex(3, automaticAdvance = false))
        assertEquals(null, manager.applyMirroredTransitionIndex(1, automaticAdvance = false))
        assertEquals(1, manager.currentIndex())

        val manualTransition = manager.applyMirroredTransitionIndex(2, automaticAdvance = false)
        assertEquals(1, manualTransition?.completedIndex)
        assertEquals(false, manualTransition?.stopAfterAutomaticAdvance)
        assertEquals(2, manager.currentIndex())

        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)
        val automaticTransition = manager.applyMirroredTransitionIndex(0, automaticAdvance = true)
        assertEquals(2, automaticTransition?.completedIndex)
        assertEquals(true, automaticTransition?.stopAfterAutomaticAdvance)
        assertEquals(2, manager.currentIndex())
    }

    @Test
    fun prepareMirroredTransitionPlaybackStatePersistsAndResetsNewCurrentTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val first = track(1L, durationMs = 10_000L)
        val second = track(2L, durationMs = 10_000L)
        provider.queue.addAll(listOf(first, second))
        provider.playbackPositionMsValue = 4200L
        val manager = queueManager(store, provider)
        manager.setCurrentIndex(0)
        provider.runtimeStateManager.setErrorMessage("stale")
        provider.transitionStateManager.setLastMarkedTrack(first)
        provider.positionManager.setRestoredPosition(second.id, 1200L, explicit = true)

        manager.applyMirroredTransitionIndex(1, automaticAdvance = false)
        manager.prepareMirroredTransitionPlaybackState()

        assertEquals(1, manager.currentIndex())
        assertEquals(listOf(2L to 4200L, 2L to 0L), store.savedPositions)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
        assertEquals(listOf("/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0L, provider.positionManager.restoredPositionFor(second))
    }

    @Test
    fun matchesMirroredQueueChecksItemCountAndDelegatesTrackIdentity() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.queue.addAll(listOf(track(1L), track(2L)))
        val manager = queueManager(store, provider)
        val matchedTrackIds = mutableListOf<Long>()

        assertFalse(
                manager.matchesMirroredQueue(1, object : PlaybackQueueManager.QueueTrackMatcher {
                    override fun matches(index: Int, track: Track): Boolean = true
                })
        )

        assertFalse(
                manager.matchesMirroredQueue(2, object : PlaybackQueueManager.QueueTrackMatcher {
                    override fun matches(index: Int, track: Track): Boolean {
                        matchedTrackIds.add(track.id)
                        return index == 0
                    }
                })
        )
        assertEquals(listOf(1L, 2L), matchedTrackIds)

        assertTrue(
                manager.matchesMirroredQueue(2, object : PlaybackQueueManager.QueueTrackMatcher {
                    override fun matches(index: Int, track: Track): Boolean = track.id == index + 1L
                })
        )
    }

    private fun queueManager(store: FakeQueueStore, provider: FakeQueueState): PlaybackQueueManager {
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
