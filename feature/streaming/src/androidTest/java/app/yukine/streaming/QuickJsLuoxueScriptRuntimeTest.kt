package app.yukine.streaming

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android runtime because QuickJS is packaged as an Android native library. */
@RunWith(AndroidJUnit4::class)
class QuickJsLuoxueScriptRuntimeTest {
    @Test
    fun runsImportedSourceAndBridgesLxRequestResponse() = runBlocking {
        val http = RecordingHttpClient()
        val runtime = QuickJsLuoxueScriptRuntime(http)
        val script = """
            const { EVENT_NAMES, request, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ source, action, info }) => {
              if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
              return new Promise((resolve, reject) => {
                request('https://source.example.test/resolve', {
                  method: 'POST',
                  headers: { 'X-LX-Test': 'yes' },
                    body: JSON.stringify({
                      hash: info.musicInfo.hash,
                      nested: info.musicInfo.nested.label,
                      alias: info.musicInfo.aliases[1],
                      quality: info.type
                    })
                }, (err, response) => {
                  if (err) return reject(err)
                  resolve(response.body.url + '?source=' + source)
                })
              })
            })
            send(EVENT_NAMES.inited, { sources: { kg: { actions: ['musicUrl'] } } })
        """.trimIndent()

        val url = runtime.resolveMusicUrl(
            source = LuoxueImportedSource("test", "测试 LX 源", sourceKinds = listOf("kg"), script = script),
            sourceKey = "kg",
            musicInfo = mapOf(
                "hash" to "abc123",
                "nested" to mapOf("label" to "nested-value"),
                "aliases" to listOf("first", "second")
            ),
            quality = "flac"
        )

        assertEquals("https://media.example.test/song.flac?source=kg", url)
        assertEquals("POST", http.request?.method)
        assertEquals("yes", http.request?.headers?.get("X-LX-Test"))
        assertTrue(http.request?.body?.contains("abc123") == true)
        assertTrue(http.request?.body?.contains("nested-value") == true)
        assertTrue(http.request?.body?.contains("second") == true)
        assertTrue(http.request?.body?.contains("flac") == true)
    }

    @Test
    fun runsQqSearchExtensionAndReturnsNestedMusicInfo() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(RecordingHttpClient())
        val script = """
            const { EVENT_NAMES, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ source, action, info }) => {
              if (source !== 'tx' || action !== 'search') {
                return Promise.reject(new Error('unsupported'))
              }
              return Promise.resolve({
                list: [{
                  songmid: 'qq-' + info.page,
                  name: info.query,
                  singer: [{ name: 'QQ Artist' }],
                  file: { media_mid: 'media-' + info.limit }
                }],
                total: 21,
                hasMore: true
              })
            })
            send(EVENT_NAMES.inited, {
              sources: { tx: { actions: ['search', 'musicUrl'] } }
            })
        """.trimIndent()
        val source = LuoxueImportedSource(
            id = "qq-search",
            name = "QQ 搜索测试源",
            sourceKinds = listOf("tx"),
            script = script
        )

        val page = runtime.search(source, "tx", "Search Query", page = 2, pageSize = 15)

