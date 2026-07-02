package app.yukine.playback;

import android.os.Handler;

import app.yukine.model.Track;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider {
    private final Supplier<Handler> mainHandlerProvider;
    private final Supplier<Track> currentTrackSupplier;
    private final Consumer<Runnable> cacheTaskScheduler;

    PlaybackVisualizationCacheStateOwner(
            Supplier<Handler> mainHandlerProvider,
            Supplier<Track> currentTrackSupplier,
            Consumer<Runnable> cacheTaskScheduler
    ) {
        this.mainHandlerProvider = mainHandlerProvider;
        this.currentTrackSupplier = currentTrackSupplier;
        this.cacheTaskScheduler = cacheTaskScheduler;
    }

    @Override
    public Handler mainHandler() {
        return mainHandlerProvider == null ? null : mainHandlerProvider.get();
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
