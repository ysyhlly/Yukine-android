package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

final class PlaybackProgressUpdateStateOwner implements PlaybackProgressUpdateManager.StateProvider {
    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface PreparingStateProvider {
        boolean isPreparing();
    }

    private final PlaybackStateProvider playbackStateProvider;
    private final PreparingStateProvider preparingStateProvider;

    PlaybackProgressUpdateStateOwner(
            PlaybackStateProvider playbackStateProvider,
            PreparingStateProvider preparingStateProvider
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider.isPreparing();
    }
}
