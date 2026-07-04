package app.yukine.playback;

import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;

final class PlaybackFavoriteCommandOwner {
    private PlaybackFavoriteCommandOwner() {
    }

    static void toggleCurrentFavorite(
            PlaybackQueueManager playbackQueueManager,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            Runnable statePublisher
    ) {
        Track track = currentTrack(playbackQueueManager);
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            if (statePublisher != null) {
                statePublisher.run();
            }
        }
    }

    private static Track currentTrack(
            PlaybackQueueManager playbackQueueManager
    ) {
        PlaybackQueueManager.QueueStateSnapshot snapshot =
                Objects.requireNonNull(playbackQueueManager, "playbackQueueManager").queueStateSnapshot();
        return snapshot.getCurrentTrack();
    }
}
