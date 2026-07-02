package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

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
    private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;
    private final BaseVolumeProvider baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider,
            BaseVolumeProvider baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.queueStateProvider = queueStateProvider;
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
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateProvider.queueStateSnapshot();
        if (snapshot == null) {
            snapshot = PlaybackQueueManager.QueueStateSnapshot.empty();
        }
        if (!snapshot.getHasMultipleTracks()) {
            return false;
        }
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !snapshot.isAtEndOfQueue();
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider.baseVolume();
    }
}
