package app.yukine.playback;

import app.yukine.model.Track;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackActiveStateOwner implements
        PlaybackNotificationStateOwner.PlaybackStateProvider,
        PlaybackLyricsStateOwner.PlaybackStateProvider {
    private final Supplier<Track> currentTrackProvider;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackActiveStateOwner(
            Supplier<Track> currentTrackProvider,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider == null ? null : currentTrackProvider.get();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider != null && playingStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }
}
