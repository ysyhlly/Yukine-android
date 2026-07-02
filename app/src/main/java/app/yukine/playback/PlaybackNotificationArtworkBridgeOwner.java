package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackSessionManager;

import java.util.function.Supplier;

final class PlaybackNotificationArtworkBridgeOwner
        implements PlaybackNotificationArtworkManager.NotificationBridge {
    interface SessionRefresher extends PlaybackNotificationManager.SessionRefresher {
        void refreshPlaybackSession();
    }

    interface SessionOperations {
        void refreshPlayer();
    }

    interface NotificationUpdater {
        void updateMediaNotification(boolean force);
    }

    static SessionRefresher sessionRefresherFromPlaybackSessionManager(
            Supplier<PlaybackSessionManager> playbackSessionManagerSupplier
    ) {
        return sessionRefresherFromSessionOperations(
                () -> {
                    PlaybackSessionManager manager = playbackSessionManagerSupplier == null
                            ? null
                            : playbackSessionManagerSupplier.get();
                    return manager == null ? null : manager::refreshPlayer;
                }
        );
    }

    static SessionRefresher sessionRefresherFromSessionOperations(
            Supplier<SessionOperations> sessionOperationsSupplier
    ) {
        return () -> {
            SessionOperations sessionOperations = sessionOperationsSupplier == null
                    ? null
                    : sessionOperationsSupplier.get();
            if (sessionOperations != null) {
                sessionOperations.refreshPlayer();
            }
        };
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
