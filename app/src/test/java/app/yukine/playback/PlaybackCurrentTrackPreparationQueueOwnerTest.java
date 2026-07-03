package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackPositionManager;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackCurrentTrackPreparationQueueOwnerTest {
    @Test
    public void delegatesQueuePreparationToPlaybackQueueManager() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager[] queueManagerRef = new PlaybackQueueManager[1];
        PlaybackPositionManager positionManager = new PlaybackPositionManager(
                store,
                new PlaybackPositionManager.StateProvider() {
                    @Override
                    public Track currentTrack() {
                        return queueManagerRef[0] == null
                                ? null
                                : queueManagerRef[0].queueStateSnapshot().getCurrentTrack();
                    }

                    @Override
                    public long positionMs() {
                        return 0L;
                    }
                }
        );
        PlaybackQueueManager queueManager = queueManager(store, positionManager);
        queueManagerRef[0] = queueManager;
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> Collections.singletonList(null)
                );
        Track currentTrack = track(7L);
        Track replacement = track(7L);
        Track nextTrack = track(8L);
        queueManager.playQueue(Arrays.asList(currentTrack, nextTrack), 0, 3200L);

        owner.replaceCurrentQueueTrack(replacement);
        long positionMs = positionManager.restoredPositionFor(replacement);
        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();
        owner.consumeRestoredPositionAfterPrepare(4300L);

        assertEquals(3200L, positionMs);
        assertSame(replacement, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
        assertEquals(0L, positionManager.restoredPositionFor(replacement));
        assertEquals(1, store.savePlaybackPositionCalls);
    }

    @Test
    public void ignoresMissingPlaybackQueueManager() {
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                null,
                tracks -> {
                    throw new AssertionError("media sources should not be requested without a queue");
                }
        );

        owner.replaceCurrentQueueTrack(track(8L));

        assertEquals(null, owner.queuePreparationForNewPlayer().currentTrack());
        owner.consumeRestoredPositionAfterPrepare(5100L);
    }

    @Test
    public void missingQueueManagerAndMediaResolverSkipsQueueActions() {
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(null, null);

        owner.replaceCurrentQueueTrack(track(10L));

        assertEquals(null, owner.queuePreparationForNewPlayer().currentTrack());
        owner.consumeRestoredPositionAfterPrepare(7100L);
    }

    @Test
    public void mediaSourceProviderFactoryFallsBackWhenProviderIsMissing() {
        PlaybackCurrentTrackPreparationQueueOwner owner =
                PlaybackCurrentTrackPreparationQueueOwner.fromMediaSourceProvider(
                        null,
                        null,
                        track -> null
                );

        assertEquals(null, owner.queuePreparationForNewPlayer().mirroredQueueMediaSources());
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            PlaybackPositionManager positionManager
    ) {
        return new PlaybackQueueManager(
                store,
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                positionManager,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null,
                new Random(1L)
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                10000L,
                Uri.parse("content://test/" + id),
                "/music/" + id
        );
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private int savePlaybackPositionCalls;

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
            savePlaybackPositionCalls++;
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
