package app.yukine.streaming

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Standard LX lyric payload returned from the script `lyric` action. */
data class LuoxueScriptLyrics(
    val lyric: String,
    val translation: String? = null,
    val romanization: String? = null,
    val wordByWord: String? = null
)

/** Optional Yukine extension page returned from an imported LX script's `search` action. */
data class LuoxueScriptSearchPage(
    val items: List<Map<String, Any?>>,
    val total: Int? = null,
    val hasMore: Boolean? = null
)

/** Narrow execution boundary for imported LX custom-source scripts. */
interface LuoxueScriptRuntime {
    /**
     * Yukine extension: lets scripts that explicitly expose `search` provide song objects. The
     * official LX custom-source contract does not require this action, so playback-only scripts
     * retain the default and continue through built-in search.
     */
    suspend fun search(
        source: LuoxueImportedSource,
        sourceKey: String,
        query: String,
        page: Int,
        pageSize: Int
    ): LuoxueScriptSearchPage? = null

    suspend fun resolveMusicUrl(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>,
        quality: String
    ): String

    /**
     * Resolves a lyric payload independently from playback URL resolution. Implementations that
     * only support playback can retain this default and let the normal lyric fallback continue.
     */
    suspend fun resolveLyrics(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>
    ): LuoxueScriptLyrics? = null

    /** Resolves an artwork URL independently from playback URL resolution. */
    suspend fun resolveCoverUrl(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>
    ): String? = null
}

internal data class LuoxueScriptHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
    val timeoutMs: Int
)

internal data class LuoxueScriptHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String
)

internal fun interface LuoxueScriptHttpClient {
    fun execute(request: LuoxueScriptHttpRequest): LuoxueScriptHttpResponse
}

/**
 * Executes the small HTTP surface exposed to LX scripts. It deliberately exposes no Android API,
 * cookies, local files, or private-network targets to imported script code.
 */
internal class DefaultLuoxueScriptHttpClient : LuoxueScriptHttpClient {
    override fun execute(request: LuoxueScriptHttpRequest): LuoxueScriptHttpResponse {
        var current = request
        repeat(MAX_REDIRECTS + 1) {
            val url = URL(current.url)
            requireAllowedUrl(url)
            val result = executeOnce(url, current)
            val redirect = result.location
            if (result.response.statusCode !in REDIRECT_STATUS_CODES || redirect.isNullOrBlank()) {
                return result.response
            }
            val nextUrl = URL(url, redirect).toString()
            current = current.copy(
                url = nextUrl,
                method = if (result.response.statusCode == HTTP_SEE_OTHER && current.method != "HEAD") "GET" else current.method,
                body = if (result.response.statusCode == HTTP_SEE_OTHER) null else current.body
            )
        }
        throw IllegalStateException("LX 脚本请求重定向次数超过限制")
    }

    private fun executeOnce(url: URL, request: LuoxueScriptHttpRequest): ScriptHttpResult {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = request.timeoutMs
            readTimeout = request.timeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android")
            request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
            val requestBody = request.body
            if (!requestBody.isNullOrEmpty() && request.method !in setOf("GET", "HEAD")) {
                doOutput = true
                outputStream.use { output ->
                    output.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            ScriptHttpResult(
                response = LuoxueScriptHttpResponse(
                    statusCode = status,
                    headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapValues { (_, values) -> values.orEmpty().joinToString(",") },
                    body = stream?.use(::readLimitedUtf8).orEmpty()
                ),
                location = connection.getHeaderField("Location")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun requireAllowedUrl(url: URL) {
        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") {
            throw IllegalArgumentException("LX 脚本只允许 HTTP/HTTPS 请求")
        }
        val host = url.host.trim().lowercase()
        if (host.isBlank() || host == "localhost" || host.endsWith(".local")) {
            throw IllegalArgumentException("LX 脚本不能访问本机地址")
        }
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress || isUniqueLocalAddress(address)
        ) {
            throw IllegalArgumentException("LX 脚本不能访问私有网络地址")
        }
    }

    private fun isUniqueLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }

    private fun readLimitedUtf8(stream: java.io.InputStream): String {
        val output = ByteArrayOutputStreamLimited(MAX_RESPONSE_BYTES)
        BufferedInputStream(stream).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private companion object {
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 4
        private const val HTTP_SEE_OTHER = 303
        private val REDIRECT_STATUS_CODES = setOf(301, 302, HTTP_SEE_OTHER, 307, 308)
    }
}

private data class ScriptHttpResult(
    val response: LuoxueScriptHttpResponse,
    val location: String?
)

private class ByteArrayOutputStreamLimited(
    private val limit: Int
) : java.io.ByteArrayOutputStream() {
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (count + length > limit) {
            throw IllegalStateException("LX 脚本响应超过 2 MB 限制")
        }
        super.write(buffer, offset, length)
    }
}

/**
 * A one-request-per-runtime LX host. A fresh QuickJS instance isolates script state and keeps
 * imported code away from the UI and playback-service lifecycles.
 */
internal class QuickJsLuoxueScriptRuntime(
    private val httpClient: LuoxueScriptHttpClient = DefaultLuoxueScriptHttpClient()
) : LuoxueScriptRuntime {
    override suspend fun search(
        source: LuoxueImportedSource,
        sourceKey: String,
        query: String,
        page: Int,
        pageSize: Int
    ): LuoxueScriptSearchPage? {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 50)
        val actionInfo = JSONObject()
            .put("query", query)
            .put("keyword", query)
            .put("page", safePage)
            .put("limit", safePageSize)
            .put("pageSize", safePageSize)
            .put("offset", (safePage - 1) * safePageSize)
        return searchPage(
            actionResult(invokeAction(source, sourceKey, action = "search", actionInfo = actionInfo)),
            safePage,
            safePageSize
        )
    }

