package app.yukine.playback;

import android.os.Process;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackMediaCacheOperations;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

final class PlaybackPrecacheManager {
    static final int PRECACHE_BYTES = 512 * 1024;
    static final int UPCOMING_TRACK_PRECACHE_BYTES = 256 * 1024;
    static final long SEGMENTED_PRECACHE_BYTES = 12L * 1024L * 1024L;
    static final long SEGMENTED_PRECACHE_CHUNK_BYTES = 1024L * 1024L;
    static final int SEGMENTED_PRECACHE_CONCURRENCY = 4;
    static final int PLAYBACK_CACHE_PRIORITY_QUEUE_INITIAL_CAPACITY = SEGMENTED_PRECACHE_CONCURRENCY * 8;
    static final int PLAYBACK_CACHE_QUEUE_CAPACITY = SEGMENTED_PRECACHE_CONCURRENCY * 8;
    static final int PRECACHE_RANGE_PROBE_BYTES = 1;
    static final long CURRENT_TRACK_LEADING_PRECACHE_DELAY_MS = 0L;
    static final long CURRENT_TRACK_SEGMENTED_PRECACHE_DELAY_MS = 250L;
    static final long UPCOMING_TRACK_PRECACHE_DELAY_MS = 5500L;

    interface CallbackScheduler {
        void postDelayed(Runnable runnable, long delayMs);
        void removeCallbacks(Runnable runnable);
    }

    private final Supplier<MediaItem> currentPlayerMediaItemSupplier;
    private final PlaybackQueueStateOwner queueStateOwner;
    private final PlaybackMediaCacheOperations mediaCacheOperations;
    private final PlaybackStreamingDiagnostics streamingDiagnostics;
    private final BiPredicate<MediaItem, Track> mediaItemTrackMatcher;
    private final CallbackScheduler callbackScheduler;
    private final Runnable audioCacheReleaseAction;
    private final ThreadPoolExecutor playbackCacheExecutor;
    private final Set<String> activePrecacheRanges =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<CacheWriter> activePrecacheWriters =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final List<Runnable> pendingPrecacheCallbacks =
            Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger precacheGeneration = new AtomicInteger();
    private final AtomicBoolean audioCacheReleased = new AtomicBoolean();
    private volatile boolean released;
    private volatile String lastPrecacheKey = "";

    PlaybackPrecacheManager(
            Supplier<MediaItem> currentPlayerMediaItemSupplier,
            PlaybackStreamingDiagnostics streamingDiagnostics,
            PlaybackQueueStateOwner queueStateOwner,
            PlaybackMediaCacheOperations mediaCacheOperations,
            BiPredicate<MediaItem, Track> mediaItemTrackMatcher,
            CallbackScheduler callbackScheduler,
            Runnable audioCacheReleaseAction
    ) {
        this(
                currentPlayerMediaItemSupplier,
                streamingDiagnostics,
                queueStateOwner,
                mediaCacheOperations,
                mediaItemTrackMatcher,
                callbackScheduler,
                audioCacheReleaseAction,
                newPlaybackCacheExecutor()
        );
    }

    PlaybackPrecacheManager(
            Supplier<MediaItem> currentPlayerMediaItemSupplier,
            PlaybackStreamingDiagnostics streamingDiagnostics,
            PlaybackQueueStateOwner queueStateOwner,
            PlaybackMediaCacheOperations mediaCacheOperations,
            BiPredicate<MediaItem, Track> mediaItemTrackMatcher,
            CallbackScheduler callbackScheduler,
            Runnable audioCacheReleaseAction,
            ThreadPoolExecutor playbackCacheExecutor
    ) {
        this.currentPlayerMediaItemSupplier = currentPlayerMediaItemSupplier;
        this.streamingDiagnostics = streamingDiagnostics == null
                ? new PlaybackStreamingDiagnostics()
                : streamingDiagnostics;
        this.queueStateOwner = queueStateOwner == null ? new PlaybackQueueStateOwner() : queueStateOwner;
        this.mediaCacheOperations = mediaCacheOperations;
        this.mediaItemTrackMatcher = mediaItemTrackMatcher;
        this.callbackScheduler = callbackScheduler;
        this.audioCacheReleaseAction = audioCacheReleaseAction;
        this.playbackCacheExecutor = playbackCacheExecutor == null
                ? newPlaybackCacheExecutor()
                : playbackCacheExecutor;
    }

