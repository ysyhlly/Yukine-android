package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    interface AppVisibilityProvider {
        boolean isAppVisible();
    }

    interface PlaybackStateProvider {
        Track currentTrack();

        boolean isPlaying();

        boolean isPreparing();
    }

    private final AppVisibilityProvider appVisibilityProvider;
    private final PlaybackStateProvider playbackStateProvider;

    PlaybackLyricsStateOwner(
            AppVisibilityProvider appVisibilityProvider,
            PlaybackStateProvider playbackStateProvider
    ) {
        this.appVisibilityProvider = appVisibilityProvider;
        this.playbackStateProvider = playbackStateProvider;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilityProvider.isAppVisible();
    }

    @Override
    public Track currentTrack() {
        return playbackStateProvider.currentTrack();
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return playbackStateProvider.isPreparing();
    }
}
