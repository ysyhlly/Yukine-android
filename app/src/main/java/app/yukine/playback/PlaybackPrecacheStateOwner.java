package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

import java.util.function.Supplier;

final class PlaybackPrecacheStateOwner implements PlaybackPrecacheManager.StateProvider {
    interface PlayerOperations {
        int playbackState();

        int mediaItemCount();

        MediaItem currentMediaItem();
    }

    private final Supplier<Track> currentTrackSupplier;
    private final Supplier<MediaItem> playerMediaItemSupplier;
    private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsSupplier;

    PlaybackPrecacheStateOwner(
            Supplier<Track> currentTrackSupplier,
            Supplier<MediaItem> playerMediaItemSupplier,
            Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsSupplier
    ) {
        this.currentTrackSupplier = currentTrackSupplier;
        this.playerMediaItemSupplier = playerMediaItemSupplier;
        this.streamingDiagnosticsSupplier = streamingDiagnosticsSupplier;
    }

    static Supplier<MediaItem> playerMediaItemSupplierFromPlayerSupplier(Supplier<Player> playerSupplier) {
        return playerMediaItemSupplierFromOperationsSupplier(() -> {
            Player player = playerSupplier == null ? null : playerSupplier.get();
            return player == null ? null : new Media3PlayerOperations(player);
        });
    }

    static Supplier<MediaItem> playerMediaItemSupplierFromOperationsSupplier(
            Supplier<PlayerOperations> playerOperationsSupplier
    ) {
        return () -> {
            PlayerOperations playerOperations = playerOperationsSupplier == null
                    ? null
                    : playerOperationsSupplier.get();
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
        return currentTrackSupplier.get();
    }

    @Override
    public MediaItem currentPlayerMediaItem() {
        return playerMediaItemSupplier.get();
    }

    @Override
    public PlaybackStreamingDiagnostics streamingDiagnostics() {
        return streamingDiagnosticsSupplier.get();
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
