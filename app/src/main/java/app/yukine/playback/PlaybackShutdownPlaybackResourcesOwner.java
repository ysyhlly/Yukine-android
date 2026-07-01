package app.yukine.playback;

final class PlaybackShutdownPlaybackResourcesOwner implements PlaybackShutdownCoordinator.PlaybackResources {
    private final Runnable releaseLyrics;
    private final Runnable releaseWifiLock;
    private final Runnable releasePlayer;
    private final Runnable resetQueueMirrorState;
    private final Runnable resetRuntimePreparingState;

    PlaybackShutdownPlaybackResourcesOwner(
            Runnable releaseLyrics,
            Runnable releaseWifiLock,
            Runnable releasePlayer
    ) {
        this(releaseLyrics, releaseWifiLock, releasePlayer, null, null);
    }

    PlaybackShutdownPlaybackResourcesOwner(
            Runnable releaseLyrics,
            Runnable releaseWifiLock,
            Runnable releasePlayer,
            Runnable resetQueueMirrorState,
            Runnable resetRuntimePreparingState
    ) {
        this.releaseLyrics = safe(releaseLyrics);
        this.releaseWifiLock = safe(releaseWifiLock);
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
    public void releasePlayer() {
        releasePlayer.run();
        resetQueueMirrorState.run();
        resetRuntimePreparingState.run();
    }

    private static Runnable safe(Runnable runnable) {
        return runnable == null ? () -> {
        } : runnable;
    }
}
