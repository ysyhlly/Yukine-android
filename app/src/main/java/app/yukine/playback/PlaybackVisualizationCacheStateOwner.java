package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;

import java.util.function.Consumer;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    private final Handler mainHandler;
    private final PlaybackQueueStateOwner queueStateOwner;
    private final Consumer<Runnable> cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            Handler mainHandler,
            PlaybackQueueStateOwner queueStateOwner,
            Consumer<Runnable> cacheTaskScheduler
    ) {
        this.mainHandler = mainHandler;
        this.queueStateOwner = queueStateOwner;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandler;
    }

    @Override
    public Track currentTrack() {
        return queueStateOwner == null ? null : queueStateOwner.currentTrack();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        if (cacheTaskScheduler != null) {
            cacheTaskScheduler.accept(task);
        }
    }
}
