package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions {
    private final Supplier<Track> currentTrackProvider;
    private final Predicate<Track> failedTrackPolicy;
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable skipToNextCommand;
    private final Consumer<String> errorMessageStore;
    private final Runnable statePublisher;
    private final BiConsumer<String, Exception> warningLogger;

    PlaybackErrorRecoveryCommandOwner(
            Supplier<Track> currentTrackProvider,
            Predicate<Track> failedTrackPolicy,
            Consumer<Boolean> playbackPreparer,
            Runnable skipToNextCommand,
            Consumer<String> errorMessageStore,
            Runnable statePublisher,
            BiConsumer<String, Exception> warningLogger
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.failedTrackPolicy = failedTrackPolicy;
        this.playbackPreparer = playbackPreparer;
        this.skipToNextCommand = skipToNextCommand;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.get();
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failedTrackPolicy.test(failed);
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
