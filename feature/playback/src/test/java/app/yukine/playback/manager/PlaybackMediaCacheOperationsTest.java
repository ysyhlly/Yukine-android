package app.yukine.playback.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

@OptIn(markerClass = UnstableApi.class)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class PlaybackMediaCacheOperationsTest {
    @Test
    public void contentRangeTotalBytesAreParsedForSegmentedPrecacheProbe() {
        assertEquals(1234567L, PlaybackMediaSourceProviderCacheOperations.totalBytesFromContentRange(
                "bytes 524288-524288/1234567"
        ));
        assertEquals(-1L, PlaybackMediaSourceProviderCacheOperations.totalBytesFromContentRange("bytes 0-0/*"));
        assertEquals(-1L, PlaybackMediaSourceProviderCacheOperations.totalBytesFromContentRange(""));
    }

    @Test
    public void fromMediaSourceProviderRequiresProvider() {
        try {
            PlaybackMediaCacheOperations.fromMediaSourceProvider(null);
        } catch (NullPointerException expected) {
            assertEquals("mediaSourceProvider", expected.getMessage());
            return;
        }

        throw new AssertionError("Expected media-source provider to be required");
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
        assertNull(operations.cacheKeyForPrecache(local));
    }

    @Test
    public void providerBackedOperationsProbeRangeWithProviderHeaders() throws Exception {
        AtomicReference<String> rawRequest = new AtomicReference<>("");
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> requestTask = executor.submit(() -> {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(),
                         StandardCharsets.ISO_8859_1
                 ))) {
                StringBuilder request = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    request.append(line).append('\n');
                }
                rawRequest.set(request.toString());
                byte[] response = (
                        "HTTP/1.1 206 Partial Content\r\n"
                                + "Content-Range: bytes 512-1023/4096\r\n"
                                + "Content-Length: 0\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                ).getBytes(StandardCharsets.ISO_8859_1);
                socket.getOutputStream().write(response);
                socket.getOutputStream().flush();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        Context context = RuntimeEnvironment.getApplication();
        MusicLibraryRepository repository =
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser());
        long sourceId = repository.saveWebDavSource(
                "NAS",
                "https://dav.example",
                "alice",
                "secret",
                "music"
        );
        Map<String, String> streamingHeaders = Collections.singletonMap("Cookie", "token=abc");
        PlaybackMediaSourceProvider provider = new PlaybackMediaSourceProvider(
                context,
                repository,
                new FakeStreamingPlaybackHeaderStore(streamingHeaders)
        );
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(provider);
        Track webDav = track(
                9L,
                "http://127.0.0.1:" + server.getLocalPort() + "/music/webdav.flac",
                "webdav:" + sourceId + ":/music/webdav.flac"
        );
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                "alice:secret".getBytes(StandardCharsets.UTF_8)
        );

        try {
            assertEquals(webDav.dataPath, operations.cacheKeyForPrecache(webDav));
            assertEquals(4096L, operations.probeSegmentedPrecacheContentLength(
                    webDav,
                    webDav.dataPath,
                    512L,
                    512L
            ));
            requestTask.get(5, TimeUnit.SECONDS);
            String request = rawRequest.get();
            assertTrue(request.contains("Range: bytes=512-1023"));
            assertTrue(request.contains("Cookie: " + streamingHeaders.get("Cookie")));
            assertTrue(request.contains("Authorization: " + expectedAuth));
        } finally {
            provider.releaseAudioCache();
            server.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void providerBackedOperationsDoNotPrecacheUnresolvedStreamingPlaceholders() {
        PlaybackMediaSourceProvider provider =
                mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(Collections.emptyMap()));
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(provider);
        Track unresolved = track(42L, "", "streaming:test:42");

        try {
            assertEquals("streaming:test:42", provider.mediaCacheKeyForTrack(unresolved));
            assertNull(operations.cacheKeyForPrecache(unresolved));
        } finally {
            provider.releaseAudioCache();
        }
    }

    @Test
    public void providerBackedOperationsOwnResolvedUriReuseAndCacheMissReads() {
        PlaybackMediaSourceProvider provider =
                mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(Collections.emptyMap()));
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(provider);
        Track current = track(42L, "https://example.test/audio.flac", "streaming:test:42");
        Track sameResolvedUri = track(43L, "https://example.test/audio.flac", "streaming:test:43");
        Track differentResolvedUri = track(44L, "https://example.test/other.flac", "streaming:test:44");

        try {
            assertTrue(operations.tracksShareResolvedUriForReuse(current, sameResolvedUri));
            assertFalse(operations.tracksShareResolvedUriForReuse(current, differentResolvedUri));
            assertEquals(0L, operations.cachedBytesInRange(
                    "streaming:test:42|url=https://example.test/audio.flac",
                    0L,
                    512L
            ));
            assertEquals(-1L, operations.contentLengthForCacheKey(
                    "streaming:test:42|url=https://example.test/audio.flac"
            ));
        } finally {
            provider.releaseAudioCache();
        }
    }

    @Test
    public void providerBackedOperationsReadCommittedCacheBytesWithSafeOffset() throws Exception {
        PlaybackMediaSourceProvider provider =
                mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(Collections.emptyMap()));
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(provider);
        String cacheKey = "streaming:test:45|url=https://example.test/cached.flac";

        try {
            CacheWriter cacheWriter = new CacheWriter(
                    new CacheDataSource(
                            provider.audioCache(),
                            new ByteArrayDataSource(new byte[]{1, 2, 3, 4})
                    ),
                    new DataSpec.Builder()
                            .setUri(Uri.parse("https://example.test/cached.flac"))
                            .setKey(cacheKey)
                            .setLength(4L)
                            .build(),
                    new byte[1024],
                    null
            );

            cacheWriter.cache();

            assertEquals(4L, operations.cachedBytesInRange(cacheKey, -128L, 512L));
            assertEquals(0L, operations.cachedBytesInRange(cacheKey, 0L, 0L));
        } finally {
            provider.releaseAudioCache();
        }
    }

    @Test
    public void providerBackedOperationsOwnAudioCacheRelease() {
        PlaybackMediaSourceProvider provider =
                mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(Collections.emptyMap()));
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(provider);

        operations.cacheDataSourceForTrack(track(46L));
        operations.releaseAudioCache();
        operations.releaseAudioCache();
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
