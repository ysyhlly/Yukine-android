package app.yukine.data

import app.yukine.model.LyricsTrackRole
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsDocumentParserTest {
    private val parser = LyricsDocumentParser()

    @Test
    fun lrcReadsMetadataAndAppliesOffsetRegardlessOfDeclarationOrder() {
        val document = parser.parseText(
            """
            [ti:夜航]
            [ar:测试歌手]
            [00:01.00]第一句
            [offset:500]
            [00:03.25]第二句
            """.trimIndent(),
            "lrc",
            "夜航.lrc"
        )

        val lines = document.track(LyricsTrackRole.PRIMARY)!!.lines
        assertEquals("夜航", document.title)
        assertEquals("测试歌手", document.artist)
        assertEquals(1_500L, lines[0].startMs)
        assertEquals(3_750L, lines[1].startMs)
    }

    @Test
    fun enhancedLrcCreatesWordTimingWithoutChangingVisibleText() {
        val document = parser.parseText(
            "[00:01.00]<00:01.00>你<00:01.50>好",
            "lrc",
            "word.lrc"
        )

        val line = document.track(LyricsTrackRole.PRIMARY)!!.lines.single()
        assertEquals("你好", line.text)
        assertEquals(listOf("你", "好"), line.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L), line.words.map { it.startMs })
        assertEquals(listOf(0 to 1, 1 to 2), line.words.map { it.startOffset to it.endOffset })
        assertTrue(line.words[0].endMs >= line.words[0].startMs)
    }

    @Test
    fun providerKaraokeKeepsWordTimingAndTranslationTrack() {
        val document = parser.parseProvider(
            primary = "[1000,1000](1000,400,0)你(1400,600,0)好",
            translation = "[00:01.00]Hello",
            sourceName = "netease"
        )

        val primary = document.track(LyricsTrackRole.PRIMARY)!!.lines.single()
        assertEquals("klyric", document.format)
        assertEquals("你好", primary.text)
        assertEquals(listOf(1_000L, 1_400L), primary.words.map { it.startMs })
        assertEquals(listOf(1_400L, 2_000L), primary.words.map { it.endMs })
        assertEquals(listOf(0 to 1, 1 to 2), primary.words.map { it.startOffset to it.endOffset })
        assertEquals("Hello", document.track(LyricsTrackRole.TRANSLATION)!!.lines.single().text)
    }

    @Test
    fun providerKaraokeUsesOneRelativeTimeBaseForTheWholeLine() {
        val document = parser.parseProvider(
            primary = "[1000,2500](0,1400,0)你(1400,1100,0)好",
            sourceName = "netease"
        )

        val words = document.track(LyricsTrackRole.PRIMARY)!!.lines.single().words
        assertEquals(listOf(1_000L, 2_400L), words.map { it.startMs })
        assertEquals(listOf(2_400L, 3_500L), words.map { it.endMs })
    }

    @Test
    fun elrcFileExtensionUsesEnhancedLrcParser() {
        val document = parser.parse(
            "[00:01.00]<00:01.00>A<00:01.50>B".toByteArray(StandardCharsets.UTF_8),
            "word.elrc"
        )

        assertEquals(listOf("A", "B"), document.track(LyricsTrackRole.PRIMARY)!!.lines.single().words.map { it.text })
    }

    @Test
    fun textFilesUseUtf8BomThenGb18030Fallback() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "第一行\n第二行".toByteArray(StandardCharsets.UTF_8)
        val utf8Document = parser.parse(utf8, "utf8.txt")
        assertEquals(
            listOf("第一行", "第二行"),
            utf8Document.track(LyricsTrackRole.PRIMARY)!!.lines.map { it.text }
        )

        val gb18030 = "[00:01.00]中文歌词".toByteArray(charset("GB18030"))
        val legacyDocument = parser.parse(gb18030, "legacy.lrc")
        assertEquals(
            "中文歌词",
            legacyDocument.track(LyricsTrackRole.PRIMARY)!!.lines.single().text
        )
    }

    @Test
    fun ttmlParsesExplicitTracksDurationsLanguagesAndWords() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div xml:lang="zh">
                  <p begin="1s" dur="2s"><span begin="1s" end="1.5s">你</span><span begin="1.5s" end="2s">好</span></p>
                </div>
                <div xml:lang="en" ttm:role="translation">
                  <p begin="1s" end="3s">Hello</p>
                </div>
                <div ttm:role="transliteration">
                  <p begin="1s" end="3s">ni hao</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = parser.parse(xml.toByteArray(StandardCharsets.UTF_8), "multi.ttml")
        val primary = document.track(LyricsTrackRole.PRIMARY)!!
        assertEquals("zh", primary.languageTag)
        assertEquals(3_000L, primary.lines.single().endMs)
        assertEquals(2, primary.lines.single().words.size)
        assertEquals("Hello", document.track(LyricsTrackRole.TRANSLATION)!!.lines.single().text)
        assertEquals("ni hao", document.track(LyricsTrackRole.ROMANIZATION)!!.lines.single().text)
    }

    @Test
    fun ttmlRejectsDoctypeAndExternalEntities() {
        val malicious = """
            <?xml version="1.0"?>
            <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="0s">&xxe;</p></body></tt>
        """.trimIndent()

        var failed = false
        try {
            parser.parse(malicious.toByteArray(StandardCharsets.UTF_8), "bad.ttml")
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun txtUsesThreeSecondIntervalsAndSkipsBlankLines() {
        val document = parser.parseText("一\n\n二", "txt")
        val lines = document.track(LyricsTrackRole.PRIMARY)!!.lines
        assertEquals(listOf(0L, 3_000L), lines.map { it.startMs })
        assertEquals(listOf(3_000L, 6_000L), lines.map { it.endMs })
        assertFalse(document.isEmpty())
    }

    @Test
    fun providerStripsLeadingCreditLinesBeforeAlignment() {
        val document = parser.parseProvider(
            primary = "[00:00.00]编曲：张三\n[00:01.00]作词：李四\n[00:15.00]第一句歌词\n[00:20.00]第二句歌词",
            translation = "[00:00.00]Arranged by: Zhang\n[00:01.00]Lyrics by: Li\n[00:15.00]First lyric line\n[00:20.00]Second lyric line",
            sourceName = "netease"
        )

        val primary = document.track(LyricsTrackRole.PRIMARY)!!
        assertEquals(2, primary.lines.size)
        assertEquals("第一句歌词", primary.lines[0].text)
        assertEquals(15_000L, primary.lines[0].startMs)

        val translation = document.track(LyricsTrackRole.TRANSLATION)!!
        assertEquals(2, translation.lines.size)
        assertEquals("First lyric line", translation.lines[0].text)
        assertEquals(15_000L, translation.lines[0].startMs)
    }

    @Test
    fun providerKeepsNonCreditLinesIntact() {
        val document = parser.parseProvider(
            primary = "[00:05.00]鼓声响起\n[00:10.00]第一句歌词",
            translation = "[00:05.00]Drums begin\n[00:10.00]First lyric",
            sourceName = "test"
        )

        val primary = document.track(LyricsTrackRole.PRIMARY)!!
        assertEquals(2, primary.lines.size)
        assertEquals("鼓声响起", primary.lines[0].text)
    }

    @Test
    fun qrcXmlParsesWordTimingsFromSentenceElements() {
        val qrc = """
            <?xml version="1.0" encoding="utf-8"?>
            <Lyric_1 LyricType="1">
              <Lyric>
                <Lyric_1>
                  <sentence starttime="1000" duration="2000">(0,500,0)你(500,700,0)好(1200,800,0)世(2000,500,0)界</sentence>
                  <sentence starttime="4000" duration="1500">(0,700,0)测(700,800,0)试</sentence>
                </Lyric_1>
              </Lyric>
            </Lyric_1>
        """.trimIndent()

        val document = parser.parseProvider(primary = qrc, sourceName = "qq")
        assertEquals("qrc", document.format)
        val primary = document.track(LyricsTrackRole.PRIMARY)!!
        assertEquals(2, primary.lines.size)

        val firstLine = primary.lines[0]
        assertEquals("你好世界", firstLine.text)
        assertEquals(1_000L, firstLine.startMs)
        assertEquals(4, firstLine.words.size)
        assertEquals(listOf("你", "好", "世", "界"), firstLine.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L, 2_200L, 3_000L), firstLine.words.map { it.startMs })
        assertEquals(listOf(1_500L, 2_200L, 3_000L, 3_500L), firstLine.words.map { it.endMs })

        val secondLine = primary.lines[1]
        assertEquals("测试", secondLine.text)
        assertEquals(4_000L, secondLine.startMs)
        assertEquals(2, secondLine.words.size)
    }

    @Test
    fun qrcXmlWithAbsoluteWordTimesParsesCorrectly() {
        val qrc = """
            <Lyric_1 LyricType="1">
              <Lyric>
                <Lyric_1>
                  <sentence starttime="5000" duration="2000">(5000,600,0)你(5600,700,0)好</sentence>
                </Lyric_1>
              </Lyric>
            </Lyric_1>
        """.trimIndent()

        val document = parser.parseProvider(primary = qrc, sourceName = "qq")
        val words = document.track(LyricsTrackRole.PRIMARY)!!.lines.single().words
        assertEquals(listOf(5_000L, 5_600L), words.map { it.startMs })
        assertEquals(listOf(5_600L, 6_300L), words.map { it.endMs })
    }

    @Test
    fun krcParsesWordTimingsWithRelativeOffsets() {
        val krc = """
            [ti:测试歌曲]
            [ar:测试歌手]
            [1000,2500]<0,500>你<500,700>好<1200,800>世<2000,500>界
            [4000,1500]<0,700>测<700,800>试
        """.trimIndent()

        val document = parser.parseKrc(krc, "kugou")
        assertEquals("krc", document.format)
        assertEquals("测试歌曲", document.title)
        assertEquals("测试歌手", document.artist)

        val primary = document.track(LyricsTrackRole.PRIMARY)!!
        assertEquals(2, primary.lines.size)

        val firstLine = primary.lines[0]
        assertEquals("你好世界", firstLine.text)
        assertEquals(1_000L, firstLine.startMs)
        assertEquals(4, firstLine.words.size)
        assertEquals(listOf("你", "好", "世", "界"), firstLine.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L, 2_200L, 3_000L), firstLine.words.map { it.startMs })
        assertEquals(listOf(1_500L, 2_200L, 3_000L, 3_500L), firstLine.words.map { it.endMs })

        val secondLine = primary.lines[1]
        assertEquals("测试", secondLine.text)
        assertEquals(4_000L, secondLine.startMs)
        assertEquals(2, secondLine.words.size)
        assertEquals(listOf(4_000L, 4_700L), secondLine.words.map { it.startMs })
    }

    @Test
    fun krcDetectedViaParseProviderPayload() {
        val krc = "[2000,1000]<0,400>歌<400,600>词"
        val document = parser.parseProvider(primary = krc, sourceName = "kugou")

        assertEquals("krc", document.format)
        val line = document.track(LyricsTrackRole.PRIMARY)!!.lines.single()
        assertEquals("歌词", line.text)
        assertEquals(2_000L, line.startMs)
        assertEquals(2, line.words.size)
    }

    @Test
    fun krcFileExtensionUsesKrcParser() {
        val krc = "[0,1000]<0,500>A<500,500>B"
        val document = parser.parse(krc.toByteArray(StandardCharsets.UTF_8), "test.krc")

        assertEquals("krc", document.format)
        assertEquals(listOf("A", "B"), document.track(LyricsTrackRole.PRIMARY)!!.lines.single().words.map { it.text })
    }

    @Test
    fun providerParsesRomanizationTrackAlignedToPrimary() {
        val document = parser.parseProvider(
            primary = "[00:01.00]さくら\n[00:05.00]ありがとう",
            translation = "[00:01.00]樱花\n[00:05.00]谢谢",
            romanization = "[00:01.00]sakura\n[00:05.00]arigatou",
            sourceName = "netease"
        )

        val roman = document.track(LyricsTrackRole.ROMANIZATION)!!
        assertEquals(2, roman.lines.size)
        assertEquals("sakura", roman.lines[0].text)
        assertEquals(1_000L, roman.lines[0].startMs)
        assertEquals("arigatou", roman.lines[1].text)
        assertEquals(5_000L, roman.lines[1].startMs)
    }

    @Test
    fun providerRomanizationWorksWithoutTranslation() {
        val document = parser.parseProvider(
            primary = "[00:01.00]夜に駆ける",
            romanization = "[00:01.00]yoru ni kakeru",
            sourceName = "netease"
        )

        assertNull(document.track(LyricsTrackRole.TRANSLATION))
        assertEquals("yoru ni kakeru", document.track(LyricsTrackRole.ROMANIZATION)!!.lines.single().text)
    }
}
