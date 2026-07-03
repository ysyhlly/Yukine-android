package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions {
    private final PlaybackQueueManager playbackQueueManager;
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable skipToNextCommand;
    private final Consumer<String> errorMessageStore;
    private final Runnable statePublisher;
    private final BiConsumer<String, Exception> warningLogger;

    PlaybackErrorRecoveryCommandOwner(
            PlaybackQueueManager playbackQueueManager,
            Consumer<Boolean> playbackPreparer,
            Runnable skipToNextCommand,
            Consumer<String> errorMessageStore,
            Runnable statePublisher,
            BiConsumer<String, Exception> warningLogger
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.playbackPreparer = playbackPreparer;
        this.skipToNextCommand = skipToNextCommand;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
    }

    @Override
    public Track currentTrack() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshot();
        return snapshot == null ? null : snapshot.getCurrentTrack();
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshot();
        return failed != null
                && failed.id != -1L
                && snapshot != null
                && snapshot.getHasMultipleTracks();
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

    String debugCurrentTrack() {
        return debugTrack(currentTrack());
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
        return playbackQueueManager == null ? null : playbackQueueManager.queueStateSnapshot();
    }
}
