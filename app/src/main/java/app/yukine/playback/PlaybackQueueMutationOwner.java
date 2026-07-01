package app.yukine.playback;

import androidx.media3.common.C;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueMutationOwner implements PlaybackControllerMediaItemsOwner.QueuePlayer {
    private final PlaybackControllerMediaItemsOwner.QueuePlayer playQueue;
    private final Consumer<List<Track>> appendToQueue;
    private final Consumer<Set<Long>> removeTracksById;
    private final Consumer<Set<Long>> retainTracksById;
    private final Runnable clearQueue;
    private final BiConsumer<Integer, Integer> moveQueueTrack;
    private final Consumer<Track> replaceQueuedTrack;
    private final BiConsumer<Long, Track> replaceQueuedTrackById;

    PlaybackQueueMutationOwner(
            PlaybackControllerMediaItemsOwner.QueuePlayer playQueue,
            Consumer<List<Track>> appendToQueue,
            Consumer<Set<Long>> removeTracksById,
            Consumer<Set<Long>> retainTracksById,
            Runnable clearQueue,
            BiConsumer<Integer, Integer> moveQueueTrack,
            Consumer<Track> replaceQueuedTrack,
            BiConsumer<Long, Track> replaceQueuedTrackById
    ) {
        this.playQueue = playQueue;
        this.appendToQueue = appendToQueue;
        this.removeTracksById = removeTracksById;
        this.retainTracksById = retainTracksById;
        this.clearQueue = clearQueue;
        this.moveQueueTrack = moveQueueTrack;
        this.replaceQueuedTrack = replaceQueuedTrack;
        this.replaceQueuedTrackById = replaceQueuedTrackById;
    }

    static PlaybackQueueMutationOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueMutationOwner(
                (tracks, startIndex, startPositionMs) -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
                    }
                },
                tracks -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.appendToQueue(tracks);
                    }
                },
                trackIds -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.removeTracksById(trackIds);
                    }
                },
                trackIdsToKeep -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.retainTracksById(trackIdsToKeep);
                    }
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.clearQueue();
                    }
                },
                (fromIndex, toIndex) -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
                    }
                },
                replacement -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.replaceQueuedTrack(replacement);
                    }
                },
                (oldTrackId, replacement) -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement);
                    }
                }
        );
    }

    void playQueue(List<Track> tracks, int startIndex) {
        playQueue(tracks, startIndex, C.TIME_UNSET);
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (playQueue != null) {
            playQueue.playQueue(tracks, startIndex, startPositionMs);
        }
    }

    void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (appendToQueue != null) {
            appendToQueue.accept(tracks);
        }
    }

    void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        if (removeTracksById != null) {
            removeTracksById.accept(trackIds);
        }
    }

    void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            return;
        }
        if (retainTracksById != null) {
            retainTracksById.accept(trackIdsToKeep);
        }
    }

    void clearQueue() {
        if (clearQueue != null) {
            clearQueue.run();
        }
    }

    void moveQueueTrack(int fromIndex, int toIndex) {
        if (moveQueueTrack != null) {
            moveQueueTrack.accept(fromIndex, toIndex);
        }
    }

    void replaceQueuedTrack(Track replacement) {
        if (replaceQueuedTrack != null) {
            replaceQueuedTrack.accept(replacement);
        }
    }

    void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        if (replaceQueuedTrackById != null) {
            replaceQueuedTrackById.accept(oldTrackId, replacement);
        }
    }

    private static PlaybackQueueManager playbackQueueManager(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
