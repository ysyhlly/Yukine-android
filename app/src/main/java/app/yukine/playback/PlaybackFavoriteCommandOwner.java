package app.yukine.playback;

import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;

import java.util.function.Supplier;

final class PlaybackFavoriteCommandOwner {
    private PlaybackFavoriteCommandOwner() {
    }

    static void toggleCurrentFavorite(
            Supplier<Track> currentTrackSupplier,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            Runnable statePublisher
    ) {
        Track track = currentTrack(currentTrackSupplier);
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            if (statePublisher != null) {
                statePublisher.run();
            }
        }
    }

    private static Track currentTrack(
            Supplier<Track> currentTrackSupplier
    ) {
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
    }
}
