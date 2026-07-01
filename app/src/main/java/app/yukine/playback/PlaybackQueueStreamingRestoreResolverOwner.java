package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

final class PlaybackQueueStreamingRestoreResolverOwner
        implements PlaybackQueueStreamingRestoreOwner.StreamingRestoreResolver {
    interface MediaSourceRestoreResolver {
        Track restoredTrackForPreparation(Track track);

        void restoreHeadersForDataPath(String dataPath);
    }

    private final MediaSourceRestoreResolver mediaSourceRestoreResolver;

    static PlaybackQueueStreamingRestoreResolverOwner fromMediaSourceProvider(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        return new PlaybackQueueStreamingRestoreResolverOwner(
                mediaSourceProvider == null
                        ? null
                        : new PlaybackMediaSourceRestoreResolver(mediaSourceProvider)
        );
    }

    PlaybackQueueStreamingRestoreResolverOwner(MediaSourceRestoreResolver mediaSourceRestoreResolver) {
        this.mediaSourceRestoreResolver = mediaSourceRestoreResolver;
    }

    @Override
    public Track restoredTrackForPreparation(Track track) {
        return mediaSourceRestoreResolver == null
                ? null
                : mediaSourceRestoreResolver.restoredTrackForPreparation(track);
    }

    @Override
    public void restoreHeadersForDataPath(String dataPath) {
        if (mediaSourceRestoreResolver != null) {
            mediaSourceRestoreResolver.restoreHeadersForDataPath(dataPath);
        }
    }

    private static final class PlaybackMediaSourceRestoreResolver implements MediaSourceRestoreResolver {
        private final PlaybackMediaSourceProvider mediaSourceProvider;

        private PlaybackMediaSourceRestoreResolver(PlaybackMediaSourceProvider mediaSourceProvider) {
            this.mediaSourceProvider = mediaSourceProvider;
        }

        @Override
        public Track restoredTrackForPreparation(Track track) {
            return mediaSourceProvider.restoredTrackForPreparation(track);
        }

        @Override
        public void restoreHeadersForDataPath(String dataPath) {
            mediaSourceProvider.restoreHeadersForDataPath(dataPath);
        }
    }
}
