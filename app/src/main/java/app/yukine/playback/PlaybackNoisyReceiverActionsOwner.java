package app.yukine.playback;

import app.yukine.playback.manager.PlaybackNoisyReceiverManager;

final class PlaybackNoisyReceiverActionsOwner implements PlaybackNoisyReceiverManager.Actions {
    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface PlaybackControls {
        void pause();
    }

    private final PlaybackStateProvider playbackStateProvider;
    private final PlaybackControls playbackControls;

    PlaybackNoisyReceiverActionsOwner(
            PlaybackStateProvider playbackStateProvider,
            PlaybackControls playbackControls
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.playbackControls = playbackControls;
    }

    @Override
    public void pauseIfPlaying() {
        if (playbackStateProvider != null && playbackStateProvider.isPlaying()
                && playbackControls != null) {
            playbackControls.pause();
        }
    }
}
