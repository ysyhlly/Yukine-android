package app.yukine.queue

import android.net.Uri
import app.yukine.LibraryStoreState
import app.yukine.MainDispatcherRule
import app.yukine.SettingsPreferencesSnapshot
import app.yukine.SettingsState
import app.yukine.model.Track
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 纯 JVM 单元测试：MVVM 参考切片 [QueueViewModel] 的核心契约。
 *
 * 这是单 Activity 迁移的「样板」ViewModel——其余 tab 都按它的形态切换：
 * 输入经 [QueueViewModel.bindStateSources] 响应式收集，派生不可变
 * [QueueViewModel.uiState]/[QueueViewModel.labels]，
 * 用户动作经 [QueueViewModel.intents] 以 [QueueIntent] 形式发出，与播放服务零耦合。
 *
 * 这些契约必须随迁移一直保持绿色。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private fun snapshot(current: Track?, currentIndex: Int = 0): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            current,
            currentIndex,
            0,      // queueSize
            0L,     // positionMs
            0L,     // durationMs
            false,  // playing
            false,  // preparing
            "",     // errorMessage
            false,  // shuffleEnabled
            0,      // repeatMode
            1.0f,   // playbackSpeed
            1.0f,   // appVolume
            0L      // sleepTimerRemainingMs
        )

    @Test
    fun bind_derivesRowsAndLocalizedLabels() {
        val vm = QueueViewModel()
        val tracks = listOf(track(1L), track(2L), track(3L))

        bind(vm, tracks, snapshot(tracks[1], currentIndex = 1), setOf(2L), "zh")

        val state = vm.uiState.value
        assertEquals(3, state.rows.size)
        assertEquals("Track 1", state.rows[0].title)
        // 当前曲目应被标记
        assertTrue(state.rows[1].current)
        assertTrue(!state.rows[0].current)
        // 收藏态反映 favoriteIds
        assertTrue(state.rows[1].favorite)
        assertTrue(!state.rows[0].favorite)
        // 标签按中文本地化（非默认英文）
        assertEquals("队列", vm.labels.value.title)
        assertEquals("拖动重新排序", vm.labels.value.dragReorder)
        assertEquals(tracks, vm.tracks())
    }

    @Test
    fun bind_largeQueueKeepsRowsLazyAndStillResolvesIndexedRows() {
        val vm = QueueViewModel()
        val tracks = (0 until 160).map { track(it.toLong()) }

        bind(vm, tracks, snapshot(tracks[120], currentIndex = 120), setOf(120L), "en")

        val state = vm.uiState.value
        assertEquals(160, state.rowCount)
        assertEquals(24, state.rows.size)
        val lateRow = state.rowAt(120)
        assertEquals("Track 120", lateRow?.title)
        assertTrue(lateRow?.current == true)
        assertTrue(lateRow?.favorite == true)
        assertSame(lateRow, state.rowAt(120))
        assertEquals(tracks, vm.tracks())
    }

    @Test
    fun bind_largeQueueCachesLateDuplicateRowKeys() {
        val vm = QueueViewModel()
        val tracks = (0 until 160).map { index ->
            if (index == 0 || index == 120) track(7L) else track(1_000L + index)
        }

        bind(vm, tracks, snapshot(null), emptySet(), "en")

        val lateDuplicate = vm.uiState.value.rowAt(120)
        assertEquals("7:2", lateDuplicate?.key)
        assertEquals("Track 7", lateDuplicate?.title)
    }

    @Test
    fun bind_duplicateTrackIdsHighlightsOnlyThePlaybackIndex() {
        val vm = QueueViewModel()
        val tracks = listOf(track(7L), track(8L), track(7L))

        bind(vm, tracks, snapshot(tracks[2], currentIndex = 2), emptySet(), "en")

        val rows = vm.uiState.value.rows
        assertTrue(!rows[0].current)
        assertTrue(!rows[1].current)
        assertTrue(rows[2].current)
    }

    @Test
    fun bind_withNullQueue_yieldsEmptyRows() {
        val vm = QueueViewModel()

        bind(vm, null, snapshot(null), emptySet(), "en")

        assertEquals(0, vm.uiState.value.rows.size)
        assertTrue(vm.tracks().isEmpty())
        assertEquals("Queue", vm.labels.value.title)
        assertEquals("Drag to reorder", vm.labels.value.dragReorder)
    }

    @Test
    fun disconnectedPlaybackPublishesInlineAvailabilityState() {
        val vm = QueueViewModel()

        bind(
            vm,
            emptyList(),
            snapshot(null),
            emptySet(),
            "en",
            PlaybackConnectionState.Disconnected
        )

        assertEquals("Playback is not ready", vm.labels.value.empty)
        assertTrue(vm.labels.value.emptyDescription.isNotBlank())
    }

    @Test
    fun onPlayAt_emitsPlayIntentWithBoundTracksAndIndex() = runTest {
        val vm = QueueViewModel()
        val tracks = listOf(track(1L), track(2L))
        bind(vm, tracks, snapshot(tracks[0]), emptySet(), "en")

        val received = mutableListOf<QueueIntent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.intents.toList(received)
        }

        vm.onPlayAt(1)

        assertEquals(1, received.size)
        val intent = received.first()
        assertTrue(intent is QueueIntent.PlayAt)
        intent as QueueIntent.PlayAt
        assertEquals(tracks, intent.tracks)
        assertEquals(1, intent.index)
        job.cancel()
    }

    @Test
    fun onToggleFavorite_emitsBoundTrack() = runTest {
        val vm = QueueViewModel()
        val tracks = listOf(track(10L), track(11L))
        bind(vm, tracks, snapshot(null), emptySet(), "en")

        val received = mutableListOf<QueueIntent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.intents.toList(received)
        }

        vm.onToggleFavorite(0)

        assertEquals(1, received.size)
        val intent = received.first() as QueueIntent.ToggleFavorite
        assertSame(tracks[0], intent.track)
        job.cancel()
    }

    @Test
    fun positionalIntents_ignoreOutOfBoundsIndex() = runTest {
        val vm = QueueViewModel()
        bind(vm, listOf(track(1L)), snapshot(null), emptySet(), "en")

        val received = mutableListOf<QueueIntent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.intents.toList(received)
        }

        vm.onPlayAt(5)
        vm.onToggleFavorite(-1)
        vm.onAddToPlaylist(99)
        vm.onRemove(7)

        assertTrue("越界索引不应发射任何 intent", received.isEmpty())
        job.cancel()
    }

    @Test
    fun clearQueueAndBack_emitObjectIntents() = runTest {
        val vm = QueueViewModel()
        bind(vm, listOf(track(1L)), snapshot(null), emptySet(), "en")

        val received = mutableListOf<QueueIntent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.intents.toList(received)
        }

        vm.onClearQueue()
        vm.onBack()

        assertEquals(2, received.size)
        assertTrue(received[0] is QueueIntent.ClearQueue)
        assertTrue(received[1] is QueueIntent.Back)
        job.cancel()
    }

    private fun bind(
        viewModel: QueueViewModel,
        tracks: List<Track>?,
        playbackState: PlaybackStateSnapshot,
        favoriteIds: Set<Long>,
        languageMode: String,
        connection: PlaybackConnectionState = PlaybackConnectionState.Connected
    ) {
        val readModel = FakePlaybackReadModel().apply {
            state.value = playbackState
            queue.value = PlaybackQueueSnapshot(
                tracks = tracks.orEmpty(),
                revision = 1L
            )
            this.connection.value = connection
        }
        viewModel.bindStateSources(
            readModel,
            MutableStateFlow(favoriteIds),
            MutableStateFlow(languageMode)
        )
    }

    private class FakePlaybackReadModel : PlaybackReadModel {
        override val state = MutableStateFlow(PlaybackStateSnapshot.empty())
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    }
}
