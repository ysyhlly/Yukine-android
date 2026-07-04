package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import app.yukine.FavoriteOperations;
import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PlaybackFavoriteCommandOwnerTest {
    @Test
    public void toggleCurrentFavoriteTogglesCurrentTrackAndPublishesState() {
        Track track = track(7L);
        FakeFavoriteOperations operations = new FakeFavoriteOperations();
        FakeStatePublisher statePublisher = new FakeStatePublisher();

        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                () -> queueSnapshot(track),
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
                PlaybackQueueManager.QueueStateSnapshot::empty,
                new ToggleFavoriteUseCase(operations),
                statePublisher::publishState
        );

        assertEquals(0, operations.setFavoriteCalls);
        assertEquals(0, statePublisher.publishStateCalls);
    }

    @Test
    public void missingUseCaseSkipsPublish() {
        FakeStatePublisher statePublisher = new FakeStatePublisher();

        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                () -> queueSnapshot(track(8L)),
                null,
                statePublisher::publishState
        );

        assertEquals(0, statePublisher.publishStateCalls);
    }

    private static PlaybackQueueManager.QueueStateSnapshot queueSnapshot(Track currentTrack) {
        return new PlaybackQueueManager.QueueStateSnapshot(currentTrack, 0, 1);
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                null,
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
}
