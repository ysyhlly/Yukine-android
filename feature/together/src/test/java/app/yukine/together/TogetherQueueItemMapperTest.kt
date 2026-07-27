package app.yukine.together

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import java.nio.charset.StandardCharsets
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class TogetherQueueItemMapperTest {
    @Test
    fun currentQueueKeepsOrderAndRecognizesStreamingMetadata() {
        val local = Track(1L, "Local", "Artist", "", 1_000L, null, "C:\\music\\one.flac")
        val streaming = Track(
            2L,
            "Cloud",
            "Artist",
            "Cloud Album",
            2_000L,
            null,
            "streaming:qqmusic:song-mid?quality=lossless",
            22L,
            Uri.parse("https://img.example/cloud.jpg")
        )

        val mapped = TogetherQueueItemMapper.fromTracks(listOf(local, streaming))

        assertEquals(
            listOf("1", "streaming:qqmusic:song-mid"),
            mapped.map(TogetherQueueItem::stableId)
        )
        val source = mapped[1].source as TogetherQueueSource.Streaming
        assertEquals("qqmusic", source.provider)
        assertEquals("song-mid", source.providerTrackId)
        assertEquals("lossless", source.quality)
        assertEquals("Cloud Album", mapped[1].album)
        assertEquals("https://img.example/cloud.jpg", mapped[1].artworkUri)
        assertEquals(2_000L, mapped[1].durationMs)
        assertTrue(mapped[1].shareable)
    }

    @Test
    fun streamingQueueItemRetainsPrivateLuoxueMetadataAndResolvedUrl() {
        val musicInfo = """{"id":"kw_123","name":"Ahead of Us"}"""
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(musicInfo.toByteArray(StandardCharsets.UTF_8))
        val track = Track(
            3L,
            "Ahead of Us",
            "Artist",
            "",
            3_000L,
            Uri.parse("https://audio.example/ahead.flac"),
            "streaming:luoxue:kw:123?quality=lossless&lxmi=$encoded&playbackMime=audio%2Fflac"
        )

        val mapped = TogetherQueueItemMapper.fromTrack(track)
        val source = mapped.source as TogetherQueueSource.Streaming

        assertEquals("https://audio.example/ahead.flac", source.resolvedUrl)
        assertEquals("audio/flac", source.mimeType)
        val restored = JSONObject(source.luoxueMusicInfoJson.orEmpty())
        assertEquals("kw_123", restored.getString("id"))
        assertEquals("Ahead of Us", restored.getString("name"))
    }

    @Test
    fun localContentUriWinsOverStreamingIdentityMetadata() {
        val track = Track(
            4L,
            "Downloaded",
            "Artist",
            "",
            4_000L,
            Uri.parse("content://media/external/audio/media/4"),
            "streaming:luoxue:kw:4"
        )

        val mapped = TogetherQueueItemMapper.fromTrack(track)

        assertEquals(
            "content://media/external/audio/media/4",
            (mapped.source as TogetherQueueSource.Local).uri
        )
        assertTrue(mapped.shareable)
    }

    @Test
    fun localLibraryStableIdMatchesMedia3TrackIdForRemoveAndRetain() {
        val track = Track(
            42L,
            "Local",
            "Artist",
            "",
            1_000L,
            Uri.parse("content://media/external/audio/media/42"),
            "content://media/external/audio/media/42"
        )

        val mapped = TogetherQueueItemMapper.fromTrack(track)

        assertEquals("42", mapped.stableId)
        val retained = retainTracksForTogetherQueue(listOf(track), listOf(mapped))
        assertEquals(1, retained.size)
        assertTrue(retained.single() === track)
    }

    @Test
    fun streamingStableIdIsCanonicalAndRetainsViaMedia3SyntheticId() {
        val stableId = TogetherStableIds.streaming("qqmusic", "song-mid")
        val media3Id = TogetherStableIds.media3TrackId(stableId)
        val track = Track(
            media3Id,
            "Cloud",
            "Artist",
            "",
            2_000L,
            Uri.parse("https://audio.example/song"),
            "streaming:qqmusic:song-mid?quality=lossless"
        )
        val item = TogetherQueueItemMapper.fromTrack(track)

        assertEquals(stableId, item.stableId)
        val retained = retainTracksForTogetherQueue(listOf(track), listOf(item))
        assertEquals(1, retained.size)
        assertTrue(retained.single() === track)
    }
}
