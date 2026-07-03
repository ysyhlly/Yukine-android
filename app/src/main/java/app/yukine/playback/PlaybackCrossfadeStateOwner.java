package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    private final BooleanSupplier transitionStateProvider;
    private final BooleanSupplier playerAvailabilityProvider;
    private final BooleanSupplier playbackStateProvider;
    private final IntSupplier repeatModeProvider;
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final DoubleSupplier baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            DoubleSupplier baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
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
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshot();
        if (!snapshot.getHasMultipleTracks()) {
            return false;
        }
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !snapshot.isAtEndOfQueue();
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider == null ? 1.0f : (float) baseVolumeProvider.getAsDouble();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
