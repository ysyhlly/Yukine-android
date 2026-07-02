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

    @Test
    public void sessionRefresherUsesCurrentSessionOperations() {
        MutableSessionOperationsSource source = new MutableSessionOperationsSource();
        FakeSessionOperations first = new FakeSessionOperations();
        FakeSessionOperations second = new FakeSessionOperations();
        PlaybackNotificationArtworkBridgeOwner.SessionRefresher refresher =
                PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromSessionOperations(
                        source::sessionOperations
                );

        source.sessionOperations = first;
        refresher.refreshPlaybackSession();
        source.sessionOperations = second;
        refresher.refreshPlaybackSession();

        assertEquals(1, first.refreshPlayerCalls);
        assertEquals(1, second.refreshPlayerCalls);
    }

    @Test
    public void sessionRefresherIgnoresMissingSessionOperations() {
        PlaybackNotificationArtworkBridgeOwner.SessionRefresher nullSupplierRefresher =
                PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromSessionOperations(null);
        PlaybackNotificationArtworkBridgeOwner.SessionRefresher missingSessionRefresher =
                PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromSessionOperations(() -> null);

        nullSupplierRefresher.refreshPlaybackSession();
        missingSessionRefresher.refreshPlaybackSession();
    }

    private static final class FakeSessionRefresher
            implements PlaybackNotificationArtworkBridgeOwner.SessionRefresher {
        private int refreshCalls;

        @Override
        public void refreshPlaybackSession() {
            refreshCalls++;
        }
    }

    private static final class MutableSessionOperationsSource {
        private FakeSessionOperations sessionOperations;

        public PlaybackNotificationArtworkBridgeOwner.SessionOperations sessionOperations() {
            return sessionOperations;
        }
    }

    private static final class FakeSessionOperations
            implements PlaybackNotificationArtworkBridgeOwner.SessionOperations {
        private int refreshPlayerCalls;

        @Override
        public void refreshPlayer() {
            refreshPlayerCalls++;
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
