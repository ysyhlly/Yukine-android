package app.yukine.playback;

import androidx.media3.common.C;
import androidx.media3.common.Player;

final class PlaybackPlayerStateOwner implements
        PlaybackActiveStateOwner.PlayingStateProvider,
        PlaybackQueueCommandOwner.PlaybackStateProvider,
        PlaybackProgressUpdateStateOwner.PlaybackStateProvider,
        PlaybackCrossfadeStateOwner.PlaybackStateProvider,
        PlaybackRealtimeVisualizationOwner.PlaybackStateProvider,
        PlaybackPositionStateOwner.PlaybackPositionProvider,
        PlaybackStateSnapshotOwner.PlaybackPositionProvider,
        PlaybackBufferedProgressOwner.PlaybackPositionProvider,
        PlaybackNoisyReceiverActionsOwner.PlaybackStateProvider,
        PlaybackShutdownPlaybackStateOwner.PlaybackStateProvider {
    interface PlayerProvider {
        Player player();
    }

    interface PlayerStateOperations {
        boolean isPlaying();

        long currentPositionMs();

        long durationMs();
    }

    interface PlayerStateOperationsProvider {
        PlayerStateOperations playerStateOperations();
    }

    private final PlayerStateOperationsProvider playerStateOperationsProvider;

    PlaybackPlayerStateOwner(PlayerStateOperationsProvider playerStateOperationsProvider) {
        this.playerStateOperationsProvider = playerStateOperationsProvider;
    }

    static PlaybackPlayerStateOwner fromPlayerProvider(PlayerProvider playerProvider) {
        return new PlaybackPlayerStateOwner(
                () -> {
                    Player player = playerProvider == null ? null : playerProvider.player();
                    return player == null ? null : new Media3PlayerStateOperations(player);
                }
        );
    }

    @Override
    public boolean isPlaying() {
        PlayerStateOperations playerStateOperations = playerStateOperations();
        if (playerStateOperations == null) {
            return false;
        }
        try {
            return playerStateOperations.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public long positionMs() {
        PlayerStateOperations playerStateOperations = playerStateOperations();
        if (playerStateOperations == null) {
            return 0L;
        }
        try {
            return Math.max(0L, playerStateOperations.currentPositionMs());
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    @Override
    public long durationMs() {
        PlayerStateOperations playerStateOperations = playerStateOperations();
        if (playerStateOperations == null) {
            return 0L;
        }
        try {
            long durationMs = playerStateOperations.durationMs();
            return durationMs == C.TIME_UNSET ? 0L : Math.max(0L, durationMs);
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    private PlayerStateOperations playerStateOperations() {
        return playerStateOperationsProvider == null ? null : playerStateOperationsProvider.playerStateOperations();
    }

    private static final class Media3PlayerStateOperations implements PlayerStateOperations {
        private final Player player;

        private Media3PlayerStateOperations(Player player) {
            this.player = player;
        }

        @Override
        public boolean isPlaying() {
            return player.isPlaying();
        }

        @Override
        public long currentPositionMs() {
            return player.getCurrentPosition();
        }

        @Override
        public long durationMs() {
            return player.getDuration();
        }
    }
}
