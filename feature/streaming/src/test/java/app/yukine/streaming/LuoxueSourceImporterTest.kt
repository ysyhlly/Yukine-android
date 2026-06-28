package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoxueSourceImporterTest {
    @Test
    fun parsesCommonLuoxueCustomSourceScript() {
        val script = """
            const sourceInfo = {
              name: '测试 LX 源',
              version: '1.0.2',
              author: 'Yukine'
            }
            globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited, {
              sources: {
                kw: { name: '酷我' },
                kg: { name: '酷狗' },
                tx: { name: 'QQ' }
              }
            })
        """.trimIndent()

        val sources = LuoxueSourceImporter.parseMany(script, "content://source/test.js")

        assertEquals(1, sources.size)
        assertEquals("测试 LX 源", sources.single().name)
        assertEquals("1.0.2", sources.single().version)
        assertEquals("Yukine", sources.single().author)
        assertTrue(sources.single().sourceKinds.contains("kw"))
        assertTrue(sources.single().sourceKinds.contains("kg"))
        assertTrue(sources.single().sourceKinds.contains("tx"))
    }

    @Test
    fun parsesMultipleScriptsFromOneText() {
        val text = """
            const sourceInfo = { name: '源一', version: '1' }
            globalThis.lx.send(EVENT_NAMES.inited, { sources: { kw: {} } })

            const sourceInfo = { name: '源二', version: '2' }
            globalThis.lx.send(EVENT_NAMES.inited, { sources: { kg: {} } })
        """.trimIndent()

        val sources = LuoxueSourceImporter.parseMany(text)

        assertEquals(listOf("源一", "源二"), sources.map { it.name })
    }

    @Test
    fun extractsMultipleNetworkUrls() {
        val urls = LuoxueSourceImporter.urlLines(
            """
            https://example.com/source-a.js
            hello
            https://example.com/source-b.js
            https://example.com/source-a.js
            """.trimIndent()
        )

        assertEquals(listOf("https://example.com/source-a.js", "https://example.com/source-b.js"), urls)
    }
}
