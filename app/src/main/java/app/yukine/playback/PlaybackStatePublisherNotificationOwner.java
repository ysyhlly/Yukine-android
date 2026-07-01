package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.Supplier;

final class PlaybackStatePublisherNotificationOwner
        implements PlaybackStatePublisher.NotificationUpdater {
    interface NotificationOperations {
        void updateMediaNotification(boolean force);
    }

    private final Supplier<NotificationOperations> notificationOperationsProvider;

    PlaybackStatePublisherNotificationOwner(Supplier<NotificationOperations> notificationOperationsProvider) {
        this.notificationOperationsProvider = notificationOperationsProvider;
    }

    static PlaybackStatePublisherNotificationOwner fromNotificationManagerProvider(
            Supplier<PlaybackNotificationManager> notificationManagerProvider
    ) {
        return new PlaybackStatePublisherNotificationOwner(
                () -> {
                    PlaybackNotificationManager manager = notificationManagerProvider == null
                            ? null
                            : notificationManagerProvider.get();
                    return manager == null ? null : new PlaybackNotificationManagerOperations(manager);
                }
        );
    }

    @Override
    public void updateMediaNotification(boolean force) {
        NotificationOperations notificationOperations = notificationOperationsProvider == null
                ? null
                : notificationOperationsProvider.get();
        if (notificationOperations != null) {
            notificationOperations.updateMediaNotification(force);
        }
    }

    private static final class PlaybackNotificationManagerOperations
            implements NotificationOperations {
        private final PlaybackNotificationManager manager;

        private PlaybackNotificationManagerOperations(PlaybackNotificationManager manager) {
            this.manager = manager;
        }

        @Override
        public void updateMediaNotification(boolean force) {
            manager.updateMediaNotification(force);
        }
    }
}
