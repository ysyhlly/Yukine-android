package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackNotificationArtworkBridgeOwnerTest {
    @Test
    public void delegatesSessionRefreshAndForcedNotificationUpdate() {
        FakeSessionRefresher sessionRefresher = new FakeSessionRefresher();
        FakeNotificationUpdater notificationUpdater = new FakeNotificationUpdater();
        PlaybackNotificationArtworkBridgeOwner owner = new PlaybackNotificationArtworkBridgeOwner(
                sessionRefresher,
                notificationUpdater
        );

        owner.refreshPlaybackSession();
        owner.updateMediaNotification();

        assertEquals(1, sessionRefresher.refreshCalls);
        assertEquals(1, notificationUpdater.updateCalls);
        assertEquals(true, notificationUpdater.lastForce);
    }

    @Test
    public void ignoresMissingDelegates() {
        PlaybackNotificationArtworkBridgeOwner owner =
                new PlaybackNotificationArtworkBridgeOwner(null, null);

        owner.refreshPlaybackSession();
        owner.updateMediaNotification();
    }

    private static final class FakeSessionRefresher
            implements PlaybackNotificationArtworkBridgeOwner.SessionRefresher {
        private int refreshCalls;

        @Override
        public void refreshPlaybackSession() {
            refreshCalls++;
        }
    }

    private static final class FakeNotificationUpdater
            implements PlaybackNotificationArtworkBridgeOwner.NotificationUpdater {
        private int updateCalls;
        private boolean lastForce;

        @Override
        public void updateMediaNotification(boolean force) {
            updateCalls++;
            lastForce = force;
        }
    }
}
