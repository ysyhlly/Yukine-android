package app.yukine.playback;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackShutdownServiceResourcesOwner implements PlaybackShutdownCoordinator.ServiceResources {
    private final Runnable unregisterNoisyReceiver;
    private final Runnable releaseWarmup;
    private final Runnable releaseVisualizationAnalyzer;
    private final Runnable releaseRecoveryScheduler;
    private final Runnable shutdownTaskSchedulers;
    private final Runnable releaseErrorRecovery;
    private final Runnable releaseProgressUpdates;
    private final Runnable releaseSleepTimer;
    private final Runnable releaseCrossfade;
    private final Runnable clearMainCallbacks;
    private final Runnable releaseVisualizationCache;
    private final Runnable releaseNotificationArtwork;
    private final Runnable releasePrecache;
    private final Runnable releaseStatePublisher;

    PlaybackShutdownServiceResourcesOwner(
            Runnable unregisterNoisyReceiver,
            Runnable releaseWarmup,
            Runnable releaseVisualizationAnalyzer,
            Runnable releaseRecoveryScheduler,
            Runnable shutdownTaskSchedulers,
            Runnable releaseErrorRecovery,
            Runnable releaseProgressUpdates,
            Runnable releaseSleepTimer,
            Runnable releaseCrossfade,
            Runnable clearMainCallbacks,
            Runnable releaseVisualizationCache,
            Runnable releaseNotificationArtwork,
            Runnable releasePrecache,
            Runnable releaseStatePublisher
    ) {
        this.unregisterNoisyReceiver = safe(unregisterNoisyReceiver);
        this.releaseWarmup = safe(releaseWarmup);
        this.releaseVisualizationAnalyzer = safe(releaseVisualizationAnalyzer);
        this.releaseRecoveryScheduler = safe(releaseRecoveryScheduler);
        this.shutdownTaskSchedulers = safe(shutdownTaskSchedulers);
        this.releaseErrorRecovery = safe(releaseErrorRecovery);
        this.releaseProgressUpdates = safe(releaseProgressUpdates);
        this.releaseSleepTimer = safe(releaseSleepTimer);
        this.releaseCrossfade = safe(releaseCrossfade);
        this.clearMainCallbacks = safe(clearMainCallbacks);
        this.releaseVisualizationCache = safe(releaseVisualizationCache);
        this.releaseNotificationArtwork = safe(releaseNotificationArtwork);
        this.releasePrecache = safe(releasePrecache);
        this.releaseStatePublisher = safe(releaseStatePublisher);
    }

    static Runnable shutdownPlaybackTaskSchedulers(PlaybackTaskScheduler... schedulers) {
        return () -> {
            if (schedulers == null) {
                return;
            }
            for (PlaybackTaskScheduler scheduler : schedulers) {
                if (scheduler != null) {
                    scheduler.shutdownNow();
                }
            }
        };
    }

    static <T> Runnable releaseFrom(Supplier<T> provider, Consumer<T> releaseAction) {
        return () -> {
            T resource = provider == null ? null : provider.get();
            if (resource != null && releaseAction != null) {
                releaseAction.accept(resource);
            }
        };
    }

    @Override
    public void unregisterNoisyReceiver() {
        unregisterNoisyReceiver.run();
    }

    @Override
    public void releaseWarmup() {
        releaseWarmup.run();
    }

    @Override
    public void releaseVisualizationAnalyzer() {
        releaseVisualizationAnalyzer.run();
    }

    @Override
    public void releaseRecoveryScheduler() {
        releaseRecoveryScheduler.run();
    }

    @Override
    public void shutdownTaskSchedulers() {
        shutdownTaskSchedulers.run();
    }

    @Override
    public void releaseErrorRecovery() {
        releaseErrorRecovery.run();
    }

    @Override
    public void releaseProgressUpdates() {
        releaseProgressUpdates.run();
    }

    @Override
    public void releaseSleepTimer() {
        releaseSleepTimer.run();
    }

    @Override
    public void releaseCrossfade() {
        releaseCrossfade.run();
    }

    @Override
    public void clearMainCallbacks() {
        clearMainCallbacks.run();
    }

    @Override
    public void releaseVisualizationCache() {
        releaseVisualizationCache.run();
    }

    @Override
    public void releaseNotificationArtwork() {
        releaseNotificationArtwork.run();
    }

    @Override
    public void releasePrecache() {
        releasePrecache.run();
    }

    @Override
    public void releaseStatePublisher() {
        releaseStatePublisher.run();
    }

    private static Runnable safe(Runnable runnable) {
        return runnable == null ? () -> {
        } : runnable;
    }
}
