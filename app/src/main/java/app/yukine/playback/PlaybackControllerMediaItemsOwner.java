package app.yukine.playback;

import androidx.media3.common.MediaItem;

import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaLibraryCallback;

final class PlaybackControllerMediaItemsOwner implements PlaybackSessionCommandOwner.ControllerMediaItems {
    interface ControllerQueueResolver {
        PlaybackMediaLibraryCallback.ControllerQueue controllerQueueForMediaItems(
                List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs
        );
    }

    interface QueuePlayer {
        void playQueue(List<Track> tracks, int startIndex, long startPositionMs);
    }

    private final ControllerQueueResolver controllerQueueResolver;
    private final QueuePlayer queuePlayer;

    PlaybackControllerMediaItemsOwner(
            ControllerQueueResolver controllerQueueResolver,
            QueuePlayer queuePlayer
    ) {
        this.controllerQueueResolver = controllerQueueResolver;
        this.queuePlayer = queuePlayer;
    }

    @Override
    public boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        PlaybackMediaLibraryCallback.ControllerQueue controllerQueue =
                controllerQueueResolver.controllerQueueForMediaItems(mediaItems, startIndex, startPositionMs);
        if (controllerQueue == null) {
            return false;
        }
        queuePlayer.playQueue(
                controllerQueue.getTracks(),
                controllerQueue.getStartIndex(),
                controllerQueue.getStartPositionMs()
        );
        return true;
    }
}
