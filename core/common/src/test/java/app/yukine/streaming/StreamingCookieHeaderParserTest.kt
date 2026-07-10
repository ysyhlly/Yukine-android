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
    fun keepsHttpOnlyNetscapeCookieRecords() {
        val raw = """
            # Netscape HTTP Cookie File
            #HttpOnly_.qq.com	TRUE	/	FALSE	1782398224	qqmusic_key	http-only-key
            .qq.com	TRUE	/	FALSE	1782398224	uin	123456
        """.trimIndent()

        assertEquals(
            "qqmusic_key=http-only-key; uin=123456",
            StreamingCookieHeaderParser.normalize(raw)
        )
    }

    @Test
    fun keepsExistingCookieHeaderUsable() {
        val header = StreamingCookieHeaderParser.normalize("qqmusic_key=key-value; uin=123456")

        assertTrue(header.contains("qqmusic_key=key-value"))
        assertTrue(header.contains("uin=123456"))
    }

    @Test
    fun mergesRefreshedRequestCookieWithoutDroppingOlderFields() {
        assertEquals(
            "MUSIC_U=new-value; __csrf=csrf-value; os=pc",
            StreamingCookieHeaderParser.merge(
                "MUSIC_U=old-value; __csrf=csrf-value",
                "MUSIC_U=new-value; os=pc"
            )
        )
    }

    @Test
    fun mergesSetCookieHeadersWithoutPersistingResponseAttributes() {
        assertEquals(
            "MUSIC_U=new-value; __csrf=csrf-value",
            StreamingCookieHeaderParser.mergeSetCookieHeaders(
                "MUSIC_U=old-value; __csrf=csrf-value",
                listOf("MUSIC_U=new-value; Path=/; HttpOnly", "empty=; Max-Age=0")
            )
        )
    }
}
