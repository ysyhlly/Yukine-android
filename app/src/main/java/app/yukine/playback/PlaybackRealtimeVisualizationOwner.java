package app.yukine.playback;

final class PlaybackRealtimeVisualizationOwner
        implements PlaybackStateSnapshotOwner.RealtimeBeatProvider {
    private static final float[] EMPTY_BANDS = new float[0];

    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface RealtimeDataProvider {
        float beat();

        float[] bands();
    }

    private final PlaybackStateProvider playbackStateProvider;
    private final RealtimeDataProvider realtimeDataProvider;

    PlaybackRealtimeVisualizationOwner(
            PlaybackStateProvider playbackStateProvider,
            RealtimeDataProvider realtimeDataProvider
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.realtimeDataProvider = realtimeDataProvider;
    }

    static PlaybackRealtimeVisualizationOwner fromRealtimeBassDetector(
            PlaybackStateProvider playbackStateProvider,
            RealtimeBassDetector realtimeBassDetector
    ) {
        return new PlaybackRealtimeVisualizationOwner(
                playbackStateProvider,
                realtimeBassDetector == null ? null : new RealtimeDataProvider() {
                    @Override
                    public float beat() {
                        return realtimeBassDetector.beat();
                    }

                    @Override
                    public float[] bands() {
                        return realtimeBassDetector.bands();
                    }
                }
        );
    }

    @Override
    public float beat() {
        return canReadRealtimeData() ? realtimeDataProvider.beat() : 0f;
    }

    float[] bands() {
        if (!canReadRealtimeData()) {
            return EMPTY_BANDS;
        }
        float[] bands = realtimeDataProvider.bands();
        return bands == null ? EMPTY_BANDS : bands;
    }

    private boolean canReadRealtimeData() {
        return playbackStateProvider != null
                && realtimeDataProvider != null
                && playbackStateProvider.isPlaying();
    }
}
