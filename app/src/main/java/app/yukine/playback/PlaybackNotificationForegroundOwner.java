package app.yukine.playback;

import android.app.Notification;
import android.app.PendingIntent;

import app.yukine.playback.manager.PlaybackNotificationManager;

final class PlaybackNotificationForegroundOwner
        implements PlaybackNotificationManager.ForegroundController,
        PlaybackNotificationCommandOwner.ForegroundController {
    interface ActivityPendingIntentProvider {
        PendingIntent activityPendingIntent();
    }

    interface ServiceActionPendingIntentProvider {
        PendingIntent serviceActionPendingIntent(String action, int requestCode);
    }

    interface ForegroundStarter {
        void startPlaybackForeground(Notification notification);
    }

    interface ForegroundStopper {
        void stopForegroundAndSelf();
    }

    private final ActivityPendingIntentProvider activityPendingIntentProvider;
    private final ServiceActionPendingIntentProvider serviceActionPendingIntentProvider;
    private final ForegroundStarter foregroundStarter;
    private final ForegroundStopper foregroundStopper;

    PlaybackNotificationForegroundOwner(
            ActivityPendingIntentProvider activityPendingIntentProvider,
            ServiceActionPendingIntentProvider serviceActionPendingIntentProvider,
            ForegroundStarter foregroundStarter
    ) {
        this(activityPendingIntentProvider, serviceActionPendingIntentProvider, foregroundStarter, null);
    }

    PlaybackNotificationForegroundOwner(
            ActivityPendingIntentProvider activityPendingIntentProvider,
            ServiceActionPendingIntentProvider serviceActionPendingIntentProvider,
            ForegroundStarter foregroundStarter,
            ForegroundStopper foregroundStopper
    ) {
        this.activityPendingIntentProvider = activityPendingIntentProvider;
        this.serviceActionPendingIntentProvider = serviceActionPendingIntentProvider;
        this.foregroundStarter = foregroundStarter;
        this.foregroundStopper = foregroundStopper;
    }

    @Override
    public PendingIntent activityPendingIntent() {
        return activityPendingIntentProvider.activityPendingIntent();
    }

    @Override
    public PendingIntent serviceActionPendingIntent(String action, int requestCode) {
        return serviceActionPendingIntentProvider.serviceActionPendingIntent(action, requestCode);
    }

    @Override
    public void startPlaybackForeground(Notification notification) {
        foregroundStarter.startPlaybackForeground(notification);
    }

    @Override
    public void stopForegroundAndSelf() {
        if (foregroundStopper != null) {
            foregroundStopper.stopForegroundAndSelf();
        }
    }
}
