package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import app.yukine.FavoriteOperations;
import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlaybackFavoriteCommandOwnerTest {
    @Test
    public void toggleCurrentFavoriteTogglesCurrentTrackAndPublishesState() {
        PlaybackQueueManager queueManager = queueManager();
        Track track = track(7L);
        queueManager.playQueue(Collections.singletonList(track), 0, 0L);
        FakeFavoriteOperations operations = new FakeFavoriteOperations();
        FakeStatePublisher statePublisher = new FakeStatePublisher();

        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                queueStateOwner(queueManager),
                new ToggleFavoriteUseCase(operations),
                statePublisher::publishState
        );

        assertSame(track, operations.updatedTrack);
        assertEquals(true, operations.isFavorite(track.id));
        assertEquals(1, operations.setFavoriteCalls);
        assertEquals(1, statePublisher.publishStateCalls);
    }

    @Test
    public void missingCurrentTrackSkipsToggleAndPublish() {
        FakeFavoriteOperations operations = new FakeFavoriteOperations();
        FakeStatePublisher statePublisher = new FakeStatePublisher();

        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                queueStateOwner(queueManager()),
                new ToggleFavoriteUseCase(operations),
                statePublisher::publishState
        );

        assertEquals(0, operations.setFavoriteCalls);
        assertEquals(0, statePublisher.publishStateCalls);
    }

    @Test
    public void missingUseCaseSkipsPublish() {
        PlaybackQueueManager queueManager = queueManager();
        queueManager.playQueue(Collections.singletonList(track(8L)), 0, 0L);
        FakeStatePublisher statePublisher = new FakeStatePublisher();

        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                queueStateOwner(queueManager),
                null,
                statePublisher::publishState
        );

        assertEquals(0, statePublisher.publishStateCalls);
    }

    private PlaybackQueueStateOwner queueStateOwner(PlaybackQueueManager queueManager) {
        return new PlaybackQueueStateOwner(queueManager::queueStateSnapshot);
    }

    private PlaybackQueueManager queueManager() {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
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
                1000L,
                Uri.EMPTY,
                "file:" + id
        );
    }

    private static final class FakeFavoriteOperations implements FavoriteOperations {
        private final Set<Long> favoriteIds = new HashSet<>();
        private int setFavoriteCalls;
        private Track updatedTrack;

        @Override
        public boolean isFavorite(long trackId) {
            return favoriteIds.contains(trackId);
        }

        @Override
        public void setFavorite(Track track, boolean favorite) {
            setFavoriteCalls++;
            updatedTrack = track;
            if (favorite) {
                favoriteIds.add(track.id);
            } else {
                favoriteIds.remove(track.id);
            }
        }
    }

    private static final class FakeStatePublisher {
        private int publishStateCalls;

        private void publishState() {
            publishStateCalls++;
        }
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
