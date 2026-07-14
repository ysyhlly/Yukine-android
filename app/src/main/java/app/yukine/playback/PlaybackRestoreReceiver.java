package app.yukine.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.PlaybackQueueState;
import app.yukine.playback.service.PlaybackServiceActions;
import app.yukine.streaming.StreamingPlaybackAdapter;

public final class PlaybackRestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        ExecutorService restoreExecutor = Executors.newSingleThreadExecutor();
        restoreExecutor.execute(() -> {
            try {
                MusicLibraryRepository repository = new MusicLibraryRepository(
                        appContext,
                        StreamingPlaybackAdapter.INSTANCE
                );
                if (!repository.loadPlaybackRestoreEnabled()) return;
                PlaybackQueueState queueState = repository.loadPlaybackQueue();
                if (queueState == null || queueState.isEmpty()) return;
                Intent serviceIntent = new Intent(appContext, EchoPlaybackService.class);
                serviceIntent.setAction(repository.loadPlaybackResumeRequested()
                        ? PlaybackServiceActions.RESTORE_AND_PLAY
                        : PlaybackServiceActions.RESTORE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent);
                } else {
                    appContext.startService(serviceIntent);
                }
            } finally {
                pendingResult.finish();
                restoreExecutor.shutdown();
            }
        });
    }
}
