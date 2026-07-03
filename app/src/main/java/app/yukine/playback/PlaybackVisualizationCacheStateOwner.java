package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    private final Handler mainHandler;
    private final PlaybackQueueManager playbackQueueManager;
    private final Consumer<Runnable> cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            Handler mainHandler,
            PlaybackQueueManager playbackQueueManager,
            Consumer<Runnable> cacheTaskScheduler
    ) {
        this.mainHandler = mainHandler;
        this.playbackQueueManager = playbackQueueManager;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandler;
    }

    @Override
    public Track currentTrack() {
        return playbackQueueManager == null ? null : playbackQueueManager.queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        if (cacheTaskScheduler != null) {
            cacheTaskScheduler.accept(task);
        }
    }
}
