package app.yukine.playback;

import java.util.function.Consumer;
import java.util.function.Supplier;

import app.yukine.model.Track;

final class PlaybackWarmupActionsOwner {
    private final Consumer<Track> precacheAction;
    private final Consumer<Track> visualizationCacheAction;

    PlaybackWarmupActionsOwner(
            Consumer<Track> precacheAction,
            Consumer<Track> visualizationCacheAction
    ) {
        this.precacheAction = precacheAction;
        this.visualizationCacheAction = visualizationCacheAction;
    }

    static PlaybackWarmupActionsOwner fromManagers(
            Supplier<PlaybackPrecacheManager> precacheManagerSupplier,
            Supplier<PlaybackVisualizationCacheManager> visualizationCacheManagerSupplier
    ) {
        return new PlaybackWarmupActionsOwner(
                track -> {
                    PlaybackPrecacheManager manager = precacheManagerSupplier == null
                            ? null
                            : precacheManagerSupplier.get();
                    if (manager != null) {
                        manager.precacheTrack(track);
                    }
                },
                track -> {
                    PlaybackVisualizationCacheManager manager = visualizationCacheManagerSupplier == null
                            ? null
                            : visualizationCacheManagerSupplier.get();
                    if (manager != null) {
                        manager.scheduleVisualizationCache(track);
                    }
                }
        );
    }

    void precacheTrack(Track track) {
        if (precacheAction != null) {
            precacheAction.accept(track);
        }
    }

    void scheduleVisualizationCache(Track track) {
        if (visualizationCacheAction != null) {
            visualizationCacheAction.accept(track);
        }
    }
}
