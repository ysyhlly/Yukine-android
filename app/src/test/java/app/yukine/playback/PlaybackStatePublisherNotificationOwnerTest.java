package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PlaybackStatePublisherNotificationOwnerTest {
    @Test
    public void delegatesForcedAndUnforcedNotificationUpdates() {
        FakeNotificationOperations notificationOperations = new FakeNotificationOperations();
        PlaybackStatePublisherNotificationOwner owner =
                new PlaybackStatePublisherNotificationOwner(() -> notificationOperations);

        owner.updateMediaNotification(false);
        owner.updateMediaNotification(true);

        assertEquals(Arrays.asList(false, true), notificationOperations.forcedUpdates);
    }

    @Test
    public void ignoresMissingNotificationOperations() {
        PlaybackStatePublisherNotificationOwner nullProviderOwner =
                new PlaybackStatePublisherNotificationOwner(null);
        PlaybackStatePublisherNotificationOwner missingOperationsOwner =
                new PlaybackStatePublisherNotificationOwner(() -> null);

        nullProviderOwner.updateMediaNotification(false);
        missingOperationsOwner.updateMediaNotification(true);
    }

    private static final class FakeNotificationOperations
            implements PlaybackStatePublisherNotificationOwner.NotificationOperations {
        private final List<Boolean> forcedUpdates = new ArrayList<>();

        @Override
        public void updateMediaNotification(boolean force) {
            forcedUpdates.add(force);
        }
    }
}
