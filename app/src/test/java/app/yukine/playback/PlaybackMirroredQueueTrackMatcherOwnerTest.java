package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

import org.junit.runner.RunWith;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;
import java.lang.reflect.Proxy;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PlaybackMirroredQueueTrackMatcherOwnerTest {
    @Test
    public void delegatesPlayerMediaItemAndTrackToMatcher() {
        MediaItem mediaItem = new MediaItem.Builder().setUri("https://example.test/one.mp3").build();
        Track track = track();
        FakeMediaItemMatcher matcher = new FakeMediaItemMatcher(true);
        PlaybackMirroredQueueTrackMatcherOwner owner = new PlaybackMirroredQueueTrackMatcherOwner(
                index -> index == 2 ? mediaItem : null,
                matcher::matches
        );

        assertEquals(true, owner.matches(2, track));
        assertEquals(mediaItem, matcher.lastMediaItem);
        assertEquals(track, matcher.lastTrack);
    }

    @Test
    public void returnsFalseWhenMatcherDependenciesAreMissingOrPlayerCannotRead() {
        Track track = track();
        PlaybackMirroredQueueTrackMatcherOwner missingProvider =
                new PlaybackMirroredQueueTrackMatcherOwner(null, (mediaItem, itemTrack) -> true);
        PlaybackMirroredQueueTrackMatcherOwner missingMatcher =
                new PlaybackMirroredQueueTrackMatcherOwner(index -> null, null);
        PlaybackMirroredQueueTrackMatcherOwner throwingProvider =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        index -> {
                            throw new IndexOutOfBoundsException("missing");
                        },
                        (mediaItem, itemTrack) -> true
                );

        assertEquals(false, missingProvider.matches(0, track));
        assertEquals(false, missingMatcher.matches(0, track));
        assertEquals(false, throwingProvider.matches(0, track));
    }

    @Test
    public void mediaSourceProviderConstructorIsSafeWhenProviderIsMissing() {
        MediaItem mediaItem = new MediaItem.Builder().setUri("https://example.test/one.mp3").build();
        PlaybackMirroredQueueTrackMatcherOwner owner =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        () -> playerWithMediaItem(mediaItem),
                        null
                );

        assertEquals(false, owner.matches(0, track()));
    }

    @Test
    public void mediaSourceProviderConstructorUsesProviderOwnedCacheKeyRule() {
        Track track = track();
        MediaItem providerMediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null);
        MediaItem missingCacheKeyMediaItem = new MediaItem.Builder()
                .setMediaId(Long.toString(track.id))
                .setUri(track.contentUri)
                .build();
        PlaybackMediaSourceProvider mediaSourceProvider = mediaSourceProvider();
        PlaybackMirroredQueueTrackMatcherOwner matchingOwner =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        () -> playerWithMediaItem(providerMediaItem),
                        mediaSourceProvider
                );
        PlaybackMirroredQueueTrackMatcherOwner missingCacheKeyOwner =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        () -> playerWithMediaItem(missingCacheKeyMediaItem),
                        mediaSourceProvider
                );

        assertTrue(matchingOwner.matches(0, track));
        assertFalse(missingCacheKeyOwner.matches(0, track));
    }

    private static Player playerWithMediaItem(MediaItem mediaItem) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getMediaItemAt".equals(method.getName())) {
                        return mediaItem;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        return null;
    }

    private static Track track() {
        return new Track(
                11L,
                "Track",
                "Artist",
                "Album",
                1000L,
                Uri.parse("https://example.test/one.mp3"),
                "streaming:test:11"
        );
    }

    private static PlaybackMediaSourceProvider mediaSourceProvider() {
        Context context = RuntimeEnvironment.getApplication();
        return new PlaybackMediaSourceProvider(
                context,
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser()),
                new FakeStreamingPlaybackHeaderStore()
        );
    }

    private static final class FakeMediaItemMatcher {
        private final boolean matches;
        private MediaItem lastMediaItem;
        private Track lastTrack;

        private FakeMediaItemMatcher(boolean matches) {
            this.matches = matches;
        }

        public boolean matches(MediaItem mediaItem, Track track) {
            lastMediaItem = mediaItem;
            lastTrack = track;
            return matches;
        }
    }

    private static final class FakeStreamingDataPathParser implements StreamingDataPathParser {
        @Override
        public boolean isStreamingTrack(String dataPath) {
            return dataPath != null && dataPath.startsWith("streaming:");
        }

        @Override
        public String providerName(String dataPath) {
            if (dataPath == null || !dataPath.startsWith("streaming:")) {
                return null;
            }
            return dataPath.substring("streaming:".length()).split(":")[0];
        }

        @Override
        public String providerTrackId(String dataPath) {
            if (dataPath == null) {
                return "";
            }
            int index = dataPath.lastIndexOf(':');
            return index < 0 ? dataPath : dataPath.substring(index + 1);
        }
    }

    private static final class FakeStreamingPlaybackHeaderStore implements StreamingPlaybackHeaderStore {
        @Override
        public void register(String dataPath, Map<String, String> headers) {
        }

        @Override
        public Map<String, String> forDataPath(String dataPath) {
            return Collections.emptyMap();
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
