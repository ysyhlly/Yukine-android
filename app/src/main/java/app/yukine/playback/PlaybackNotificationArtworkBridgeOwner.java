package app.yukine.playback;

final class PlaybackNotificationArtworkBridgeOwner
        implements PlaybackNotificationArtworkManager.NotificationBridge {
    interface SessionRefresher {
        void refreshPlaybackSession();
    }

    interface NotificationUpdater {
        void updateMediaNotification(boolean force);
    }

    private final SessionRefresher sessionRefresher;
    private final NotificationUpdater notificationUpdater;

    PlaybackNotificationArtworkBridgeOwner(
            SessionRefresher sessionRefresher,
            NotificationUpdater notificationUpdater
    ) {
        this.sessionRefresher = sessionRefresher;
        this.notificationUpdater = notificationUpdater;
    }

    @Override
    public void refreshPlaybackSession() {
        if (sessionRefresher != null) {
            sessionRefresher.refreshPlaybackSession();
        }
    }

    @Override
    public void updateMediaNotification() {
        if (notificationUpdater != null) {
            notificationUpdater.updateMediaNotification(true);
        }
    }
}
