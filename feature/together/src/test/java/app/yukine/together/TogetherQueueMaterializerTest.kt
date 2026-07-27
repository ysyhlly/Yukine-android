package app.yukine.together

import android.content.Context
import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

class TogetherQueueMaterializerTest {
    @Test
    fun nonRangeStreamingSourceIsDownloadedAndMaterializedAsLocal() {
        val body = "complete audio bytes".toByteArray()
        val observedToken = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                observedToken.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val cache = Files.createTempDirectory("together-materializer").toFile()

        try {
            val result = materializer(cache) { source ->
                source.copy(
                    resolvedUrl = "http://127.0.0.1:${server.address.port}/audio",
                    headers = mapOf("Authorization" to "Bearer playback"),
                    supportsRange = false,
                    sizeBytes = 0L
                )
            }.prepare(listOf(streamingItem())).single()

            val local = result.source as TogetherQueueSource.Local
            val cachedFile = File(result.sourceUri)
            assertTrue(cachedFile.canonicalPath.startsWith(cache.canonicalPath))
            assertArrayEquals(body, cachedFile.readBytes())
            assertEquals(body.size.toLong(), result.sizeBytes)
            assertEquals(body.size.toLong(), local.sizeBytes)
            assertEquals("", result.contentRoot)
            assertEquals("", local.contentRoot)
            assertEquals("Bearer playback", observedToken.get())
            assertTrue(cache.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            server.stop(0)
            cache.deleteRecursively()
        }
    }

    @Test
    fun failedStreamingDownloadLeavesNoPartialCacheFile() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            start()
        }
        val cache = Files.createTempDirectory("together-materializer").toFile()

        try {
            assertPrepareFails(cache, server)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop(0)
            cache.deleteRecursively()
        }
    }

    @Test
    fun truncatedStreamingDownloadLeavesNoPartialCacheFile() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/audio") { exchange ->
                exchange.sendResponseHeaders(200, 100)
                exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
            }
            start()
        }
        val cache = Files.createTempDirectory("together-materializer").toFile()

        try {
            assertPrepareFails(cache, server)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop(0)
            cache.deleteRecursively()
        }
    }

    @Test
    fun unavailableStreamingItemDoesNotBlockPlayableItem() {
        val cache = Files.createTempDirectory("together-materializer").toFile()
        val playableItem = TogetherQueueItem(
            stableId = "streaming:test:2",
            title = "Playable",
            artist = "Artist",
            sourceUri = "https://playable.example/audio",
            sizeBytes = 12_345L,
            source = TogetherQueueSource.Streaming(
                provider = "test",
                providerTrackId = "2",
                quality = "lossless",
                resolvedUrl = "https://playable.example/audio",
                supportsRange = true,
                sizeBytes = 12_345L
            )
        )

        try {
            val prepared = materializer(cache) { null }
                .prepare(listOf(streamingItem(), playableItem))

            assertEquals(listOf("streaming:test:2"), prepared.map(TogetherQueueItem::stableId))
            assertEquals("https://playable.example/audio", prepared.single().sourceUri)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun allUnavailableItemsPreserveTheFirstActionableFailure() {
        val cache = Files.createTempDirectory("together-materializer").toFile()
        var failure: Throwable? = null

        try {
            try {
                materializer(cache) { null }.prepare(listOf(streamingItem()))
            } catch (error: Throwable) {
                failure = error
            }

            assertEquals(
                "Ahead of Us 的云端音源暂时不可用",
                failure?.message
            )
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun oversizedNonRangeFallbackIsSkippedSoPlayableSiblingStillPrepares() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/huge") { exchange ->
                // Declared length above mobile fallback cap; body is never sent.
                exchange.sendResponseHeaders(200, 200L * 1024L * 1024L)
                exchange.close()
            }
            start()
        }
        val cache = Files.createTempDirectory("together-materializer-cap").toFile()
        val playable = TogetherQueueItem(
            stableId = "streaming:test:ok",
            title = "Playable",
            artist = "Artist",
            sourceUri = "https://playable.example/audio",
            sizeBytes = 4_096L,
            source = TogetherQueueSource.Streaming(
                provider = "test",
                providerTrackId = "ok",
                quality = "lossless",
                resolvedUrl = "https://playable.example/audio",
                supportsRange = true,
                sizeBytes = 4_096L
            )
        )
        val huge = TogetherQueueItem(
            stableId = "streaming:test:huge",
            title = "Huge",
            artist = "Artist",
            sourceUri = "",
            source = TogetherQueueSource.Streaming(
                provider = "test",
                providerTrackId = "huge",
                quality = "lossless",
                resolvedUrl = "",
                supportsRange = false,
                sizeBytes = 0L
            )
        )

        try {
            val prepared = materializer(cache) { source ->
                if (source.providerTrackId == "huge") {
                    source.copy(
                        resolvedUrl = "http://127.0.0.1:${server.address.port}/huge",
                        supportsRange = false,
                        sizeBytes = 0L
                    )
                } else {
                    source
                }
            }.prepare(listOf(huge, playable))

            assertEquals(listOf("streaming:test:ok"), prepared.map(TogetherQueueItem::stableId))
            assertTrue(cache.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            server.stop(0)
            cache.deleteRecursively()
        }
    }

    private fun assertPrepareFails(cache: File, server: HttpServer) {
        var failure: Throwable? = null
        try {
            materializer(cache) { source ->
                source.copy(
                    resolvedUrl = "http://127.0.0.1:${server.address.port}/audio",
                    supportsRange = false,
                    sizeBytes = 0L
                )
            }.prepare(listOf(streamingItem()))
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue("Expected materialization to fail", failure != null)
    }

    private fun materializer(
        cache: File,
        resolver: (TogetherQueueSource.Streaming) -> TogetherQueueSource.Streaming?
    ): TogetherQueueMaterializer =
        TogetherQueueMaterializer(
            context = TestContext(cache),
            sessionCache = cache,
            streamingResolver = TogetherStreamingResolverPort(resolver)
        )

    private fun streamingItem(): TogetherQueueItem =
        TogetherQueueItem(
            stableId = "streaming:test:1",
            title = "Ahead of Us",
            artist = "Test Artist",
            sourceUri = "",
            source = TogetherQueueSource.Streaming(
                provider = "test",
                providerTrackId = "1",
                quality = "lossless",
                resolvedUrl = "",
                supportsRange = false,
                sizeBytes = 0L
            )
        )

    private class TestContext(private val cache: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = cache
    }
}
