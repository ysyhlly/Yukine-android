package app.yukine.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.List;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackSessionPlayer;

final class PlaybackSessionCommandOwner implements PlaybackSessionPlayer.Delegate {
    interface SeekController {
        void seekTo(long positionMs);
    }

    interface RepeatModeController {
        void setRepeatMode(int appRepeatMode);
    }

    interface ControllerMediaItems {
        boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs);
    }

    interface MetadataProvider {
        MediaMetadata mediaMetadataForTrack(Track track);
    }

    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final SeekController seekController;
    private final RepeatModeController repeatModeController;
    private final ControllerMediaItems controllerMediaItems;
    private final Supplier<Track> currentTrackSupplier;
    private final MetadataProvider metadataProvider;

    PlaybackSessionCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            SeekController seekController,
            RepeatModeController repeatModeController,
            ControllerMediaItems controllerMediaItems,
            Supplier<Track> currentTrackSupplier,
            MetadataProvider metadataProvider
    ) {
        this.playbackCommands = playbackCommands;
        this.seekController = seekController;
        this.repeatModeController = repeatModeController;
        this.controllerMediaItems = controllerMediaItems;
        this.currentTrackSupplier = currentTrackSupplier;
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
        seekController.seekTo(positionMs);
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
        repeatModeController.setRepeatMode(appRepeatMode);
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
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
    }

    @Override
    public MediaMetadata mediaMetadataForTrack(Track track) {
        return metadataProvider.mediaMetadataForTrack(track);
    }

}
