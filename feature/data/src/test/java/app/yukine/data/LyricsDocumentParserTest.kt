package app.yukine.data

import app.yukine.model.LyricsTrackRole
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(line.words[0].endMs >= line.words[0].startMs)
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
}
