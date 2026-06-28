package app.yukine.playback;

import android.net.Uri;
import android.os.Handler;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheWriter;
import app.yukine.model.Track;

final class PlaybackVisualizationCacheManager {
    private static final long VISUALIZATION_CACHE_BYTES = 64L * 1024L * 1024L;

    interface StateProvider {
        boolean isHttpUri(Uri uri);
        String cacheKeyForTrack(Track track);
        long continuousCachedBytes(String cacheKey);
        PlaybackTaskScheduler visualizationTaskScheduler();
        PlaybackCacheDependencies cacheDependencies();
        Handler mainHandler();
        Track currentTrack();
    }

    interface PlaybackCacheDependencies {
        androidx.media3.datasource.cache.CacheDataSource cacheDataSourceForTrack(Track track);
    }

    private final StateProvider stateProvider;

    PlaybackVisualizationCacheManager(StateProvider stateProvider) {
        this.stateProvider = stateProvider;
    }

    void scheduleVisualizationCache(Track track) {
        if (track == null || !stateProvider.isHttpUri(track.contentUri)) {
            return;
        }
        final Track visualTrack = track;
        stateProvider.mainHandler().post(() -> {
            Track active = stateProvider.currentTrack();
            if (active == null
                    || visualTrack.id != active.id
                    || active.contentUri == null
                    || !active.contentUri.equals(visualTrack.contentUri)) {
                return;
            }
            stateProvider.visualizationTaskScheduler().schedule(
                    PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                    () -> cacheVisualizationWindow(visualTrack)
            );
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cacheVisualizationWindow(Track track) {
        if (track == null || !stateProvider.isHttpUri(track.contentUri)) {
            return;
        }
        String cacheKey = stateProvider.cacheKeyForTrack(track);
        if (cacheKey == null || cacheKey.isEmpty()) {
            return;
        }
        long cached = stateProvider.continuousCachedBytes(cacheKey);
        if (cached >= VISUALIZATION_CACHE_BYTES) {
            return;
        }
        try {
            DataSpec dataSpec = new DataSpec.Builder()
                    .setUri(track.contentUri)
                    .setPosition(cached)
                    .setLength(VISUALIZATION_CACHE_BYTES - cached)
                    .setKey(cacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build();
            CacheWriter writer = new CacheWriter(
                    stateProvider.cacheDependencies().cacheDataSourceForTrack(track),
                    dataSpec,
                    new byte[16 * 1024],
                    null
            );
            writer.cache();
        } catch (Exception ignored) {
        }
    }
}
