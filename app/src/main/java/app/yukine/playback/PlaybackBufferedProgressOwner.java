package app.yukine.playback;

import androidx.media3.common.Player;

final class PlaybackBufferedProgressOwner
        implements PlaybackVisualizationStateOwner.BufferedProgressProvider {
    interface PlaybackPositionProvider {
        long positionMs();
    }

    interface PlayerProvider {
        Player player();
    }

    private final PlaybackPositionProvider playbackPositionProvider;
    private final PlayerProvider playerProvider;

    PlaybackBufferedProgressOwner(
            PlaybackPositionProvider playbackPositionProvider,
            PlayerProvider playerProvider
    ) {
        this.playbackPositionProvider = playbackPositionProvider;
        this.playerProvider = playerProvider;
    }

    static PlaybackBufferedProgressOwner fromPlayerProvider(
            PlaybackPositionProvider playbackPositionProvider,
            PlayerProvider playerProvider
    ) {
        return new PlaybackBufferedProgressOwner(playbackPositionProvider, playerProvider);
    }

    @Override
    public float bufferedProgress(long durationMs) {
        if (durationMs <= 0L) {
            return 0.0f;
        }
        Player player = player();
        if (player == null) {
            return 0.0f;
        }
        try {
            long positionMs = playbackPositionProvider == null ? 0L : playbackPositionProvider.positionMs();
            long bufferedMs = Math.max(positionMs, player.getBufferedPosition());
            return Math.max(0.0f, Math.min(1.0f, bufferedMs / (float) durationMs));
        } catch (IllegalStateException ignored) {
            return 0.0f;
        }
    }

    private Player player() {
        return playerProvider == null ? null : playerProvider.player();
    }
}
