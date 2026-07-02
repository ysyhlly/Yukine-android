package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    interface BaseVolumeProvider {
        float baseVolume();
    }

    private final BooleanSupplier transitionStateProvider;
    private final BooleanSupplier playerAvailabilityProvider;
    private final BooleanSupplier playbackStateProvider;
    private final IntSupplier repeatModeProvider;
    private final BooleanSupplier hasMultipleTracksProvider;
    private final BooleanSupplier atEndOfQueueProvider;
    private final BaseVolumeProvider baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            BooleanSupplier hasMultipleTracksProvider,
            BooleanSupplier atEndOfQueueProvider,
            BaseVolumeProvider baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.hasMultipleTracksProvider = hasMultipleTracksProvider;
        this.atEndOfQueueProvider = atEndOfQueueProvider;
        this.baseVolumeProvider = baseVolumeProvider;
    }

    @Override
    public boolean fadeOutAdvancing() {
        return transitionStateProvider.getAsBoolean();
    }

    @Override
    public boolean playerAvailable() {
        return playerAvailabilityProvider.getAsBoolean();
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.getAsBoolean();
    }

    @Override
    public boolean canCrossfadeAdvance() {
        if (hasMultipleTracksProvider == null || !hasMultipleTracksProvider.getAsBoolean()) {
            return false;
        }
        boolean atEndOfQueue = atEndOfQueueProvider != null && atEndOfQueueProvider.getAsBoolean();
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !atEndOfQueue;
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider.baseVolume();
    }
}
