package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackPrecacheStateOwnerTest {
    @Test
    public void delegatesPrecacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(1L);
        Track upcomingTrack = track(2L);
        PlaybackRuntimeStateManager runtimeStateManager = playbackRuntimeStateManager();
        runtimeStateManager.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF);
        PlaybackQueueManager queueManager = playbackQueueManager(runtimeStateManager);
        queueManager.playQueue(Arrays.asList(track, upcomingTrack), 0, -1L);
        PlaybackQueueStateOwner queueStateOwner = new PlaybackQueueStateOwner(queueManager);
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/one.mp3");
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackPrecacheStateOwner owner = new PlaybackPrecacheStateOwner(
                queueStateOwner,
                () -> {
                    events.add("mediaItem");
                    return mediaItem;
                },
                diagnostics
        );

        assertSame(track, owner.currentTrack());
        assertSame(mediaItem, owner.currentPlayerMediaItem());
        assertTrackIds(Collections.singletonList(2L), owner.upcomingTracksForPrecache(4));
        assertSame(diagnostics, owner.streamingDiagnostics());
        assertEquals(
                java.util.Collections.singletonList("mediaItem"),
                events
        );
    }

    @Test
    public void returnsNullCurrentTrackWhenSupplierIsMissing() {
        PlaybackPrecacheStateOwner missingProviderOwner = new PlaybackPrecacheStateOwner(
                null,
                () -> null,
                new PlaybackStreamingDiagnostics()
        );
        PlaybackQueueStateOwner missingQueueManagerOwner = new PlaybackQueueStateOwner();
        PlaybackPrecacheStateOwner nullTrackOwner = new PlaybackPrecacheStateOwner(
                missingQueueManagerOwner,
                () -> null,
                new PlaybackStreamingDiagnostics()
        );

        assertNull(missingProviderOwner.currentTrack());
        assertNull(nullTrackOwner.currentTrack());
        assertEquals(Collections.emptyList(), missingProviderOwner.upcomingTracksForPrecache(3));
        assertEquals(Collections.emptyList(), nullTrackOwner.upcomingTracksForPrecache(3));
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("https://example.test/" + id + ".mp3"),
                "streaming:test:" + id
        );
    }

    private static void assertTrackIds(List<Long> expectedIds, List<Track> tracks) {
        List<Long> actualIds = new ArrayList<>();
        for (Track track : tracks) {
            actualIds.add(track.id);
        }
        assertEquals(expectedIds, actualIds);
    }

    private static PlaybackQueueManager playbackQueueManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                runtimeStateManager,
                new PlaybackTransitionStateManager(),
                new Random(1L)
        );
    }

    private static PlaybackRuntimeStateManager playbackRuntimeStateManager() {
        return new PlaybackRuntimeStateManager(
                new PlaybackRuntimeStateManager.StateProvider() {
                    @Override
                    public ExoPlayer player() {
                        return null;
                    }

                    @Override
                    public boolean playerMirrorsQueue() {
                        return false;
                    }

                    @Override
                    public Track currentTrack() {
                        return null;
                    }
                }
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

    private static final class NoopQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class NoopMirroredQueuePlayer
            implements PlaybackQueueManager.MirroredQueuePlayer {
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
