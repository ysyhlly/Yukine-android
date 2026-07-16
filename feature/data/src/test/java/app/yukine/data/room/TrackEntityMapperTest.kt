package app.yukine.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrackEntityMapperTest {
    @Test
    fun preserveAudioSpecsKeepsParsedFieldsForUnchangedMedia() {
        val existing = entity(codec = "flac", bitrate = 1200, updatedAt = 10)
        val incoming = entity(codec = "", bitrate = 0, updatedAt = 20)

        val merged = TrackEntityMapper.preserveAudioSpecs(incoming, existing)

        assertEquals("flac", merged.codec)
        assertEquals(1200, merged.bitrateKbps)
        assertEquals(20, merged.updatedAt)
        assertEquals(incoming.title, merged.title)
    }

    @Test
    fun preserveAudioSpecsDoesNotReuseFieldsAfterContentChanges() {
        val existing = entity(codec = "flac", bitrate = 1200, updatedAt = 10)
        val incoming = entity(codec = "", bitrate = 0, updatedAt = 20, contentUri = "content://audio/2")

        assertSame(incoming, TrackEntityMapper.preserveAudioSpecs(incoming, existing))
    }

    private fun entity(
        codec: String,
        bitrate: Int,
        updatedAt: Long,
        contentUri: String = "content://audio/1"
    ) = TrackEntity(
        id = 1,
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        contentUri = contentUri,
        dataPath = "/music/song.flac",
        albumId = 1,
        albumArtUri = "",
        codec = codec,
        bitrateKbps = bitrate,
        sampleRateHz = if (codec.isEmpty()) 0 else 96_000,
        bitsPerSample = if (codec.isEmpty()) 0 else 24,
        channelCount = if (codec.isEmpty()) 0 else 2,
        replayGainTrackDb = 0.0,
        replayGainAlbumDb = 0.0,
        updatedAt = updatedAt
    )
}
