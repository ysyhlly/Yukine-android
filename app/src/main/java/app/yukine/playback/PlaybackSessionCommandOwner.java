package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackSessionPlayer;

final class PlaybackSessionCommandOwner implements PlaybackSessionPlayer.Delegate {
    interface ControllerMediaItems {
        boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs);
    }

    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final LongConsumer seekController;
    private final IntConsumer repeatModeController;
    private final ControllerMediaItems controllerMediaItems;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier;
    private final Function<Track, MediaMetadata> metadataProvider;

    PlaybackSessionCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            LongConsumer seekController,
            IntConsumer repeatModeController,
            ControllerMediaItems controllerMediaItems,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        this.playbackCommands = playbackCommands;
        this.seekController = seekController;
        this.repeatModeController = repeatModeController;
        this.controllerMediaItems = controllerMediaItems;
        this.queueStateSnapshotSupplier = queueStateSnapshotSupplier;
        this.metadataProvider = metadataProvider;
    }

    @Override
    public void play() {
        playbackCommands.play();
    }

    @Override
    public void pause() {
        playbackCommands.pause();
    }

    @Override
    public void seekTo(long positionMs) {
        seekController.accept(positionMs);
    }

    @Override
    public void skipToPrevious() {
        playbackCommands.skipToPrevious();
    }

    @Override
    public void skipToNext() {
        playbackCommands.skipToNext();
    }

    @Override
    public void setRepeatMode(int appRepeatMode) {
        repeatModeController.accept(appRepeatMode);
    }

    @Override
    public void stopAndClear() {
        playbackCommands.stopAndClear();
    }

    @Override
    public boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        return controllerMediaItems.setControllerMediaItems(mediaItems, startIndex, startPositionMs);
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public MediaMetadata mediaMetadataForTrack(Track track) {
        return metadataProvider.apply(track);
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
