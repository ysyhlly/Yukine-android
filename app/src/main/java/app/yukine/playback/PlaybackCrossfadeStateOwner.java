package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider {
    interface TransitionStateProvider {
        boolean fadeOutAdvancing();
    }

    interface PlayerAvailabilityProvider {
        boolean playerAvailable();
    }

    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface RepeatModeProvider {
        int repeatMode();
    }

    interface QueueStateProvider {
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot();
    }

    interface BaseVolumeProvider {
        float baseVolume();
    }

    private final TransitionStateProvider transitionStateProvider;
    private final PlayerAvailabilityProvider playerAvailabilityProvider;
    private final PlaybackStateProvider playbackStateProvider;
    private final RepeatModeProvider repeatModeProvider;
    private final QueueStateProvider queueStateProvider;
    private final BaseVolumeProvider baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            TransitionStateProvider transitionStateProvider,
            PlayerAvailabilityProvider playerAvailabilityProvider,
            PlaybackStateProvider playbackStateProvider,
            RepeatModeProvider repeatModeProvider,
            QueueStateProvider queueStateProvider,
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
        return transitionStateProvider.fadeOutAdvancing();
    }

    @Override
    public boolean playerAvailable() {
        return playerAvailabilityProvider.playerAvailable();
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.isPlaying();
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
        return repeatModeProvider.repeatMode() != REPEAT_OFF || !snapshot.isAtEndOfQueue();
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider.baseVolume();
    }
}
