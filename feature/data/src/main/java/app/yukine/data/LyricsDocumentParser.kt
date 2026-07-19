package app.yukine.data

import app.yukine.model.LyricLine
import app.yukine.model.LyricWord
import app.yukine.model.LyricsDocument
import app.yukine.model.LyricsTrack
import app.yukine.model.LyricsTrackRole
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

class LyricsDocumentParser {
    fun parse(bytes: ByteArray, fileName: String): LyricsDocument {
        require(bytes.size <= MAX_FILE_BYTES) { "Lyrics file exceeds 2 MB" }
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "ttml", "xml" -> parseTtml(bytes, fileName)
            "txt" -> parsePlain(decodeText(bytes), fileName)
            "lrc", "elrc" -> parseLrc(decodeText(bytes), fileName)
            else -> throw IllegalArgumentException("Unsupported lyrics format: $extension")
        }
    }

    fun parseText(text: String, format: String, sourceName: String = ""): LyricsDocument =
        when (format.trim().lowercase(Locale.ROOT)) {
            "ttml", "xml" -> parseTtml(text.toByteArray(StandardCharsets.UTF_8), sourceName)
            "txt", "plain" -> parsePlain(text, sourceName)
            "lrc", "elrc", "enhanced_lrc" -> parseLrc(text, sourceName)
            else -> throw IllegalArgumentException("Unsupported lyrics format: $format")
        }

    @JvmOverloads
    fun parseProvider(
        primary: String,
        translation: String = "",
        sourceName: String = ""
    ): LyricsDocument {
        val primaryDocument = parseProviderPayload(primary, sourceName)
        if (primaryDocument.isEmpty()) return LyricsDocument.empty()
        val translationDocument = parseProviderPayload(translation, sourceName)
        if (translationDocument.isEmpty()) return primaryDocument
        val translationTracks = translationDocument.tracks
            .filter { it.lines.isNotEmpty() }
            .map { track -> track.copy(role = LyricsTrackRole.TRANSLATION) }
        if (translationTracks.isEmpty()) return primaryDocument
        return primaryDocument.copy(tracks = primaryDocument.tracks + translationTracks)
    }

    private fun parseProviderPayload(text: String, sourceName: String): LyricsDocument {
        val clean = text.trim()
        if (clean.isEmpty()) return LyricsDocument.empty()
        if (clean.startsWith("<")) {
            runCatching {
                return parseTtml(clean.toByteArray(StandardCharsets.UTF_8), sourceName)
            }
        }
        parseNeteaseKaraoke(clean, sourceName).takeUnless(LyricsDocument::isEmpty)?.let { return it }
        parseLrc(clean, sourceName).takeUnless(LyricsDocument::isEmpty)?.let { return it }
        return parsePlain(clean, sourceName)
    }

    private fun parsePlain(text: String, sourceName: String): LyricsDocument {
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapIndexed { index, value ->
                val start = index * 3_000L
                LyricLine(start, start + 3_000L, value)
            }
            .toList()
        return document(sourceName, "txt", emptyMap(), lines)
    }

    private fun parseLrc(text: String, sourceName: String): LyricsDocument {
        val metadata = linkedMapOf<String, String>()
        var offsetMs = 0L
        val pending = mutableListOf<PendingLine>()
        text.lineSequence().forEachIndexed { order, raw ->
            val trimmed = raw.trim()
            val metadataMatch = METADATA_PATTERN.matchEntire(trimmed)
            if (metadataMatch != null && !TIME_PATTERN.containsMatchIn(trimmed)) {
                val key = metadataMatch.groupValues[1].lowercase(Locale.ROOT)
                val value = metadataMatch.groupValues[2].trim()
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                } else {
                    metadata[key] = value
                }
                return@forEachIndexed
            }
            val times = TIME_PATTERN.findAll(trimmed).map { parseLrcTime(it.groupValues) }.toList()
            if (times.isEmpty()) return@forEachIndexed
            val contentStart = TIME_PATTERN.findAll(trimmed).last().range.last + 1
            val content = trimmed.substring(contentStart).trim()
            if (content.isEmpty()) return@forEachIndexed
            times.forEach { time ->
                pending += PendingLine(
                    startMs = time,
                    order = order,
                    sourceText = content
                )
            }
        }
        val sorted = pending
            .map { it.copy(startMs = (it.startMs + offsetMs).coerceAtLeast(0L)) }
            .sortedWith(compareBy<PendingLine> { it.startMs }.thenBy { it.order })
        val lines = sorted.mapIndexed { index, value ->
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val parsedWords = parseEnhancedWords(value.sourceText, offsetMs)
            val plainText = if (parsedWords.isEmpty()) {
                value.sourceText.trim()
            } else {
                WORD_TIME_PATTERN.replace(value.sourceText, "").trim()
            }
            val lineEnd = (nextStart ?: parsedWords.lastOrNull()?.first?.plus(3_000L)
                ?: value.startMs.plus(3_000L)).coerceAtLeast(value.startMs)
            val rawWords = parsedWords.mapIndexed { wordIndex, word ->
                val end = parsedWords.getOrNull(wordIndex + 1)?.first ?: lineEnd
                LyricWord(
                    startMs = word.first.coerceAtLeast(value.startMs),
                    endMs = end.coerceAtLeast(word.first),
                    text = word.second
                )
            }
            val words = withTextOffsets(plainText, rawWords)
            LyricLine(value.startMs, lineEnd, plainText, words)
        }.filter { it.text.isNotBlank() }
        return document(sourceName, if (lines.any { it.words.isNotEmpty() }) "elrc" else "lrc", metadata, lines)
    }

    private fun parseEnhancedWords(text: String, offsetMs: Long): List<Pair<Long, String>> {
        val matches = WORD_TIME_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val valueStart = match.range.last + 1
            val valueEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val word = text.substring(valueStart, valueEnd)
            if (word.isEmpty()) null else {
                val start = (parseLrcTime(match.groupValues) + offsetMs).coerceAtLeast(0L)
                start to word
            }
        }
    }

    private fun parseNeteaseKaraoke(text: String, sourceName: String): LyricsDocument {
        val lines = text.lineSequence().mapNotNull line@ { raw ->
            val lineMatch = KARAOKE_LINE_PATTERN.matchEntire(raw.trim()) ?: return@line null
            val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: return@line null
            val lineDuration = lineMatch.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val body = lineMatch.groupValues[3]
            val matches = KARAOKE_WORD_PATTERN.findAll(body).toList()
            if (matches.isEmpty()) return@line null
            val firstWordStart = matches.first().groupValues[1].toLongOrNull() ?: return@line null
            val usesLineRelativeWordTimes = firstWordStart < lineStart
            val rawWords = matches.mapNotNull word@ { match ->
                val rawStart = match.groupValues[1].toLongOrNull() ?: return@word null
                val duration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(0L)
                    ?: return@word null
                val wordText = match.groupValues[3]
                if (wordText.isEmpty()) return@word null
                val start = if (usesLineRelativeWordTimes) lineStart + rawStart else rawStart
                LyricWord(
                    startMs = start.coerceAtLeast(lineStart),
                    endMs = (start + duration).coerceAtLeast(start),
                    text = wordText
                )
            }
            if (rawWords.isEmpty()) return@line null
            val visibleText = rawWords.joinToString(separator = "", transform = LyricWord::text).trim()
            if (visibleText.isEmpty()) return@line null
            val lineEnd = maxOf(
                lineStart + lineDuration,
                rawWords.maxOf(LyricWord::endMs),
                lineStart + 1L
            )
            LyricLine(
                startMs = lineStart,
                endMs = lineEnd,
                text = visibleText,
                words = withTextOffsets(visibleText, rawWords)
            )
        }.sortedBy(LyricLine::startMs).toList()
        return document(sourceName, "klyric", emptyMap(), lines)
    }

    private fun withTextOffsets(text: String, words: List<LyricWord>): List<LyricWord> {
        var searchFrom = 0
        return words.map { word ->
            val start = text.indexOf(word.text, searchFrom)
            if (start < 0) {
                word
            } else {
                val end = (start + word.text.length).coerceAtMost(text.length)
                searchFrom = end
                word.copy(startOffset = start, endOffset = end)
            }
        }
    }

    private fun parseTtml(bytes: ByteArray, sourceName: String): LyricsDocument {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            setExpandEntityReferences(false)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ ->
                throw org.xml.sax.SAXException("External entities are disabled")
            }
        }
        val xml = builder.parse(ByteArrayInputStream(bytes))
        val root = xml.documentElement ?: throw IllegalArgumentException("TTML document is empty")
        val rootName = (root.localName ?: root.nodeName).substringAfter(':').lowercase(Locale.ROOT)
        require(rootName == "tt") { "XML document is not TTML" }

        val grouped = linkedMapOf<Pair<LyricsTrackRole, String>, MutableList<LyricLine>>()
        val paragraphs = xml.getElementsByTagNameNS("*", "p").let { nodes ->
            if (nodes.length == 0) xml.getElementsByTagName("p") else nodes
        }
        for (index in 0 until paragraphs.length) {
            val element = paragraphs.item(index) as? Element ?: continue
            val start = timeAttribute(element, "begin") ?: continue
            val end = timeAttribute(element, "end")
                ?: timeAttribute(element, "dur")?.let(start::plus)
                ?: start + 3_000L
            val role = role(element)
            val language = language(element)
            val words = mutableListOf<LyricWord>()
            val spans = element.getElementsByTagNameNS("*", "span").let { nodes ->
                if (nodes.length == 0) element.getElementsByTagName("span") else nodes
            }
            for (spanIndex in 0 until spans.length) {
                val span = spans.item(spanIndex) as? Element ?: continue
                val wordStart = timeAttribute(span, "begin") ?: continue
                val wordEnd = timeAttribute(span, "end")
                    ?: timeAttribute(span, "dur")?.let(wordStart::plus)
                    ?: wordStart
                val wordText = span.textContent.orEmpty()
                if (wordText.isNotEmpty()) {
                    words += LyricWord(wordStart, wordEnd.coerceAtLeast(wordStart), wordText)
                }
            }
            val text = element.textContent.orEmpty().replace(WHITESPACE_PATTERN, " ").trim()
            if (text.isNotEmpty()) {
                grouped.getOrPut(role to language) { mutableListOf() } +=
                    LyricLine(start, end.coerceAtLeast(start), text, withTextOffsets(text, words))
            }
        }
        val tracks = grouped.map { (identity, lines) ->
            LyricsTrack(identity.first, identity.second, lines.sortedBy(LyricLine::startMs))
        }
        require(tracks.any { it.lines.isNotEmpty() }) { "TTML contains no timed lyrics" }
        return LyricsDocument(
            sourceName = sourceName,
            format = "ttml",
            title = root.attributeValue("title"),
            artist = root.attributeValue("artist"),
            album = root.attributeValue("album"),
            tracks = tracks
        )
    }

    private fun role(element: Element): LyricsTrackRole {
        var node: Node? = element
        while (node is Element) {
            val value = (node.attributeValue("role") + " " + node.attributeValue("type"))
                .lowercase(Locale.ROOT)
            when {
                "translation" in value -> return LyricsTrackRole.TRANSLATION
                "transliteration" in value || "roman" in value -> return LyricsTrackRole.ROMANIZATION
            }
            node = node.parentNode
        }
        return LyricsTrackRole.PRIMARY
    }

    private fun language(element: Element): String {
        var node: Node? = element
        while (node is Element) {
            val value = node.getAttributeNS(XMLConstants.XML_NS_URI, "lang")
                .ifBlank { node.getAttribute("xml:lang") }
            if (value.isNotBlank()) return value.trim()
            node = node.parentNode
        }
        return ""
    }

    private fun timeAttribute(element: Element, name: String): Long? =
        element.attributeValue(name).takeIf(String::isNotBlank)?.let(::parseTtmlTime)

    private fun Element.attributeValue(name: String): String {
        if (hasAttribute(name)) return getAttribute(name)
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if ((attribute.localName ?: attribute.nodeName).substringAfter(':') == name) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun parseTtmlTime(value: String): Long {
        val text = value.trim()
        return when {
            text.endsWith("ms") -> text.dropLast(2).toDouble().toLong()
            text.endsWith("s") -> (text.dropLast(1).toDouble() * 1_000.0).toLong()
            ':' in text -> {
                val parts = text.split(':')
                require(parts.size == 3) { "Unsupported TTML time: $value" }
                ((parts[0].toLong() * 3_600L + parts[1].toLong() * 60L) * 1_000L +
                    parts[2].toDouble() * 1_000.0).toLong()
            }
            else -> throw IllegalArgumentException("Unsupported TTML time: $value")
        }.coerceAtLeast(0L)
    }

    private fun decodeText(bytes: ByteArray): String {
        val content = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        val utf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { utf8.decode(ByteBuffer.wrap(content)).toString() }
            .getOrElse { String(content, charset("GB18030")) }
    }

    private fun document(
        sourceName: String,
        format: String,
        metadata: Map<String, String>,
        lines: List<LyricLine>
    ): LyricsDocument = LyricsDocument(
        sourceName = sourceName,
        format = format,
        title = metadata["ti"].orEmpty(),
        artist = metadata["ar"].orEmpty(),
        album = metadata["al"].orEmpty(),
        tracks = if (lines.isEmpty()) emptyList() else {
            listOf(LyricsTrack(LyricsTrackRole.PRIMARY, metadata["lang"].orEmpty(), lines))
        }
    )

    private fun parseLrcTime(groups: List<String>): Long {
        val minutes = groups[1].toLong()
        val seconds = groups[2].toLong()
        val fraction = groups.getOrElse(3) { "" }
        val milliseconds = when (fraction.length) {
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            3 -> fraction.toLong()
            else -> 0L
        }
        return (minutes * 60L + seconds) * 1_000L + milliseconds
    }

    private data class PendingLine(
        val startMs: Long,
        val order: Int,
        val sourceText: String
    )

    companion object {
        const val MAX_FILE_BYTES: Int = 2 * 1024 * 1024
        private val TIME_PATTERN =
            Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val WORD_TIME_PATTERN =
            Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
        private val KARAOKE_LINE_PATTERN = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*](.*)""")
        private val KARAOKE_WORD_PATTERN =
            Regex("""\(\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*\d+)?\s*\)([^()]*)""")
        private val METADATA_PATTERN = Regex("""\[([A-Za-z]+):\s*(.*)]""")
        private val WHITESPACE_PATTERN = Regex("""\s+""")

        @JvmStatic
        fun normalizeMatchText(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
    }
}
