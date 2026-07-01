package app.yukine.playback;

import app.yukine.model.Track;

final class PlaybackWarmupActionsOwner {
    interface PrecacheManagerProvider {
        PlaybackPrecacheManager playbackPrecacheManager();
    }

    interface VisualizationCacheManagerProvider {
        PlaybackVisualizationCacheManager playbackVisualizationCacheManager();
    }

    interface PrecacheOperations {
        void precacheTrack(Track track);
    }

    interface VisualizationCacheOperations {
        void scheduleVisualizationCache(Track track);
    }

    interface PrecacheOperationsProvider {
        PrecacheOperations precacheOperations();
    }

    interface VisualizationCacheOperationsProvider {
        VisualizationCacheOperations visualizationCacheOperations();
    }

    private final PrecacheOperationsProvider precacheOperationsProvider;
    private final VisualizationCacheOperationsProvider visualizationCacheOperationsProvider;

    PlaybackWarmupActionsOwner(
            PrecacheOperationsProvider precacheOperationsProvider,
            VisualizationCacheOperationsProvider visualizationCacheOperationsProvider
    ) {
        this.precacheOperationsProvider = precacheOperationsProvider;
        this.visualizationCacheOperationsProvider = visualizationCacheOperationsProvider;
    }

    static PlaybackWarmupActionsOwner fromManagers(
            PrecacheManagerProvider precacheManagerProvider,
            VisualizationCacheManagerProvider visualizationCacheManagerProvider
    ) {
        return new PlaybackWarmupActionsOwner(
                new PrecacheManagerOperationsProvider(precacheManagerProvider),
                new VisualizationCacheManagerOperationsProvider(visualizationCacheManagerProvider)
        );
    }

    void precacheTrack(Track track) {
        PrecacheOperations operations = precacheOperationsProvider == null
                ? null
                : precacheOperationsProvider.precacheOperations();
        if (operations != null) {
            operations.precacheTrack(track);
        }
    }

    void scheduleVisualizationCache(Track track) {
        VisualizationCacheOperations operations = visualizationCacheOperationsProvider == null
                ? null
                : visualizationCacheOperationsProvider.visualizationCacheOperations();
        if (operations != null) {
            operations.scheduleVisualizationCache(track);
        }
    }

    private static final class PrecacheManagerOperationsProvider implements PrecacheOperationsProvider {
        private final PrecacheManagerProvider precacheManagerProvider;

        private PrecacheManagerOperationsProvider(PrecacheManagerProvider precacheManagerProvider) {
            this.precacheManagerProvider = precacheManagerProvider;
        }

        @Override
        public PrecacheOperations precacheOperations() {
            PlaybackPrecacheManager manager = precacheManagerProvider == null
                    ? null
                    : precacheManagerProvider.playbackPrecacheManager();
            return manager == null ? null : new PrecacheManagerOperations(manager);
        }
    }

    private static final class VisualizationCacheManagerOperationsProvider
            implements VisualizationCacheOperationsProvider {
        private final VisualizationCacheManagerProvider visualizationCacheManagerProvider;

        private VisualizationCacheManagerOperationsProvider(
                VisualizationCacheManagerProvider visualizationCacheManagerProvider
        ) {
            this.visualizationCacheManagerProvider = visualizationCacheManagerProvider;
        }

        @Override
        public VisualizationCacheOperations visualizationCacheOperations() {
            PlaybackVisualizationCacheManager manager = visualizationCacheManagerProvider == null
                    ? null
                    : visualizationCacheManagerProvider.playbackVisualizationCacheManager();
            return manager == null ? null : new VisualizationCacheManagerOperations(manager);
        }
    }

    private static final class PrecacheManagerOperations implements PrecacheOperations {
        private final PlaybackPrecacheManager manager;

        private PrecacheManagerOperations(PlaybackPrecacheManager manager) {
            this.manager = manager;
        }

        @Override
        public void precacheTrack(Track track) {
            manager.precacheTrack(track);
        }
    }

    private static final class VisualizationCacheManagerOperations
            implements VisualizationCacheOperations {
        private final PlaybackVisualizationCacheManager manager;

        private VisualizationCacheManagerOperations(PlaybackVisualizationCacheManager manager) {
            this.manager = manager;
        }

        @Override
        public void scheduleVisualizationCache(Track track) {
            manager.scheduleVisualizationCache(track);
        }
    }
}
