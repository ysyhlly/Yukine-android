package app.yukine.playback;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

import app.yukine.MainActivity;
import app.yukine.R;
import app.yukine.model.Track;

public final class EchoPlaybackWidgetProvider extends AppWidgetProvider {
    private static PlaybackStateSnapshot lastSnapshot = PlaybackStateSnapshot.empty();
    private static Bitmap lastArtwork;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        updateWidgets(context, lastSnapshot, lastArtwork);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (context != null) {
            updateWidgets(context, lastSnapshot, lastArtwork);
        }
    }

    public static void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
        if (context == null) {
            return;
        }
        lastSnapshot = snapshot == null ? PlaybackStateSnapshot.empty() : snapshot;
        lastArtwork = artwork;
        updateWidgets(context, lastSnapshot, lastArtwork);
    }

    private static void updateWidgets(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, EchoPlaybackWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) {
            return;
        }
        for (int id : ids) {
            manager.updateAppWidget(id, views(context, snapshot, artwork));
        }
    }

    @SuppressLint("RemoteViewLayout")
    private static RemoteViews views(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_playback);
        Track track = snapshot == null ? null : snapshot.currentTrack;
        boolean playing = snapshot != null && snapshot.playing;
        views.setTextViewText(R.id.widget_title, track == null ? context.getString(R.string.widget_empty_title) : track.title);
        views.setTextViewText(R.id.widget_subtitle, track == null ? context.getString(R.string.widget_empty_subtitle) : track.subtitle());
        views.setImageViewResource(R.id.widget_play_pause, playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play);
        if (artwork != null && !artwork.isRecycled()) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork);
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_stat_echo);
        }
        views.setOnClickPendingIntent(R.id.widget_root, activityIntent(context));
        views.setOnClickPendingIntent(R.id.widget_previous, serviceIntent(context, PlaybackServiceActions.PREVIOUS, 21));
        views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                serviceIntent(context, playing ? PlaybackServiceActions.PAUSE : PlaybackServiceActions.RESTORE_AND_PLAY, 22)
        );
        views.setOnClickPendingIntent(R.id.widget_next, serviceIntent(context, PlaybackServiceActions.NEXT, 23));
        return views;
    }

    private static PendingIntent activityIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 20, intent, pendingIntentFlags());
    }

    private static PendingIntent serviceIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, EchoPlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, requestCode, intent, pendingIntentFlags());
        }
        return PendingIntent.getService(context, requestCode, intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
