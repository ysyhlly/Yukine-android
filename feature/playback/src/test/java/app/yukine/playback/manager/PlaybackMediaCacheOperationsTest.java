package app.yukine.playback.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
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

    @Test
    public void providerBackedOperationsOnlyPrecacheHttpTracks() {
        Map<String, String> headers = Collections.singletonMap("Cookie", "token=abc");
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(
                        mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(headers))
                );
        Track streaming = track(42L, "https://example.test/audio.flac", "streaming:test:42");
        Track local = track(7L, "content://media/audio/7", "/music/local.flac");

        assertEquals(
                "streaming:test:42|url=https://example.test/audio.flac",
                operations.cacheKeyForPrecache(streaming)
        );
        assertEquals(headers, operations.headersForTrack(streaming));
        assertNull(operations.cacheKeyForPrecache(local));
    }

    private static Track track(long id) {
        return track(id, "https://example.com/audio-" + id + ".mp3", "streaming:test:" + id);
    }

    private static Track track(long id, String uri, String dataPath) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180_000L,
                Uri.parse(uri),
                dataPath
        );
    }

    private static PlaybackMediaSourceProvider mediaSourceProvider(
            StreamingPlaybackHeaderStore headerStore
    ) {
        Context context = RuntimeEnvironment.getApplication();
        return new PlaybackMediaSourceProvider(
                context,
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser()),
                headerStore
        );
    }

    private static final class FakeStreamingDataPathParser implements StreamingDataPathParser {
        @Override
        public boolean isStreamingTrack(String dataPath) {
            return dataPath != null && dataPath.startsWith("streaming:");
        }

        @Override
        public String providerName(String dataPath) {
            return "test";
        }

        @Override
        public String providerTrackId(String dataPath) {
            return dataPath == null ? "" : dataPath.substring(dataPath.lastIndexOf(':') + 1);
        }
    }

    private static final class FakeStreamingPlaybackHeaderStore implements StreamingPlaybackHeaderStore {
        private final Map<String, String> headers;

        private FakeStreamingPlaybackHeaderStore(Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public void register(String dataPath, Map<String, String> headers) {
        }

        @Override
        public Map<String, String> forDataPath(String dataPath) {
            return headers;
        }

        @Override
        public boolean restoreForDataPath(String dataPath) {
            return false;
        }

        @Override
        public Track restoredTrackFor(Track track) {
            return null;
        }
    }
}
