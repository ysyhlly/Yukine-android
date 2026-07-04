package app.yukine.playback.manager;

import androidx.media3.datasource.cache.CacheDataSource;

import java.net.HttpURLConnection;
import java.net.URL;
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

    long probeSegmentedPrecacheContentLength(Track track, String cacheKey, long start, long length);

    long cachedBytesInRange(String cacheKey, long position, long length);

    CacheDataSource cacheDataSourceForTrack(Track track);

    void releaseAudioCache();
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
    public long probeSegmentedPrecacheContentLength(Track track, String cacheKey, long start, long length) {
        if (mediaSourceProvider == null
                || track == null
                || track.contentUri == null
                || cacheKey == null
                || cacheKey.isEmpty()
                || length <= 0L) {
            return -1L;
        }
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(track.contentUri.toString()).openConnection();
            try {
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                for (Map.Entry<String, String> entry : mediaSourceProvider.headersForTrack(track).entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                long safeStart = Math.max(0L, start);
                connection.setRequestProperty(
                        "Range",
                        "bytes=" + safeStart + "-" + (safeStart + length - 1)
                );
                int responseCode = connection.getResponseCode();
                long totalBytes = totalBytesFromContentRange(connection.getHeaderField("Content-Range"));
                return responseCode == HttpURLConnection.HTTP_PARTIAL && totalBytes > safeStart
                        ? totalBytes
                        : -1L;
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return -1L;
        }
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
    public void releaseAudioCache() {
        if (mediaSourceProvider != null) {
            mediaSourceProvider.releaseAudioCache();
        }
    }

    static long totalBytesFromContentRange(String contentRange) {
        if (contentRange == null || contentRange.trim().isEmpty()) {
            return -1L;
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash >= contentRange.length() - 1) {
            return -1L;
        }
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

}
