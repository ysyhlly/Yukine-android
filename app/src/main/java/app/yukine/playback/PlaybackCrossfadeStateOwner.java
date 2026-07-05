package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    private final BooleanSupplier transitionStateProvider;
    private final BooleanSupplier playerAvailabilityProvider;
    private final BooleanSupplier playbackStateProvider;
    private final IntSupplier repeatModeProvider;
    private final PlaybackQueueManager playbackQueueManager;
    private final DoubleSupplier baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            BooleanSupplier transitionStateProvider,
            BooleanSupplier playerAvailabilityProvider,
            BooleanSupplier playbackStateProvider,
            IntSupplier repeatModeProvider,
            PlaybackQueueManager playbackQueueManager,
            DoubleSupplier baseVolumeProvider
    ) {
        this.transitionStateProvider = Objects.requireNonNull(transitionStateProvider, "transitionStateProvider");
        this.playerAvailabilityProvider = Objects.requireNonNull(
                playerAvailabilityProvider,
                "playerAvailabilityProvider"
        );
        this.playbackStateProvider = Objects.requireNonNull(playbackStateProvider, "playbackStateProvider");
        this.repeatModeProvider = Objects.requireNonNull(repeatModeProvider, "repeatModeProvider");
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.baseVolumeProvider = Objects.requireNonNull(baseVolumeProvider, "baseVolumeProvider");
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
        if (queueStateSnapshot.getQueueSize() < 2) {
            return false;
        }
        return repeatModeProvider.getAsInt() != REPEAT_OFF || !isAtEndOfQueue(queueStateSnapshot);
    }

    @Override
    public float baseVolume() {
        return (float) baseVolumeProvider.getAsDouble();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }

    private static boolean isAtEndOfQueue(PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot) {
        return queueStateSnapshot.getQueueSize() > 0
                && queueStateSnapshot.getCurrentIndex() >= queueStateSnapshot.getQueueSize() - 1;
    }
}
