package app.yukine.playback.manager;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.cache.CacheDataSource;

import java.util.Collections;
import java.util.Map;

import app.yukine.model.Track;

public interface PlaybackMediaCacheOperations {
    static PlaybackMediaCacheOperations fromMediaSourceProvider(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        return new PlaybackMediaSourceProviderCacheOperations(mediaSourceProvider);
    }

    boolean tracksShareResolvedUriForReuse(Track current, Track candidate);

    long contentLengthForCacheKey(String cacheKey);

    String cacheKeyForPrecache(Track track);

    Map<String, String> headersForTrack(Track track);

    long cachedBytesInRange(String cacheKey, long position, long length);

    CacheDataSource cacheDataSourceForTrack(Track track);

    boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, Track track);
}

final class PlaybackMediaSourceProviderCacheOperations implements PlaybackMediaCacheOperations {
    private final PlaybackMediaSourceProvider mediaSourceProvider;

    PlaybackMediaSourceProviderCacheOperations(PlaybackMediaSourceProvider mediaSourceProvider) {
        this.mediaSourceProvider = mediaSourceProvider;
    }

    @Override
    public boolean tracksShareResolvedUriForReuse(Track current, Track candidate) {
        return mediaSourceProvider != null
                && mediaSourceProvider.tracksShareResolvedUriForReuse(current, candidate);
    }

    @Override
    public long contentLengthForCacheKey(String cacheKey) {
        return mediaSourceProvider == null ? -1L : mediaSourceProvider.contentLengthForCacheKey(cacheKey);
    }

    @Override
    public String cacheKeyForPrecache(Track track) {
        if (mediaSourceProvider == null || !mediaSourceProvider.isHttpTrack(track)) {
            return null;
        }
        return mediaSourceProvider.cacheKeyForTrack(track);
    }

    @Override
    public Map<String, String> headersForTrack(Track track) {
        return mediaSourceProvider == null ? Collections.emptyMap() : mediaSourceProvider.headersForTrack(track);
    }

    @Override
    public long cachedBytesInRange(String cacheKey, long position, long length) {
        if (mediaSourceProvider == null || cacheKey == null || cacheKey.isEmpty() || length <= 0L) {
            return 0L;
        }
        try {
            long cached = mediaSourceProvider.audioCache().getCachedLength(cacheKey, Math.max(0L, position), length);
            return cached > 0L ? cached : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    @Override
    public CacheDataSource cacheDataSourceForTrack(Track track) {
        if (mediaSourceProvider == null) {
            throw new IllegalStateException("Media cache operations are unavailable");
        }
        return mediaSourceProvider.cacheDataSourceForTrack(track);
    }

    @Override
    public boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, Track track) {
        return mediaSourceProvider != null
                && mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track);
    }
}
