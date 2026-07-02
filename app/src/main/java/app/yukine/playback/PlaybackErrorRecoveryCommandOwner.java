package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions {
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable skipToNextCommand;
    private final Consumer<String> errorMessageStore;
    private final Runnable statePublisher;
    private final BiConsumer<String, Exception> warningLogger;

    PlaybackErrorRecoveryCommandOwner(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier,
            Consumer<Boolean> playbackPreparer,
            Runnable skipToNextCommand,
            Consumer<String> errorMessageStore,
            Runnable statePublisher,
            BiConsumer<String, Exception> warningLogger
    ) {
        this.queueStateSupplier = queueStateSupplier;
        this.playbackPreparer = playbackPreparer;
        this.skipToNextCommand = skipToNextCommand;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failed != null
                && failed.id != -1L
                && queueStateSnapshot().getHasMultipleTracks();
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

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSupplier == null
                ? null
                : queueStateSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
