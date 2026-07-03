package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;

import java.util.function.BooleanSupplier;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    interface PlaybackStateProvider {
        Track currentTrack();

        boolean isPlaying();

        boolean isPreparing();
    }

    static PlaybackStateProvider playbackStateProviderFromPlaybackState(
            PlaybackQueueStateOwner queueStateOwner,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        return new PlaybackStateProvider() {
            @Override
            public Track currentTrack() {
                return queueStateOwner == null
                        ? null
                        : queueStateOwner.queueStateSnapshot().getCurrentTrack();
            }

            @Override
            public boolean isPlaying() {
                return playingStateProvider != null && playingStateProvider.getAsBoolean();
            }

            @Override
            public boolean isPreparing() {
                return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
            }

        };
    }

    private final BooleanSupplier appVisibilitySupplier;
    private final PlaybackStateProvider playbackStateProvider;

    PlaybackLyricsStateOwner(
            BooleanSupplier appVisibilitySupplier,
            PlaybackStateProvider playbackStateProvider
    ) {
        this.appVisibilitySupplier = appVisibilitySupplier;
        this.playbackStateProvider = playbackStateProvider;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilitySupplier.getAsBoolean();
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
