package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private final Consumer<Boolean> notificationPublisher;
    private final BooleanSupplier notificationWorthySupplier;
    private final PlaybackCommands playbackCommands;
    private final Runnable foregroundStopper;

    static PlaybackNotificationCommandOwner fromNotificationOwners(
            Supplier<PlaybackStatePublisher> statePublisherProvider,
            Supplier<PlaybackNotificationManager> notificationManagerProvider,
            BooleanSupplier notificationWorthySupplier,
            PlaybackCommands playbackCommands,
            Runnable foregroundStopper
    ) {
        return new PlaybackNotificationCommandOwner(
                force -> {
                    PlaybackStatePublisher statePublisher = statePublisherProvider == null
                            ? null
                            : statePublisherProvider.get();
                    if (statePublisher != null) {
                        statePublisher.publishNotification(force);
                        return;
                    }
                    PlaybackNotificationManager notificationManager = notificationManagerProvider == null
                            ? null
                            : notificationManagerProvider.get();
                    if (notificationManager != null) {
                        notificationManager.updateMediaNotification(force);
                    }
                },
                notificationWorthySupplier,
                playbackCommands,
                foregroundStopper
        );
    }

    PlaybackNotificationCommandOwner(
            Consumer<Boolean> notificationPublisher,
            BooleanSupplier notificationWorthySupplier,
            PlaybackCommands playbackCommands,
            Runnable foregroundStopper
    ) {
        this.notificationPublisher = notificationPublisher;
        this.notificationWorthySupplier = notificationWorthySupplier;
        this.playbackCommands = playbackCommands;
        this.foregroundStopper = foregroundStopper;
    }

    @Override
    public void publishPlaybackNotification(boolean force) {
        notificationPublisher.accept(force);
    }

    boolean hasNotificationWorthyState() {
        return notificationWorthySupplier != null && notificationWorthySupplier.getAsBoolean();
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
        foregroundStopper.run();
    }
}
