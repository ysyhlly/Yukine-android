package app.yukine.playback;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import app.yukine.common.StreamingDataPathMetadata;
import app.yukine.model.Track;
import app.yukine.together.TogetherPlayerPort;
import app.yukine.together.TogetherQueueItem;
import app.yukine.together.TogetherQueueSource;
import app.yukine.together.TogetherStableIds;

/**
 * Narrow Media3 boundary used by junto. Remote commands always enter through the playback
 * runtime's application-looper methods; protocol and session state remain in feature:together.
 */
public final class TogetherMedia3PlayerAdapter implements TogetherPlayerPort {
    private final PlaybackServiceRuntime runtime;

    TogetherMedia3PlayerAdapter(PlaybackServiceRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void play() {
        runtime.play();
    }

    @Override
    public void pause() {
        runtime.pause();
    }

    @Override
    public void seekTo(long positionMs) {
        runtime.seekTo(positionMs);
    }

    @Override
    public void setSpeed(float speed) {
        runtime.setPlaybackSpeed(speed);
    }

    @Override
    public void skipToQueueIndex(int index) {
        runtime.seekToTogetherQueueIndex(index);
    }

    @Override
    public long currentPositionMs() {
        PlaybackStateSnapshot snapshot = runtime.snapshot();
        return snapshot == null ? 0L : snapshot.positionMs;
    }

    @Override
    public int currentQueueIndex() {
        PlaybackStateSnapshot snapshot = runtime.snapshot();
        return snapshot == null ? 0 : snapshot.currentIndex;
    }

    @Override
    public List<Track> currentQueueTracks() {
        return runtime.queueSnapshot();
    }

    @Override
    public void setRoomPlaybackConstraints(boolean enabled) {
        runtime.setTogetherRoomActive(enabled);
    }

    @Override
    public void replaceQueueWithTracks(List<Track> tracks) {
        if (tracks != null && !tracks.isEmpty()) {
            runtime.replaceTogetherQueue(new ArrayList<>(tracks));
        }
    }

    @Override
    public void replaceQueueWithItems(List<TogetherQueueItem> queue) {
        List<Track> tracks = tracksForQueueItems(queue);
        if (!tracks.isEmpty()) {
            runtime.replaceTogetherQueue(tracks);
        }
    }

    @Override
    public void replaceQueueWithStreamUrls(List<TogetherQueueItem> queue, List<String> urls) {
        List<Track> tracks = tracksForStreamUrls(queue, urls);
        if (!tracks.isEmpty()) {
            runtime.replaceTogetherQueue(tracks);
        }
    }

    static List<Track> tracksForQueueItems(List<TogetherQueueItem> queue) {
        if (queue == null || queue.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> urls = new ArrayList<>(queue.size());
        for (TogetherQueueItem item : queue) {
            urls.add(item == null ? "" : item.getSourceUri());
        }
        return tracksForStreamUrls(queue, urls);
    }

    static List<Track> tracksForStreamUrls(
            List<TogetherQueueItem> queue,
            List<String> urls
    ) {
        List<Track> tracks = new ArrayList<>();
        if (queue == null || urls == null || queue.isEmpty() || queue.size() != urls.size()) {
            return tracks;
        }
        for (int i = 0; i < queue.size(); i++) {
            String url = urls.get(i);
            TogetherQueueItem item = queue.get(i);
            // Abort the whole replace when any row is missing a URL so Media3 length always
            // matches the Together room queue (remote index / currentIndex stay aligned).
            if (item == null || url == null || url.trim().isEmpty()) {
                return new ArrayList<>();
            }
            tracks.add(trackForStreamUrl(item, url));
        }
        return tracks;
    }

    static Track trackForStreamUrl(TogetherQueueItem item, String url) {
        String dataPath = url;
        long durationMs = item.getDurationMs();
        TogetherQueueSource source = item.getSource();
        if (source instanceof TogetherQueueSource.Streaming) {
            TogetherQueueSource.Streaming streaming = (TogetherQueueSource.Streaming) source;
            String streamingPath = StreamingDataPathMetadata.streamingDataPath(
                    streaming.getProvider(),
                    streaming.getProviderTrackId(),
                    streaming.getQuality(),
                    streaming.getMimeType(),
                    streaming.getLuoxueMusicInfoJson()
            );
            if (!streamingPath.trim().isEmpty()) {
                dataPath = streamingPath;
            }
            durationMs = Math.max(durationMs, streaming.getDurationMs());
        } else if (source instanceof TogetherQueueSource.Local) {
            String localUri = ((TogetherQueueSource.Local) source).getUri();
            if (localUri != null && !localUri.trim().isEmpty()) {
                dataPath = localUri;
            }
        }
        return new Track(
                TogetherStableIds.media3TrackId(item.getStableId()),
                item.getTitle(),
                item.getArtist(),
                item.getAlbum(),
                durationMs,
                Uri.parse(url),
                dataPath,
                0L,
                albumArtUri(item.getArtworkUri())
        );
    }

    private static Uri albumArtUri(String value) {
        return value == null || value.trim().isEmpty() ? null : Uri.parse(value);
    }

    /**
     * Maps Together queue stable ids onto Media3 track ids used for remove/retain.
     * Delegates to [TogetherStableIds] so catalog/mapper/adapter share one id space.
     */
    public static long media3TrackId(String stableId) {
        return TogetherStableIds.media3TrackId(stableId);
    }
}
