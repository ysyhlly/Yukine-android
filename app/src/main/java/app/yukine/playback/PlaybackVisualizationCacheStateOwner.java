package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
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
        this.mainHandler = Objects.requireNonNull(mainHandler, "mainHandler");
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.cacheTaskScheduler = Objects.requireNonNull(cacheTaskScheduler, "cacheTaskScheduler");
    }

    @Override
    public Handler mainHandler() {
        return mainHandler;
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        cacheTaskScheduler.accept(task);
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }
}
