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

    interface ForegroundController {
        void stopForegroundAndSelf();
    }

    private final NotificationPublisher notificationPublisher;
    private final PlaybackCommands playbackCommands;
    private final ForegroundController foregroundController;

    PlaybackNotificationCommandOwner(
            NotificationPublisher notificationPublisher,
            PlaybackCommands playbackCommands,
            ForegroundController foregroundController
    ) {
        this.notificationPublisher = notificationPublisher;
        this.playbackCommands = playbackCommands;
        this.foregroundController = foregroundController;
    }

    @Override
    public void publishPlaybackNotification(boolean force) {
        notificationPublisher.publishPlaybackNotification(force);
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
