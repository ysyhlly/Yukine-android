package app.yukine.playback.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import org.junit.Test;

import java.util.Collections;

import app.yukine.model.Track;

public final class PlaybackMediaCacheOperationsTest {
    @Test
    public void nullMediaSourceProviderReturnsSafeCacheNoops() {
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(null);
        Track track = track(1L);

        assertFalse(operations.tracksShareResolvedUriForReuse(track, track));
        assertEquals(-1L, operations.contentLengthForCacheKey("cache-key"));
        assertNull(operations.cacheKeyForPrecache(track));
        assertEquals(Collections.emptyMap(), operations.headersForTrack(track));
        assertEquals(0L, operations.cachedBytesInRange("cache-key", 0L, 512L));
        assertEquals(0L, operations.cachedBytesInRange("", 0L, 512L));
        assertEquals(0L, operations.cachedBytesInRange("cache-key", 0L, 0L));
        assertFalse(operations.mediaItemMatchesTrackForReuse(
                new MediaItem.Builder().setUri(track.contentUri).build(),
                track
        ));
    }

    @Test
    public void nullMediaSourceProviderRejectsCacheDataSourceCreation() {
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(null);

        try {
            operations.cacheDataSourceForTrack(track(2L));
        } catch (IllegalStateException expected) {
            assertEquals("Media cache operations are unavailable", expected.getMessage());
            return;
        }

        throw new AssertionError("Expected cacheDataSourceForTrack to reject a null provider");
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180_000L,
                Uri.parse("https://example.com/audio-" + id + ".mp3"),
                "streaming:test:" + id
        );
    }
}
