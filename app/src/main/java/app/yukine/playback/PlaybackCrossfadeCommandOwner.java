package app.yukine.playback;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackCrossfadeCommandOwner implements PlaybackCrossfadeAdvanceManager.Actions {
    private final Consumer<Boolean> transitionState;
    private final Consumer<Float> playerVolumeController;
    private final Runnable immediateSkipCommand;
    private final Runnable appVolumeApplier;
    private final Supplier<PlaybackCrossfadeAdvanceManager> crossfadeAdvanceManagerProvider;

    PlaybackCrossfadeCommandOwner(
            Consumer<Boolean> transitionState,
            Consumer<Float> playerVolumeController,
            Runnable immediateSkipCommand,
            Runnable appVolumeApplier
    ) {
        this(transitionState, playerVolumeController, immediateSkipCommand, appVolumeApplier, null);
    }

    PlaybackCrossfadeCommandOwner(
            Consumer<Boolean> transitionState,
            Consumer<Float> playerVolumeController,
            Runnable immediateSkipCommand,
            Runnable appVolumeApplier,
            Supplier<PlaybackCrossfadeAdvanceManager> crossfadeAdvanceManagerProvider
    ) {
        this.transitionState = transitionState;
        this.playerVolumeController = playerVolumeController;
        this.immediateSkipCommand = immediateSkipCommand;
        this.appVolumeApplier = appVolumeApplier;
        this.crossfadeAdvanceManagerProvider = crossfadeAdvanceManagerProvider;
    }

    @Override
    public void setFadeOutAdvancing(boolean enabled) {
        transitionState.accept(enabled);
    }

    @Override
    public void setPlayerVolume(float volume) {
        playerVolumeController.accept(volume);
    }

    @Override
    public void skipToNextImmediately() {
        immediateSkipCommand.run();
    }

    @Override
    public void applyAppVolume() {
        appVolumeApplier.run();
    }

    void cancelCrossfadeAdvance() {
        PlaybackCrossfadeAdvanceManager manager = crossfadeAdvanceManager();
        if (manager != null) {
            manager.cancel();
        }
    }

    boolean startFadeOutThenNext() {
        PlaybackCrossfadeAdvanceManager manager = crossfadeAdvanceManager();
        return manager != null && manager.startFadeOutThenNext();
    }

    private PlaybackCrossfadeAdvanceManager crossfadeAdvanceManager() {
        return crossfadeAdvanceManagerProvider == null
                ? null
                : crossfadeAdvanceManagerProvider.get();
    }
}
