package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions {
    private final Supplier<Track> currentTrackSupplier;
    private final BooleanSupplier hasMultipleTracksSupplier;
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable skipToNextCommand;
    private final Consumer<String> errorMessageStore;
    private final Runnable statePublisher;
    private final BiConsumer<String, Exception> warningLogger;

    PlaybackErrorRecoveryCommandOwner(
            Supplier<Track> currentTrackSupplier,
            BooleanSupplier hasMultipleTracksSupplier,
            Consumer<Boolean> playbackPreparer,
            Runnable skipToNextCommand,
            Consumer<String> errorMessageStore,
            Runnable statePublisher,
            BiConsumer<String, Exception> warningLogger
    ) {
        this.currentTrackSupplier = currentTrackSupplier;
        this.hasMultipleTracksSupplier = hasMultipleTracksSupplier;
        this.playbackPreparer = playbackPreparer;
        this.skipToNextCommand = skipToNextCommand;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
    }

    @Override
    public Track currentTrack() {
        return currentTrackSupplier == null ? null : currentTrackSupplier.get();
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failed != null
                && failed.id != -1L
                && hasMultipleTracksSupplier != null
                && hasMultipleTracksSupplier.getAsBoolean();
    }

    @Override
    public String debugTrack(Track track) {
        if (track == null) {
            return "track=<null>";
        }
        return "trackId=" + track.id
                + ", title=" + track.title
                + ", dataPath=" + track.dataPath
                + ", uri=" + track.contentUri;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.accept(playWhenReady);
    }

    @Override
    public void skipToNext() {
        skipToNextCommand.run();
    }

    @Override
    public void setErrorMessage(String message) {
        errorMessageStore.accept(message);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void logWarning(String message, Exception error) {
        warningLogger.accept(message, error);
    }
}
