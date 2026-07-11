package app.yukine.streaming

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class LuoxueImportedSource(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val sourceKinds: List<String> = emptyList(),
    val origin: String = "",
    val script: String = "",
    val enabled: Boolean = true,
    val order: Int = 0
)

data class LuoxueSourceImportResult(
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val sources: List<LuoxueImportedSource> = emptyList()
)

/**
 * Persists only LX source metadata in SharedPreferences. The executable script body lives in the
 * app-private files directory so a large imported source is not repeatedly copied through the
 * preferences XML and is never exposed as a user-visible shared file.
 */
interface LuoxueSourceStoreManager {
    fun load(): List<LuoxueImportedSource>

    fun saveAll(sources: List<LuoxueImportedSource>): Int

    fun setEnabled(sourceId: String, enabled: Boolean): Boolean

    /**
     * Moves a source by one position. [direction] must be negative for up or positive for down.
     */
    fun move(sourceId: String, direction: Int): Boolean

    fun remove(sourceId: String): Boolean
}

class LuoxueSourceStore(context: Context) : LuoxueSourceStoreManager {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("luoxue_sources", Context.MODE_PRIVATE)
    private val scriptsDirectory = File(applicationContext.filesDir, SCRIPT_DIRECTORY)

    @Synchronized
    override fun saveAll(sources: List<LuoxueImportedSource>): Int {
        val imported = sources
            .asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
            .toList()
        if (imported.isEmpty()) return 0

        val merged = normalizeOrder(load()).toMutableList()
        var saved = 0
        imported.forEach { incoming ->
            val existingIndex = merged.indexOfFirst { it.id == incoming.id }
            val existing = merged.getOrNull(existingIndex)
            val script = incoming.script.ifBlank { existing?.script.orEmpty() }
            if (script.isBlank() || !writeScript(incoming.id, script)) {
                return@forEach
            }
            val updated = incoming.copy(
                script = script,
                enabled = existing?.enabled ?: incoming.enabled,
                order = existing?.order ?: merged.size
            )
            if (existingIndex >= 0) {
                merged[existingIndex] = updated
            } else {
                merged += updated
            }
            saved += 1
        }
        return if (saved > 0 && writeMetadata(normalizeOrder(merged))) saved else 0
    }

    @Synchronized
    override fun load(): List<LuoxueImportedSource> {
        val stored = readStoredSources()
        if (stored.isEmpty()) return emptyList()

        var foundLegacyScripts = false
        var canClearLegacyScripts = true
        val loaded = stored.map { storedSource ->
            var script = readScript(storedSource.source.id)
            if (script.isBlank() && storedSource.legacyScript.isNotBlank()) {
                if (writeScript(storedSource.source.id, storedSource.legacyScript)) {
                    script = storedSource.legacyScript
                } else {
                    canClearLegacyScripts = false
                }
            }
            if (storedSource.legacyScript.isNotBlank()) {
                foundLegacyScripts = true
            }
            storedSource.source.copy(script = script)
        }
        val normalized = normalizeOrder(loaded)
        if (foundLegacyScripts && canClearLegacyScripts) {
            writeMetadata(normalized)
        }
        return normalized
    }

    @Synchronized
    override fun setEnabled(sourceId: String, enabled: Boolean): Boolean {
        val current = normalizeOrder(load()).toMutableList()
        val index = current.indexOfFirst { it.id == sourceId }
        if (index < 0) return false
        if (current[index].enabled == enabled) return true
        current[index] = current[index].copy(enabled = enabled)
        return writeMetadata(normalizeOrder(current))
    }

    @Synchronized
    override fun move(sourceId: String, direction: Int): Boolean {
        val offset = direction.compareTo(0)
        if (offset == 0) return false
        val current = normalizeOrder(load()).toMutableList()
        val from = current.indexOfFirst { it.id == sourceId }
        if (from < 0) return false
        val to = (from + offset).takeIf { it in current.indices } ?: return false
        val source = current.removeAt(from)
        current.add(to, source)
        return writeMetadata(normalizeOrder(current))
    }

