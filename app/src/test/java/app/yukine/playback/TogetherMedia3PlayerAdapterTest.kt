package app.yukine.playback

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.together.TogetherQueueItem
import app.yukine.together.TogetherQueueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TogetherMedia3PlayerAdapterTest {
    @Test
    fun streamingProxyUrlDoesNotReplaceLogicalCloudIdentity() {
        val item = TogetherQueueItem(
            stableId = "streaming:luoxue:kw:123",
            title = "Ahead of Us",
            artist = "Artist",
            sourceUri = "https://cloud.example/audio",
            album = "Album",
            artworkUri = "https://img.example/album.jpg",
            durationMs = 123_000L,
            source = TogetherQueueSource.Streaming(
                provider = "luoxue",
                providerTrackId = "kw:123",
                quality = "lossless",
                durationMs = 123_000L,
                resolvedUrl = "https://cloud.example/audio",
                mimeType = "audio/flac",
                luoxueMusicInfoJson = """{"id":"kw_123","name":"Ahead of Us"}"""
            )
        )

        val track = TogetherMedia3PlayerAdapter.trackForStreamUrl(
            item,
            "http://127.0.0.1:7777/stream/0"
        )

        assertEquals("http://127.0.0.1:7777/stream/0", track.contentUri.toString())
        assertEquals("kw:123", StreamingDataPathMetadata.providerTrackId(track.dataPath))
        assertEquals("lossless", StreamingDataPathMetadata.quality(track.dataPath))
        assertEquals("audio/flac", StreamingDataPathMetadata.playbackMimeType(track.dataPath))
        assertEquals(
            """{"id":"kw_123","name":"Ahead of Us"}""",
            StreamingDataPathMetadata.luoxueMusicInfoJson(track.dataPath)
        )
        assertEquals(123_000L, track.durationMs)
        assertEquals("Album", track.album)
        assertEquals("https://img.example/album.jpg", track.albumArtUri.toString())
    }

    @Test
    fun localProxyUrlKeepsOriginalLocalSourceIdentity() {
        val item = TogetherQueueItem(
            stableId = "42",
            title = "Local",
            artist = "Artist",
            sourceUri = "content://media/external/audio/42",
            source = TogetherQueueSource.Local("content://media/external/audio/42")
        )

        val track = TogetherMedia3PlayerAdapter.trackForStreamUrl(
            item,
            "http://127.0.0.1:7777/file/0"
        )

        assertEquals("http://127.0.0.1:7777/file/0", track.contentUri.toString())
        assertEquals("content://media/external/audio/42", track.dataPath)
    }

    @Test
    fun queueAndUrlCountMismatchIsRejectedInsteadOfTruncatingPlaybackQueue() {
        val queue = listOf(
            TogetherQueueItem("1", "One", "A", "content://audio/1"),
            TogetherQueueItem("2", "Two", "B", "content://audio/2")
        )

        val tracks = TogetherMedia3PlayerAdapter.tracksForStreamUrls(
            queue,
            listOf("http://127.0.0.1:7777/file/0")
        )

        assertTrue(tracks.isEmpty())
    }

    @Test
    fun blankStreamUrlAbortsReplacementSoPlayerLengthMatchesRoomQueue() {
        val queue = listOf(
            TogetherQueueItem("1", "One", "A", "content://audio/1"),
            TogetherQueueItem("2", "Two", "B", "content://audio/2"),
            TogetherQueueItem("3", "Three", "C", "content://audio/3")
        )

        val tracks = TogetherMedia3PlayerAdapter.tracksForStreamUrls(
            queue,
            listOf(
                "http://127.0.0.1:7777/file/0",
                "",
                "http://127.0.0.1:7777/file/2"
            )
        )

        assertTrue(tracks.isEmpty())
    }

    @Test
    fun media3TrackIdMapsNumericAndNonNumericStableIdsForRemove() {
        assertEquals(42L, TogetherMedia3PlayerAdapter.media3TrackId("42"))
        val streamingId = TogetherMedia3PlayerAdapter.media3TrackId("streaming:qqmusic:song-mid")
        assertTrue(streamingId < 0L)
        assertEquals(
            streamingId,
            TogetherMedia3PlayerAdapter.media3TrackId("streaming:qqmusic:song-mid")
        )
        val track = TogetherMedia3PlayerAdapter.trackForStreamUrl(
            TogetherQueueItem(
                stableId = "streaming:qqmusic:song-mid",
                title = "Cloud",
                artist = "A",
                sourceUri = "https://example.invalid/a"
            ),
            "http://127.0.0.1:7777/stream/0"
        )
        assertEquals(streamingId, track.id)
    }

    @Test
    fun playableQueueItemsKeepTheirExactOrder() {
        val queue = listOf(
            TogetherQueueItem("2", "Two", "B", "content://audio/2"),
            TogetherQueueItem("1", "One", "A", "content://audio/1")
        )

        val tracks = TogetherMedia3PlayerAdapter.tracksForQueueItems(queue)

        assertEquals(listOf(2L, 1L), tracks.map { it.id })
        assertEquals(
            listOf("content://audio/2", "content://audio/1"),
            tracks.map { it.contentUri.toString() }
        )
    }

}
