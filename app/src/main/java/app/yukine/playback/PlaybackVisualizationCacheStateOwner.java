package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    private final Handler mainHandler;
    private final Supplier<Track> currentTrackSupplier;
    private final Consumer<Runnable> cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            Handler mainHandler,
            Supplier<Track> currentTrackSupplier,
            Consumer<Runnable> cacheTaskScheduler
    ) {
        this.mainHandler = mainHandler;
        this.currentTrackSupplier = currentTrackSupplier;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandler;
    }

    @Override
    public Track currentTrack() {
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        if (cacheTaskScheduler != null) {
            cacheTaskScheduler.accept(task);
        }
    }
}
