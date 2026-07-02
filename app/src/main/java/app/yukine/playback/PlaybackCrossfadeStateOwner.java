package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    interface BaseVolumeProvider {
        float baseVolume();
    }

    private final BooleanSupplier transitionStateProvider;
    private final BooleanSupplier playerAvailabilityProvider;
    private final BooleanSupplier playbackStateProvider;
    private final IntSupplier repeatModeProvider;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;
    private final BaseVolumeProvider baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier,
            BaseVolumeProvider baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.queueStateSupplier = queueStateSupplier;
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
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        if (!queueStateSnapshot.getHasMultipleTracks()) {
            return false;
        }
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !queueStateSnapshot.isAtEndOfQueue();
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider.baseVolume();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSupplier == null
                ? null
                : queueStateSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