    @Synchronized
    override fun remove(sourceId: String): Boolean {
        val current = normalizeOrder(load()).toMutableList()
        val removed = current.firstOrNull { it.id == sourceId } ?: return false
        current.removeAll { it.id == sourceId }
        if (!writeMetadata(normalizeOrder(current))) {
            return false
        }
        scriptFile(removed.id).delete()
        return true
    }

    private fun readStoredSources(): List<StoredSource> {
        val raw = preferences.getString(KEY_SOURCES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val seen = linkedSetOf<String>()
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val source = LuoxueImportedSource(
                    id = item.optString("id").trim(),
                    name = item.optString("name").trim(),
                    version = item.optString("version"),
                    author = item.optString("author"),
                    origin = item.optString("origin"),
                    sourceKinds = jsonStringList(item.optJSONArray("sourceKinds")),
                    enabled = item.optBoolean("enabled", true),
                    order = if (item.has("order")) item.optInt("order", index) else index
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && seen.add(it.id) }
                    ?: return@mapNotNull null
                StoredSource(source, item.optString("script"))
            }
        }.getOrDefault(emptyList())
    }

    private fun normalizeOrder(sources: List<LuoxueImportedSource>): List<LuoxueImportedSource> {
        return sources
            .withIndex()
            .sortedWith(compareBy<IndexedValue<LuoxueImportedSource>> { it.value.order }.thenBy { it.index })
            .mapIndexed { index, indexed -> indexed.value.copy(order = index) }
    }

    private fun writeMetadata(sources: List<LuoxueImportedSource>): Boolean {
        val array = JSONArray()
        normalizeOrder(sources).forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("name", source.name)
                    .put("version", source.version)
                    .put("author", source.author)
                    .put("origin", source.origin)
                    .put("enabled", source.enabled)
                    .put("order", source.order)
                    .put("sourceKinds", JSONArray(source.sourceKinds))
            )
        }
        return preferences.edit().putString(KEY_SOURCES, array.toString()).commit()
    }

    private fun readScript(sourceId: String): String {
        val file = scriptFile(sourceId)
        if (!file.isFile) return ""
        return runCatching { file.readText(StandardCharsets.UTF_8) }.getOrDefault("")
    }

    private fun writeScript(sourceId: String, script: String): Boolean {
        if ((!scriptsDirectory.exists() && !scriptsDirectory.mkdirs()) || !scriptsDirectory.isDirectory) {
            return false
        }
        val atomicFile = AtomicFile(scriptFile(sourceId))
        return try {
            val stream = atomicFile.startWrite()
            try {
                stream.write(script.toByteArray(StandardCharsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (error: Throwable) {
                atomicFile.failWrite(stream)
                throw error
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun scriptFile(sourceId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sourceId.toByteArray(StandardCharsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(scriptsDirectory, "source-${digest}.js")
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
    }

    private data class StoredSource(
        val source: LuoxueImportedSource,
        val legacyScript: String
    )

    private companion object {
        private const val KEY_SOURCES = "sources_json"
        private const val SCRIPT_DIRECTORY = "luoxue-sources"
    }
}

object LuoxueSourceImporter {
    fun parseMany(input: String?, origin: String = ""): List<LuoxueImportedSource> {
        val raw = input.orEmpty()
        if (raw.isBlank()) return emptyList()
        return splitSourceScripts(raw)
            .mapNotNull { parseScript(it, origin) }
            .distinctBy { it.id }
    }

    fun fetchUrl(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/javascript,text/javascript,text/plain,*/*")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android")
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    fun urlLines(input: String?): List<String> {
        return input.orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
            .toList()
    }

    private fun splitSourceScripts(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n")
        val markers = listOf("sourceInfo", "EVENT_NAMES.inited", "lx.on", "lx.eventNames")
        if (markers.count { normalized.contains(it) } <= 1) {
            return listOf(normalized)
        }
        val chunks = normalized.split(Regex("(?=\\n\\s*(?:/\\*|//)?\\s*(?:const\\s+)?sourceInfo\\b)"))
            .map(String::trim)
            .filter(String::isNotBlank)
        return chunks.ifEmpty { listOf(normalized) }
    }

    private fun parseScript(script: String, origin: String): LuoxueImportedSource? {
        if (!looksLikeLuoxueScript(script)) return null
        val infoObject = extractObjectLiteral(script, "sourceInfo")
        val name = stringField(infoObject, "name")
            ?: stringField(script, "name")
            ?: fileNameFromOrigin(origin)
            ?: "LX 自定义音源"
        val version = stringField(infoObject, "version") ?: stringField(script, "version") ?: ""
        val author = stringField(infoObject, "author") ?: stringField(script, "author") ?: ""
        val kinds = sourceKinds(script)
        val id = stableId(listOf(origin, name, version, author, kinds.joinToString(","), script.take(512)).joinToString("|"))
        return LuoxueImportedSource(
            id = id,
            name = name,
            version = version,
            author = author,
            sourceKinds = kinds,
            origin = origin,
            script = script
        )
    }

    private fun looksLikeLuoxueScript(script: String): Boolean {
        val lower = script.lowercase()
        return lower.contains("globalthis.lx") ||
            lower.contains("event_names.inited") ||
            lower.contains("sourceinfo") ||
            lower.contains("musicurl") && lower.contains("lyric") ||
            lower.contains("lx.custom") ||
            lower.contains("lxmusic")
    }

    private fun extractObjectLiteral(script: String, variableName: String): String {
        val idx = Regex("\\b(?:const|let|var)?\\s*${Regex.escape(variableName)}\\s*=").find(script)?.range?.last ?: return ""
        val start = script.indexOf('{', idx)
        if (start < 0) return ""
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until script.length) {
            val c = script[index]
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == quote) {
                    quote = null
                }
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
            } else if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) return script.substring(start, index + 1)
            }
        }
        return ""
    }

    private fun stringField(text: String, field: String): String? {
        if (text.isBlank()) return null
        val quoted = Regex("""["']${Regex.escape(field)}["']\s*:\s*["']([^"']{1,120})["']""").find(text)
        val bare = Regex("""\b${Regex.escape(field)}\s*:\s*["']([^"']{1,120})["']""").find(text)
        return (quoted ?: bare)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun sourceKinds(script: String): List<String> {
        val kinds = linkedSetOf<String>()
        Regex("""(?:^|[,{])\s*["']?(kw|kg|tx|wy|mg|git|local)["']?\s*:""")
            .findAll(script)
            .map { normalizeKind(it.groupValues[1]) }
            .forEach(kinds::add)
        val checks = listOf(
            "kw" to listOf("kuwo", "酷我", "kw"),
            "kg" to listOf("kugou", "酷狗", "kg"),
            "tx" to listOf("tencent", "qq", "tx"),
            "wy" to listOf("netease", "网易", "wy"),
            "mg" to listOf("migu", "咪咕", "mg"),
            "git" to listOf("git")
        )
        val lower = script.lowercase()
        checks.forEach { (kind, needles) ->
            if (needles.any { lower.contains(it.lowercase()) }) kinds += kind
        }
        Regex("""\b(?:source|platform|type)\s*:\s*["']([a-zA-Z0-9_-]{1,16})["']""")
            .findAll(script)
            .map { normalizeKind(it.groupValues[1]) }
            .filter(String::isNotBlank)
            .forEach(kinds::add)
        return kinds.toList()
    }

    private fun normalizeKind(value: String): String = when (value.lowercase()) {
        "kuwo" -> "kw"
        "kugou" -> "kg"
        "qq", "tencent", "tencentmusic" -> "tx"
        "netease", "neteasecloud", "163" -> "wy"
        "migu" -> "mg"
        else -> value.lowercase()
    }

    private fun fileNameFromOrigin(origin: String): String? {
        return origin.substringAfterLast('/').substringAfterLast(':')
            .substringBefore('?')
            .removeSuffix(".js")
            .takeIf { it.isNotBlank() }
    }

    private fun stableId(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
