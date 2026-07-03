package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import org.junit.Test;

import java.lang.reflect.Proxy;

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
}
