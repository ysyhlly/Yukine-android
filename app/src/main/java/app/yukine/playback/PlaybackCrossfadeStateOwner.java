package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    private final BooleanSupplier transitionStateProvider;
    private final BooleanSupplier playerAvailabilityProvider;
    private final BooleanSupplier playbackStateProvider;
    private final IntSupplier repeatModeProvider;
    private final PlaybackQueueStateOwner queueStateOwner;
    private final DoubleSupplier baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            PlaybackQueueStateOwner queueStateOwner,
            DoubleSupplier baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.queueStateOwner = queueStateOwner;
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
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateOwner.queueStateSnapshot();
        if (!snapshot.getHasMultipleTracks()) {
            return false;
        }
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !snapshot.isAtEndOfQueue();
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider == null ? 1.0f : (float) baseVolumeProvider.getAsDouble();
    }
}
