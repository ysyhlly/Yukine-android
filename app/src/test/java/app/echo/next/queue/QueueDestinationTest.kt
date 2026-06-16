package app.echo.next.queue

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.EchoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed Compose UI 测试：MVVM 参考切片的渲染端 [QueueDestination]。
 *
 * 验证「ViewModel 状态 → Compose 渲染 → 用户动作回传 ViewModel」的闭环：
 * destination 经 collectAsState 读取 [QueueViewModel] 的状态/标签，
 * 并把点击转成 [QueueIntent]。这是其余 tab 切换时复刻的形态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun track(id: Long): Track =
        Track(id, "曲目$id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")

    private fun snapshot(current: Track?): PlaybackStateSnapshot =
        PlaybackStateSnapshot(current, 0, 0, 0L, 0L, false, false, "", false, 0, 1.0f, 1.0f, 0L)

    @Test
    fun rendersBoundTracksFromViewModel() {
        val vm = QueueViewModel()
        vm.bind(listOf(track(1L), track(2L)), snapshot(track(1L)), setOf(1L), "zh")

        composeRule.setContent {
            EchoTheme.EchoTheme { QueueDestination(vm) }
        }

        composeRule.onNodeWithText("曲目1").assertIsDisplayed()
        composeRule.onNodeWithText("曲目2").assertIsDisplayed()
    }

    @Test
    fun emptyQueue_showsEmptyState() {
        val vm = QueueViewModel()
        vm.bind(emptyList(), snapshot(null), emptySet(), "zh")

        composeRule.setContent {
            EchoTheme.EchoTheme { QueueDestination(vm) }
        }

        // 中文空状态标题
        composeRule.onNodeWithText("队列暂无歌曲").assertIsDisplayed()
    }

    @Test
    fun clearQueueClick_forwardsIntentToViewModel() {
        val vm = QueueViewModel()
        vm.bind(listOf(track(1L)), snapshot(track(1L)), emptySet(), "zh")

        val received = mutableListOf<QueueIntent>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { vm.intents.toList(received) }

        composeRule.setContent {
            EchoTheme.EchoTheme { QueueDestination(vm) }
        }

        composeRule.onNodeWithContentDescription("清空队列").performClick()

        assertEquals(1, received.size)
        assertTrue(received.first() is QueueIntent.ClearQueue)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
