package app.yukine.data.room

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    @Test
    fun extendedSourceMetadataRoundTripsThroughTrackEntity() {
        val track = Track(
            2L,
            "中文标题",
            "表演者",
            "专辑",
            201_000L,
            Uri.parse("content://audio/2"),
            "/music/2.flac",
            0L,
            Uri.EMPTY,
            "",
            0,
            0,
            0,
            0,
            0f,
            0f,
            app.yukine.model.TrackIdentityTags.EMPTY,
            "专辑艺术家",
            "作曲者",
            "single",
            2024
        )

        val restored = TrackEntityMapper.track(TrackEntityMapper.entity(track, 1L))

        assertEquals("专辑艺术家", restored.albumArtist)
        assertEquals("作曲者", restored.composer)
        assertEquals("single", restored.releaseType)
        assertEquals(2024, restored.year)
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
