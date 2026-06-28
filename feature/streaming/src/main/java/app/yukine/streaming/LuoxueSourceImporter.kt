package app.yukine.streaming

import android.content.Context
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
    val script: String = ""
)

data class LuoxueSourceImportResult(
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val sources: List<LuoxueImportedSource> = emptyList()
)

class LuoxueSourceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("luoxue_sources", Context.MODE_PRIVATE)

    fun saveAll(sources: List<LuoxueImportedSource>): Int {
        if (sources.isEmpty()) return 0
        val existing = load().associateBy { it.id }.toMutableMap()
        sources.forEach { existing[it.id] = it }
        val array = JSONArray()
        existing.values.sortedBy { it.name.lowercase() }.forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("name", source.name)
                    .put("version", source.version)
                    .put("author", source.author)
                    .put("origin", source.origin)
                    .put("script", source.script)
                    .put("sourceKinds", JSONArray(source.sourceKinds))
            )
        }
        preferences.edit().putString(KEY_SOURCES, array.toString()).apply()
        return sources.size
    }

    fun load(): List<LuoxueImportedSource> {
        val raw = preferences.getString(KEY_SOURCES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                LuoxueImportedSource(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    version = item.optString("version"),
                    author = item.optString("author"),
                    origin = item.optString("origin"),
                    script = item.optString("script"),
                    sourceKinds = jsonStringList(item.optJSONArray("sourceKinds"))
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
    }

    private companion object {
        private const val KEY_SOURCES = "sources_json"
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
        val checks = listOf(
            "kw" to listOf("kuwo", "酷我", "kw"),
            "kg" to listOf("kugou", "酷狗", "kg"),
            "tx" to listOf("tencent", "qq", "tx"),
            "wy" to listOf("netease", "网易", "wy"),
            "mg" to listOf("migu", "咪咕", "mg")
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
