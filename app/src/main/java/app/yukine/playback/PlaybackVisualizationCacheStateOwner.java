package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;

import java.util.function.Supplier;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    interface MainHandlerProvider {
        Handler mainHandler();
    }

    interface CacheTaskScheduler {
        void scheduleVisualizationCacheTask(Runnable task);
    }

    private final MainHandlerProvider mainHandlerProvider;
    private final Supplier<Track> currentTrackSupplier;
    private final CacheTaskScheduler cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            MainHandlerProvider mainHandlerProvider,
            Supplier<Track> currentTrackSupplier,
            CacheTaskScheduler cacheTaskScheduler
    ) {
        this.mainHandlerProvider = mainHandlerProvider;
        this.currentTrackSupplier = currentTrackSupplier;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandlerProvider.mainHandler();
    }

    @Override
    public Track currentTrack() {
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        cacheTaskScheduler.scheduleVisualizationCacheTask(task);
    }
}
