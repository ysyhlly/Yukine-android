package app.yukine.playback;

import org.junit.Test;

import android.net.Uri;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PlaybackShutdownLifecycleResourcesOwnerTest {
    @Test
    public void delegatesLifecyclePersistenceStateAndNotification() {
        List<String> calls = new ArrayList<>();
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                () -> calls.add("position"),
                new FakeQueueLifecycleStore(calls),
                new FakePlaybackStateProvider(true, false),
                () -> true,
                () -> calls.add("notification")
        );

        owner.persistPlaybackPosition();
        owner.persistPlaybackQueue();
        owner.savePlaybackResumeRequested(owner.isPlaying() || owner.isPreparing());
        assertTrue(owner.hasNotificationWorthyState());
        owner.publishPlaybackNotification();

        assertEquals(
                Arrays.asList(
                        "position",
                        "queue",
                        "resume:true",
                        "notification"
                ),
                calls
        );
    }

    @Test
    public void reportsPreparingPlaybackForResumeRequest() {
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                null,
                null,
                new FakePlaybackStateProvider(false, true),
                null,
                null
        );

        assertFalse(owner.isPlaying());
        assertTrue(owner.isPreparing());
    }

    @Test
    public void toleratesMissingOptionalOwners() {
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                null,
                null,
                null,
                null,
                null
        );

        owner.persistPlaybackPosition();
        owner.persistPlaybackQueue();
        owner.savePlaybackResumeRequested(true);
        owner.publishPlaybackNotification();
        assertFalse(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertFalse(owner.hasNotificationWorthyState());
    }

    @Test
    public void playbackStateProviderFromPlaybackStateDelegatesPlaybackAndPreparingState() {
        PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider provider =
                PlaybackShutdownLifecycleResourcesOwner.playbackStateProviderFromPlaybackState(
                        () -> true,
                        () -> true
                );

        assertTrue(provider.isPlaying());
        assertTrue(provider.isPreparing());
    }

    @Test
    public void playbackStateProviderFromPlaybackStateDefaultsMissingSuppliersToInactive() {
        PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider provider =
                PlaybackShutdownLifecycleResourcesOwner.playbackStateProviderFromPlaybackState(null, null);

        assertFalse(provider.isPlaying());
        assertFalse(provider.isPreparing());
    }

    @Test
    public void queueLifecycleStorePersistsQueueManagerAndResumeFlag() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager queueManager = queueManager(store);
        queueManager.playQueue(Collections.singletonList(track(42L)), 0, -1L);
        store.resetCounts();
        PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore lifecycleStore =
                PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStore(queueManager, store);

        lifecycleStore.persistQueueState();
        lifecycleStore.savePlaybackResumeRequested(true);
        lifecycleStore.savePlaybackResumeRequested(false);

        assertEquals(1, store.saveCalls);
        assertEquals(Collections.singletonList(42L), trackIds(store.lastSavedTracks));
        assertEquals(0, store.lastSavedIndex);
        assertEquals(2, store.resumeCalls);
        assertFalse(store.lastResumeRequested);
    }

    @Test
    public void queueLifecycleStoreToleratesMissingQueueDependencies() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore missingManager =
                PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStore(null, store);
        PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore missingStore =
                PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStore(null, null);

        missingManager.persistQueueState();
        missingManager.savePlaybackResumeRequested(true);
        missingStore.persistQueueState();
        missingStore.savePlaybackResumeRequested(false);

        assertEquals(0, store.saveCalls);
        assertEquals(1, store.resumeCalls);
        assertTrue(store.lastResumeRequested);
    }

    private static PlaybackQueueManager queueManager(FakeQueueStore store) {
        return new PlaybackQueueManager(
                store,
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeQueueLifecycleStore
            implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
        private final List<String> calls;

        FakeQueueLifecycleStore(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void persistQueueState() {
            calls.add("queue");
        }

        @Override
        public void savePlaybackResumeRequested(boolean requested) {
            calls.add("resume:" + requested);
        }
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private int saveCalls;
        private int resumeCalls;
        private boolean lastResumeRequested;
        private List<Track> lastSavedTracks = Collections.emptyList();
        private int lastSavedIndex = -1;

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
            saveCalls++;
            lastSavedTracks = new ArrayList<>(tracks);
            lastSavedIndex = currentIndex;
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
            resumeCalls++;
            lastResumeRequested = requested;
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

        private void resetCounts() {
            saveCalls = 0;
            resumeCalls = 0;
            lastResumeRequested = false;
            lastSavedTracks = Collections.emptyList();
            lastSavedIndex = -1;
        }
    }

    private static List<Long> trackIds(List<Track> tracks) {
        List<Long> ids = new ArrayList<>();
        for (Track track : tracks) {
            ids.add(track.id);
        }
        return ids;
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

    private static final class FakePlaybackStateProvider
            implements PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider {
        private final boolean playing;
        private final boolean preparing;

        FakePlaybackStateProvider(boolean playing, boolean preparing) {
            this.playing = playing;
            this.preparing = preparing;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isPreparing() {
            return preparing;
        }
    }
}
