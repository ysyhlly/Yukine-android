package app.yukine.playback;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

final class PlaybackMediaSourceResolutionOwner {
    interface MediaSourceResolver {
        MediaSource mediaSourceForTrack(Track track, MetadataProvider metadataProvider);

        List<MediaSource> mediaSourcesForTracks(List<Track> tracks, MetadataProvider metadataProvider);
    }

    interface MetadataProvider {
        MediaMetadata mediaMetadataForTrack(Track track);
    }

    private final Supplier<MediaSourceResolver> mediaSourceResolverSupplier;
    private final MetadataProvider metadataProvider;

    static PlaybackMediaSourceResolutionOwner fromMediaSourceProvider(
            PlaybackMediaSourceProvider mediaSourceProvider,
            MetadataProvider metadataProvider
    ) {
        return new PlaybackMediaSourceResolutionOwner(
                () -> mediaSourceProvider == null
                        ? null
                        : new PlaybackMediaSourceProviderResolver(mediaSourceProvider),
                metadataProvider
        );
    }

    PlaybackMediaSourceResolutionOwner(
            Supplier<MediaSourceResolver> mediaSourceResolverSupplier,
            MetadataProvider metadataProvider
    ) {
        this.mediaSourceResolverSupplier = mediaSourceResolverSupplier;
        this.metadataProvider = metadataProvider;
    }

    MediaSource mediaSourceForTrack(Track track) {
        MediaSourceResolver resolver = mediaSourceResolver();
        return resolver == null ? null : resolver.mediaSourceForTrack(track, metadataProvider);
    }

    List<MediaSource> mediaSourcesForTracks(List<Track> tracks) {
        MediaSourceResolver resolver = mediaSourceResolver();
        return resolver == null
                ? Collections.emptyList()
                : resolver.mediaSourcesForTracks(tracks, metadataProvider);
    }

    private MediaSourceResolver mediaSourceResolver() {
        return mediaSourceResolverSupplier == null ? null : mediaSourceResolverSupplier.get();
    }

    private static final class PlaybackMediaSourceProviderResolver implements MediaSourceResolver {
        private final PlaybackMediaSourceProvider mediaSourceProvider;

        private PlaybackMediaSourceProviderResolver(PlaybackMediaSourceProvider mediaSourceProvider) {
            this.mediaSourceProvider = mediaSourceProvider;
        }

        @Override
        public MediaSource mediaSourceForTrack(Track track, MetadataProvider metadataProvider) {
            return mediaSourceProvider.mediaSourceForTrack(
                    track,
                    metadataProvider == null ? null : metadataProvider::mediaMetadataForTrack
            );
        }

        @Override
        public List<MediaSource> mediaSourcesForTracks(List<Track> tracks, MetadataProvider metadataProvider) {
            return mediaSourceProvider.mediaSourcesForTracks(
                    tracks,
                    metadataProvider == null ? null : metadataProvider::mediaMetadataForTrack
            );
        }

    }
}
