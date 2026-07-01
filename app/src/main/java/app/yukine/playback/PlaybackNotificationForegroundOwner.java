package app.yukine.playback;

import android.app.Notification;
import android.app.PendingIntent;

import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackNotificationForegroundOwner
        implements PlaybackNotificationManager.ForegroundController,
        PlaybackNotificationCommandOwner.ForegroundController {
    private final Supplier<PendingIntent> activityPendingIntentProvider;
    private final BiFunction<String, Integer, PendingIntent> serviceActionPendingIntentProvider;
    private final Consumer<Notification> foregroundStarter;
    private final Runnable foregroundStopper;

    PlaybackNotificationForegroundOwner(
            Supplier<PendingIntent> activityPendingIntentProvider,
            BiFunction<String, Integer, PendingIntent> serviceActionPendingIntentProvider,
            Consumer<Notification> foregroundStarter
    ) {
        this(activityPendingIntentProvider, serviceActionPendingIntentProvider, foregroundStarter, null);
    }

    PlaybackNotificationForegroundOwner(
            Supplier<PendingIntent> activityPendingIntentProvider,
            BiFunction<String, Integer, PendingIntent> serviceActionPendingIntentProvider,
            Consumer<Notification> foregroundStarter,
            Runnable foregroundStopper
    ) {
        this.activityPendingIntentProvider = activityPendingIntentProvider;
        this.serviceActionPendingIntentProvider = serviceActionPendingIntentProvider;
        this.foregroundStarter = foregroundStarter;
        this.foregroundStopper = foregroundStopper;
    }

    @Override
    public PendingIntent activityPendingIntent() {
        return activityPendingIntentProvider.get();
    }

    @Override
    public PendingIntent serviceActionPendingIntent(String action, int requestCode) {
        return serviceActionPendingIntentProvider.apply(action, requestCode);
    }

    @Override
    public void startPlaybackForeground(Notification notification) {
        foregroundStarter.accept(notification);
    }

    @Override
    public void stopForegroundAndSelf() {
        if (foregroundStopper != null) {
            foregroundStopper.run();
        }
    }
}
