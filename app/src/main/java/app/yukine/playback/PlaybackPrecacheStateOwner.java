package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

final class PlaybackPrecacheStateOwner implements PlaybackPrecacheManager.StateProvider {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface PlayerMediaItemProvider {
        MediaItem currentPlayerMediaItem();
    }

    interface PlayerProvider {
        Player player();
    }

    interface PlayerOperations {
        int playbackState();

        int mediaItemCount();

        MediaItem currentMediaItem();
    }

    interface PlayerOperationsProvider {
        PlayerOperations playerOperations();
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

    static PlayerMediaItemProvider playerMediaItemProviderFromPlayerProvider(PlayerProvider playerProvider) {
        return playerMediaItemProviderFromOperationsProvider(() -> {
            Player player = playerProvider == null ? null : playerProvider.player();
            return player == null ? null : new Media3PlayerOperations(player);
        });
    }

    static PlayerMediaItemProvider playerMediaItemProviderFromOperationsProvider(
            PlayerOperationsProvider playerOperationsProvider
    ) {
        return () -> {
            PlayerOperations playerOperations = playerOperationsProvider == null
                    ? null
                    : playerOperationsProvider.playerOperations();
            if (playerOperations == null) {
                return null;
            }
            try {
                if (playerOperations.playbackState() == Player.STATE_IDLE
                        || playerOperations.mediaItemCount() <= 0) {
                    return null;
                }
                return playerOperations.currentMediaItem();
            } catch (IllegalStateException ignored) {
                return null;
            }
        };
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

    private static final class Media3PlayerOperations implements PlayerOperations {
        private final Player player;

        private Media3PlayerOperations(Player player) {
            this.player = player;
        }

        @Override
        public int playbackState() {
            return player.getPlaybackState();
        }

        @Override
        public int mediaItemCount() {
            return player.getMediaItemCount();
        }

        @Override
        public MediaItem currentMediaItem() {
            return player.getCurrentMediaItem();
        }
    }
}
