package app.yukine.playback

import android.net.Uri
import androidx.media3.common.MediaMetadata
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackMediaSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMediaSourceProviderTest {
    @Test
    fun playbackMediaItemForLocalTrackPreservesUriAndMetadataWithoutCacheKey() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/local.flac")
        val track = Track(7L, "Local", "Artist", "Album", 180_000L, uri, "/storage/emulated/0/Music/local.flac")
        val metadata = MediaMetadata.Builder()
            .setTitle("Local")
            .build()

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, metadata)

        assertEquals("7", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertNull(mediaItem.localConfiguration?.customCacheKey)
        assertEquals("Local", mediaItem.mediaMetadata.title.toString())
    }

    @Test
    fun playbackMediaItemForStreamingTrackPreservesResolvedUriAndStreamingCacheKeyRule() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, uri, "streaming:netease:42")

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null)

        assertEquals("42", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:42", "https://audio.example/current.flac")
        )
    }

    @Test
    fun streamingCacheKeyIncludesResolvedUrl() {
        assertNotEquals(
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/a.flac"),
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/b.flac")
        )
    }

    @Test
    fun mirroredQueueReuseRequiresResolvedUriToMatch() {
        assertFalse(
            PlaybackMediaSourceProvider.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/first.flac",
                "streaming:netease:42|url=https://audio.example/first.flac",
                42L,
                "https://audio.example/second.flac",
                "streaming:netease:42|url=https://audio.example/second.flac"
            )
        )
    }

    @Test
    fun mirroredQueueReuseAllowsSameResolvedMediaItem() {
        assertTrue(
            PlaybackMediaSourceProvider.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac",
                42L,
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac"
            )
        )
    }
}
