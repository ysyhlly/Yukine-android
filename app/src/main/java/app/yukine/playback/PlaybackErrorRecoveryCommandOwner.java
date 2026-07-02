package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;

final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions {
    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface FailedTrackPolicy {
        boolean canSkipFailedTrack(Track failed);
    }

    interface PlaybackPreparer {
        void prepareCurrent(boolean playWhenReady);
    }

    interface ErrorMessageStore {
        void setErrorMessage(String message);
    }

    interface StatePublisher {
        void publishState();
    }

    interface WarningLogger {
        void logWarning(String message, Exception error);
    }

    private final CurrentTrackProvider currentTrackProvider;
    private final FailedTrackPolicy failedTrackPolicy;
    private final PlaybackPreparer playbackPreparer;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final ErrorMessageStore errorMessageStore;
    private final StatePublisher statePublisher;
    private final WarningLogger warningLogger;

    PlaybackErrorRecoveryCommandOwner(
            CurrentTrackProvider currentTrackProvider,
            FailedTrackPolicy failedTrackPolicy,
            PlaybackPreparer playbackPreparer,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            ErrorMessageStore errorMessageStore,
            StatePublisher statePublisher,
            WarningLogger warningLogger
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.failedTrackPolicy = failedTrackPolicy;
        this.playbackPreparer = playbackPreparer;
        this.playbackCommands = playbackCommands;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
    }

    @Override
    public Track currentTrack() {
        return currentTrackProvider.currentTrack();
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failedTrackPolicy.canSkipFailedTrack(failed);
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
        playbackPreparer.prepareCurrent(playWhenReady);
    }

    @Override
    public void skipToNext() {
        playbackCommands.skipToNext();
    }

    @Override
    public void setErrorMessage(String message) {
        errorMessageStore.setErrorMessage(message);
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }

    @Override
    public void logWarning(String message, Exception error) {
        warningLogger.logWarning(message, error);
    }
}
