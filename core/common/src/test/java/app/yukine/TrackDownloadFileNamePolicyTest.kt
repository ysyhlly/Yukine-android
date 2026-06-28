package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackDownloadFileNamePolicyTest {
    @Test
    fun audioFileNameUsesArtistDashTitle() {
        val track = track(title = "春を告げる", artist = "yama")

        assertEquals("yama - 春を告げる.flac", TrackDownloadFileNamePolicy.audioFileName(track, "flac"))
    }

    @Test
    fun audioFileNameSkipsUnknownArtist() {
        val track = track(title = "花がら", artist = "未知艺人")

        assertEquals("花がら.mp3", TrackDownloadFileNamePolicy.audioFileName(track, "mp3"))
    }

    @Test
    fun audioFileNameSkipsUnknownSingerAlias() {
        val track = track(title = "花がら", artist = "未知歌手")

        assertEquals("花がら.mp3", TrackDownloadFileNamePolicy.audioFileName(track, "mp3"))
    }

    @Test
    fun audioFileNameDeduplicatesSameArtistAndTitle() {
        val track = track(title = "Aimer", artist = "Aimer")

        assertEquals("Aimer.m4a", TrackDownloadFileNamePolicy.audioFileName(track, "m4a"))
    }

    @Test
    fun audioFileNameSanitizesIllegalCharactersAndWhitespace() {
        val track = track(title = "  Test:/Song?  ", artist = "Artist|Name\n")
        val fileName = TrackDownloadFileNamePolicy.audioFileName(track, "MP3")

        assertEquals("Artist Name - Test Song.mp3", fileName)
        assertFalse(fileName.any { it in "\\/:*?\"<>|\r\n\t" })
    }

    @Test
    fun artworkFileNameUsesSameBaseName() {
        val track = track(title = "Song", artist = "Artist")

        assertEquals("Artist - Song - cover.jpg", TrackDownloadFileNamePolicy.artworkFileName(track, "jpg"))
    }

    @Test
    fun audioFileNameFallsBackToTrackIdWhenMetadataIsUnknown() {
        val track = track(title = "未知歌曲", artist = "unknown", id = 99L)

        assertEquals("Yukine Track 99.mp3", TrackDownloadFileNamePolicy.audioFileName(track, ""))
    }

    private fun track(
        title: String,
        artist: String,
        id: Long = 42L
    ): Track =
        Track(
            id,
            title,
            artist,
            "Album",
            180_000L,
            Uri.parse("https://example.test/song.mp3"),
            "streaming:netease:42"
        )
}
