package app.yukine.playback;

import java.util.function.BooleanSupplier;

import app.yukine.playback.manager.PlaybackNoisyReceiverManager;

final class PlaybackNoisyReceiverActionsOwner implements PlaybackNoisyReceiverManager.Actions {
    interface PlaybackControls {
        void pause();
    }

    private final BooleanSupplier playbackStateProvider;
    private final PlaybackControls playbackControls;

    PlaybackNoisyReceiverActionsOwner(
            BooleanSupplier playbackStateProvider,
            PlaybackControls playbackControls
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.playbackControls = playbackControls;
    }

    @Override
    public void pauseIfPlaying() {
        if (playbackStateProvider != null && playbackStateProvider.getAsBoolean()
                && playbackControls != null) {
            playbackControls.pause();
        }
    }
}
