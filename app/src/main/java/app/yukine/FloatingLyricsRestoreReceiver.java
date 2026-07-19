package app.yukine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.streaming.StreamingPlaybackAdapter;

/** Restores only the independently enabled floating-lyrics service after boot. */
public final class FloatingLyricsRestoreReceiver extends BroadcastReceiver {
    private static final String TAG = "FloatingLyricsRestore";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null
                || intent == null
                || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                MusicLibraryRepository repository = new MusicLibraryRepository(
                        appContext,
                        StreamingPlaybackAdapter.INSTANCE
                );
                if (!repository.loadFloatingLyricsEnabled()) return;
                if (!FloatingLyricsService.canShow(appContext)) {
                    repository.saveFloatingLyricsEnabled(false);
                    return;
                }
                if (!FloatingLyricsService.start(appContext)) {
                    Log.w(TAG, "System blocked floating lyrics startup after boot");
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Floating lyrics restore was skipped", error);
            } finally {
                pendingResult.finish();
                executor.shutdown();
            }
        });
    }
}
