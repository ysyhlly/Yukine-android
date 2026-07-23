package app.yukine.playback;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import app.yukine.diagnostics.DiagnosticLog;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession;
import javax.inject.Inject;
import app.yukine.R;
import app.yukine.PlaybackServiceHostPort;
import app.yukine.playback.manager.PlaybackNotificationChannelOwner;
import app.yukine.playback.service.PlaybackServiceActions;
import app.yukine.together.TogetherSessionHostPort;
import app.yukine.together.TogetherSessionOwner;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@OptIn(markerClass = UnstableApi.class)
public final class EchoPlaybackService extends MediaLibraryService {
    public static final String ACTION_PLAY = PlaybackServiceActions.PLAY;
    public static final String ACTION_PAUSE = PlaybackServiceActions.PAUSE;
    public static final String ACTION_PREVIOUS = PlaybackServiceActions.PREVIOUS;
    public static final String ACTION_NEXT = PlaybackServiceActions.NEXT;
    public static final String ACTION_STOP = PlaybackServiceActions.STOP;
    public static final String ACTION_TOGGLE_FAVORITE = PlaybackServiceActions.TOGGLE_FAVORITE;
    public static final String ACTION_RESTORE = PlaybackServiceActions.RESTORE;
    public static final String ACTION_RESTORE_AND_PLAY = PlaybackServiceActions.RESTORE_AND_PLAY;

    public static final int REPEAT_ALL = PlaybackRepeatMode.REPEAT_ALL;
    public static final int REPEAT_ONE = PlaybackRepeatMode.REPEAT_ONE;
    public static final int REPEAT_OFF = PlaybackRepeatMode.REPEAT_OFF;

    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "EchoPlaybackService";
    private final LocalBinder binder = new LocalBinder();
    private PlaybackServiceRuntime runtime;
    private TogetherSessionOwner togetherSessionOwner;
    private boolean togetherDataSyncActive;

    @Inject
    PlaybackServiceRuntimeFactory runtimeFactory;

    public final class LocalBinder extends Binder {
        public PlaybackServiceHostPort getService() {
            return runtime;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        runtime = runtimeFactory.create(this);
        runtime.create();
        togetherSessionOwner = TogetherSessionOwner.create(
                this,
                new TogetherMedia3PlayerAdapter(runtime),
                this::setTogetherDataSyncActive
        );
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null
                && (MediaLibraryService.SERVICE_INTERFACE.equals(intent.getAction())
                || androidx.media3.session.MediaSessionService.SERVICE_INTERFACE.equals(intent.getAction()))) {
            return super.onBind(intent);
        }
        return binder;
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return runtime == null ? null : runtime.session();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (runtime != null) {
            String action = intent == null ? "" : intent.getAction();
            if (runtime.requiresBootstrapForeground(action)) {
                startPlaybackForeground(restoringPlaybackNotification());
            }
            runtime.handleServiceAction(action);
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (runtime != null) {
            runtime.handleTaskRemoved();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (togetherSessionOwner != null) {
            togetherSessionOwner.close();
            togetherSessionOwner = null;
        }
        if (runtime != null) {
            runtime.destroy();
            runtime = null;
        }
        super.onDestroy();
    }

    boolean startPlaybackForeground(Notification notification) {
        if (notification == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
                if (togetherDataSyncActive) {
                    foregroundTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                }
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        foregroundTypes
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException error) {
            DiagnosticLog.w(TAG, "Unable to start playback foreground notification", error);
            return false;
        }
    }

    TogetherSessionHostPort togetherSessionHost() {
        return togetherSessionOwner;
    }

    TogetherSessionOwner togetherSessionOwner() {
        return togetherSessionOwner;
    }

    private void setTogetherDataSyncActive(boolean active) {
        if (togetherDataSyncActive == active) {
            return;
        }
        togetherDataSyncActive = active;
        startPlaybackForeground(restoringPlaybackNotification());
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        if (Build.VERSION.SDK_INT >= 35
                && (fgsType & ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) != 0) {
            togetherDataSyncActive = false;
            if (togetherSessionOwner != null) {
                togetherSessionOwner.requestLeave("data_sync_timeout");
            }
            startPlaybackForeground(restoringPlaybackNotification());
        }
        super.onTimeout(startId, fgsType);
    }

    private Notification restoringPlaybackNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, PlaybackNotificationChannelOwner.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_stat_echo)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.app_name))
                .setContentIntent(activityPendingIntent())
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    void clearPlaybackNotification() {
        stopForeground(true);
    }

    PendingIntent activityPendingIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(getPackageName());
        }
        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, pendingIntentFlags());
    }

    PendingIntent serviceActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, EchoPlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(this, requestCode, intent, pendingIntentFlags());
        }
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
