package app.echo.next.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import app.echo.next.data.MusicLibraryRepository;
import app.echo.next.model.PlaybackQueueState;

public final class PlaybackRestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        MusicLibraryRepository repository = new MusicLibraryRepository(context.getApplicationContext());
        PlaybackQueueState queueState = repository.loadPlaybackQueue();
        if (queueState == null || queueState.isEmpty()) {
            return;
        }
        Intent serviceIntent = new Intent(context, EchoPlaybackService.class);
        serviceIntent.setAction(repository.loadPlaybackResumeRequested()
                ? EchoPlaybackService.ACTION_RESTORE_AND_PLAY
                : EchoPlaybackService.ACTION_RESTORE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
