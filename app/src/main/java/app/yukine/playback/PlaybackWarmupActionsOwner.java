package app.yukine.playback;

import java.util.function.Consumer;

import app.yukine.model.Track;

final class PlaybackWarmupActionsOwner {
    interface PrecacheManagerProvider {
        PlaybackPrecacheManager playbackPrecacheManager();
    }

    interface VisualizationCacheManagerProvider {
        PlaybackVisualizationCacheManager playbackVisualizationCacheManager();
    }

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
            PrecacheManagerProvider precacheManagerProvider,
            VisualizationCacheManagerProvider visualizationCacheManagerProvider
    ) {
        return new PlaybackWarmupActionsOwner(
                track -> {
                    PlaybackPrecacheManager manager = precacheManagerProvider == null
                            ? null
                            : precacheManagerProvider.playbackPrecacheManager();
                    if (manager != null) {
                        manager.precacheTrack(track);
                    }
                },
                track -> {
                    PlaybackVisualizationCacheManager manager = visualizationCacheManagerProvider == null
                            ? null
                            : visualizationCacheManagerProvider.playbackVisualizationCacheManager();
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
