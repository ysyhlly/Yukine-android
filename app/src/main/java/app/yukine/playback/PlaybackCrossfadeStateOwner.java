package app.yukine.playback;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;

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

    interface CrossfadeAdvancePolicy {
        boolean canCrossfadeAdvance(int repeatMode);
    }

    interface BaseVolumeProvider {
        float baseVolume();
    }

    private final TransitionStateProvider transitionStateProvider;
    private final PlayerAvailabilityProvider playerAvailabilityProvider;
    private final PlaybackStateProvider playbackStateProvider;
    private final RepeatModeProvider repeatModeProvider;
    private final CrossfadeAdvancePolicy crossfadeAdvancePolicy;
    private final BaseVolumeProvider baseVolumeProvider;

    PlaybackCrossfadeStateOwner(
            TransitionStateProvider transitionStateProvider,
            PlayerAvailabilityProvider playerAvailabilityProvider,
            PlaybackStateProvider playbackStateProvider,
            RepeatModeProvider repeatModeProvider,
            CrossfadeAdvancePolicy crossfadeAdvancePolicy,
            BaseVolumeProvider baseVolumeProvider
    ) {
        this.transitionStateProvider = transitionStateProvider;
        this.playerAvailabilityProvider = playerAvailabilityProvider;
        this.playbackStateProvider = playbackStateProvider;
        this.repeatModeProvider = repeatModeProvider;
        this.crossfadeAdvancePolicy = crossfadeAdvancePolicy;
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
        return crossfadeAdvancePolicy.canCrossfadeAdvance(repeatModeProvider.repeatMode());
    }

    @Override
    public float baseVolume() {
        return baseVolumeProvider.baseVolume();
    }
}
