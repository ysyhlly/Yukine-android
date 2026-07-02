package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    interface PlaybackStateProvider {
        Track currentTrack();

        boolean isPlaying();

        boolean isPreparing();
    }

    static PlaybackStateProvider playbackStateProviderFromPlaybackState(
            PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        return new PlaybackStateProvider() {
            @Override
            public Track currentTrack() {
                return queueStateSnapshot().getCurrentTrack();
            }

            @Override
            public boolean isPlaying() {
                return playingStateProvider != null && playingStateProvider.getAsBoolean();
            }

            @Override
            public boolean isPreparing() {
                return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
            }

            private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
                if (queueStateProvider == null) {
                    return PlaybackQueueManager.QueueStateSnapshot.empty();
                }
                PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();
                return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
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
