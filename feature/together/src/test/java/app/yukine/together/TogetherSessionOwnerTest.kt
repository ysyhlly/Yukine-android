package app.yukine.together

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherSessionOwnerTest {
    @Test
    fun terminalEventReleasesRoomConstraintsAndAllowsAnotherJoin() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner").toFile()
        val bridge = FakeBridge()
        val player = FakePlayer()
        val foregroundStates = mutableListOf<Boolean>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController(foregroundStates::add),
            bridge,
            dispatcher,
            dispatcher
        )
        try {
            assertTrue(owner.join(ROOM_CODE, emptyList(), OPTIONS).isSuccess)
            assertTrue(player.constraintsEnabled)

            bridge.callbacks.single().onEvent(
                """{"type":"terminal","reason":"relay_lost","message":"relay lost","recoverable":true}"""
            )
            advanceUntilIdle()

            val failed = owner.state.value as TogetherSessionState.Failed
            assertEquals("relay lost", failed.message)
            assertTrue(failed.recoverable)
            assertFalse(player.constraintsEnabled)
            assertEquals(false, foregroundStates.last())
            assertEquals(1, bridge.sessions.single().leaveCalls)

            assertTrue(owner.join(ROOM_CODE, emptyList(), OPTIONS).isSuccess)
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun callbacksFromPreviousGenerationCannotControlNewRoom() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner").toFile()
        val bridge = FakeBridge()
        val player = FakePlayer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController {},
            bridge,
            dispatcher,
            dispatcher
        )
        try {
            assertTrue(owner.join(ROOM_CODE, emptyList(), OPTIONS).isSuccess)
            val oldCallback = bridge.callbacks.single()
            owner.leave("test")
            assertTrue(owner.join(ROOM_CODE, emptyList(), OPTIONS).isSuccess)

            oldCallback.onCommand("""{"type":"seek","position_ms":54321}""")
            oldCallback.onEvent(
                """{"type":"snapshot","current_index":3,"paused":false,"buffering":false}"""
            )
            advanceUntilIdle()

            assertTrue(player.seekPositions.isEmpty())
            assertFalse(owner.state.value is TogetherSessionState.Active)
            assertTrue(player.constraintsEnabled)
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    private class TestContext(private val cache: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = cache
    }

    private class FakeBridge : TogetherNativeBridge {
        val callbacks = mutableListOf<TogetherNativeBridge.Callback>()
        val sessions = mutableListOf<FakeSession>()

        override fun testConnection(configJson: String): String = "ok"

        override fun preview(configJson: String, roomCode: String): String =
            """{"v":1,"items":[]}"""

        override fun create(
            configJson: String,
            queueJson: String,
            callback: TogetherNativeBridge.Callback
        ): TogetherNativeBridge.NativeSession = newSession(callback)

        override fun join(
            configJson: String,
            roomCode: String,
            localMatchesJson: String,
            callback: TogetherNativeBridge.Callback
        ): TogetherNativeBridge.NativeSession = newSession(callback)

        private fun newSession(callback: TogetherNativeBridge.Callback): FakeSession {
            callbacks += callback
            return FakeSession().also(sessions::add)
        }
    }

    private class FakeSession : TogetherNativeBridge.NativeSession {
        var leaveCalls = 0

        override fun roomCode(): String = ROOM_CODE
        override fun notifyPlayback(eventJson: String) = Unit
        override fun receivedFilePath(fileId: String): String = ""
        override fun receivedFileRoot(fileId: String): String = ""
        override fun leave() {
            leaveCalls += 1
        }
    }

    private class FakePlayer : TogetherPlayerPort {
        var constraintsEnabled = false
        val seekPositions = mutableListOf<Long>()

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }
        override fun setSpeed(speed: Float) = Unit
        override fun skipToQueueIndex(index: Int) = Unit
        override fun currentPositionMs(): Long = 0L
        override fun currentQueueIndex(): Int = 0
        override fun setRoomPlaybackConstraints(enabled: Boolean) {
            constraintsEnabled = enabled
        }
        override fun replaceQueueWithStreamUrls(urls: List<String>) = Unit
    }

    private companion object {
        const val ROOM_CODE = "jun1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
        val OPTIONS = TogetherConnectOptions(nickname = "tester")
    }
}
