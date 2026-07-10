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
import org.junit.Assert.assertNull
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
    fun playFirstQueuedTrackPersistsResumeRequestForColdStartRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), -1)

        manager.playFirstQueuedTrack()

        assertEquals(0, manager.queueStateSnapshot().currentIndex)
        assertTrue(provider.prepareCurrentCalled)
        assertTrue(provider.queuePlaybackActions.lastPreparePlayWhenReady)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(0, store.savedIndex)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertTrue(store.resumeRequested)
    }

    @Test
    fun explicitPlayAfterActiveColdRestoreStartsCurrentTrackFromBeginning() {
        val store = FakeQueueStore()
        store.resumeRequested = true
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.positionManager.setRestoredPosition(current.id, 6400L, explicit = true)

        assertTrue(manager.prepareCurrentForExplicitPlay())

        assertTrue(provider.prepareCurrentCalled)
        assertTrue(provider.queuePlaybackActions.lastPreparePlayWhenReady)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
        assertTrue(store.resumeRequested)
    }

    @Test
    fun explicitPlayAfterPausedColdRestoreKeepsSavedPosition() {
        val store = FakeQueueStore()
        store.resumeRequested = false
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.positionManager.setRestoredPosition(current.id, 6400L, explicit = true)

        assertTrue(manager.prepareCurrentForExplicitPlay())

        assertTrue(provider.prepareCurrentCalled)
        assertTrue(provider.queuePlaybackActions.lastPreparePlayWhenReady)
        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
        assertEquals(6400L, provider.positionManager.restoredPositionFor(current))
        assertTrue(store.resumeRequested)
    }

    @Test
    fun explicitPlayWithoutCurrentTrackDoesNothing() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        assertFalse(manager.prepareCurrentForExplicitPlay())

        assertFalse(provider.prepareCurrentCalled)
        assertTrue(store.savedPositions.isEmpty())
        assertFalse(store.resumeRequested)
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
    fun playQueueClampsExplicitStartPositionForImmediateRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val shortTrack = track(10L, durationMs = 10_000L)

        manager.playQueue(listOf(shortTrack), 0, 99_000L)

        assertEquals(8_000L, provider.positionManager.restoredPositionFor(shortTrack))

        manager.playQueue(listOf(shortTrack), 0, -500L)

        assertEquals(0L, provider.positionManager.restoredPositionFor(shortTrack))
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
    fun serviceBoundaryResumeRequestPersistsThroughQueueOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.savePlaybackResumeRequested(true)
        assertTrue(store.resumeRequested)

        manager.savePlaybackResumeRequested(false)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun serviceBoundaryPlaybackPositionDoesNotPersistThroughQueueOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(10L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 4100L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)

        manager.persistCurrentPlaybackPosition(force = false)
        provider.playbackPositionMsValue = 4700L
        manager.persistCurrentPlaybackPosition(force = true)

        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
    }

    @Test
    fun serviceBoundaryPausePositionPersistsThroughQueueOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(10L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 4_100L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)

        manager.persistPausedPlaybackPosition()

        assertEquals(listOf(current.id to 4_100L), store.savedPositions)
    }

    @Test
    fun resumingPlaybackConsumesTheSavedPauseCheckpoint() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(10L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 4_100L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)

        manager.persistPausedPlaybackPosition()
        manager.clearPausedPlaybackPosition()

        assertEquals(
                listOf(current.id to 4_100L, -1L to 0L),
                store.savedPositions
        )
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
    fun completionActionRepeatsCurrentWhenRepeatOne() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)
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
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
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
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        assertEquals(
            PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT,
            manager.playbackCompletionAction()
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
    fun preparePlaybackCompletionResetsCompletedTrackAndClearsRepeatRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        manager.preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction.REPEAT_CURRENT)

        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
    }

    @Test
    fun preparePlaybackCompletionKeepsRestoreForAdvanceAction() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
        provider.positionManager.setRestoredPosition(current.id, 4500L, explicit = true)

        manager.preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT)

        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
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
    fun prepareStopAfterAutomaticAdvanceClearsThePauseCheckpoint() {
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
        assertEquals(listOf(-1L to 0L), store.savedPositions)
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
    fun consumeRestoredPositionAfterPrepareClearsOnlyWhenStartPositionWasUsed() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)
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
    fun persistCurrentPlaybackPositionDoesNotWriteProgress() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val current = track(1L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 2300L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(current), 0)

        manager.persistCurrentPlaybackPosition(force = false)
        manager.persistCurrentPlaybackPosition(force = false)
        manager.persistCurrentPlaybackPosition(force = true)

        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
    }

    @Test
    fun replaceQueuedTrackContinuesPreparingPlaybackFromRuntimeStateOwner() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)
        provider.runtimeStateManager.setPreparing(true)

        manager.replaceQueuedTrack(track(1L, durationMs = 2_000L))

        assertTrue(provider.prepareCurrentCalled)
    }

    @Test
    fun replaceCurrentQueueTrackPersistsCurrentSlotOnly() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 1)
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
        setRawCurrentIndex(manager, 3)

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
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
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

        val snapshot = manager.restorePlaybackQueue()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
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

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(listOf("streaming:netease:2", "/music/3"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0, manager.queueStateSnapshot().currentIndex)
        assertEquals(2L, snapshot.currentTrack?.id)
        assertEquals(0, snapshot.currentIndex)
        assertEquals(2, snapshot.queueSize)
        assertFalse(snapshot.isQueueEmpty)
        assertTrue(snapshot.hasCurrentTrack)
    }

    @Test
    fun restorePlaybackQueueKeepsCurrentTrackAfterFilteringInvalidRows() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(
                        track(-1L, android.net.Uri.parse("content://music/invalid"), "/music/invalid"),
                        track(1L, null, "/music/missing-uri"),
                        track(2L, null, "streaming:netease:2"),
                        track(3L, android.net.Uri.parse("content://music/3"), "/music/3")
                ),
                3
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(1, snapshot.currentIndex)
        assertEquals(3L, snapshot.currentTrack?.id)
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(3L, manager.queueStateSnapshot().currentTrack?.id)
        assertEquals(1, store.savedIndex)
    }

    @Test
    fun restorePlaybackQueueFallsForwardWhenPersistedCurrentTrackIsFilteredOut() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(
                        track(1L, android.net.Uri.parse("content://music/1"), "/music/1"),
                        track(2L, null, "/music/missing-uri"),
                        track(3L, android.net.Uri.parse("content://music/3"), "/music/3"),
                        track(4L, null, "streaming:netease:4")
                ),
                1
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(listOf(1L, 3L, 4L), provider.queue.map { it.id })
        assertEquals(1, snapshot.currentIndex)
        assertEquals(3L, snapshot.currentTrack?.id)
        assertEquals(1, store.savedIndex)
    }

    @Test
    fun restoreLargeQueueOnlyHydratesTheSelectedCurrentStreamingTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val tracks = List(65) { index ->
            track(index.toLong(), null, "streaming:netease:$index")
        }
        store.restore = PlaybackQueueState(tracks, 42)
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(65, provider.queue.size)
        assertEquals(42, snapshot.currentIndex)
        assertEquals(42L, snapshot.currentTrack?.id)
        assertEquals(listOf(42L), provider.streamingRestoreProvider.restoredTrackIds)
        assertEquals(listOf("streaming:netease:42"), provider.streamingRestoreProvider.restoredDataPaths)
    }

    @Test
    fun restoreLargeQueueStagesTracksBeforeOneQueueCommit() {
        val store = FakeQueueStore()
        val recordingQueue = CountingQueue()
        val provider = FakeQueueState(recordingQueue)
        val tracks = List(1_000) { index ->
            track(index.toLong(), android.net.Uri.parse("content://music/$index"), "/music/$index")
        }
        store.restore = PlaybackQueueState(tracks, 700)
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(1_000, snapshot.queueSize)
        assertEquals(700, snapshot.currentIndex)
        assertEquals(1, recordingQueue.addAllCalls)
        assertEquals(0, recordingQueue.singleAddCalls)
    }

    @Test
    fun restoreLargeQueueReplacesAndHydratesTheSelectedCurrentStreamingTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val tracks = List(65) { index ->
            track(index.toLong(), null, "streaming:netease:$index")
        }
        val replacement = track(
            42L,
            android.net.Uri.parse("https://example.com/42.mp3"),
            "streaming:netease:42"
        )
        provider.streamingRestoreProvider.replacements[42L] = replacement
        store.restore = PlaybackQueueState(tracks, 42)
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(replacement, provider.queue[42])
        assertEquals(replacement, snapshot.currentTrack)
        assertEquals(listOf(42L), provider.streamingRestoreProvider.restoredTrackIds)
        assertEquals(listOf(replacement.dataPath), provider.streamingRestoreProvider.restoredDataPaths)
    }

    @Test
    fun restorePlaybackQueueClearsPersistedStateWhenAllRowsAreFilteredOut() {
        val store = FakeQueueStore()
        store.resumeRequested = true
        store.restore = PlaybackQueueState(
                listOf(
                        track(-1L, android.net.Uri.parse("content://music/invalid"), "/music/invalid"),
                        track(2L, null, "/music/missing-uri")
                ),
                0
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertTrue(snapshot.isQueueEmpty)
        assertEquals(-1, snapshot.currentIndex)
        assertEquals(emptyList<Track>(), store.savedTracks)
        assertEquals(-1, store.savedIndex)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun restorePlaybackQueueRestoresPausedProgress() {
        val store = FakeQueueStore()
        val current = track(8L, durationMs = 10_000L)
        store.restore = PlaybackQueueState(listOf(restorableTrack(current)), 0)
        store.storedPlaybackPositionTrackId = current.id
        store.storedPlaybackPositionMs = 6_400L
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(current.id, snapshot.currentTrack?.id)
        assertEquals(6_400L, provider.positionManager.restoredPositionFor(current))
        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
        assertEquals(current.id, store.storedPlaybackPositionTrackId)
        assertEquals(6_400L, store.storedPlaybackPositionMs)
    }

    @Test
    fun restorePlaybackQueueRestoresPausedStreamingProgress() {
        val store = FakeQueueStore()
        val current = track(8L, dataPath = "streaming:qq:8", durationMs = 10_000L)
        store.restore = PlaybackQueueState(listOf(restorableTrack(current)), 0)
        store.storedPlaybackPositionTrackId = current.id
        store.storedPlaybackPositionMs = 6_400L
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(current.id, snapshot.currentTrack?.id)
        assertEquals(6_400L, provider.positionManager.restoredPositionFor(current))
        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
    }

    @Test
    fun restorePlaybackQueueClearsCheckpointAfterAnActiveShutdown() {
        val store = FakeQueueStore()
        store.resumeRequested = true
        val current = track(8L, durationMs = 10_000L)
        store.restore = PlaybackQueueState(listOf(restorableTrack(current)), 0)
        store.storedPlaybackPositionTrackId = current.id
        store.storedPlaybackPositionMs = 6_400L
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()

        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertEquals(-1L, store.storedPlaybackPositionTrackId)
        assertEquals(0L, store.storedPlaybackPositionMs)
    }

    @Test
    fun restorePlaybackQueueClearsMismatchedPausedCheckpoint() {
        val store = FakeQueueStore()
        val current = track(8L, durationMs = 10_000L)
        store.restore = PlaybackQueueState(listOf(restorableTrack(current)), 0)
        store.storedPlaybackPositionTrackId = 99L
        store.storedPlaybackPositionMs = 6_400L
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        manager.restorePlaybackQueue()

        assertEquals(0L, provider.positionManager.restoredPositionFor(current))
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertEquals(-1L, store.storedPlaybackPositionTrackId)
        assertEquals(0L, store.storedPlaybackPositionMs)
    }

    @Test
    fun restorePlaybackQueueClampsOutOfRangePersistedIndexAfterFilteringInvalidRows() {
        val store = FakeQueueStore()
        store.restore = PlaybackQueueState(
                listOf(
                        track(2L, null, "streaming:netease:2"),
                        track(3L, android.net.Uri.parse("content://music/3"), "/music/3")
                ),
                99
        )
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)

        val snapshot = manager.restorePlaybackQueue()

        assertEquals(listOf(2L, 3L), provider.queue.map { it.id })
        assertEquals(1, snapshot.currentIndex)
        assertEquals(3L, snapshot.currentTrack?.id)
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
        assertTrue(store.resumeRequested)
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
    fun skipToPreviousMovesCursorAndClearsThePauseCheckpoint() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L, durationMs = 10_000L), track(2L)), 1)

        manager.skipToPrevious()

        assertEquals(0, manager.queueStateSnapshot().currentIndex)
        assertTrue(provider.prepareCurrentCalled)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
    }

    @Test
    fun skipToNextAtQueueEndWithRepeatOffDoesNotRestartCurrentTrack() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1300L
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(track(1L, durationMs = 10_000L), track(2L, durationMs = 10_000L)),
                1
        )
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)

        val reused = manager.skipToNextImmediately()

        assertFalse(reused)
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(2L, manager.queueStateSnapshot().currentTrack?.id)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertEquals(emptyList<Pair<Long, Long>>(), store.savedPositions)
        assertFalse(provider.prepareCurrentCalled)
        assertTrue(provider.queuePlaybackActions.published)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun skipToNextMovesCursorAndClearsThePauseCheckpoint() {
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
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertTrue(store.resumeRequested)
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
    }

    @Test
    fun skipToPreviousWrapsToQueueEndAndClearsThePauseCheckpoint() {
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
        assertEquals(listOf(-1L to 0L), store.savedPositions)
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
    fun retainTracksWithEmptyKeepSetClearsQueueAndStopsPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)

        manager.retainTracksById(emptySet())
        manager.prepareStopAndClearPlaybackState()

        assertTrue(provider.queue.isEmpty())
        assertEquals(-1, manager.queueStateSnapshot().currentIndex)
        assertTrue(provider.queuePlaybackActions.stoppedAndCleared)
        assertEquals(emptyList<Track>(), store.savedTracks)
        assertEquals(-1, store.savedIndex)
        assertFalse(store.resumeRequested)
    }

    @Test
    fun removeCurrentTrackKeepsQueueAtNextTrackAndPreparesPausedPlayback() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 2400L
        val manager = queueManager(store, provider)
        restoreQueue(
                manager,
                store,
                listOf(
                        track(1L, durationMs = 10_000L),
                        track(2L, durationMs = 10_000L),
                        track(3L, durationMs = 10_000L)
                ),
                1
        )

        manager.removeTracksById(setOf(2L))

        assertEquals(listOf(1L, 3L), manager.queueSnapshot().map { it.id })
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(3L, manager.queueStateSnapshot().currentTrack?.id)
        assertEquals(listOf(1L, 3L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertTrue(provider.prepareCurrentCalled)
        assertFalse(provider.queuePlaybackActions.lastPreparePlayWhenReady)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
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
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertEquals(listOf("/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
        assertFalse(provider.prepareCurrentCalled)
    }

    @Test
    fun shuffledNextStartsTheDifferentMirroredTrackAtZeroAndDropsThePauseCheckpoint() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 4_200L
        provider.mirroredQueuePlayer.matchesCurrentQueueValue = true
        provider.mirroredQueuePlayer.seekToValue = true
        val manager = queueManager(
            store,
            provider,
            object : java.util.Random() {
                override fun nextInt(bound: Int): Int = 2
            }
        )
        val first = track(1L, durationMs = 10_000L)
        val second = track(2L, durationMs = 10_000L)
        val third = track(3L, durationMs = 10_000L)
        restoreQueue(manager, store, listOf(first, second, third), 0)
        provider.runtimeStateManager.setShuffleEnabled(true)
        manager.persistPausedPlaybackPosition()

        val reused = manager.skipToNextImmediately()

        assertTrue(reused)
        assertEquals(2, manager.queueStateSnapshot().currentIndex)
        assertEquals(third.id, manager.queueStateSnapshot().currentTrack?.id)
        assertEquals(2, provider.mirroredQueuePlayer.seekIndex)
        assertEquals(0L, provider.mirroredQueuePlayer.seekPositionMs)
        assertEquals(listOf(first.id to 4_200L, -1L to 0L), store.savedPositions)
        assertEquals(0L, provider.positionManager.restoredPositionFor(third))
    }

    @Test
    fun replaceCurrentTrackAndResumeIgnoresAStaleDifferentTrackRecovery() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1200L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L)), 0)
        val replacement = track(2L, durationMs = 10_000L)

        val recovery = manager.replaceCurrentTrackAndResume(replacement, 800L)

        assertNull(recovery)
        assertEquals(1L, provider.queue.first().id)
        assertEquals(0L, provider.positionManager.restoredPositionFor(replacement))
    }

    @Test
    fun replaceCurrentTrackAndResumeKeepsPositionForTheSameTrackWithARefreshedUri() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1200L
        val manager = queueManager(store, provider)
        val current = track(
                1L,
                android.net.Uri.parse("https://old.example/1.mp3"),
                "streaming:qq:1",
                10_000L
        )
        val replacement = track(
                1L,
                android.net.Uri.parse("https://new.example/1.mp3"),
                "streaming:qq:1",
                10_000L
        )
        restoreQueue(manager, store, listOf(current), 0)

        val recovery = requireNotNull(manager.replaceCurrentTrackAndResume(replacement, 800L))

        assertEquals(replacement, provider.queue.first())
        assertEquals(1200L, provider.positionManager.restoredPositionFor(replacement))
        assertEquals(replacement, recovery.track)
        assertEquals(1200L, recovery.restoredPositionMs)
        assertTrue(recovery.playWhenReady)
    }

    @Test
    fun replaceCurrentSourceAndResumeKeepsPositionForAConfirmedAlternateSource() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        provider.playbackPositionMsValue = 1_200L
        val manager = queueManager(store, provider)
        val current = track(
            1L,
            android.net.Uri.parse("https://old.example/1.mp3"),
            "streaming:qq:1",
            10_000L
        )
        val replacement = track(
            2L,
            android.net.Uri.parse("https://alternate.example/2.mp3"),
            "streaming:netease:2",
            10_000L
        )
        restoreQueue(manager, store, listOf(current), 0)

        val recovery = requireNotNull(
            manager.replaceCurrentSourceAndResume(current.id, replacement, 800L)
        )

        assertEquals(replacement, provider.queue.first())
        assertEquals(1_200L, provider.positionManager.restoredPositionFor(replacement))
        assertEquals(replacement, recovery.track)
        assertEquals(1_200L, recovery.restoredPositionMs)
        assertTrue(recovery.playWhenReady)
    }

    @Test
    fun replaceCurrentSourceAndResumeIgnoresAStaleCurrentTrackId() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val current = track(1L, durationMs = 10_000L)
        val replacement = track(2L, durationMs = 10_000L)
        restoreQueue(manager, store, listOf(current), 0)

        val recovery = manager.replaceCurrentSourceAndResume(99L, replacement, 800L)

        assertNull(recovery)
        assertEquals(current.id, provider.queue.first().id)
        assertEquals(0L, provider.positionManager.restoredPositionFor(replacement))
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
    fun queuePreparationForLargeQueueAvoidsMirroringAndStreamingRestore() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        val tracks = (1L..120L).map { id ->
            track(id, android.net.Uri.parse("content://music/$id"), "streaming:netease:$id")
        }
        restoreQueue(manager, store, tracks, 70)
        provider.streamingRestoreProvider.restoredDataPaths.clear()

        val preparation = manager.queuePreparationForNewPlayer()

        assertEquals(71L, preparation.currentTrack?.id)
        assertEquals(70, preparation.startIndex)
        assertEquals(null, preparation.mirroredQueueTracks)
        assertTrue(provider.streamingRestoreProvider.restoredDataPaths.isEmpty())
    }

    @Test
    fun playLargeQueueDefersFullQueuePersistenceUntilAfterPlaybackStart() {
        val events = mutableListOf<String>()
        val store = FakeQueueStore(events)
        val provider = FakeQueueState()
        provider.queuePlaybackActions.deferQueuePersistence = true
        provider.queuePlaybackActions.eventSink = events
        val manager = queueManager(store, provider)
        val tracks = (1L..120L).map { id ->
            track(id, android.net.Uri.parse("content://music/$id"), "streaming:netease:$id")
        }

        manager.playQueue(tracks, 70, 0L)

        assertTrue(provider.prepareCurrentCalled)
        assertTrue(store.savedTracks.isEmpty())
        assertEquals(120, provider.queuePlaybackActions.deferredQueueTracks.size)
        assertEquals(70, provider.queuePlaybackActions.deferredQueueIndex)
        assertEquals(listOf("resume", "prepare", "persist"), events)

        provider.queuePlaybackActions.flushDeferredQueue(store)

        assertEquals((1L..120L).toList(), store.savedTracks.map { it.id })
        assertEquals(70, store.savedIndex)
    }

    @Test
    fun playLargeQueueFallsBackToSyncPersistenceAfterPlaybackStartWhenAsyncIsRejected() {
        val events = mutableListOf<String>()
        val store = FakeQueueStore(events)
        val provider = FakeQueueState()
        provider.queuePlaybackActions.eventSink = events
        val manager = queueManager(store, provider)
        val tracks = (1L..120L).map { id ->
            track(id, android.net.Uri.parse("content://music/$id"), "/music/$id")
        }

        manager.playQueue(tracks, 70, 0L)

        assertEquals(listOf("resume", "prepare", "save"), events)
        assertEquals((1L..120L).toList(), store.savedTracks.map { it.id })
        assertEquals(70, store.savedIndex)
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
    fun queueContentRevisionChangesWhenAQueuedSourceIsReplaced() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(track(1L), track(2L)), 0)
        val before = manager.queueStateSnapshot().queueRevision

        manager.replaceQueuedTrack(track(2L, dataPath = "/resolved/2"))

        assertEquals(before + 1L, manager.queueStateSnapshot().queueRevision)
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
        assertFalse(provider.queuePlaybackActions.stoppedAndCleared)
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
    fun prepareMirroredTransitionPlaybackStateClearsThePauseCheckpoint() {
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
        manager.prepareMirroredTransitionPlaybackState()

        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(listOf(-1L to 0L), store.savedPositions)
        assertEquals(listOf(1L, 2L), store.savedTracks.map { it.id })
        assertEquals(1, store.savedIndex)
        assertEquals("", provider.runtimeStateManager.errorMessage())
        assertEquals(null, provider.transitionStateManager.lastMarkedTrack())
        assertEquals(listOf("/music/2"), provider.streamingRestoreProvider.restoredDataPaths)
        assertEquals(0L, provider.positionManager.restoredPositionFor(second))
    }

    @Test
    fun automaticMirroredTransitionClearsAUserPauseCheckpointBeforeTheNextTrackStarts() {
        val store = FakeQueueStore()
        val provider = FakeQueueState()
        val first = track(1L, durationMs = 10_000L)
        val second = track(2L, durationMs = 10_000L)
        provider.playbackPositionMsValue = 4_200L
        val manager = queueManager(store, provider)
        restoreQueue(manager, store, listOf(first, second), 0)
        manager.persistPausedPlaybackPosition()
        provider.runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)

        val transition = manager.applyMirroredTransitionIndex(1, automaticAdvance = true)
        manager.prepareMirroredTransitionPlaybackState()

        assertEquals(0, transition?.completedIndex)
        assertFalse(transition?.stopAfterAutomaticAdvance ?: true)
        assertEquals(1, manager.queueStateSnapshot().currentIndex)
        assertEquals(
                listOf(first.id to 4_200L, -1L to 0L),
                store.savedPositions
        )
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

    private fun queueManager(
        store: FakeQueueStore,
        provider: FakeQueueState,
        random: java.util.Random = java.util.Random()
    ): PlaybackQueueManager {
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
                provider.transitionStateManager,
                random
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

    private class FakeQueueStore(
        private val eventSink: MutableList<String>? = null
    ) : PlaybackQueueStore {
        var savedTracks: List<Track> = emptyList()
        var savedIndex: Int = -1
        val savedPositions = mutableListOf<Pair<Long, Long>>()
        var restore = PlaybackQueueState(emptyList(), -1)
        var resumeRequested = false
        var storedPlaybackPositionTrackId = -1L
        var storedPlaybackPositionMs = 0L
        var playbackRestoreEnabled = true
        val savedPlaybackRestoreEnabledValues = mutableListOf<Boolean>()

        override fun load(): PlaybackQueueState = restore
        override fun save(tracks: List<Track>, currentIndex: Int) {
            savedTracks = tracks
            savedIndex = currentIndex
            eventSink?.add("save")
        }
        override fun loadResumeRequested(): Boolean = resumeRequested
        override fun saveResumeRequested(requested: Boolean) {
            resumeRequested = requested
            eventSink?.add("resume")
        }
        override fun loadPlaybackRestoreEnabled(): Boolean = playbackRestoreEnabled
        override fun savePlaybackRestoreEnabled(enabled: Boolean) {
            playbackRestoreEnabled = enabled
            savedPlaybackRestoreEnabledValues.add(enabled)
        }
        override fun loadPlaybackPositionTrackId(): Long = storedPlaybackPositionTrackId
        override fun loadPlaybackPositionMs(): Long = storedPlaybackPositionMs
        override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
            savedPositions.add(trackId to positionMs)
            storedPlaybackPositionTrackId = trackId
            storedPlaybackPositionMs = positionMs
        }
    }

    private class FakeQueueState(
        val queue: MutableList<Track> = mutableListOf()
    ) {
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
        var lastPreparePlayWhenReady = false
        var published = false
        var stoppedAndCleared = false
        var deferQueuePersistence = false
        var deferredQueueTracks: List<Track> = emptyList()
        var deferredQueueIndex = -1
        var eventSink: MutableList<String>? = null
        override fun prepareCurrent(playWhenReady: Boolean) {
            prepareCurrentCalled = true
            lastPreparePlayWhenReady = playWhenReady
            eventSink?.add("prepare")
        }
        override fun publishState() {
            published = true
        }
        override fun stopAndClear() {
            stoppedAndCleared = true
        }
        override fun persistQueueAsync(tracks: List<Track>, currentIndex: Int): Boolean {
            if (!deferQueuePersistence) {
                return false
            }
            eventSink?.add("persist")
            deferredQueueTracks = tracks.toList()
            deferredQueueIndex = currentIndex
            return true
        }
        fun flushDeferredQueue(store: FakeQueueStore) {
            store.save(deferredQueueTracks, deferredQueueIndex)
        }
    }

    private class CountingQueue : AbstractMutableList<Track>() {
        private val backing = mutableListOf<Track>()
        var singleAddCalls = 0
        var addAllCalls = 0

        override val size: Int
            get() = backing.size

        override fun get(index: Int): Track = backing[index]

        override fun add(index: Int, element: Track) {
            singleAddCalls += 1
            backing.add(index, element)
        }

        override fun addAll(elements: Collection<Track>): Boolean {
            addAllCalls += 1
            return backing.addAll(elements)
        }

        override fun removeAt(index: Int): Track = backing.removeAt(index)

        override fun set(index: Int, element: Track): Track = backing.set(index, element)
    }

    private class FakeStreamingRestoreProvider : PlaybackQueueManager.StreamingRestoreProvider {
        val restoredDataPaths = mutableListOf<String?>()
        val restoredTrackIds = mutableListOf<Long>()
        val replacements = mutableMapOf<Long, Track>()
        override fun restoredTrackFor(track: Track): Track? {
            restoredTrackIds += track.id
            return replacements[track.id] ?: track
        }
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
