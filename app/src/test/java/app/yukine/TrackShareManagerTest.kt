package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackShareManagerTest {
    private val manager = TrackShareManager()

    @Test
    fun buildsStreamingSourceCardWithProviderLink() {
        val track = Track(
            1L,
            "晴天",
            "周杰伦",
            "叶惠美",
            240_000L,
            Uri.parse("https://cdn.example/song.mp3"),
            "streaming:netease:186016"
        )

        val card = manager.card(track)

        assertTrue(card.title.contains("晴天"))
        assertTrue(card.text.contains("Yukine 音源卡片"))
        assertTrue(card.text.contains("音源：网易云音乐"))
        assertTrue(card.text.contains("音源 ID：186016"))
        assertTrue(card.text.contains("https://music.163.com/song?id=186016"))
    }

    @Test
    fun buildsOneBotMusicPayloadForNeteaseCardShare() {
        val track = Track(
            4L,
            "Song",
            "Artist",
            "Album",
            240_000L,
            Uri.parse("https://cdn.example/song.mp3"),
            "streaming:netease:186016"
        )

        val payload = manager.musicSharePayload(track)

        assertEquals("https://music.163.com/song?id=186016", payload?.url)
        assertEquals("""{"type":"music","data":{"type":"163","id":"186016"}}""", payload?.oneBotJson)
    }

    @Test
    fun buildsLuoxueSourceCard() {
        val track = Track(
            2L,
            "LX Song",
            "LX Artist",
            "Streaming",
            200_000L,
            Uri.EMPTY,
            "streaming:lx:kw:12345"
        )

        val card = manager.card(track)

        assertTrue(card.text.contains("音源：LX/洛雪音源"))
        assertTrue(card.text.contains("音源 ID：kw:12345"))
        assertTrue(card.text.contains("lxmusic://music/song/kw:12345"))
    }

    @Test
    fun buildsLocalLibraryCard() {
        val track = Track(
            3L,
            "Local Song",
            "Local Artist",
            "Local Album",
            180_000L,
            Uri.EMPTY,
            "file:/music/local.mp3"
        )

        val card = manager.card(track)

        assertTrue(card.text.contains("来源：本地曲库"))
        assertTrue(card.text.contains("歌曲：Local Song"))
        assertTrue(card.text.contains("歌手：Local Artist"))
    }
}
