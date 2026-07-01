package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNotificationManager;

final class PlaybackNotificationCommandOwner implements PlaybackNotificationManager.ActionCallbacks {
    interface PlaybackCommands {
        void play();

        void pause();

        void skipToPrevious();

        void skipToNext();

        void toggleCurrentFavorite();

        void restoreLastPlayback(boolean playWhenReady);

        void stopAndClear();
    }

    interface NotificationPublisher {
        void publishPlaybackNotification(boolean force);
    }

    interface StatePublisherProvider {
        PlaybackStatePublisher playbackStatePublisher();
    }

    interface NotificationManagerProvider {
        PlaybackNotificationManager playbackNotificationManager();
    }

    interface NotificationStateProvider {
        boolean hasNotificationWorthyState();
    }

    interface ForegroundController {
        void stopForegroundAndSelf();
    }

    private final NotificationPublisher notificationPublisher;
    private final NotificationStateProvider notificationStateProvider;
    private final PlaybackCommands playbackCommands;
    private final ForegroundController foregroundController;

    static PlaybackNotificationCommandOwner fromNotificationOwners(
            StatePublisherProvider statePublisherProvider,
            NotificationManagerProvider notificationManagerProvider,
            NotificationStateProvider notificationStateProvider,
            PlaybackCommands playbackCommands,
            ForegroundController foregroundController
    ) {
        return new PlaybackNotificationCommandOwner(
                force -> {
                    PlaybackStatePublisher statePublisher = statePublisherProvider == null
                            ? null
                            : statePublisherProvider.playbackStatePublisher();
                    if (statePublisher != null) {
                        statePublisher.publishNotification(force);
                        return;
                    }
                    PlaybackNotificationManager notificationManager = notificationManagerProvider == null
                            ? null
                            : notificationManagerProvider.playbackNotificationManager();
                    if (notificationManager != null) {
                        notificationManager.updateMediaNotification(force);
                    }
                },
                notificationStateProvider,
                playbackCommands,
                foregroundController
        );
    }

    PlaybackNotificationCommandOwner(
            NotificationPublisher notificationPublisher,
            NotificationStateProvider notificationStateProvider,
            PlaybackCommands playbackCommands,
            ForegroundController foregroundController
    ) {
        this.notificationPublisher = notificationPublisher;
        this.notificationStateProvider = notificationStateProvider;
        this.playbackCommands = playbackCommands;
        this.foregroundController = foregroundController;
    }

    @Override
    public void publishPlaybackNotification(boolean force) {
        notificationPublisher.publishPlaybackNotification(force);
    }

    boolean hasNotificationWorthyState() {
        return notificationStateProvider != null && notificationStateProvider.hasNotificationWorthyState();
    }

    void publishPlaybackNotificationIfWorthy() {
        if (hasNotificationWorthyState()) {
            publishPlaybackNotification(true);
        }
    }

    @Override
    public void play() {
        playbackCommands.play();
    }

    @Override
    public void pause() {
        playbackCommands.pause();
    }

    @Override
    public void skipToPrevious() {
        playbackCommands.skipToPrevious();
    }

    @Override
    public void skipToNext() {
        playbackCommands.skipToNext();
    }

    @Override
    public void toggleCurrentFavorite() {
        playbackCommands.toggleCurrentFavorite();
    }

    @Override
    public void restoreLastPlayback(boolean playWhenReady) {
        playbackCommands.restoreLastPlayback(playWhenReady);
    }

    @Override
    public void stopAndClear() {
        playbackCommands.stopAndClear();
    }

    @Override
    public void stopForegroundAndSelf() {
        foregroundController.stopForegroundAndSelf();
    }
}
