package app.yukine.playback;

import app.yukine.playback.manager.PlaybackRecoveryScheduler;

final class PlaybackRecoveryCommandOwner implements PlaybackRecoveryScheduler.Actions {
    interface PlaybackPreparer {
        void prepareCurrent(boolean playWhenReady);
    }

    private final PlaybackPreparer playbackPreparer;

    PlaybackRecoveryCommandOwner(PlaybackPreparer playbackPreparer) {
        this.playbackPreparer = playbackPreparer;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.prepareCurrent(playWhenReady);
    }
}
