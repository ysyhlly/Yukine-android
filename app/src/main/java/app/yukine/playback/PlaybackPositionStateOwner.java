package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackPositionManager;

final class PlaybackPositionStateOwner implements PlaybackPositionManager.StateProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface PlaybackPositionProvider {
        long positionMs();
    }

    private final CurrentTrackProvider currentTrackProvider;
    private final PlaybackPositionProvider playbackPositionProvider;

    PlaybackPositionStateOwner(
            CurrentTrackProvider currentTrackProvider,
            PlaybackPositionProvider playbackPositionProvider
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.playbackPositionProvider = playbackPositionProvider;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }

    @Override
    public long positionMs() {
        return playbackPositionProvider.positionMs();
    }
}
