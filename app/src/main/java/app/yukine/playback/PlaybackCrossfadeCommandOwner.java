package app.yukine.playback;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;

final class PlaybackCrossfadeCommandOwner implements PlaybackCrossfadeAdvanceManager.Actions {
    interface TransitionState {
        void setFadeOutAdvancing(boolean enabled);
    }

    interface PlayerVolumeController {
        void setPlayerVolume(float volume);
    }

    interface ImmediateSkipCommand {
        void skipToNextImmediately();
    }

    interface AppVolumeApplier {
        void applyAppVolume();
    }

    private final TransitionState transitionState;
    private final PlayerVolumeController playerVolumeController;
    private final ImmediateSkipCommand immediateSkipCommand;
    private final AppVolumeApplier appVolumeApplier;

    PlaybackCrossfadeCommandOwner(
            TransitionState transitionState,
            PlayerVolumeController playerVolumeController,
            ImmediateSkipCommand immediateSkipCommand,
            AppVolumeApplier appVolumeApplier
    ) {
        this.transitionState = transitionState;
        this.playerVolumeController = playerVolumeController;
        this.immediateSkipCommand = immediateSkipCommand;
        this.appVolumeApplier = appVolumeApplier;
    }

    @Override
    public void setFadeOutAdvancing(boolean enabled) {
        transitionState.setFadeOutAdvancing(enabled);
    }

    @Override
    public void setPlayerVolume(float volume) {
        playerVolumeController.setPlayerVolume(volume);
    }

    @Override
    public void skipToNextImmediately() {
        immediateSkipCommand.skipToNextImmediately();
    }

    @Override
    public void applyAppVolume() {
        appVolumeApplier.applyAppVolume();
    }
}
