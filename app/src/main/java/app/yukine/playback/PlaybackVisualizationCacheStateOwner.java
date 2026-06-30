package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    interface MainHandlerProvider {
        Handler mainHandler();
    }

    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface CacheTaskScheduler {
        void scheduleVisualizationCacheTask(Runnable task);
    }

    private final MainHandlerProvider mainHandlerProvider;
    private final CurrentTrackProvider currentTrackProvider;
    private final CacheTaskScheduler cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            MainHandlerProvider mainHandlerProvider,
            CurrentTrackProvider currentTrackProvider,
            CacheTaskScheduler cacheTaskScheduler
    ) {
        this.mainHandlerProvider = mainHandlerProvider;
        this.currentTrackProvider = currentTrackProvider;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandlerProvider.mainHandler();
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        cacheTaskScheduler.scheduleVisualizationCacheTask(task);
    }
}