    override suspend fun resolveMusicUrl(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>,
        quality: String
    ): String {
        val value = actionResult(
            invokeAction(
                source,
                sourceKey,
                action = "musicUrl",
                actionInfo = musicActionInfo(musicInfo, quality)
            )
        )
        val url = extractUrl(value).orEmpty().trim()
        if (url.isBlank()) {
            throw StreamingGatewayException(
                "LX 音源「${source.name}」未返回 HTTP 播放地址",
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        }
        return url
    }

    override suspend fun resolveLyrics(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>
    ): LuoxueScriptLyrics? {
        val value = actionResult(
            invokeAction(source, sourceKey, action = "lyric", actionInfo = musicActionInfo(musicInfo))
        )
        val payload = when (value) {
            is String -> LuoxueScriptLyrics(lyric = value)
            is JSONObject -> LuoxueScriptLyrics(
                lyric = value.firstText("lyric", "lrc", "lyrics"),
                translation = value.firstText("tlyric", "translation", "trans").ifBlank { null },
                romanization = value.firstText("rlyric", "romanization").ifBlank { null },
                wordByWord = value.firstText("lxlyric", "wordByWord").ifBlank { null }
            )
            else -> null
        }
        return payload?.takeIf { it.lyric.isNotBlank() }
    }

    override suspend fun resolveCoverUrl(
        source: LuoxueImportedSource,
        sourceKey: String,
        musicInfo: Map<String, Any?>
    ): String? {
        val value = actionResult(
            invokeAction(source, sourceKey, action = "pic", actionInfo = musicActionInfo(musicInfo))
        )
        return extractUrl(
            value,
            directFields = listOf("url", "pic", "cover", "coverUrl", "image", "img", "artwork")
        )?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun invokeAction(
        source: LuoxueImportedSource,
        sourceKey: String,
        action: String,
        actionInfo: JSONObject
    ): String = withTimeout(SCRIPT_TIMEOUT_MS) {
        val quickJs = QuickJs.create(Dispatchers.IO)
        try {
            quickJs.memoryLimit = MAX_MEMORY_BYTES
            quickJs.maxStackSize = MAX_STACK_BYTES
            quickJs.function("__yukineLxMd5") { args ->
                md5(args.firstOrNull()?.toString().orEmpty())
            }
            quickJs.asyncFunction("__yukineLxHttp") { args ->
                val rawRequest = args.firstOrNull() as? String
                    ?: throw IllegalArgumentException("LX 请求参数无效")
                httpClient.execute(parseHttpRequest(rawRequest)).toScriptJson()
            }
            quickJs.asyncFunction("__yukineLxSleep") { args ->
                delay(args.firstOrNull()?.toString()?.toLongOrNull()?.coerceIn(1L, 250L) ?: 25L)
                true
            }
            quickJs.evaluate<Any?>(bootstrap(source), source.origin.ifBlank { "lx-source.js" })
            quickJs.evaluate<Any?>(source.script, source.origin.ifBlank { "lx-source.js" })
            awaitSourceInitialization(quickJs)
            validateInitializedSource(quickJs, source, sourceKey, action)
            quickJs.evaluate<String>(
                invocation(sourceKey, action, actionInfo),
                source.origin.ifBlank { "lx-source.js" }
            )
        } catch (error: StreamingGatewayException) {
            throw error
        } catch (error: Throwable) {
            throw StreamingGatewayException(
                "LX 音源「${source.name}」${action} 解析失败：${error.message ?: "未知错误"}",
                cause = error,
                code = StreamingErrorCode.SOURCE_UNAVAILABLE,
                retryable = true
            )
        } finally {
            quickJs.close()
        }
    }

    /**
     * Some widely shared LX sources fetch their remote configuration before sending `inited`.
     * The desktop/mobile LX host keeps the script context alive for that asynchronous bootstrap;
     * wait for the same signal instead of validating immediately after top-level evaluation.
     */
    private suspend fun awaitSourceInitialization(quickJs: QuickJs) {
        // Keep Promise values out of quickjs-kt's alpha13 type converter. Synchronous sources are
        // detected immediately; asynchronous sources get host-side polling while QuickJS drains
        // jobs created by bound HTTP functions between evaluations.
        val attempts = (SOURCE_INIT_TIMEOUT_MS / SOURCE_INIT_POLL_MS).toInt().coerceAtLeast(1)
        repeat(attempts) {
            val initJson = quickJs.evaluate<String>("globalThis.__yukineLxInit || ''")
            if (initJson.isNotBlank()) return
            delay(SOURCE_INIT_POLL_MS)
        }
        throw IllegalStateException("LX 音源异步初始化超时")
    }

    private suspend fun validateInitializedSource(
        quickJs: QuickJs,
        source: LuoxueImportedSource,
        sourceKey: String,
        action: String
    ) {
        val hasHandler = quickJs.evaluate<Boolean>(
            "typeof globalThis.__yukineLxRequestHandler === 'function'"
        )
        if (!hasHandler) {
            throw IllegalStateException("LX 音源未注册 request 处理器")
        }
        val initJson = quickJs.evaluate<String>("globalThis.__yukineLxInit || ''")
        if (initJson.isBlank()) {
            throw IllegalStateException("LX 音源未发送 inited 事件")
        }
        val sources = runCatching {
            JSONObject(initJson).optJSONObject("sources")
        }.getOrNull()
        val sourceConfig = sources?.optJSONObject(sourceKey)
        if (sourceConfig == null) {
            throw IllegalStateException("LX 音源「${source.name}」不支持 $sourceKey 子源")
        }
        val actions = sourceConfig.optJSONArray("actions")
        if (actions != null && actions.length() > 0 && !actions.containsText(action)) {
            throw IllegalStateException("LX 音源「${source.name}」的 $sourceKey 子源不支持 $action")
        }
    }

    private fun bootstrap(source: LuoxueImportedSource): String {
        val scriptInfo = JSONObject()
            .put("name", source.name)
            .put("version", source.version)
            .put("author", source.author)
            .put("rawScript", source.script)
            .toString()
        return """
            (() => {
              const eventNames = { inited: 'inited', request: 'request', updateAlert: 'updateAlert' };
              globalThis.__yukineLxInit = '';
              globalThis.__yukineLxRequestHandler = null;
              globalThis.console = globalThis.console || {
                log: () => {}, info: () => {}, warn: () => {}, error: () => {},
                group: () => {}, groupEnd: () => {}
              };
              const scriptInfo = $scriptInfo;
              const responseBody = value => {
                if (typeof value !== 'string') return value;
                try { return JSON.parse(value); } catch (_) { return value; }
              };
              const request = (url, options, callback) => {
                let cancelled = false;
                globalThis.__yukineLxHttp(JSON.stringify({ url, options: options || {} }))
                  .then(raw => {
                    if (cancelled) return;
                    const response = JSON.parse(raw);
                    response.body = responseBody(response.body);
                    callback(null, response, response.body);
                  })
                  .catch(error => {
                    if (!cancelled) callback(String(error), null, null);
                  });
                return () => { cancelled = true; };
              };
              globalThis.lx = {
                version: '1.0.0',
                env: 'mobile',
                currentScriptInfo: scriptInfo,
                EVENT_NAMES: eventNames,
                eventNames,
                on: (eventName, handler) => {
                  if (eventName === eventNames.request) globalThis.__yukineLxRequestHandler = handler;
                },
                send: (eventName, data) => {
                  if (eventName === eventNames.inited) globalThis.__yukineLxInit = JSON.stringify(data || {});
                },
                request,
                utils: {
                  buffer: { from: value => value, bufToString: value => String(value) },
                  crypto: { md5: value => globalThis.__yukineLxMd5(String(value)) },
                  randomBytes: size => Array.from({ length: Math.max(0, Number(size) || 0) }, () =>
                    Math.floor(Math.random() * 256))
                }
              };
            })();
        """.trimIndent()
    }

    private fun invocation(
        sourceKey: String,
        action: String,
        actionInfo: JSONObject
    ): String {
        val info = JSONObject()
            .put("source", sourceKey)
            .put("action", action)
            .put("info", actionInfo)
            .toString()
        return """
            (async () => {
              const result = await globalThis.__yukineLxRequestHandler($info);
              return JSON.stringify(typeof result === 'undefined' ? null : result);
            })()
        """.trimIndent()
    }

    private fun musicActionInfo(musicInfo: Map<String, Any?>, quality: String? = null): JSONObject {
        return JSONObject()
            .put("musicInfo", JSONObject(musicInfo))
            .also { info ->
                quality?.takeIf { it.isNotBlank() }?.let { info.put("type", it) }
            }
    }

    private fun searchPage(value: Any?, page: Int, pageSize: Int): LuoxueScriptSearchPage? {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.findSearchItems()
                ?: value.takeIf { it.looksLikeSearchItem() }?.let { JSONArray().put(it) }
            else -> null
        } ?: return null
        val items = (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index) }
            .take(pageSize)
            .map { item -> item.toValueMap() }
        if (items.isEmpty()) return null
        val root = value as? JSONObject
        val total = root?.findInt("total", "totalCount", "totalnum", "totalNum", "count")
        val hasMore = root?.findBoolean("hasMore", "has_more", "more")
            ?: total?.let { page * pageSize < it }
        return LuoxueScriptSearchPage(items = items, total = total, hasMore = hasMore)
    }

