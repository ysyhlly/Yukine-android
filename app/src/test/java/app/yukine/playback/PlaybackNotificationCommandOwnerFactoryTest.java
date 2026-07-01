package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackNotificationCommandOwnerFactoryTest {
    @Test
    public void fromNotificationOwnersPublishesThroughStatePublisherWhenAvailable() {
        List<Boolean> forcedUpdates = new ArrayList<>();
        PlaybackStatePublisher statePublisher = new PlaybackStatePublisher(
                PlaybackStateSnapshot::empty,
                null,
                forcedUpdates::add,
                null,
                (snapshot, artwork) -> {
                }
        );
        PlaybackNotificationCommandOwner owner = PlaybackNotificationCommandOwner.fromNotificationOwners(
                () -> statePublisher,
                () -> null,
                () -> true,
                new FakePlaybackCommands(),
                () -> {
                }
        );

        owner.publishPlaybackNotification(true);
        owner.publishPlaybackNotificationIfWorthy();

        assertEquals(Arrays.asList(true, true), forcedUpdates);
    }

    @Test
    public void fromNotificationOwnersIgnoresMissingPublishers() {
        PlaybackNotificationCommandOwner owner = PlaybackNotificationCommandOwner.fromNotificationOwners(
                () -> null,
                () -> null,
                () -> true,
                new FakePlaybackCommands(),
                () -> {
                }
        );

        owner.publishPlaybackNotification(true);
    }

    private static final class FakePlaybackCommands
            implements PlaybackNotificationCommandOwner.PlaybackCommands {
        @Override
        public void play() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void skipToPrevious() {
        }

        @Override
        public void skipToNext() {
        }

        @Override
        public void toggleCurrentFavorite() {
        }

        @Override
        public void restoreLastPlayback(boolean playWhenReady) {
        }

        @Override
        public void stopAndClear() {
        }
    }
}
