package app.yukine.playback;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import java.util.function.Supplier;

final class PlaybackPlayerStateOwner implements
        PlaybackStateSnapshotOwner.PlaybackPositionProvider {

    private final Supplier<Player> playerProvider;

    PlaybackPlayerStateOwner(Supplier<Player> playerProvider) {
        this.playerProvider = playerProvider;
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

    long bufferedPositionMs() {
        Player player = player();
        if (player == null) {
            return 0L;
        }
        try {
            return Math.max(0L, player.getBufferedPosition());
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    private Player player() {
        return playerProvider == null ? null : playerProvider.get();
    }
}
