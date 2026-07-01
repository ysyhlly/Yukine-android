package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class PlaybackPrecacheStateOwner implements PlaybackPrecacheManager.StateProvider {
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
        return () -> {
            Player player = playerSupplier == null ? null : playerSupplier.get();
            if (player == null) {
                return null;
            }
            return currentMediaItemForPlayerState(
                    player::getPlaybackState,
                    player::getMediaItemCount,
                    player::getCurrentMediaItem
            );
        };
    }

    static Supplier<MediaItem> playerMediaItemSupplierFromStateSuppliers(
            IntSupplier playbackStateSupplier,
            IntSupplier mediaItemCountSupplier,
            Supplier<MediaItem> currentMediaItemSupplier
    ) {
        return () -> currentMediaItemForPlayerState(
                playbackStateSupplier,
                mediaItemCountSupplier,
                currentMediaItemSupplier
        );
    }

    private static MediaItem currentMediaItemForPlayerState(
            IntSupplier playbackStateSupplier,
            IntSupplier mediaItemCountSupplier,
            Supplier<MediaItem> currentMediaItemSupplier
    ) {
        if (playbackStateSupplier == null
                || mediaItemCountSupplier == null
                || currentMediaItemSupplier == null) {
            return null;
        }
        try {
            if (playbackStateSupplier.getAsInt() == Player.STATE_IDLE
                    || mediaItemCountSupplier.getAsInt() <= 0) {
                return null;
            }
            return currentMediaItemSupplier.get();
        } catch (IllegalStateException ignored) {
            return null;
        }
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
}
