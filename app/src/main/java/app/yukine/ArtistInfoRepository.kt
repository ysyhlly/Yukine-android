package app.yukine

import app.yukine.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.LinkedHashMap
import kotlin.math.max

data class ArtistInfo(
    val artist: String,
    val source: String,
    val summary: String
)

class ArtistInfoRepository(
    private val connectTimeoutMs: Int = 2500,
    private val readTimeoutMs: Int = 3500
) {
    private val cache = object : LinkedHashMap<String, ArtistInfo>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistInfo>?): Boolean = size > 32
    }

    fun loadArtistInfo(artist: String, tracks: List<Track> = emptyList()): ArtistInfo? {
        val name = artist.trim()
        if (name.isBlank()) return null
        synchronized(cache) {
            cache[name]?.let { return it }
        }

        val candidates = artistCandidates(name, tracks)
        val result = candidates.firstNotNullOfOrNull { candidate ->
            runCatching { fetchNeteaseArtistInfo(candidate, name, tracks) }.getOrNull()
        } ?: candidates.firstNotNullOfOrNull { candidate ->
            runCatching { fetchBaiduBaikeCardInfo(candidate) }.getOrNull()
                ?: runCatching { fetchBaiduBaikePageInfo(candidate) }.getOrNull()
                ?: runCatching { fetchWikipediaArtistInfo(candidate) }.getOrNull()
                ?: runCatching { fetchMusicBrainzArtistInfo(candidate) }.getOrNull()
        } ?: return null

        synchronized(cache) {
            cache[name] = result
        }
        return result
    }

    private fun fetchNeteaseArtistInfo(
        artist: String,
        originalArtist: String = artist,
        tracks: List<Track> = emptyList()
    ): ArtistInfo? {
        val directId = searchNeteaseArtistId(artist)
        val id = directId ?: searchNeteaseArtistIdFromTracks(originalArtist, artist, tracks) ?: return null
        val head = requestJson("https://music.163.com/api/artist/head/info/get?id=${encode(id)}")
        val artistBody = head.optJSONObject("data")?.optJSONObject("artist")
            ?: head.optJSONObject("artist")
        val headIntro = artistBody?.optString("briefDesc").orEmpty().trim()
        val resolvedName = artistBody?.optString("name").orEmpty().trim().ifBlank { artist }
        val headMeta = listOfNotNull(
            artistBody?.optJSONArray("alias")
                ?.let(::jsonStringList)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?.let { "别名：$it" },
            artistBody?.optJSONArray("identities")
                ?.let(::jsonStringList)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?.let { "身份：$it" },
            artistBody?.optInt("musicSize", 0)
                ?.takeIf { it > 0 }
                ?.let { "网易云收录歌曲：$it 首" },
            artistBody?.optInt("albumSize", 0)
                ?.takeIf { it > 0 }
                ?.let { "专辑：$it 张" }
        ).joinToString("\n")
        val detail = runCatching {
            requestJson("https://music.163.com/api/artist/introduction?id=${encode(id)}")
                .optJSONArray("introduction")
                ?.let(::neteaseIntroduction)
                .orEmpty()
        }.getOrDefault("")
        val summary = listOf(headIntro, headMeta, detail)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return summary.takeIf { it.isNotBlank() }?.let {
            ArtistInfo(artist = resolvedName, source = "网易云音乐", summary = cleanSummary(it))
        }
    }

    private fun searchNeteaseArtistId(artist: String): String? {
        val body = requestJson(
            "https://music.163.com/api/cloudsearch/pc?s=${encode(artist)}&type=100&limit=8&offset=0&total=false"
        )
        val artists = body.optJSONObject("result")?.optJSONArray("artists")
            ?: body.optJSONObject("data")?.optJSONArray("artists")
            ?: body.optJSONArray("artists")
            ?: return null
        var bestId: String? = null
        var bestScore = 0.0
        for (index in 0 until artists.length()) {
            val item = artists.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val id = item.opt("id")?.toString()?.trim().orEmpty()
            if (id.isBlank() || looksLikeGenericArtist(name)) continue
            val aliases = item.optJSONArray("alias")?.let(::jsonStringList).orEmpty()
            val score = max(similarity(artist, name), aliases.maxOfOrNull { similarity(artist, it) } ?: 0.0)
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        return bestId.takeIf { bestScore >= 0.55 || artist.length <= 3 && bestScore >= 0.45 }
    }

    private fun searchNeteaseArtistIdFromTracks(originalArtist: String, artist: String, tracks: List<Track>): String? {
        val expectedArtists = artistCandidates(originalArtist, tracks) + artist
        val expectedTitles = tracks.map { cleanSearchTitle(it.title) }.filter { it.isNotBlank() }
        for (query in neteaseTrackSearchQueries(originalArtist, artist, tracks)) {
            val body = runCatching {
                requestJson("https://music.163.com/api/search/get/web?type=1&s=${encode(query)}&limit=10&offset=0")
            }.getOrNull() ?: continue
            val songs = body.optJSONObject("result")?.optJSONArray("songs")
                ?: body.optJSONArray("songs")
                ?: continue
            val id = bestArtistIdFromSongs(songs, expectedArtists, expectedTitles)
            if (id != null) return id
        }
        return null
    }

    private fun neteaseTrackSearchQueries(originalArtist: String, artist: String, tracks: List<Track>): List<String> {
        val result = LinkedHashSet<String>()
        tracks.take(8).forEach { track ->
            val cleanTitle = cleanSearchTitle(track.title)
            if (cleanTitle.isNotBlank()) {
                result.add(cleanTitle)
                result.add(listOf(cleanTitle, artist).filter { it.isNotBlank() }.joinToString(" "))
                result.add(listOf(cleanTitle, mainArtistCandidate(originalArtist)).filter { it.isNotBlank() }.joinToString(" "))
            }
        }
        artistCandidates(originalArtist, tracks).take(4).forEach(result::add)
        return result.filter { it.isNotBlank() }.distinct()
    }

    private fun bestArtistIdFromSongs(
        songs: JSONArray,
        expectedArtists: List<String>,
        expectedTitles: List<String>
    ): String? {
        var fallback: Pair<String, Double>? = null
        for (index in 0 until songs.length()) {
            val song = songs.optJSONObject(index) ?: continue
            val songTitle = song.optString("name").trim()
            val titleScore = expectedTitles.maxOfOrNull { similarity(it, songTitle) } ?: 0.0
            val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar") ?: continue
            for (artistIndex in 0 until artists.length()) {
                val item = artists.optJSONObject(artistIndex) ?: continue
                val name = item.optString("name").trim()
                val id = item.opt("id")?.toString()?.trim().orEmpty()
                if (id.isBlank() || id == "0" || looksLikeGenericArtist(name)) continue
                val artistScore = expectedArtists.maxOfOrNull { similarity(it, name) } ?: 0.0
                if (artistScore >= 0.55) return id
                val combined = artistScore * 0.7 + titleScore * 0.3
                if (titleScore >= 0.68 && combined > (fallback?.second ?: 0.0)) {
                    fallback = id to combined
                }
            }
        }
        return fallback?.first.takeIf { (fallback?.second ?: 0.0) >= 0.42 }
    }

    private fun neteaseIntroduction(array: JSONArray): String {
        val sections = ArrayList<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("ti").trim()
            val text = item.optString("txt").trim()
            if (text.isBlank()) continue
            sections += if (title.isBlank()) text else "$title：$text"
        }
        return sections.joinToString("\n")
    }

    private fun fetchBaiduBaikeCardInfo(artist: String): ArtistInfo? {
        val params = "scope=103&format=json&appid=379020&bk_key=${encode(artist)}&bk_length=1200"
        val body = requestJson("https://baike.baidu.com/api/openapi/BaikeLemmaCardApi?$params")
        val title = body.optString("lemmaTitle").trim()
            .ifBlank { body.optString("title").trim() }
            .ifBlank { artist }
        val abstract = body.optString("abstract").trim()
        val summary = cleanSummary(stripHtml(abstract))
        if (summary.isBlank() || !isRelevantCandidate(artist, title, summary)) return null
        return ArtistInfo(
            artist = title,
            source = "百度百科",
            summary = summary
        )
    }

    private fun fetchBaiduBaikePageInfo(artist: String): ArtistInfo? {
        val html = requestText("https://baike.baidu.com/item/${encodePath(artist)}")
        if (html.contains("百度安全验证") || html.contains("BIOC_OPTIONS") || html.contains("创建词条")) {
            return null
        }
        val title = baikeDisplayTitle(htmlMetaContent(html, "og:title") ?: htmlTitle(html) ?: artist)
        val summary = baikeSummary(html)
        if (summary.isBlank() || !isRelevantCandidate(artist, title, summary)) return null
        return ArtistInfo(artist = title, source = "百度百科", summary = cleanSummary(summary))
    }

    private fun baikeSummary(html: String): String {
        val candidates = listOf(
            Regex("<meta\\s+name=\"description\"\\s+content=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("<meta\\s+property=\"og:description\"\\s+content=\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            Regex("\"description\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        )
        for (regex in candidates) {
            val match = regex.find(html) ?: continue
            return decodeHtml(match.groupValues[1])
        }
        return ""
    }

    private fun fetchWikipediaArtistInfo(artist: String): ArtistInfo? {
        val queries = listOf(
            artist,
            "$artist singer",
            "$artist musician",
            "$artist band"
        ).distinct()
        for (language in listOf("zh", "ja", "en")) {
            for (query in queries) {
                val search = runCatching {
                    requestJson("https://$language.wikipedia.org/w/rest.php/v1/search/page?q=${encode(query)}&limit=4")
                }.getOrNull() ?: continue
                val pages = search.optJSONArray("pages") ?: continue
                val best = bestWikipediaPage(artist, query, pages) ?: continue
                val summary = runCatching {
                    requestJson("https://$language.wikipedia.org/api/rest_v1/page/summary/${encode(best)}")
                }.getOrNull() ?: continue
                val extract = summary.optString("extract").trim()
                val title = summary.optString("title").trim().ifBlank { best }
                if (extract.isBlank() || !isRelevantCandidate(artist, title, extract)) continue
                val label = when (language) {
                    "zh" -> "中文维基百科"
                    "ja" -> "日文维基百科"
                    else -> "Wikipedia"
                }
                return ArtistInfo(artist = title, source = label, summary = cleanSummary(extract))
            }
        }
        return null
    }

    private fun bestWikipediaPage(artist: String, query: String, pages: JSONArray): String? {
        var bestKey: String? = null
        var bestScore = 0.0
        for (index in 0 until pages.length()) {
            val page = pages.optJSONObject(index) ?: continue
            val key = page.optString("key").trim()
            val title = page.optString("title").trim()
            if (key.isBlank() || title.isBlank()) continue
            val score = max(similarity(artist, title), similarity(query, title))
            if (score > bestScore) {
                bestScore = score
                bestKey = key
            }
        }
        return bestKey.takeIf { bestScore >= 0.34 }
    }

    private fun fetchMusicBrainzArtistInfo(artist: String): ArtistInfo? {
        val body = requestJson(
            "https://musicbrainz.org/ws/2/artist/?query=artist:${encode(artist)}&fmt=json&limit=5"
        )
        val artists = body.optJSONArray("artists") ?: return null
        for (index in 0 until artists.length()) {
            val item = artists.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val aliases = item.optJSONArray("aliases")?.let(::musicBrainzAliases).orEmpty()
            val matched = similarity(name, artist) >= 0.55 ||
                aliases.any { similarity(it, artist) >= 0.55 }
            if (!matched && index > 0) continue
            val summary = musicBrainzSummary(item, aliases)
            if (summary.isNotBlank()) {
                return ArtistInfo(artist = name.ifBlank { artist }, source = "MusicBrainz", summary = cleanSummary(summary))
            }
        }
        return null
    }

    private fun musicBrainzAliases(array: JSONArray): List<String> {
        val result = ArrayList<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            item.optString("name").trim().takeIf { it.isNotBlank() }?.let(result::add)
        }
        return result.distinct()
    }

    private fun musicBrainzSummary(item: JSONObject, aliases: List<String>): String {
        val tags = item.optJSONArray("tags")?.let { array ->
            val result = ArrayList<String>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.optString("name")?.trim()?.takeIf { it.isNotBlank() }?.let(result::add)
            }
            result.distinct().take(6)
        }.orEmpty()
        return listOfNotNull(
            item.optString("name").trim().takeIf { it.isNotBlank() }?.let { "艺人：$it" },
            aliases.takeIf { it.isNotEmpty() }?.take(5)?.joinToString(" / ")?.let { "别名：$it" },
            item.optString("country").trim().takeIf { it.isNotBlank() }?.let { "地区：$it" },
            item.optJSONObject("life-span")?.optString("begin")?.trim()?.takeIf { it.isNotBlank() }?.let { "开始活跃/出生：$it" },
            tags.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let { "风格标签：$it" }
        ).joinToString("\n")
    }

    private fun jsonStringList(array: JSONArray): List<String> {
        val result = ArrayList<String>()
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(result::add)
        }
        return result.distinct()
    }

    private fun artistCandidates(raw: String, tracks: List<Track> = emptyList()): List<String> {
        val original = cleanDisplayText(raw)
        if (original.isBlank()) return emptyList()
        val candidates = LinkedHashSet<String>()
        splitArtistCredit(original).forEach(candidates::add)
        tracks.groupingBy { cleanDisplayText(it.artist) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .flatMap { splitArtistCredit(it.key) }
            .forEach(candidates::add)
        candidates.add(original)
        return candidates
            .map(::cleanArtistCandidate)
            .filter { it.isNotBlank() && !looksLikeGenericArtist(it) && !looksLikeVersionOrRole(it) }
            .distinct()
    }

    private fun mainArtistCandidate(value: String): String =
        artistCandidates(value).firstOrNull().orEmpty()

    private fun splitArtistCredit(value: String): List<String> {
        val normalized = cleanDisplayText(value)
        if (normalized.isBlank()) return emptyList()
        val withoutParenthesized = normalized
            .replace(Regex("[（(][^()（）]{1,80}[）)]"), " ")
            .trim()
        val collaborationCut = Regex(
            "\\s+(feat\\.?|ft\\.?|featuring|with|vs\\.?|x|×|合作|合唱|联合|prod\\.?|remix(?:ed)?\\s+by)\\s+",
            RegexOption.IGNORE_CASE
        )
        val primary = collaborationCut.split(withoutParenthesized, limit = 2).firstOrNull().orEmpty().trim()
        val separator = Regex("\\s*(/|／|、|，|,|;|；|\\||&|＆|\\+|＋)\\s*")
        val parts = listOf(primary, withoutParenthesized, normalized)
            .flatMap { separator.split(it) }
            .map(::cleanArtistCandidate)
            .filter { it.isNotBlank() }
        return parts.distinct()
    }

    private fun cleanArtistCandidate(value: String): String =
        cleanDisplayText(value)
            .replace(Regex("^[*＊·・•#@\\-—_\\s]+|[*＊·・•#@\\-—_\\s]+$"), "")
            .replace(Regex("\\b(feat\\.?|ft\\.?|featuring|prod\\.?|remix(?:ed)?\\s+by)\\b.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun cleanSearchTitle(value: String): String =
        cleanDisplayText(value)
            .replace(Regex("[（(]\\s*(remix|cover|live|edit|ver\\.?|version|instrumental|伴奏|翻唱)[^()（）]*[）)]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizeArtistLookupText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("[\\s*＊·・•#@\\-—_./／、，,;；|&＆+＋()（）\\[\\]【】]"), "")
            .trim()

    private fun normalizeComparableText(value: String): String =
        Normalizer.normalize(decodeHtml(value), Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun cleanDisplayText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun looksLikeGenericArtist(value: String): Boolean {
        val normalized = normalizeArtistLookupText(value)
        return normalized in setOf("va", "v.a", "variousartists", "群星", "众艺人", "unknownartist", "未知艺人")
    }

    private fun looksLikeVersionOrRole(value: String): Boolean {
        val normalized = cleanDisplayText(value).lowercase()
        return normalized.isBlank() ||
            normalized in setOf("remix", "cover", "live", "edit", "ver", "version", "instrumental", "伴奏", "翻唱") ||
            normalized.contains("remix") ||
            normalized.contains("version") ||
            normalized.contains("ver.") ||
            normalized.contains("feat.") ||
            normalized.contains("ft.")
    }

    private fun isRelevantCandidate(artist: String, title: String, extract: String): Boolean {
        val comparableTitle = title.replace(Regex("[（(][^()（）]{1,80}[）)]\\s*$"), "").trim()
        return similarity(artist, title) >= 0.34 ||
            similarity(artist, comparableTitle) >= 0.34 ||
            hasNormalizedTerm(title, artist) ||
            hasNormalizedTerm(comparableTitle, artist) ||
            hasNormalizedTerm(extract, artist)
    }

    private fun hasNormalizedTerm(value: String, term: String): Boolean {
        val normalizedValue = normalizeComparableText(value)
        val normalizedTerm = normalizeComparableText(term)
        if (normalizedValue.isBlank() || normalizedTerm.isBlank()) return false
        return normalizedValue == normalizedTerm ||
            normalizedValue.startsWith("$normalizedTerm ") ||
            normalizedValue.endsWith(" $normalizedTerm") ||
            normalizedValue.contains(" $normalizedTerm ") ||
            normalizedTerm.length >= 4 && normalizedValue.contains(normalizedTerm)
    }

    private fun similarity(left: String, right: String): Double {
        val a = normalizeComparableText(left)
        val b = normalizeComparableText(right)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val maxLength = max(a.length, b.length)
        return (1.0 - levenshtein(a, b).toDouble() / maxLength).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val costs = IntArray(right.length + 1) { it }
        for (i in 1..left.length) {
            var previous = costs[0]
            costs[0] = i
            for (j in 1..right.length) {
                val current = costs[j]
                costs[j] = if (left[i - 1] == right[j - 1]) {
                    previous
                } else {
                    minOf(previous, costs[j - 1], current) + 1
                }
                previous = current
            }
        }
        return costs[right.length]
    }

    private fun requestJson(url: String): JSONObject = JSONObject(requestText(url))

    private fun requestText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/json,text/html,*/*")
        connection.setRequestProperty(
            "User-Agent",
            if (url.contains("musicbrainz.org")) {
                "Yukine-Android/1.0 (artist-info)"
            } else {
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Yukine-Android"
            }
        )
        connection.setRequestProperty(
            "Referer",
            when {
                url.contains("baike.baidu.com") -> "https://baike.baidu.com/"
                url.contains("wikipedia.org") -> "https://www.wikipedia.org/"
                else -> "https://music.163.com/"
            }
        )
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
            }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanSummary(value: String): String =
        decodeHtml(stripHtml(value))
            .replace(Regex("\\[[0-9]+]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(520)

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")

    private fun htmlMetaContent(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val patterns = listOf(
            Regex("<meta\\s+[^>]*(?:property|name)=[\"']$escaped[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE),
            Regex("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$escaped[\"'][^>]*>", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.let(::decodeHtml)
    }

    private fun htmlTitle(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { decodeHtml(stripHtml(it)).trim() }

    private fun baikeDisplayTitle(rawTitle: String): String =
        rawTitle.replace(Regex("[_-]\\s*百度百科\\s*$"), "")
            .replace(Regex("\\s*百度百科\\s*$"), "")
            .trim()

    private fun decodeHtml(value: String): String =
        value.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun encodePath(value: String): String = value.split('/').joinToString("/") { encode(it) }
}
