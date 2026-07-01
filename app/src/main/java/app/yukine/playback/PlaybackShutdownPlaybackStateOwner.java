package app.yukine.playback;

final class PlaybackShutdownPlaybackStateOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider {
    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface PreparingStateProvider {
        boolean isPreparing();
    }

    private final PlaybackStateProvider playbackStateProvider;
    private final PreparingStateProvider preparingStateProvider;

    PlaybackShutdownPlaybackStateOwner(
            PlaybackStateProvider playbackStateProvider,
            PreparingStateProvider preparingStateProvider
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider != null && playbackStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.isPreparing();
    }
}
