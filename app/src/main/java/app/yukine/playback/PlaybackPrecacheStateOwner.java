package app.yukine.playback;

import androidx.media3.common.MediaItem;

import app.yukine.model.Track;

final class PlaybackPrecacheStateOwner implements PlaybackPrecacheManager.StateProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface PlayerMediaItemProvider {
        MediaItem currentPlayerMediaItem();
    }

    interface StreamingDiagnosticsProvider {
        PlaybackStreamingDiagnostics streamingDiagnostics();
    }

    private final CurrentTrackProvider currentTrackProvider;
    private final PlayerMediaItemProvider playerMediaItemProvider;
    private final StreamingDiagnosticsProvider streamingDiagnosticsProvider;

    PlaybackPrecacheStateOwner(
            CurrentTrackProvider currentTrackProvider,
            PlayerMediaItemProvider playerMediaItemProvider,
            StreamingDiagnosticsProvider streamingDiagnosticsProvider
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.playerMediaItemProvider = playerMediaItemProvider;
        this.streamingDiagnosticsProvider = streamingDiagnosticsProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }

    @Override
    public MediaItem currentPlayerMediaItem() {
        return playerMediaItemProvider.currentPlayerMediaItem();
    }

    @Override
    public PlaybackStreamingDiagnostics streamingDiagnostics() {
        return streamingDiagnosticsProvider.streamingDiagnostics();
    }
}
