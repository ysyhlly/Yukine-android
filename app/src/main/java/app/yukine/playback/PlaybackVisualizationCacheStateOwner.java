package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    interface MainHandlerProvider {
        Handler mainHandler();
    }

    interface CacheTaskScheduler {
        void scheduleVisualizationCacheTask(Runnable task);
    }

    private final MainHandlerProvider mainHandlerProvider;
    private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;
    private final CacheTaskScheduler cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            MainHandlerProvider mainHandlerProvider,
            PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider,
            CacheTaskScheduler cacheTaskScheduler
    ) {
        this.mainHandlerProvider = mainHandlerProvider;
        this.queueStateProvider = queueStateProvider;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandlerProvider.mainHandler();
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public void scheduleVisualizationCacheTask(Runnable task) {
        cacheTaskScheduler.scheduleVisualizationCacheTask(task);
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        if (queueStateProvider == null) {
            return PlaybackQueueManager.QueueStateSnapshot.empty();
        }
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
