package app.yukine.playback;

import app.yukine.model.Track;

final class PlaybackActiveStateOwner implements
        PlaybackNotificationStateOwner.PlaybackStateProvider,
        PlaybackLyricsStateOwner.PlaybackStateProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface PlayingStateProvider {
        boolean isPlaying();
    }

    interface PreparingStateProvider {
        boolean isPreparing();
    }

    private final CurrentTrackProvider currentTrackProvider;
    private final PlayingStateProvider playingStateProvider;
    private final PreparingStateProvider preparingStateProvider;

    PlaybackActiveStateOwner(
            CurrentTrackProvider currentTrackProvider,
            PlayingStateProvider playingStateProvider,
            PreparingStateProvider preparingStateProvider
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider.isPlaying();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider.isPreparing();
    }
}