    private fun JSONObject.findSearchItems(depth: Int = 0): JSONArray? {
        if (depth > MAX_SEARCH_RESULT_DEPTH) return null
        for (name in SEARCH_ARRAY_FIELDS) {
            optJSONArray(name)?.let { return it }
        }
        for (name in SEARCH_CONTAINER_FIELDS) {
            optJSONObject(name)?.findSearchItems(depth + 1)?.let { return it }
        }
        return null
    }

    private fun JSONObject.findInt(vararg names: String, depth: Int = 0): Int? {
        if (depth > MAX_SEARCH_RESULT_DEPTH) return null
        names.firstNotNullOfOrNull { name ->
            if (!has(name) || isNull(name)) null else opt(name)?.toString()?.toIntOrNull()
        }?.let { return it }
        for (name in SEARCH_CONTAINER_FIELDS) {
            optJSONObject(name)?.findInt(*names, depth = depth + 1)?.let { return it }
        }
        return null
    }

    private fun JSONObject.findBoolean(vararg names: String, depth: Int = 0): Boolean? {
        if (depth > MAX_SEARCH_RESULT_DEPTH) return null
        names.firstNotNullOfOrNull { name ->
            if (!has(name) || isNull(name)) {
                null
            } else {
                when (val raw = opt(name)) {
                    is Boolean -> raw
                    is Number -> raw.toInt() != 0
                    else -> raw?.toString()?.trim()?.lowercase()?.let { text ->
                        when (text) {
                            "true", "1", "yes" -> true
                            "false", "0", "no" -> false
                            else -> null
                        }
                    }
                }
            }
        }?.let { return it }
        for (name in SEARCH_CONTAINER_FIELDS) {
            optJSONObject(name)?.findBoolean(*names, depth = depth + 1)?.let { return it }
        }
        return null
    }

