package app.yukine.playback;

import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackFavoriteCommandOwner {
    private PlaybackFavoriteCommandOwner() {
    }

    static void toggleCurrentFavorite(
            PlaybackQueueStateOwner queueStateOwner,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            Runnable statePublisher
    ) {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateOwner.queueStateSnapshot();
        Track track = snapshot.getCurrentTrack();
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            if (statePublisher != null) {
                statePublisher.run();
            }
        }
    }
}
