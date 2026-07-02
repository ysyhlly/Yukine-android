package app.yukine.playback;

import androidx.media3.common.C;
import androidx.media3.common.Player;

final class PlaybackPlayerStateOwner implements
        PlaybackRealtimeVisualizationOwner.PlaybackStateProvider,
        PlaybackStateSnapshotOwner.PlaybackPositionProvider,
        PlaybackBufferedProgressOwner.PlaybackPositionProvider {
    interface PlayerProvider {
        Player player();
    }

    private final PlayerProvider playerProvider;

    PlaybackPlayerStateOwner(PlayerProvider playerProvider) {
        this.playerProvider = playerProvider;
    }

    static PlaybackPlayerStateOwner fromPlayerProvider(PlayerProvider playerProvider) {
        return new PlaybackPlayerStateOwner(playerProvider);
    }

    @Override
    public boolean isPlaying() {
        Player player = player();
        if (player == null) {
            return false;
        }
        try {
            return player.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public long positionMs() {
        Player player = player();
        if (player == null) {
            return 0L;
        }
        try {
            return Math.max(0L, player.getCurrentPosition());
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    @Override
    public long durationMs() {
        Player player = player();
        if (player == null) {
            return 0L;
        }
        try {
            long durationMs = player.getDuration();
            return durationMs == C.TIME_UNSET ? 0L : Math.max(0L, durationMs);
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    private Player player() {
        return playerProvider == null ? null : playerProvider.player();
    }
}
