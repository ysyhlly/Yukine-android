package app.yukine.together

import android.content.Context
import android.content.ContextWrapper
import app.yukine.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class TogetherSessionOwnerTest {
    @Test
    fun leaveDuringCreateDoesNotLeaveOrphanHostingOrReportBareSuccess() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner-create").toFile()
        val player = FakePlayer()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        lateinit var owner: TogetherSessionOwner
        val bridge = LeaveDuringCreateBridge { runBlocking { owner.leave("concurrent") } }
        owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController {},
            bridge,
            dispatcher,
            dispatcher
        )
        try {
            val result = owner.create(listOf(rangeStreamingItem("race")), OPTIONS)
            assertTrue(
                "expected failure, got success state=${owner.state.value}",
                result.isFailure
            )
            assertFalse(player.constraintsEnabled)
            assertFalse(owner.canEditQueue())
            assertTrue(
                owner.state.value is TogetherSessionState.Idle ||
                    owner.state.value is TogetherSessionState.Leaving
            )
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun successfulCreateEnablesConstraintsOnlyAfterNativeSessionExists() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner-create-ok").toFile()
        val player = FakePlayer()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController {},
            FakeBridge(),
            dispatcher,
            dispatcher
        )
        try {
            val result = owner.create(listOf(rangeStreamingItem("ok")), OPTIONS)
            assertTrue(
                "create failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            assertTrue(player.constraintsEnabled)
            assertTrue(owner.canEditQueue())
            assertTrue(owner.state.value is TogetherSessionState.WaitingReady)
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

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

    @Test
    fun joinerResolvesPrivateCloudSourceWithoutChangingLogicalIdentity() = runTest {
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
            dispatcher,
            streamingResolver = TogetherStreamingResolverPort { source ->
                source.copy(
                    resolvedUrl = "https://private.example/audio",
                    headers = mapOf("Authorization" to "Bearer local-only"),
                    supportsRange = true
                )
            }
        )
        try {
            assertTrue(owner.join(ROOM_CODE, emptyList(), OPTIONS).isSuccess)
            val callback = bridge.callbacks.single()
            val raw = callback.resolveSource(
                """{"provider":"qqmusic","track_id":"song-mid","quality":"lossless","duration_ms":123000,"size":4096}"""
            )
            val resolved = JSONObject(raw)
            assertEquals("qqmusic", resolved.getString("provider"))
            assertEquals("song-mid", resolved.getString("track_id"))
            assertEquals("https://private.example/audio", resolved.getString("url"))
            assertEquals(4096L, resolved.getLong("size"))
            assertEquals(
                "Bearer local-only",
                resolved.getJSONObject("headers").getString("Authorization")
            )

            owner.leave("test")
            assertEquals("", callback.resolveSource(
                """{"provider":"qqmusic","track_id":"song-mid","quality":"lossless","size":4096}"""
            ))
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun liveQueueSnapshotKeepsActiveStateAndRebindsPlayerMetadata() = runTest {
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
            val callback = bridge.callbacks.single()
            callback.onEvent(
                """{"type":"queue","items":[{"id":"one","title":"One","artist":"A","size":10,"stream_url":"http://127.0.0.1:7777/file/one"}]}"""
            )
            callback.onEvent(
                """{"type":"snapshot","current_index":0,"paused":false,"buffering":false}"""
            )
            callback.onEvent(
                """{"type":"queue","items":[{"id":"two","title":"Two","artist":"B","size":20,"stream_url":"http://127.0.0.1:7777/file/two"}]}"""
            )
            callback.onEvent(
                """{"type":"stream_urls","urls":["http://127.0.0.1:7777/file/stale"]}"""
            )
            advanceUntilIdle()

            val active = owner.state.value as TogetherSessionState.Active
            assertEquals(listOf("two"), active.queue.map(TogetherQueueItem::stableId))
            assertEquals(listOf("Two"), player.replacementQueue.map(TogetherQueueItem::title))
            assertEquals(listOf("http://127.0.0.1:7777/file/two"), player.replacementUrls)
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun legacyStreamUrlsUseTheQueueSnapshotThatRequestedThem() = runTest {
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
            val callback = bridge.callbacks.single()
            callback.onEvent(
                """{"type":"queue","items":[{"id":"legacy","title":"Legacy","artist":"A","size":10}]}"""
            )
            callback.onEvent(
                """{"type":"stream_urls","urls":["http://127.0.0.1:7777/file/legacy"]}"""
            )
            advanceUntilIdle()

            assertEquals(listOf("legacy"), player.replacementQueue.map(TogetherQueueItem::stableId))
            assertEquals(
                listOf("http://127.0.0.1:7777/file/legacy"),
                player.replacementUrls
            )
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun queueSignatureTracksLogicalSourcesInsteadOfTransportIds() {
        val first = TogetherQueueItem(
            stableId = "streaming:luoxue:kw:123",
            title = "Ahead of Us",
            artist = "Artist",
            sourceUri = "https://cloud.example/first",
            source = TogetherQueueSource.Streaming(
                provider = "luoxue",
                providerTrackId = "kw:123",
                quality = "lossless"
            )
        )
        val rebuilt = first.copy(
            stableId = "-11900123",
            sourceUri = "http://127.0.0.1:7777/stream/0"
        )

        assertEquals(
            togetherQueueSignature(listOf(first)),
            togetherQueueSignature(listOf(rebuilt))
        )
    }

    @Test
    fun nativeQueueRestoresHostPrivateStreamingMetadata() {
        val privateSource = TogetherQueueSource.Streaming(
            provider = "luoxue",
            providerTrackId = "kw:123",
            quality = "lossless",
            resolvedUrl = "https://cloud.example/audio",
            headers = mapOf("Authorization" to "private"),
            mimeType = "audio/flac",
            luoxueMusicInfoJson = """{"id":"kw_123","name":"Ahead of Us"}"""
        )
        val privateItem = TogetherQueueItem(
            stableId = "original",
            title = "Ahead of Us",
            artist = "Artist",
            sourceUri = privateSource.resolvedUrl,
            source = privateSource
        )
        val parsedItem = privateItem.copy(
            stableId = "native",
            sourceUri = "http://127.0.0.1:7777/stream/0",
            source = TogetherQueueSource.Streaming(
                provider = "luoxue",
                providerTrackId = "kw:123",
                quality = "lossless",
                resolvedUrl = "http://127.0.0.1:7777/stream/0"
            )
        )

        val restored = restoreHostPrivateQueueSources(
            listOf(parsedItem),
            listOf(privateItem),
            mapOf(privateItem.dedupeKey to privateItem)
        ).single()

        assertEquals("native", restored.stableId)
        assertEquals(privateSource, restored.source)
    }

    @Test
    fun nativeQueueRestoresHostLocalSourceIdentity() {
        val original = TogetherQueueItem(
            stableId = "42",
            title = "Local",
            artist = "Artist",
            sourceUri = "content://media/external/audio/42",
            album = "Original Album",
            artworkUri = "content://media/external/audio/albumart/42",
            durationMs = 42_000L,
            source = TogetherQueueSource.Local("content://media/external/audio/42")
        )
        val parsed = original.copy(
            sourceUri = "http://127.0.0.1:7777/file/0",
            artist = "",
            album = "",
            artworkUri = "",
            durationMs = 0L,
            source = TogetherQueueSource.Local("http://127.0.0.1:7777/file/0")
        )

        val restored = restoreHostPrivateQueueSources(
            listOf(parsed),
            listOf(original),
            emptyMap()
        ).single()

        assertEquals(original.source, restored.source)
        assertEquals("Artist", restored.artist)
        assertEquals("Original Album", restored.album)
        assertEquals(original.artworkUri, restored.artworkUri)
        assertEquals(42_000L, restored.durationMs)
    }

    @Test
    fun queueItemSkippedRemovesOriginalIndexFromHostRoomAndPlayer() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner-skip").toFile()
        val bridge = FakeBridge()
        val player = FakePlayer()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController {},
            bridge,
            dispatcher,
            dispatcher
        )
        try {
            val queue = listOf(
                rangeStreamingItem("a"),
                rangeStreamingItem("b"),
                rangeStreamingItem("c")
            )
            assertTrue(owner.create(queue, OPTIONS).isSuccess)
            val callback = bridge.callbacks.single()
            callback.onEvent(
                """{"type":"queue_item_skipped","index":1,"id":"streaming:test:b","reason":"remote item is not relayable"}"""
            )
            advanceUntilIdle()

            val waiting = owner.state.value as TogetherSessionState.WaitingReady
            assertEquals(
                listOf("streaming:test:a", "streaming:test:c"),
                waiting.queue.map(TogetherQueueItem::stableId)
            )
            assertEquals(
                listOf("streaming:test:a", "streaming:test:c"),
                player.itemReplacementQueue.map(TogetherQueueItem::stableId)
            )
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSourcePrefersRefreshedSizeOverStalePrivateEntry() = runTest {
        val cacheDir = Files.createTempDirectory("together-owner-resolve").toFile()
        val bridge = FakeBridge()
        val player = FakePlayer()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val resolver = TogetherStreamingResolverPort { source ->
            source.copy(
                resolvedUrl = "https://refreshed.example/audio",
                supportsRange = true,
                sizeBytes = 9_999L,
                headers = mapOf("Authorization" to "new")
            )
        }
        val owner = TogetherSessionOwner(
            TestContext(cacheDir),
            player,
            TogetherForegroundController {},
            bridge,
            dispatcher,
            dispatcher,
            streamingResolver = resolver
        )
        try {
            val item = rangeStreamingItem("size").copy(sizeBytes = 0L).let {
                it.copy(source = (it.source as TogetherQueueSource.Streaming).copy(sizeBytes = 0L))
            }
            assertTrue(owner.create(listOf(item), OPTIONS).isSuccess)
            val callback = bridge.callbacks.single()
            val raw = callback.resolveSource(
                """{"provider":"test","track_id":"size","quality":"lossless"}"""
            )
            assertTrue(raw.isNotBlank())
            val json = JSONObject(raw)
            assertEquals(9_999L, json.getLong("size"))
            assertEquals("https://refreshed.example/audio", json.getString("url"))
            assertTrue(json.getBoolean("supports_range"))
        } finally {
            owner.close()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun playableSubsetRetainsOriginalTrackMetadataObjects() {
        val original = Track(
            7L,
            "Title",
            "Artist",
            "Original Album",
            10_000L,
            null,
            "content://media/external/audio/7",
            70L,
            null,
            "flac",
            1_411,
            96_000,
            24,
            2
        )
        val queue = listOf(
            TogetherQueueItem(
                stableId = "7",
                title = original.title,
                artist = original.artist,
                sourceUri = original.dataPath
            )
        )

        val retained = retainTracksForTogetherQueue(listOf(original), queue)

        assertTrue(retained.single() === original)
        assertEquals("Original Album", retained.single().album)
        assertEquals("flac", retained.single().codec)
        assertEquals(96_000, retained.single().sampleRateHz)
    }

    private class TestContext(private val cache: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = cache
    }

    private class LeaveDuringCreateBridge(
        private val onCreate: () -> Unit
    ) : TogetherNativeBridge {
        override fun testConnection(configJson: String): String = "ok"
        override fun preview(configJson: String, roomCode: String): String =
            """{"v":1,"items":[]}"""

        override fun create(
            configJson: String,
            queueJson: String,
            callback: TogetherNativeBridge.Callback
        ): TogetherNativeBridge.NativeSession {
            onCreate()
            return FakeSession()
        }

        override fun join(
            configJson: String,
            roomCode: String,
            localMatchesJson: String,
            callback: TogetherNativeBridge.Callback
        ): TogetherNativeBridge.NativeSession = FakeSession()
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
        val queueUpdates = mutableListOf<String>()

        override fun roomCode(): String = ROOM_CODE
        override fun notifyPlayback(eventJson: String) = Unit
        override fun updateQueue(queueJson: String) {
            queueUpdates += queueJson
        }
        override fun receivedFilePath(fileId: String): String = ""
        override fun receivedFileRoot(fileId: String): String = ""
        override fun leave() {
            leaveCalls += 1
        }
    }

    private class FakePlayer : TogetherPlayerPort {
        var constraintsEnabled = false
        val seekPositions = mutableListOf<Long>()
        var replacementQueue: List<TogetherQueueItem> = emptyList()
        var itemReplacementQueue: List<TogetherQueueItem> = emptyList()
        var replacementUrls: List<String> = emptyList()

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
        override fun replaceQueueWithItems(queue: List<TogetherQueueItem>) {
            itemReplacementQueue = queue
        }
        override fun replaceQueueWithStreamUrls(
            queue: List<TogetherQueueItem>,
            urls: List<String>
        ) {
            replacementQueue = queue
            replacementUrls = urls
        }
    }

    private fun rangeStreamingItem(id: String) = TogetherQueueItem(
        stableId = "streaming:test:$id",
        title = "Cloud $id",
        artist = "Artist",
        sourceUri = "https://example.invalid/$id",
        sizeBytes = 4_096L,
        shareable = true,
        source = TogetherQueueSource.Streaming(
            provider = "test",
            providerTrackId = id,
            quality = "lossless",
            resolvedUrl = "https://example.invalid/$id",
            supportsRange = true,
            sizeBytes = 4_096L
        )
    )

    private companion object {
        const val ROOM_CODE = "jun1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
        val OPTIONS = TogetherConnectOptions(nickname = "tester")
    }
}
