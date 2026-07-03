package app.yukine.playback;

import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;

final class PlaybackFavoriteCommandOwner {
    private PlaybackFavoriteCommandOwner() {
    }

    static void toggleCurrentFavorite(
            PlaybackQueueStateOwner queueStateOwner,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            Runnable statePublisher
    ) {
        Track track = currentTrack(queueStateOwner);
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            if (statePublisher != null) {
                statePublisher.run();
            }
        }
    }

    private static Track currentTrack(PlaybackQueueStateOwner queueStateOwner) {
        return queueStateOwner == null
                ? null
                : queueStateOwner.currentTrack();
    }
}
