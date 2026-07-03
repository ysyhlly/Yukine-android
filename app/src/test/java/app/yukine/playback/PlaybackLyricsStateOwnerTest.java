package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackLyricsStateOwnerTest {
    @Test
    public void delegatesLyricsStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(13L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:13");
        PlaybackLyricsStateOwner owner = new PlaybackLyricsStateOwner(
                () -> {
                    events.add("visible");
                    return true;
                },
                new PlaybackLyricsStateOwner.PlaybackStateProvider() {
                    @Override
                    public Track currentTrack() {
                        events.add("track");
                        return track;
                    }

                    @Override
                    public boolean isPlaying() {
                        events.add("playing");
                        return false;
                    }

                    @Override
                    public boolean isPreparing() {
                        events.add("preparing");
                        return true;
                    }
                }
        );

        assertTrue(owner.isAppVisible());
        assertSame(track, owner.currentTrack());
        assertFalse(owner.isPlaying());
        assertTrue(owner.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "visible",
                        "track",
                        "playing",
                        "preparing"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateDelegatesPlaybackSuppliers() {
        List<String> events = new ArrayList<>();
        Track track = new Track(14L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:14");
        PlaybackQueueManager queueManager = queueManager();
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        PlaybackQueueStateOwner queueStateOwner = new PlaybackQueueStateOwner(queueManager);
        PlaybackLyricsStateOwner.PlaybackStateProvider provider =
                PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(
                        queueStateOwner,
                        () -> {
                            events.add("playing");
                            return false;
                        },
                        () -> {
                            events.add("preparing");
                            return true;
                        }
                );

        assertSame(track, provider.currentTrack());
        assertFalse(provider.isPlaying());
        assertTrue(provider.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "playing",
                        "preparing"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateReturnsInactiveForMissingSuppliers() {
        PlaybackLyricsStateOwner.PlaybackStateProvider provider =
                PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(null, null, null);

        assertNull(provider.currentTrack());
        assertFalse(provider.isPlaying());
        assertFalse(provider.isPreparing());
    }

    private static PlaybackQueueManager queueManager() {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
        }

        @Override
        public boolean loadPlaybackRestoreEnabled() {
            return true;
        }

        @Override
        public void savePlaybackRestoreEnabled(boolean enabled) {
        }

        @Override
        public long loadPlaybackPositionTrackId() {
            return -1L;
        }

        @Override
        public long loadPlaybackPositionMs() {
            return 0L;
        }

        @Override
        public void savePlaybackPosition(long trackId, long positionMs) {
        }
    }

    private static final class NoopQueuePlaybackActions implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class NoopStreamingRestoreProvider implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class NoopMirroredQueuePlayer implements PlaybackQueueManager.MirroredQueuePlayer {
        @Override
        public boolean matchesCurrentQueue() {
            return false;
        }

        @Override
        public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
            return false;
        }
    }
}
