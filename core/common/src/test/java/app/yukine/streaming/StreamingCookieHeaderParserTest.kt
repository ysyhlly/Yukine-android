package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingCookieHeaderParserTest {
    @Test
    fun convertsNetscapeCookieFileToHttpCookieHeader() {
        val raw = """
            # Netscape HTTP Cookie File
            .qq.com	TRUE	/	FALSE	1782398224	qqmusic_key	key-value
            .qq.com	TRUE	/	FALSE	1782398224	uin	123456
            .qq.com	TRUE	/	FALSE	1782398224	psrf_qqaccess_token	token-value
        """.trimIndent()

        val header = StreamingCookieHeaderParser.normalize(raw)

        assertEquals(
            "qqmusic_key=key-value; uin=123456; psrf_qqaccess_token=token-value",
            header
        )
    }

    @Test
    fun keepsExistingCookieHeaderUsable() {
        val header = StreamingCookieHeaderParser.normalize("qqmusic_key=key-value; uin=123456")

        assertTrue(header.contains("qqmusic_key=key-value"))
        assertTrue(header.contains("uin=123456"))
    }
}
