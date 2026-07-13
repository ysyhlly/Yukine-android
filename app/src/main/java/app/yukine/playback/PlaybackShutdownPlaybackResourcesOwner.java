package app.yukine.playback;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackShutdownPlaybackResourcesOwner implements PlaybackShutdownCoordinator.PlaybackResources {
    private final Runnable releaseLyrics;
    private final Runnable releaseWifiLock;
    private final Runnable releaseSession;
    private final Runnable releasePlayer;
    private final Runnable resetQueueMirrorState;
    private final Runnable resetRuntimePreparingState;

    PlaybackShutdownPlaybackResourcesOwner(
            Runnable releaseLyrics,
            Runnable releaseWifiLock,
            Runnable releasePlayer
    ) {
        this(releaseLyrics, releaseWifiLock, null, releasePlayer, null, null);
    }

    PlaybackShutdownPlaybackResourcesOwner(
            Runnable releaseLyrics,
            Runnable releaseWifiLock,
            Runnable releasePlayer,
            Runnable resetQueueMirrorState,
            Runnable resetRuntimePreparingState
    ) {
        this(
                releaseLyrics,
                releaseWifiLock,
                null,
                releasePlayer,
                resetQueueMirrorState,
                resetRuntimePreparingState
        );
    }

    PlaybackShutdownPlaybackResourcesOwner(
            Runnable releaseLyrics,
            Runnable releaseWifiLock,
            Runnable releaseSession,
            Runnable releasePlayer,
            Runnable resetQueueMirrorState,
            Runnable resetRuntimePreparingState
    ) {
        this.releaseLyrics = safe(releaseLyrics);
        this.releaseWifiLock = safe(releaseWifiLock);
        this.releaseSession = safe(releaseSession);
        this.releasePlayer = safe(releasePlayer);
        this.resetQueueMirrorState = safe(resetQueueMirrorState);
        this.resetRuntimePreparingState = safe(resetRuntimePreparingState);
    }

    @Override
    public void releaseLyrics() {
        releaseLyrics.run();
    }

    @Override
    public void releaseWifiLock() {
        releaseWifiLock.run();
    }

    @Override
    public void releaseSession() {
        releaseSession.run();
    }

    @Override
    public void releasePlayer() {
        releasePlayer.run();
        resetQueueMirrorState.run();
        resetRuntimePreparingState.run();
    }

    static <T> Runnable releaseFrom(Supplier<T> provider, Consumer<T> releaseAction) {
        return () -> {
            T resource = provider == null ? null : provider.get();
            if (resource != null && releaseAction != null) {
                releaseAction.accept(resource);
            }
        };
    }

    private static Runnable safe(Runnable runnable) {
        return runnable == null ? () -> {
        } : runnable;
    }
}