        assertEquals(21, page?.total)
        assertEquals(true, page?.hasMore)
        val item = page?.items?.single().orEmpty()
        assertEquals("qq-2", item["songmid"])
        assertEquals("Search Query", item["name"])
        assertEquals("media-15", (item["file"] as Map<*, *>)["media_mid"])
    }

    @Test
    fun runsIndependentLyricAndArtworkActionsForLocalSource() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(RecordingHttpClient())
        val script = """
            const { EVENT_NAMES, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ source, action, info }) => {
              if (source !== 'local') return Promise.reject(new Error('wrong source'))
              if (action === 'lyric') {
                return Promise.resolve({
                  lyric: '[00:01.00]' + info.musicInfo.name,
                  tlyric: '[00:01.00]Translation'
                })
              }
              if (action === 'pic') return Promise.resolve({ url: 'https://image.example.test/cover.jpg' })
              return Promise.reject(new Error('unsupported'))
            })
            send(EVENT_NAMES.inited, {
              sources: { local: { actions: ['musicUrl', 'lyric', 'pic'] } }
            })
        """.trimIndent()
        val source = LuoxueImportedSource(
            id = "metadata",
            name = "元数据测试源",
            sourceKinds = listOf("local"),
            script = script
        )

        val lyrics = runtime.resolveLyrics(source, "local", mapOf("name" to "Song"))
        val cover = runtime.resolveCoverUrl(source, "local", mapOf("name" to "Song"))

        assertEquals("[00:01.00]Song", lyrics?.lyric)
        assertEquals("[00:01.00]Translation", lyrics?.translation)
        assertEquals("https://image.example.test/cover.jpg", cover)
    }

    @Test
    fun waitsForAsynchronousSourceInitialization() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(RecordingHttpClient())
        val script = """
            const { EVENT_NAMES, request, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ action }) => {
              if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
              return Promise.resolve('http://media.example.test/async.mp3')
            })
            request('https://source.example.test/config', {}, (err) => {
              if (err) throw new Error(err)
              Promise.resolve().then(() => send(EVENT_NAMES.inited, {
                sources: { tx: { actions: ['musicUrl'] } }
              }))
            })
        """.trimIndent()

        val url = runtime.resolveMusicUrl(
            source = LuoxueImportedSource(
                id = "async-init",
                name = "异步初始化测试源",
                sourceKinds = listOf("tx"),
                script = script
            ),
            sourceKey = "tx",
            musicInfo = mapOf("songmid" to "0039MnYb0qxYhV"),
            quality = "128k"
        )

        assertEquals("http://media.example.test/async.mp3", url)
    }

    @Test
    fun unwrapsDoubleEncodedNestedPlaybackUrl() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(RecordingHttpClient())
        val script = """
            const { EVENT_NAMES, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ action }) => {
              if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
              return JSON.stringify({ data: { play_url: 'http://media.example.test/nested.mp3' } })
            })
            send(EVENT_NAMES.inited, { sources: { kw: { actions: ['musicUrl'] } } })
        """.trimIndent()

        val url = runtime.resolveMusicUrl(
            source = LuoxueImportedSource(
                id = "nested-url",
                name = "嵌套地址测试源",
                sourceKinds = listOf("kw"),
                script = script
            ),
            sourceKey = "kw",
            musicInfo = mapOf("rid" to "101"),
            quality = "128k"
        )

        assertEquals("http://media.example.test/nested.mp3", url)
    }

    @Test
    fun callerCancellationDoesNotCloseQuickJsBeforeAsyncCallbackReturns() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(DelayedHttpClient(delayMs = 250L))
        val script = """
            const { EVENT_NAMES, request, on, send } = globalThis.lx
            on(EVENT_NAMES.request, ({ action }) => {
              if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
              return new Promise((resolve, reject) => {
                request('https://source.example.test/slow', {}, (err, response) => {
                  if (err) return reject(err)
                  resolve(response.body.url)
                })
              })
            })
            send(EVENT_NAMES.inited, { sources: { kw: { actions: ['musicUrl'] } } })
        """.trimIndent()
        val source = LuoxueImportedSource(
            id = "cancel-safe",
            name = "取消安全测试源",
            sourceKinds = listOf("kw"),
            script = script
        )

        val cancelledResult = withTimeoutOrNull(50L) {
            runtime.resolveMusicUrl(source, "kw", mapOf("rid" to "first"), "128k")
        }
        assertNull(cancelledResult)

        // The first request keeps ownership of its native runtime until the HTTP callback has
        // returned. A later resolution proves that teardown completed and released the serial gate.
        delay(350L)
        val nextUrl = runtime.resolveMusicUrl(
            source,
            "kw",
            mapOf("rid" to "second"),
            "128k"
        )
        assertEquals("https://media.example.test/cancel-safe.mp3", nextUrl)
    }

    @Test
    fun emptyHttpErrorBodyIsExposedAsStructuredLxFailure() = runBlocking {
        val runtime = QuickJsLuoxueScriptRuntime(EmptyHttpErrorClient(statusCode = 520))
        val script = """
            const { EVENT_NAMES, request, on, send } = globalThis.lx
            const httpFetch = (url, options = { method: 'GET' }) => new Promise((resolve, reject) => {
              request(url, options, (err, response) => err ? reject(err) : resolve(response))
            })
            on(EVENT_NAMES.request, ({ action }) => {
              if (action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
              return httpFetch('https://source.example.test/music/url').then(({ body }) => {
                if (!body || isNaN(Number(body.code))) throw new Error('unknown error')
                if (body.code === 200) return body.url
                throw new Error(body.message ?? '未知错误')
              })
            })
            send(EVENT_NAMES.inited, { sources: { tx: { actions: ['musicUrl'] } } })
        """.trimIndent()
        val source = LuoxueImportedSource(
            id = "empty-http-error",
            name = "空响应测试源",
            sourceKinds = listOf("tx"),
            script = script
        )

        val error = runCatching {
            runtime.resolveMusicUrl(source, "tx", mapOf("songmid" to "001SongMid"), "128k")
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("HTTP 520 返回空响应"))
        assertTrue(!error?.message.orEmpty().contains("unknown error"))
    }

    private class RecordingHttpClient : LuoxueScriptHttpClient {
        var request: LuoxueScriptHttpRequest? = null

        override fun execute(request: LuoxueScriptHttpRequest): LuoxueScriptHttpResponse {
            this.request = request
            return LuoxueScriptHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"url\":\"https://media.example.test/song.flac\"}"
            )
        }
    }

    private class DelayedHttpClient(
        private val delayMs: Long
    ) : LuoxueScriptHttpClient {
        override fun execute(request: LuoxueScriptHttpRequest): LuoxueScriptHttpResponse {
            Thread.sleep(delayMs)
            return LuoxueScriptHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"url\":\"https://media.example.test/cancel-safe.mp3\"}"
            )
        }
    }

    private class EmptyHttpErrorClient(
        private val statusCode: Int
    ) : LuoxueScriptHttpClient {
        override fun execute(request: LuoxueScriptHttpRequest): LuoxueScriptHttpResponse {
            return LuoxueScriptHttpResponse(
                statusCode = statusCode,
                headers = emptyMap(),
                body = ""
            )
        }
    }
}