    private fun JSONObject.looksLikeSearchItem(): Boolean {
        return listOf("id", "mid", "songmid", "rid", "hash", "title", "name", "songname")
            .any(::has)
    }

    private fun JSONObject.toValueMap(): Map<String, Any?> {
        return keys().asSequence().associateWith { key -> jsonValue(opt(key)) }
    }

    private fun jsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONObject -> value.toValueMap()
            is JSONArray -> (0 until value.length()).map { index -> jsonValue(value.opt(index)) }
            else -> value
        }
    }

    private fun actionResult(value: String): Any? {
        return decodeJsonValue(value, depth = 0)
    }

    private fun decodeJsonValue(value: String, depth: Int): Any? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrElse { return raw }
        if (parsed is String && depth < MAX_JSON_UNWRAP_DEPTH) {
            val nested = parsed.trim()
            if (nested.startsWith("{") || nested.startsWith("[") ||
                (nested.startsWith('"') && nested.endsWith('"'))
            ) {
                return decodeJsonValue(nested, depth + 1)
            }
        }
        return parsed
    }

    private fun extractUrl(
        value: Any?,
        directFields: List<String> = PLAYBACK_URL_FIELDS,
        depth: Int = 0
    ): String? {
        if (depth > MAX_URL_RESULT_DEPTH) return null
        return when (value) {
            is String -> {
                val decoded = decodeJsonValue(value, depth)
                if (decoded is String) decoded.trim().takeIf { it.isNotBlank() }
                else extractUrl(decoded, directFields, depth + 1)
            }
            is JSONObject -> {
                directFields.firstNotNullOfOrNull { field ->
                    if (!value.has(field) || value.isNull(field)) null
                    else extractUrl(value.opt(field), directFields, depth + 1)
                } ?: URL_CONTAINER_FIELDS.firstNotNullOfOrNull { field ->
                    if (!value.has(field) || value.isNull(field)) null
                    else extractUrl(value.opt(field), directFields, depth + 1)
                }
            }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index ->
                extractUrl(value.opt(index), directFields, depth + 1)
            }
            else -> null
        }
    }

    private fun JSONObject.firstText(vararg names: String): String {
        return names.firstNotNullOfOrNull { name ->
            if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() } else null
        }.orEmpty()
    }

    private fun JSONArray.containsText(target: String): Boolean {
        return (0 until length()).any { index -> optString(index).equals(target, ignoreCase = true) }
    }

    private fun parseHttpRequest(rawRequest: String): LuoxueScriptHttpRequest {
        val value = JSONObject(rawRequest)
        val url = value.optString("url").trim()
        if (url.isBlank()) throw IllegalArgumentException("LX 请求缺少 URL")
        val options = value.optJSONObject("options") ?: JSONObject()
        val method = options.optString("method", "GET").uppercase().let { candidate ->
            candidate.takeIf { it in ALLOWED_METHODS }
                ?: throw IllegalArgumentException("LX 请求方法不受支持：$candidate")
        }
        val headers = options.optJSONObject("headers")?.toStringMap().orEmpty()
        val body = options.bodyText() ?: options.formText()
        val timeout = options.optLong("timeout", DEFAULT_HTTP_TIMEOUT_MS.toLong())
            .coerceIn(MIN_HTTP_TIMEOUT_MS.toLong(), MAX_HTTP_TIMEOUT_MS.toLong())
            .toInt()
        return LuoxueScriptHttpRequest(url, method, headers, body, timeout)
    }

    private fun JSONObject.bodyText(): String? {
        if (!has("body") || isNull("body")) return null
        return get("body").toString()
    }

    private fun JSONObject.formText(): String? {
        val form = optJSONObject("form") ?: return null
        return form.keys().asSequence().joinToString("&") { key ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8.name())}=" +
                URLEncoder.encode(form.opt(key)?.toString().orEmpty(), StandardCharsets.UTF_8.name())
        }
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        return keys().asSequence().associateWith { key -> opt(key)?.toString().orEmpty() }
    }

    private fun LuoxueScriptHttpResponse.toScriptJson(): String {
        return JSONObject()
            .put("statusCode", statusCode)
            .put("headers", JSONObject(headers))
            .put("body", body)
            .toString()
    }

    private fun md5(value: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        private const val SCRIPT_TIMEOUT_MS = 25_000L
        private const val SOURCE_INIT_TIMEOUT_MS = 8_000L
        private const val SOURCE_INIT_POLL_MS = 25L
        private const val DEFAULT_HTTP_TIMEOUT_MS = 10_000
        private const val MIN_HTTP_TIMEOUT_MS = 1_000
        private const val MAX_HTTP_TIMEOUT_MS = 20_000
        // Current shared sources can be heavily obfuscated and exceed 8 MB while QuickJS compiles
        // their lookup tables. The cap still isolates imported code from the rest of the app.
        private const val MAX_MEMORY_BYTES = 32L * 1024 * 1024
        private const val MAX_STACK_BYTES = 512L * 1024
        private const val MAX_SEARCH_RESULT_DEPTH = 5
        private const val MAX_JSON_UNWRAP_DEPTH = 4
        private const val MAX_URL_RESULT_DEPTH = 5
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        private val PLAYBACK_URL_FIELDS = listOf("url", "playUrl", "play_url", "musicUrl", "src", "location")
        private val URL_CONTAINER_FIELDS = listOf("data", "body", "result", "response")
        private val SEARCH_ARRAY_FIELDS = listOf("list", "items", "tracks", "songs", "data")
        private val SEARCH_CONTAINER_FIELDS = listOf(
            "result",
            "data",
            "body",
            "song",
            "search",
            "req_1",
            "music.search.SearchCgiService"
        )
    }
}