    static PlaybackPrecacheManager fromMediaSourceProvider(
            Supplier<MediaItem> currentPlayerMediaItemSupplier,
            PlaybackStreamingDiagnostics streamingDiagnostics,
            PlaybackQueueStateOwner queueStateOwner,
            PlaybackMediaSourceProvider mediaSourceProvider,
            CallbackScheduler callbackScheduler
    ) {
        return new PlaybackPrecacheManager(
                currentPlayerMediaItemSupplier,
                streamingDiagnostics,
                queueStateOwner,
                PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider),
                (mediaItem, track) -> mediaSourceProvider != null
                        && mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track),
                callbackScheduler,
                () -> {
                    if (mediaSourceProvider != null) {
                        mediaSourceProvider.releaseAudioCache();
                    }
                }
        );
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        precacheGeneration.incrementAndGet();
        cancelPendingPrecacheCallbacks();
        activePrecacheRanges.clear();
        cancelActivePrecacheWriters();
        playbackCacheExecutor.shutdownNow();
        releaseAudioCache();
    }

    void releaseAudioCache() {
        if (audioCacheReleaseAction != null && audioCacheReleased.compareAndSet(false, true)) {
            audioCacheReleaseAction.run();
        }
    }

    void precacheTrack(Track track) {
        String cacheKey = cacheKeyForPrecache(track);
        if (released || cacheKey == null) {
            return;
        }
        if (cacheKey.equals(lastPrecacheKey) && shouldKeepExistingPrecache(cacheKey)) {
            return;
        }
        Track current = currentTrack();
        if (mediaCacheOperations.tracksShareResolvedUriForReuse(current, track)) {
            scheduleCurrentTrackPrecache(track);
            return;
        }
        if (current != null) {
            int generation = precacheGeneration.get();
            final Track upcomingTrack = track;
            streamingDiagnostics.recordPrecacheQueued(upcomingTrack);
            submitPlaybackCacheTask(
                    PrecachePriority.UPCOMING_TRACK,
                    () -> precacheWithMediaCache(upcomingTrack, generation, PrecacheMode.UPCOMING_TRACK, false)
            );
            return;
        }
        lastPrecacheKey = cacheKey;
        int generation = precacheGeneration.incrementAndGet();
        activePrecacheRanges.clear();
        cancelPendingPrecacheCallbacks();
        cancelActivePrecacheWriters();
        playbackCacheExecutor.getQueue().clear();
        playbackCacheExecutor.purge();
        final Track precacheTrack = track;
        streamingDiagnostics.recordPrecacheQueued(precacheTrack);
        scheduleCurrentTrackPrecache(precacheTrack, generation);
    }

    private void scheduleCurrentTrackPrecache(Track track) {
        String cacheKey = cacheKeyForPrecache(track);
        if (cacheKey == null) {
            return;
        }
        if (cacheKey.equals(lastPrecacheKey) && shouldKeepExistingPrecache(cacheKey)) {
            return;
        }
        lastPrecacheKey = cacheKey;
        int generation = precacheGeneration.incrementAndGet();
        activePrecacheRanges.clear();
        cancelPendingPrecacheCallbacks();
        cancelActivePrecacheWriters();
        playbackCacheExecutor.getQueue().clear();
        playbackCacheExecutor.purge();
        streamingDiagnostics.recordPrecacheQueued(track);
        scheduleCurrentTrackPrecache(track, generation);
    }

    private void scheduleCurrentTrackPrecache(Track track, int generation) {
        final Track precacheTrack = track;
        final String cacheKey = cacheKeyForPrecache(precacheTrack);
        if (cacheKey == null) {
            return;
        }
        Runnable task = () -> {
            Track current = currentTrack();
            if (!mediaCacheOperations.tracksShareResolvedUriForReuse(current, precacheTrack)
                    || !isCurrentPrecacheGeneration(generation, cacheKey)) {
                return;
            }
            final boolean playerAlreadyLoadsLeadingRange =
                    currentPlayerLoadsTrack(precacheTrack);
            submitPlaybackCacheTask(
                    PrecachePriority.CURRENT_LEADING,
                    () -> precacheWithMediaCache(
                            precacheTrack,
                            generation,
                            PrecacheMode.CURRENT_TRACK,
                            playerAlreadyLoadsLeadingRange
                    )
            );
            scheduleCurrentSegmentedPrecache(precacheTrack, cacheKey, generation);
            scheduleUpcomingTrackPrecache(generation);
        };
        if (CURRENT_TRACK_LEADING_PRECACHE_DELAY_MS <= 0L) {
            task.run();
        } else {
            postDelayedPrecacheCallback(task, CURRENT_TRACK_LEADING_PRECACHE_DELAY_MS);
        }
    }

    private boolean shouldKeepExistingPrecache(String cacheKey) {
        if (!activePrecacheRanges.isEmpty()
                || playbackCacheExecutor.getActiveCount() > 0
                || !playbackCacheExecutor.getQueue().isEmpty()) {
            return true;
        }
        if (cacheKey == null || cacheKey.isEmpty()) {
            return false;
        }
        long contentLength = mediaCacheOperations.contentLengthForCacheKey(cacheKey);
        long targetBytes = contentLength > 0L
                ? Math.min(contentLength, SEGMENTED_PRECACHE_BYTES)
                : SEGMENTED_PRECACHE_BYTES;
        return targetBytes > 0L && cachedBytesInRange(cacheKey, 0L, targetBytes) >= targetBytes;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void precacheUpcomingTracks(int generation) {
        for (Track upcomingTrack : upcomingTracksForPrecache()) {
            if (cacheKeyForPrecache(upcomingTrack) == null) {
                continue;
            }
            submitPlaybackCacheTask(
                    PrecachePriority.UPCOMING_TRACK,
                    () -> precacheWithMediaCache(upcomingTrack, generation, PrecacheMode.UPCOMING_TRACK, false)
            );
        }
    }

    private List<Track> upcomingTracksForPrecache() {
        return queueStateOwner.upcomingTracksForPrecache(SEGMENTED_PRECACHE_CONCURRENCY);
    }

    private Track currentTrack() {
        return queueStateOwner.currentTrack();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void precacheWithMediaCache(
            Track track,
            int generation,
            PrecacheMode mode,
            boolean playerAlreadyLoadsLeadingRange
    ) {
        String cacheKey = cacheKeyForPrecache(track);
        if (cacheKey == null) {
            return;
        }
        if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
            return;
        }
        try {
            long leadingTargetBytes = leadingPrecacheBytes(mode);
            if (shouldLetPlayerFillCurrentLeadingRange(mode, playerAlreadyLoadsLeadingRange)) {
                streamingDiagnostics.recordPrecacheComplete(track, 0L);
                return;
            }
            long leadingBytes = cacheMediaRange(track, cacheKey, 0L, leadingTargetBytes, generation);
            if (leadingBytes > 0L || leadingTargetBytes <= 0L) {
                streamingDiagnostics.recordPrecacheComplete(track, leadingBytes);
            }
        } catch (PrecacheSupersededException ignored) {
        } catch (Exception error) {
            streamingDiagnostics.recordPrecacheFailed(track, error);
        }
    }

    private long leadingPrecacheBytes(PrecacheMode mode) {
        return mode == PrecacheMode.UPCOMING_TRACK ? UPCOMING_TRACK_PRECACHE_BYTES : PRECACHE_BYTES;
    }

    private boolean shouldLetPlayerFillCurrentLeadingRange(
            PrecacheMode mode,
            boolean playerAlreadyLoadsLeadingRange
    ) {
        return mode == PrecacheMode.CURRENT_TRACK && playerAlreadyLoadsLeadingRange;
    }

    private String cacheKeyForPrecache(Track track) {
        if (mediaCacheOperations == null) {
            return null;
        }
        String cacheKey = mediaCacheOperations.cacheKeyForPrecache(track);
        return cacheKey == null || cacheKey.isEmpty() ? null : cacheKey;
    }

    private void scheduleUpcomingTrackPrecache(int generation) {
        postDelayedPrecacheCallback(() -> {
            if (generation != precacheGeneration.get()) {
                return;
            }
            precacheUpcomingTracks(generation);
        }, UPCOMING_TRACK_PRECACHE_DELAY_MS);
    }

    private void scheduleCurrentSegmentedPrecache(Track track, String cacheKey, int generation) {
        postDelayedPrecacheCallback(() -> {
            Track current = currentTrack();
            if (!mediaCacheOperations.tracksShareResolvedUriForReuse(current, track)
                    || !isCurrentPrecacheGeneration(generation, cacheKey)) {
                return;
            }
            submitPlaybackCacheTask(PrecachePriority.CURRENT_SEGMENT, () -> {
                SegmentedPrecacheProbe probe = probeSegmentedPrecache(track, cacheKey, generation);
                if (!probe.supported || !isCurrentPrecacheGeneration(generation, cacheKey)) {
                    return;
                }
                precacheMediaSegments(
                        track,
                        cacheKey,
                        generation,
                        probe.totalBytes,
                        currentSegmentedPrecacheStart(cacheKey)
                );
            });
        }, CURRENT_TRACK_SEGMENTED_PRECACHE_DELAY_MS);
    }

    private void postDelayedPrecacheCallback(Runnable task, long delayMs) {
        if (released || task == null || playbackCacheExecutor.isShutdown()) {
            return;
        }
        Runnable callback = new Runnable() {
            @Override
            public void run() {
                pendingPrecacheCallbacks.remove(this);
                if (released || playbackCacheExecutor.isShutdown()) {
                    return;
                }
                task.run();
            }
        };
        pendingPrecacheCallbacks.add(callback);
        callbackScheduler.postDelayed(callback, delayMs);
    }

    private void cancelPendingPrecacheCallbacks() {
        List<Runnable> callbacks;
        synchronized (pendingPrecacheCallbacks) {
            callbacks = new ArrayList<>(pendingPrecacheCallbacks);
            pendingPrecacheCallbacks.clear();
        }
        for (Runnable callback : callbacks) {
            callbackScheduler.removeCallbacks(callback);
        }
    }

    private SegmentedPrecacheProbe probeSegmentedPrecache(Track track, String cacheKey, int generation) {
        if (track == null || track.contentUri == null || cacheKey == null || cacheKey.isEmpty()) {
            return SegmentedPrecacheProbe.unsupported();
        }
        if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
            return SegmentedPrecacheProbe.unsupported();
        }
        long totalBytes = mediaCacheOperations.probeSegmentedPrecacheContentLength(
                track,
                cacheKey,
                PRECACHE_BYTES,
                PRECACHE_RANGE_PROBE_BYTES
        );
        return totalBytes > PRECACHE_BYTES && isCurrentPrecacheGeneration(generation, cacheKey)
                ? new SegmentedPrecacheProbe(true, totalBytes)
                : SegmentedPrecacheProbe.unsupported();
    }

    private void precacheMediaSegments(
            Track track,
            String cacheKey,
            int generation,
            long probedContentLength,
            long startBytes
    ) {
        long contentLength = mediaCacheOperations.contentLengthForCacheKey(cacheKey);
        long effectiveContentLength = contentLength > 0L ? contentLength : probedContentLength;
        for (PrecacheSegment segment : planPrecacheSegments(
                Math.max(PRECACHE_BYTES, startBytes),
                SEGMENTED_PRECACHE_CHUNK_BYTES,
                SEGMENTED_PRECACHE_BYTES,
                effectiveContentLength
        )) {
            if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
                return;
            }
            if (cachedBytesInRange(cacheKey, segment.start, segment.length) >= segment.length) {
                continue;
            }
            submitPlaybackCacheTask(PrecachePriority.CURRENT_SEGMENT, () -> {
                try {
                    if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
                        return;
                    }
                    long cached = cacheMediaRange(track, cacheKey, segment.start, segment.length, generation);
                    if (cached > 0L) {
                        streamingDiagnostics.recordPrecacheSegmentComplete(track, segment.start, cached);
                    }
                } catch (PrecacheSupersededException ignored) {
                } catch (Exception error) {
                    streamingDiagnostics.recordPrecacheSegmentFailed(track, segment.start, error);
                }
            });
        }
    }

    private long currentSegmentedPrecacheStart(String cacheKey) {
        long continuousCachedBytes = continuousCachedBytes(cacheKey);
        return segmentedPrecacheStart(PRECACHE_BYTES, continuousCachedBytes);
    }

    static long segmentedPrecacheStart(long leadingBytes, long continuousCachedBytes) {
        return Math.max(Math.max(0L, leadingBytes), Math.max(0L, continuousCachedBytes));
    }

    static List<PrecacheSegment> planPrecacheSegments(
            long leadingBytes,
            long segmentBytes,
            long targetBytes,
            long contentLength
    ) {
        long safeLeadingBytes = Math.max(0L, leadingBytes);
        long safeSegmentBytes = Math.max(1L, segmentBytes);
        long safeTargetBytes = Math.max(0L, targetBytes);
        long maxBytes = contentLength > 0L
                ? Math.min(contentLength, safeTargetBytes)
                : safeTargetBytes;
        if (maxBytes <= safeLeadingBytes) {
            return Collections.emptyList();
        }
        ArrayList<PrecacheSegment> segments = new ArrayList<>();
        for (long start = safeLeadingBytes; start < maxBytes; start += safeSegmentBytes) {
            long length = Math.min(safeSegmentBytes, maxBytes - start);
            if (length > 0L) {
                segments.add(new PrecacheSegment(start, length));
            }
        }
        return segments;
    }

    private void submitPlaybackCacheTask(PrecachePriority priority, Runnable task) {
        if (released || task == null || playbackCacheExecutor.isShutdown()) {
            return;
        }
        trimPlaybackCacheQueueIfNeeded(priority);
        try {
            playbackCacheExecutor.execute(new PrecacheTask(
                    priority,
                    task,
                    () -> !released && !playbackCacheExecutor.isShutdown()
            ));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void trimPlaybackCacheQueueIfNeeded(PrecachePriority priority) {
        if (priority != PrecachePriority.UPCOMING_TRACK) {
            return;
        }
        if (playbackCacheExecutor.getQueue().size() < PLAYBACK_CACHE_QUEUE_CAPACITY) {
            return;
        }
        playbackCacheExecutor.getQueue().removeIf(runnable ->
                runnable instanceof PrecacheTask
                        && ((PrecacheTask) runnable).priority == PrecachePriority.UPCOMING_TRACK
        );
    }

    private boolean isCurrentPrecacheGeneration(int generation, String cacheKey) {
        return generation == precacheGeneration.get()
                && !released
                && cacheKey != null
                && !cacheKey.isEmpty();
    }

    private long cachedBytesInRange(String cacheKey, long position, long length) {
        if (cacheKey == null || cacheKey.isEmpty() || length <= 0L) {
            return 0L;
        }
        try {
            long cached = mediaCacheOperations.cachedBytesInRange(cacheKey, Math.max(0L, position), length);
            return cached > 0L ? Math.min(cached, length) : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private long continuousCachedBytes(String cacheKey) {
        return cachedBytesInRange(cacheKey, 0L, Long.MAX_VALUE);
    }

    private boolean currentPlayerLoadsTrack(Track track) {
        MediaItem currentPlayerMediaItem = currentPlayerMediaItemSupplier == null
                ? null
                : currentPlayerMediaItemSupplier.get();
        return mediaItemTrackMatcher != null
                && mediaItemTrackMatcher.test(currentPlayerMediaItem, track);
    }

    @OptIn(markerClass = UnstableApi.class)
    private long cacheMediaRange(Track track, String cacheKey, long position, long length, int generation) throws IOException {
        if (track == null || track.contentUri == null || cacheKey == null || cacheKey.isEmpty() || length <= 0L) {
            return 0L;
        }
        if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
            return 0L;
        }
        long start = Math.max(0L, position);
        long remaining = length - cachedBytesInRange(cacheKey, start, length);
        if (remaining <= 0L) {
            return length;
        }
        String rangeKey = cacheKey + "@" + start + "+" + length;
        if (!activePrecacheRanges.add(rangeKey)) {
            return 0L;
        }
        final long[] bytesCached = {0L};
        CacheWriter writer = null;
        try {
            DataSpec dataSpec = cacheRangeDataSpec(track, cacheKey, start, length);
            if (dataSpec == null) {
                return 0L;
            }
            writer = new CacheWriter(
                    mediaCacheOperations.cacheDataSourceForTrack(track),
                    dataSpec,
                    new byte[16 * 1024],
                    (requestLength, bytesCachedTotal, newBytesCached) -> {
                        if (!isCurrentPrecacheGeneration(generation, cacheKey)) {
                            throw new PrecacheSupersededException();
                        }
                        bytesCached[0] = bytesCachedTotal;
                    }
            );
            activePrecacheWriters.add(writer);
            writer.cache();
            return bytesCached[0];
        } finally {
            if (writer != null) {
                activePrecacheWriters.remove(writer);
            }
            activePrecacheRanges.remove(rangeKey);
        }
    }

    private void cancelActivePrecacheWriters() {
        for (CacheWriter writer : activePrecacheWriters) {
            if (writer != null) {
                writer.cancel();
            }
        }
        activePrecacheWriters.clear();
    }

    private void clearPrecacheState() {
        activePrecacheRanges.clear();
        cancelActivePrecacheWriters();
        playbackCacheExecutor.getQueue().clear();
        playbackCacheExecutor.purge();
    }

    static DataSpec cacheRangeDataSpec(Track track, String cacheKey, long position, long length) {
        if (track == null || track.contentUri == null || cacheKey == null || cacheKey.isEmpty() || length <= 0L) {
            return null;
        }
        return new DataSpec.Builder()
                .setUri(track.contentUri)
                .setPosition(Math.max(0L, position))
                .setLength(length)
                .setKey(cacheKey)
                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build();
    }

    private static final class PlaybackCacheThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(() -> {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } catch (RuntimeException ignored) {
                }
                runnable.run();
            }, "YukinePlaybackCache-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }

    private static ThreadPoolExecutor newPlaybackCacheExecutor() {
        return new ThreadPoolExecutor(
                SEGMENTED_PRECACHE_CONCURRENCY,
                SEGMENTED_PRECACHE_CONCURRENCY,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(PLAYBACK_CACHE_PRIORITY_QUEUE_INITIAL_CAPACITY),
                new PlaybackCacheThreadFactory()
        );
    }

    private static final class PrecacheTask implements Runnable, Comparable<PrecacheTask> {
        private static final AtomicInteger sequenceGenerator = new AtomicInteger();
        private final PrecachePriority priority;
        private final Runnable delegate;
        private final BooleanSupplier canRun;
        private final int sequence;

        PrecacheTask(PrecachePriority priority, Runnable delegate, BooleanSupplier canRun) {
            this.priority = priority == null ? PrecachePriority.CURRENT_SEGMENT : priority;
            this.delegate = delegate;
            this.canRun = canRun;
            this.sequence = sequenceGenerator.incrementAndGet();
        }

        @Override
        public void run() {
            if (canRun != null && !canRun.getAsBoolean()) {
                return;
            }
            delegate.run();
        }

        @Override
        public int compareTo(PrecacheTask other) {
            int priorityCompare = Integer.compare(priority.order, other.priority.order);
            return priorityCompare != 0 ? priorityCompare : Integer.compare(sequence, other.sequence);
        }
    }

    private static final class PrecacheSupersededException extends RuntimeException {
    }

    static final class SegmentedPrecacheProbe {
        final boolean supported;
        final long totalBytes;

        SegmentedPrecacheProbe(boolean supported, long totalBytes) {
            this.supported = supported;
            this.totalBytes = totalBytes;
        }

        static SegmentedPrecacheProbe unsupported() {
            return new SegmentedPrecacheProbe(false, -1L);
        }
    }

    static final class PrecacheSegment {
        final long start;
        final long length;

        PrecacheSegment(long start, long length) {
            this.start = start;
            this.length = length;
        }
    }

    private enum PrecachePriority {
        CURRENT_LEADING(0),
        CURRENT_SEGMENT(1),
        UPCOMING_TRACK(2);

        final int order;

        PrecachePriority(int order) {
            this.order = order;
        }
    }

    private enum PrecacheMode {
        CURRENT_TRACK,
        UPCOMING_TRACK
    }
}
