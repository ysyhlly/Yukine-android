package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class PlaybackQueueNavigationOwnerTest {
    @Test
    public void delegatesNavigationToPlaybackQueueManager() {
        FakeQueuePlaybackActions actions = new FakeQueuePlaybackActions();
        PlaybackQueueManager queueManager =
                queueManager(new FakeQueueStore(), actions, new FakeMirroredQueuePlayer(false, true));
        queueManager.playQueue(Arrays.asList(track(1L), track(2L)), 0, -1L);
        actions.events.clear();
        PlaybackQueueNavigationOwner owner = owner(queueManager, new ArrayList<>());

        owner.playFirstQueuedTrack();
        owner.skipToNextImmediately();
        owner.skipToPrevious();

        assertEquals(
                Arrays.asList("prepare:true", "prepare:true", "prepare:true"),
                actions.events
        );
        assertEquals(0, queueManager.queueStateSnapshot().getCurrentIndex());
    }

    @Test
    public void notifiesWhenMirroredQueueIsReused() {
        FakeMirroredQueuePlayer mirroredQueuePlayer = new FakeMirroredQueuePlayer(true, true);
        PlaybackQueueManager queueManager =
                queueManager(new FakeQueueStore(), new FakeQueuePlaybackActions(), mirroredQueuePlayer);
        queueManager.playQueue(Arrays.asList(track(1L), track(2L)), 0, -1L);
        List<Boolean> reuseNotifications = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = owner(queueManager, reuseNotifications);

        owner.skipToNextImmediately();
        owner.skipToPrevious();

        assertEquals(Arrays.asList(true, true), reuseNotifications);
        assertEquals(2, mirroredQueuePlayer.seekCalls);
        assertEquals(true, mirroredQueuePlayer.lastPlayWhenReady);
    }

    @Test
    public void ignoresMissingPlaybackQueueManager() {
        List<Boolean> reuseNotifications = new ArrayList<>();
        PlaybackQueueNavigationOwner missingManager =
                new PlaybackQueueNavigationOwner(
                        null,
                        reuseNotifications::add
                );

        missingManager.skipToNextImmediately();
        missingManager.playFirstQueuedTrack();
        missingManager.skipToPrevious();
        missingManager.reuseMirroredQueueIfAvailable(false, 300L);

        assertEquals(Collections.emptyList(), reuseNotifications);
    }

    @Test
    public void delegatesExistingMirroredQueueReuseAndNotifiesBoundary() {
        FakeMirroredQueuePlayer mirroredQueuePlayer = new FakeMirroredQueuePlayer(true, true);
        PlaybackQueueManager queueManager =
                queueManager(new FakeQueueStore(), new FakeQueuePlaybackActions(), mirroredQueuePlayer);
        queueManager.playQueue(Collections.singletonList(track(5L)), 0, -1L);
        List<Boolean> reuseNotifications = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = owner(queueManager, reuseNotifications);

        assertTrue(owner.reuseMirroredQueueIfAvailable(true, 4500L));

        assertEquals(Collections.singletonList(true), reuseNotifications);
        assertEquals(1, mirroredQueuePlayer.seekCalls);
        assertEquals(0, mirroredQueuePlayer.lastIndex);
        assertEquals(4500L, mirroredQueuePlayer.lastPositionMs);
        assertEquals(true, mirroredQueuePlayer.lastPlayWhenReady);
    }

    @Test
    public void doesNotNotifyBoundaryWhenExistingMirroredQueueCannotBeReused() {
        FakeMirroredQueuePlayer mirroredQueuePlayer = new FakeMirroredQueuePlayer(false, true);
        PlaybackQueueManager queueManager =
                queueManager(new FakeQueueStore(), new FakeQueuePlaybackActions(), mirroredQueuePlayer);
        queueManager.playQueue(Collections.singletonList(track(7L)), 0, -1L);
        List<Boolean> reuseNotifications = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = owner(queueManager, reuseNotifications);

        assertFalse(owner.reuseMirroredQueueIfAvailable(false, 100L));

        assertEquals(Collections.emptyList(), reuseNotifications);
        assertEquals(0, mirroredQueuePlayer.seekCalls);
    }

    private static PlaybackQueueNavigationOwner owner(
            PlaybackQueueManager queueManager,
            List<Boolean> reuseNotifications
    ) {
        return new PlaybackQueueNavigationOwner(
                queueManager,
                reuseNotifications::add
        );
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            FakeQueuePlaybackActions actions,
            FakeMirroredQueuePlayer mirroredQueuePlayer
    ) {
        return new PlaybackQueueManager(
                store,
                actions,
                null,
                new NoopStreamingRestoreProvider(),
                mirroredQueuePlayer,
                null,
                null
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
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

    private static final class FakeQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        private final List<String> events = new ArrayList<>();

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            events.add("prepare:" + playWhenReady);
        }

        @Override
        public void publishState() {
            events.add("publish");
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class FakeMirroredQueuePlayer
            implements PlaybackQueueManager.MirroredQueuePlayer {
        private final boolean matchesCurrentQueue;
        private final boolean seekResult;
        private int seekCalls;
        private int lastIndex = -1;
        private long lastPositionMs = -1L;
        private boolean lastPlayWhenReady;

        private FakeMirroredQueuePlayer(boolean matchesCurrentQueue, boolean seekResult) {
            this.matchesCurrentQueue = matchesCurrentQueue;
            this.seekResult = seekResult;
        }

        @Override
        public boolean matchesCurrentQueue() {
            return matchesCurrentQueue;
        }

        @Override
        public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
            seekCalls++;
            lastIndex = index;
            lastPositionMs = positionMs;
            lastPlayWhenReady = playWhenReady;
            return seekResult;
        }
    }
}
