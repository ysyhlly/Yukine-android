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

    interface FailedStreamingTrackRefresher {
        boolean refresh(Track failed);
    }

    private final CurrentTrackProvider currentTrackProvider;
    private final FailedTrackPolicy failedTrackPolicy;
    private final PlaybackPreparer playbackPreparer;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final ErrorMessageStore errorMessageStore;
    private final StatePublisher statePublisher;
    private final WarningLogger warningLogger;
    private final FailedStreamingTrackRefresher failedStreamingTrackRefresher;

    PlaybackErrorRecoveryCommandOwner(
            CurrentTrackProvider currentTrackProvider,
            FailedTrackPolicy failedTrackPolicy,
            PlaybackPreparer playbackPreparer,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            ErrorMessageStore errorMessageStore,
            StatePublisher statePublisher,
            WarningLogger warningLogger
    ) {
        this(
                currentTrackProvider,
                failedTrackPolicy,
                playbackPreparer,
                playbackCommands,
                errorMessageStore,
                statePublisher,
                warningLogger,
                null
        );
    }

    PlaybackErrorRecoveryCommandOwner(
            CurrentTrackProvider currentTrackProvider,
            FailedTrackPolicy failedTrackPolicy,
            PlaybackPreparer playbackPreparer,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            ErrorMessageStore errorMessageStore,
            StatePublisher statePublisher,
            WarningLogger warningLogger,
            FailedStreamingTrackRefresher failedStreamingTrackRefresher
    ) {
        this.currentTrackProvider = currentTrackProvider;
        this.failedTrackPolicy = failedTrackPolicy;
        this.playbackPreparer = playbackPreparer;
        this.playbackCommands = playbackCommands;
        this.errorMessageStore = errorMessageStore;
        this.statePublisher = statePublisher;
        this.warningLogger = warningLogger;
        this.failedStreamingTrackRefresher = failedStreamingTrackRefresher;
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
    public boolean refreshStreamingTrack(Track failed) {
        return failedStreamingTrackRefresher != null && failedStreamingTrackRefresher.refresh(failed);
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
