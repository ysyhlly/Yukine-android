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

    interface PlayerBufferProvider {
        long bufferedPositionMs();
    }

    interface PlayerBufferProviderSource {
        PlayerBufferProvider playerBufferProvider();
    }

    private final PlaybackPositionProvider playbackPositionProvider;
    private final PlayerBufferProviderSource playerBufferProviderSource;

    PlaybackBufferedProgressOwner(
            PlaybackPositionProvider playbackPositionProvider,
            PlayerBufferProviderSource playerBufferProviderSource
    ) {
        this.playbackPositionProvider = playbackPositionProvider;
        this.playerBufferProviderSource = playerBufferProviderSource;
    }

    static PlaybackBufferedProgressOwner fromPlayerProvider(
            PlaybackPositionProvider playbackPositionProvider,
            PlayerProvider playerProvider
    ) {
        return new PlaybackBufferedProgressOwner(
                playbackPositionProvider,
                () -> {
                    Player player = playerProvider == null ? null : playerProvider.player();
                    return player == null ? null : player::getBufferedPosition;
                }
        );
    }

    @Override
    public float bufferedProgress(long durationMs) {
        if (durationMs <= 0L) {
            return 0.0f;
        }
        PlayerBufferProvider playerBufferProvider = playerBufferProvider();
        if (playerBufferProvider == null) {
            return 0.0f;
        }
        try {
            long positionMs = playbackPositionProvider == null ? 0L : playbackPositionProvider.positionMs();
            long bufferedMs = Math.max(positionMs, playerBufferProvider.bufferedPositionMs());
            return Math.max(0.0f, Math.min(1.0f, bufferedMs / (float) durationMs));
        } catch (IllegalStateException ignored) {
            return 0.0f;
        }
    }

    private PlayerBufferProvider playerBufferProvider() {
        return playerBufferProviderSource == null ? null : playerBufferProviderSource.playerBufferProvider();
    }
}
