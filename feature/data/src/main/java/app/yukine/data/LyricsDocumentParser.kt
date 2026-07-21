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
            "qrc" -> parseProviderPayload(decodeText(bytes), fileName)
            "krc" -> parseKrc(decodeText(bytes), fileName)
            else -> throw IllegalArgumentException("Unsupported lyrics format: $extension")
        }
    }

    fun parseText(text: String, format: String, sourceName: String = ""): LyricsDocument =
        when (format.trim().lowercase(Locale.ROOT)) {
            "ttml", "xml" -> parseTtml(text.toByteArray(StandardCharsets.UTF_8), sourceName)
            "txt", "plain" -> parsePlain(text, sourceName)
            "lrc", "elrc", "enhanced_lrc" -> parseLrc(text, sourceName)
            "qrc" -> parseProviderPayload(text, sourceName)
            "krc" -> parseKrc(text, sourceName)
            else -> throw IllegalArgumentException("Unsupported lyrics format: $format")
        }

    @JvmOverloads
    fun parseProvider(
        primary: String,
        translation: String = "",
        romanization: String = "",
        sourceName: String = ""
    ): LyricsDocument {
        val primaryDocument = stripCreditLines(parseProviderPayload(primary, sourceName))
        if (primaryDocument.isEmpty()) return LyricsDocument.empty()
        val primaryTrack = primaryDocument.primaryOrFirstTrack() ?: return primaryDocument
        var result = primaryDocument

        val translationDocument = stripCreditLines(parseProviderPayload(translation, sourceName))
        if (!translationDocument.isEmpty()) {
            val translationTracks = translationDocument.tracks
                .filter { it.lines.isNotEmpty() }
                .map { track -> alignToPrimary(track.copy(role = LyricsTrackRole.TRANSLATION), primaryTrack) }
            if (translationTracks.isNotEmpty()) {
                result = result.copy(tracks = result.tracks + translationTracks)
            }
        }

        val romanDocument = stripCreditLines(parseProviderPayload(romanization, sourceName))
        if (!romanDocument.isEmpty()) {
            val romanTracks = romanDocument.tracks
                .filter { it.lines.isNotEmpty() }
                .map { track -> alignToPrimary(track.copy(role = LyricsTrackRole.ROMANIZATION), primaryTrack) }
            if (romanTracks.isNotEmpty()) {
                result = result.copy(tracks = result.tracks + romanTracks)
            }
        }
        return result
    }

    /**
     * Removes consecutive leading credit/metadata lines (作词、编曲、作曲 etc.) from every track
     * in the document. These lines are production credits, not sung lyrics, and cause the first
     * translation line to "stick" to them during time-based alignment.
     */
    private fun stripCreditLines(document: LyricsDocument): LyricsDocument {
        if (document.isEmpty()) return document
        val stripped = document.tracks.map { track ->
            val lines = track.lines
            var firstNonCredit = 0
            while (firstNonCredit < lines.size && isCreditLine(lines[firstNonCredit].text)) {
                firstNonCredit++
            }
            if (firstNonCredit == 0) track else track.copy(lines = lines.drop(firstNonCredit))
        }.filter { it.lines.isNotEmpty() }
        return document.copy(tracks = stripped)
    }

    private fun isCreditLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 80) return false
        return CREDIT_LINE_PATTERN.containsMatchIn(trimmed)
    }

    /**
     * Snaps each translation line's [LyricLine.startMs] to the closest primary line start so that
     * downstream UI matching (e.g. `closestText`) can reliably pair them even when the source
     * timestamps differ slightly between the primary and translation payloads.
     */
    private fun alignToPrimary(translation: LyricsTrack, primary: LyricsTrack): LyricsTrack {
        if (primary.lines.isEmpty() || translation.lines.isEmpty()) return translation
        val aligned = translation.lines.map { tLine ->
            val closest = primary.lines.minByOrNull { kotlin.math.abs(it.startMs - tLine.startMs) }
            if (closest != null && kotlin.math.abs(closest.startMs - tLine.startMs) <= ALIGN_TOLERANCE_MS) {
                tLine.copy(startMs = closest.startMs, endMs = maxOf(closest.endMs, closest.startMs))
            } else {
                tLine
            }
        }
        return translation.copy(lines = aligned)
    }

    private fun parseProviderPayload(text: String, sourceName: String): LyricsDocument {
        val clean = text.trim()
        if (clean.isEmpty()) return LyricsDocument.empty()
        if (clean.startsWith("<")) {
            runCatching {
                val qrc = parseQrcXml(clean, sourceName)
                if (!qrc.isEmpty()) return qrc
            }
            runCatching {
                return parseTtml(clean.toByteArray(StandardCharsets.UTF_8), sourceName)
            }
        }
        parseNeteaseKaraoke(clean, sourceName).takeUnless(LyricsDocument::isEmpty)?.let { return it }
        parseKrc(clean, sourceName).takeUnless(LyricsDocument::isEmpty)?.let { return it }
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

    /**
     * Parses QQ Music QRC XML format with per-character timing.
     * Format: `<Lyric_1><Lyric><Lyric_1><sentence starttime="ms" duration="ms">(charTime,charDur,0)char...</sentence>...`
     */
    private fun parseQrcXml(text: String, sourceName: String): LyricsDocument {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setExpandEntityReferences(false)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are disabled") }
        }
        val xml = builder.parse(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))
        val root = xml.documentElement ?: return LyricsDocument.empty()
        val rootName = (root.localName ?: root.nodeName).lowercase(Locale.ROOT)
        if (rootName != "lyric_1" && rootName != "qrc" && rootName != "lyric") {
            return LyricsDocument.empty()
        }
        val metadata = linkedMapOf<String, String>()
        for (attrIndex in 0 until root.attributes.length) {
            val attr = root.attributes.item(attrIndex)
            val name = attr.nodeName.lowercase(Locale.ROOT)
            when {
                name == "lyriccontent" -> { /* inline content, handled below */ }
                name.startsWith("ti") || name == "title" -> metadata["ti"] = attr.nodeValue
                name.startsWith("ar") || name == "artist" -> metadata["ar"] = attr.nodeValue
                name.startsWith("al") || name == "album" -> metadata["al"] = attr.nodeValue
            }
        }
        val sentences = xml.getElementsByTagName("sentence")
        if (sentences.length == 0) return LyricsDocument.empty()
        val lines = mutableListOf<LyricLine>()
        for (i in 0 until sentences.length) {
            val sentence = sentences.item(i) as? Element ?: continue
            val lineStart = sentence.getAttribute("starttime").toLongOrNull() ?: continue
            val lineDuration = sentence.getAttribute("duration").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val content = sentence.textContent.orEmpty()
            if (content.isBlank()) continue
            val words = parseQrcWords(content, lineStart)
            val visibleText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }
            } else {
                content.trim()
            }
            if (visibleText.isBlank()) continue
            val lineEnd = if (words.isNotEmpty()) {
                maxOf(lineStart + lineDuration, words.maxOf(LyricWord::endMs), lineStart + 1L)
            } else {
                lineStart + maxOf(lineDuration, 1L)
            }
            lines += LyricLine(
                startMs = lineStart,
                endMs = lineEnd,
                text = visibleText,
                words = if (words.isNotEmpty()) withTextOffsets(visibleText, words) else emptyList()
            )
        }
        if (lines.isEmpty()) return LyricsDocument.empty()
        return document(sourceName, "qrc", metadata, lines.sortedBy(LyricLine::startMs))
    }

    /**
     * Parses QRC word timing from sentence content.
     * Format: `(charStart,charDuration,0)char` where charStart is relative to line start or absolute.
     */
    private fun parseQrcWords(content: String, lineStart: Long): List<LyricWord> {
        val matches = KARAOKE_WORD_PATTERN.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        val firstWordStart = matches.first().groupValues[1].toLongOrNull() ?: return emptyList()
        val usesRelativeTimes = firstWordStart < lineStart
        return matches.mapNotNull { match ->
            val rawStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: return@mapNotNull null
            val wordText = match.groupValues[3]
            if (wordText.isEmpty()) return@mapNotNull null
            val start = if (usesRelativeTimes) lineStart + rawStart else rawStart
            LyricWord(
                startMs = start.coerceAtLeast(lineStart),
                endMs = (start + duration).coerceAtLeast(start),
                text = wordText
            )
        }
    }

    /**
     * Parses Kugou KRC word-by-word lyrics format.
     * Format: `[lineStartMs,lineDurationMs]<charStartMs,charDurationMs>char...`
     * All times are in milliseconds; char times are relative to line start.
     */
    fun parseKrc(text: String, sourceName: String): LyricsDocument {
        val metadata = linkedMapOf<String, String>()
        val lines = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            // Metadata lines: [key:value]
            val metaMatch = KRC_METADATA_PATTERN.matchEntire(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1].lowercase(Locale.ROOT)
                val value = metaMatch.groupValues[2].trim()
                when (key) {
                    "ti" -> metadata["ti"] = value
                    "ar" -> metadata["ar"] = value
                    "al" -> metadata["al"] = value
                }
                continue
            }
            // Lyric lines: [lineStart,lineDuration]<wordStart,wordDuration>text...
            val lineMatch = KRC_LINE_PATTERN.matchEntire(trimmed) ?: continue
            val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = lineMatch.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val body = lineMatch.groupValues[3]
            val wordMatches = KRC_WORD_PATTERN.findAll(body).toList()
            if (wordMatches.isEmpty()) continue
            val rawWords = wordMatches.mapNotNull { match ->
                val wordOffset = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val wordDuration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(0L)
                    ?: return@mapNotNull null
                val wordText = match.groupValues[3]
                if (wordText.isEmpty()) return@mapNotNull null
                val start = lineStart + wordOffset
                LyricWord(
                    startMs = start.coerceAtLeast(lineStart),
                    endMs = (start + wordDuration).coerceAtLeast(start),
                    text = wordText
                )
            }
            if (rawWords.isEmpty()) continue
            val visibleText = rawWords.joinToString("") { it.text }.trim()
            if (visibleText.isEmpty()) continue
            val lineEnd = maxOf(
                lineStart + lineDuration,
                rawWords.maxOf(LyricWord::endMs),
                lineStart + 1L
            )
            lines += LyricLine(
                startMs = lineStart,
                endMs = lineEnd,
                text = visibleText,
                words = withTextOffsets(visibleText, rawWords)
            )
        }
        if (lines.isEmpty()) return LyricsDocument.empty()
        return document(sourceName, "krc", metadata, lines.sortedBy(LyricLine::startMs))
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
        /** Maximum time difference (ms) for snapping a translation line to its closest primary line. */
        private const val ALIGN_TOLERANCE_MS = 5_000L
        private val TIME_PATTERN =
            Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val WORD_TIME_PATTERN =
            Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
        private val KARAOKE_LINE_PATTERN = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*](.*)""")
        private val KARAOKE_WORD_PATTERN =
            Regex("""\(\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*\d+)?\s*\)([^()]*)""")
        private val KRC_LINE_PATTERN = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*](.*)""")
        private val KRC_WORD_PATTERN = Regex("""<\s*(\d+)\s*,\s*(\d+)\s*>([^<]*)""")
        private val KRC_METADATA_PATTERN = Regex("""\[([A-Za-z]+):(.*)]""")
        private val METADATA_PATTERN = Regex("""\[([A-Za-z]+):\s*(.*)]""")
        private val WHITESPACE_PATTERN = Regex("""\s+""")
        private val CREDIT_LINE_PATTERN = Regex(
            """^(?:作词|作詞|作曲|编曲|編曲|词曲|詞曲|词|詞|曲|制作人|製作人|制作|製作|监制|監製|混音|录音|錄音|母带|母帶|出品|发行|發行|企划|企劃|统筹|統籌|吉他|贝斯|貝斯|鼓|和声|和聲|弦乐|弦樂|原唱|演唱|歌手|专辑|專輯|曲风|曲風|OP|SP)\s*[:：]|^(?:Lyrics?|Composed?|Arranged?|Produced?|Mixed?|Recorded?|Mastered?|Written?|Performed?|Vocals?|Guitar|Bass|Drums|Piano|Strings|Chorus|Producer|Engineer|Studio|Label|Copyright|℗|©)(?:\s+by)?\s*[:：]""",
            RegexOption.IGNORE_CASE
        )

        @JvmStatic
        fun normalizeMatchText(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
    }
}
