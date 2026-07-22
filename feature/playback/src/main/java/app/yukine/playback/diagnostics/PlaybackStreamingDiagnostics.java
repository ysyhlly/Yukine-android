package app.yukine.playback.diagnostics;

import app.yukine.diagnostics.DiagnosticLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

import app.yukine.model.Track;

public final class PlaybackStreamingDiagnostics {
    private static final String TAG = "PlaybackDiagnostics";
    private static final int MAX_EVENTS = 24;
    private static final int MAX_LATENCY_SAMPLES = 100;
    private static final PlaybackStreamingDiagnostics PROCESS_INSTANCE = new PlaybackStreamingDiagnostics();

    private final ArrayDeque<Event> recentEvents = new ArrayDeque<>();
    private final ArrayDeque<Long> prepareToReadySamplesMs = new ArrayDeque<>();
    private final ArrayDeque<Long> prepareToFirstAudioSamplesMs = new ArrayDeque<>();
    private final ArrayDeque<Long> resolveToUrlSamplesMs = new ArrayDeque<>();
    private final ArrayDeque<Long> clickToReadySamplesMs = new ArrayDeque<>();
    private final ArrayDeque<Long> clickToFirstAudioSamplesMs = new ArrayDeque<>();
    private final LinkedHashMap<String, ArrayDeque<Long>> sourceToReadySamplesMs = new LinkedHashMap<>();
    private final LinkedHashMap<String, ArrayDeque<Long>> sourceToFirstAudioSamplesMs = new LinkedHashMap<>();
    private final LinkedHashMap<String, ArrayDeque<Long>> providerStageSamplesMs = new LinkedHashMap<>();
    private final LongSupplier clockMs;
    private int bufferingEvents;
    private int recoveryEvents;
    private int precacheAttempts;
    private int precacheSuccesses;
    private int precacheFailures;
    private int precacheSegmentSuccesses;
    private int precacheSegmentFailures;
    private String preparingTrackKey = "";
    private long prepareStartedAtMs = -1L;
    private String foregroundTrackIdentity = "";
    private long foregroundResolveStartedAtMs = -1L;
    private String foregroundSourceCategory = "";
    private String audioPreparingTrackKey = "";
    private long audioPrepareStartedAtMs = -1L;
    private String audioForegroundTrackIdentity = "";
    private long audioForegroundStartedAtMs = -1L;
    private String audioForegroundSourceCategory = "";
    private int providerStageFailures;
    private int providerStageTimeouts;
    private int providerStageCancellations;

    public static PlaybackStreamingDiagnostics process() {
        return PROCESS_INSTANCE;
    }

    public PlaybackStreamingDiagnostics() {
        this(System::currentTimeMillis);
    }

    public PlaybackStreamingDiagnostics(LongSupplier clockMs) {
        this.clockMs = clockMs == null ? System::currentTimeMillis : clockMs;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                bufferingEvents,
                recoveryEvents,
                precacheAttempts,
                precacheSuccesses,
                precacheFailures,
                precacheSegmentSuccesses,
                precacheSegmentFailures,
                StageLatency.from(prepareToReadySamplesMs),
                StageLatency.from(prepareToFirstAudioSamplesMs),
                StageLatency.from(resolveToUrlSamplesMs),
                StageLatency.from(clickToReadySamplesMs),
                StageLatency.from(clickToFirstAudioSamplesMs),
                sourceLatencySnapshot(),
                sourceFirstAudioLatencySnapshot(),
                providerStageFailures,
                providerStageTimeouts,
                providerStageCancellations,
                latencySnapshot(providerStageSamplesMs),
                new ArrayList<>(recentEvents)
        );
    }

    public synchronized void recordResolveStarted(Track track) {
        foregroundResolveStartedAtMs = nowMs();
        foregroundTrackIdentity = identityKey(track);
        foregroundSourceCategory = SOURCE_COLD_RESOLVE;
        addLocked(Event.stage("resolve_started", track, foregroundSourceCategory, 0L, foregroundResolveStartedAtMs));
    }

    public synchronized void recordPlaybackRequested(Track track) {
        String identity = identityKey(track);
        if (foregroundResolveStartedAtMs >= 0L && foregroundTrackIdentity.equals(identity)) {
            return;
        }
        foregroundResolveStartedAtMs = nowMs();
        foregroundTrackIdentity = identity;
        foregroundSourceCategory = classifySource(track);
        addLocked(Event.stage(
                "playback_requested",
                track,
                foregroundSourceCategory,
                0L,
                foregroundResolveStartedAtMs
        ));
    }

    public synchronized void recordActiveSourceLookupCompleted(Track track) {
        recordForegroundStage("active_source_lookup", track, false);
    }

    public synchronized void recordPlaybackUrlReady(Track track) {
        recordPlaybackUrlReady(track, "");
    }

    public synchronized void recordPlaybackUrlReady(Track track, String resolutionPath) {
        foregroundSourceCategory = sourceCategoryForResolution(track, resolutionPath);
        recordForegroundStage("playback_url_ready", track, true);
    }

    public synchronized void recordPrepareStarted(Track track) {
        prepareStartedAtMs = nowMs();
        preparingTrackKey = trackKey(track);
        if (foregroundResolveStartedAtMs < 0L || !foregroundTrackIdentity.equals(identityKey(track))) {
            foregroundResolveStartedAtMs = prepareStartedAtMs;
            foregroundTrackIdentity = identityKey(track);
            foregroundSourceCategory = classifySource(track);
        }
        addLocked(Event.stage("player_prepare", track, foregroundSourceCategory, 0L, prepareStartedAtMs));
        audioPreparingTrackKey = preparingTrackKey;
        audioPrepareStartedAtMs = prepareStartedAtMs;
        audioForegroundTrackIdentity = foregroundTrackIdentity;
        audioForegroundStartedAtMs = foregroundResolveStartedAtMs;
        audioForegroundSourceCategory = foregroundSourceCategory;
        logDebug("PLAYER_PREPARE track=" + preparingTrackKey);
    }

    public synchronized void recordPlayerReady(Track track) {
        long now = nowMs();
        String readyTrackKey = trackKey(track);
        if (prepareStartedAtMs < 0L || !preparingTrackKey.equals(readyTrackKey)) {
            return;
        }
        long durationMs = Math.max(0L, now - prepareStartedAtMs);
        addLatencySample(prepareToReadySamplesMs, durationMs);
        addLocked(Event.stage("player_ready", track, foregroundSourceCategory, durationMs, now));
        prepareStartedAtMs = -1L;
        preparingTrackKey = "";
        if (foregroundResolveStartedAtMs >= 0L && foregroundTrackIdentity.equals(identityKey(track))) {
            long clickToReadyMs = Math.max(0L, now - foregroundResolveStartedAtMs);
            addLatencySample(clickToReadySamplesMs, clickToReadyMs);
            addSourceLatencySample(foregroundSourceCategory, clickToReadyMs);
            addLocked(Event.stage("first_audio_ready", track, foregroundSourceCategory, clickToReadyMs, now));
            foregroundResolveStartedAtMs = -1L;
            foregroundTrackIdentity = "";
            foregroundSourceCategory = "";
        }
        logDebug("PLAYER_READY track=" + readyTrackKey + " prepareToReadyMs=" + durationMs);
    }

    /** Records the first decoded PCM buffer handed through the playback audio processor. */
    public synchronized void recordFirstAudioOutput(Track track) {
        long now = nowMs();
        String outputTrackKey = trackKey(track);
        long prepareToFirstAudioMs = 0L;
        boolean matchedPrepare = audioPrepareStartedAtMs >= 0L
                && audioPreparingTrackKey.equals(outputTrackKey);
        if (matchedPrepare) {
            prepareToFirstAudioMs = Math.max(0L, now - audioPrepareStartedAtMs);
            addLatencySample(prepareToFirstAudioSamplesMs, prepareToFirstAudioMs);
        }

        long clickToFirstAudioMs = 0L;
        boolean matchedForeground = audioForegroundStartedAtMs >= 0L
                && audioForegroundTrackIdentity.equals(identityKey(track));
        if (matchedForeground) {
            clickToFirstAudioMs = Math.max(0L, now - audioForegroundStartedAtMs);
            addLatencySample(clickToFirstAudioSamplesMs, clickToFirstAudioMs);
            addSourceLatencySample(
                    sourceToFirstAudioSamplesMs,
                    audioForegroundSourceCategory,
                    clickToFirstAudioMs
            );
        }

        String category = matchedForeground
                ? audioForegroundSourceCategory
                : classifySource(track);
        long eventDurationMs = matchedForeground ? clickToFirstAudioMs : prepareToFirstAudioMs;
        addLocked(Event.stage("first_pcm_output", track, category, eventDurationMs, now));
        audioPreparingTrackKey = "";
        audioPrepareStartedAtMs = -1L;
        audioForegroundTrackIdentity = "";
        audioForegroundStartedAtMs = -1L;
        audioForegroundSourceCategory = "";
        logDebug("FIRST_PCM_OUTPUT track=" + outputTrackKey
                + " prepareToFirstAudioMs=" + prepareToFirstAudioMs
                + " clickToFirstAudioMs=" + clickToFirstAudioMs);
    }

    /** Records sanitized per-provider stages without retaining queries, URLs, cookies or tokens. */
    public synchronized void recordProviderStage(
            String stage,
            String provider,
            long durationMs,
            boolean success,
            boolean timedOut,
            boolean cancelled,
            boolean cacheHit,
            String errorCode,
            String resolutionPath,
            int candidateCount
    ) {
        String cleanStage = compactDiagnosticValue(stage, "unknown_stage");
        String cleanProvider = compactDiagnosticValue(provider, "unknown_provider");
        if (cancelled) {
            providerStageCancellations++;
        } else {
            addSourceLatencySample(
                    providerStageSamplesMs,
                    cleanStage + ":" + cleanProvider,
                    durationMs
            );
            if (!success) {
                providerStageFailures++;
            }
            if (timedOut) {
                providerStageTimeouts++;
            }
        }
        String message = "success=" + success
                + ",timeout=" + timedOut
                + ",cancelled=" + cancelled
                + ",cache=" + cacheHit
                + ",error=" + compactErrorCode(errorCode)
                + ",path=" + compactDiagnosticValue(resolutionPath, "")
                + ",candidates=" + Math.max(0, candidateCount);
        addLocked(new Event(
                "provider_" + cleanStage,
                null,
                0L,
                0L,
                "",
                message,
                cleanProvider,
                durationMs,
                nowMs()
        ));
    }

    private void recordForegroundStage(String type, Track track, boolean urlReady) {
        if (foregroundResolveStartedAtMs < 0L || !foregroundTrackIdentity.equals(identityKey(track))) {
            return;
        }
        long now = nowMs();
        long durationMs = Math.max(0L, now - foregroundResolveStartedAtMs);
        if (urlReady) {
            addLatencySample(resolveToUrlSamplesMs, durationMs);
        }
        addLocked(Event.stage(type, track, foregroundSourceCategory, durationMs, now));
        logDebug(type.toUpperCase() + " track=" + trackKey(track) + " durationMs=" + durationMs);
    }

    private static void addLatencySample(ArrayDeque<Long> samples, long durationMs) {
        samples.addLast(Math.max(0L, durationMs));
        while (samples.size() > MAX_LATENCY_SAMPLES) {
            samples.removeFirst();
        }
    }

    private void addSourceLatencySample(String sourceCategory, long durationMs) {
        addSourceLatencySample(sourceToReadySamplesMs, sourceCategory, durationMs);
    }

    private static void addSourceLatencySample(
            LinkedHashMap<String, ArrayDeque<Long>> destination,
            String sourceCategory,
            long durationMs
    ) {
        String category = sourceCategory == null || sourceCategory.trim().isEmpty()
                ? SOURCE_LOCAL
                : sourceCategory.trim();
        ArrayDeque<Long> samples = destination.get(category);
        if (samples == null) {
            samples = new ArrayDeque<>();
            destination.put(category, samples);
        }
        addLatencySample(samples, durationMs);
    }

    private Map<String, StageLatency> sourceLatencySnapshot() {
        return latencySnapshot(sourceToReadySamplesMs);
    }

    private Map<String, StageLatency> sourceFirstAudioLatencySnapshot() {
        return latencySnapshot(sourceToFirstAudioSamplesMs);
    }

    private static Map<String, StageLatency> latencySnapshot(
            LinkedHashMap<String, ArrayDeque<Long>> source
    ) {
        LinkedHashMap<String, StageLatency> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayDeque<Long>> entry : source.entrySet()) {
            snapshot.put(entry.getKey(), StageLatency.from(entry.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public static String classifySource(Track track) {
        if (track == null) {
            return SOURCE_LOCAL;
        }
        String dataPath = track.dataPath == null ? "" : track.dataPath.trim().toLowerCase(Locale.ROOT);
        if (dataPath.startsWith("webdav:")) {
            return SOURCE_WEBDAV;
        }
        if (dataPath.startsWith("streaming:") || dataPath.startsWith("stream:")) {
            return SOURCE_CACHE_HIT;
        }
        return SOURCE_LOCAL;
    }

    private static String sourceCategoryForResolution(Track track, String resolutionPath) {
        String path = resolutionPath == null ? "" : resolutionPath.trim().toLowerCase(Locale.ROOT);
        if ("url_cache".equals(path) || "active_source".equals(path)) {
            return classifySource(track);
        }
        if ("known_provider_id".equals(path) || "title_search".equals(path)) {
            return SOURCE_COLD_RESOLVE;
        }
        return classifySource(track);
    }

    public void recordBuffering(Track track, long positionMs) {
        synchronized (this) {
            bufferingEvents++;
            addLocked(Event.buffering(track, positionMs));
        }
        logDebug("BUFFERING track=" + trackKey(track) + " host=" + host(track) + " positionMs=" + positionMs);
    }

    public void recordRecovery(Track track, long positionMs, String quality) {
        synchronized (this) {
            recoveryEvents++;
            addLocked(Event.recovery(track, positionMs, quality));
        }
        logDebug("RECOVERY track=" + trackKey(track) + " quality=" + quality + " positionMs=" + positionMs);
    }

    public void recordPrecacheQueued(Track track) {
        synchronized (this) {
            precacheAttempts++;
            addLocked(Event.precacheQueued(track));
        }
        logDebug("PRECACHE_QUEUED track=" + trackKey(track) + " host=" + host(track));
    }

    public void recordPrecacheComplete(Track track, long bytesCached) {
        synchronized (this) {
            precacheSuccesses++;
            addLocked(Event.precacheComplete(track, bytesCached));
        }
        logDebug("PRECACHE_OK track=" + trackKey(track) + " bytes=" + bytesCached);
    }

    public void recordPrecacheFailed(Track track, Throwable error) {
        synchronized (this) {
            precacheFailures++;
            addLocked(Event.precacheFailed(track, message(error)));
        }
        logWarning("PRECACHE_FAILED track=" + trackKey(track), error);
    }

    public void recordPrecacheSegmentComplete(Track track, long position, long bytesCached) {
        synchronized (this) {
            precacheSegmentSuccesses++;
            addLocked(Event.precacheSegmentComplete(track, position, bytesCached));
        }
        logDebug("PRECACHE_SEGMENT_OK track=" + trackKey(track) + " position=" + position + " bytes=" + bytesCached);
    }

    public void recordPrecacheSegmentFailed(Track track, long position, Throwable error) {
        synchronized (this) {
            precacheSegmentFailures++;
            addLocked(Event.precacheSegmentFailed(track, position, message(error)));
        }
        logWarning("PRECACHE_SEGMENT_FAILED track=" + trackKey(track) + " position=" + position, error);
    }

    private void addLocked(Event event) {
        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }
    }

    private long nowMs() {
        return clockMs.getAsLong();
    }

    private static String trackKey(Track track) {
        if (track == null) {
            return "<null>";
        }
        if (track.dataPath != null && !track.dataPath.isEmpty()) {
            return track.dataPath;
        }
        return String.valueOf(track.id);
    }

    private static String identityKey(Track track) {
        if (track == null) {
            return "<null>";
        }
        return track.id != 0L ? String.valueOf(track.id) : trackKey(track);
    }

    private static String host(Track track) {
        if (track == null || track.contentUri == null) {
            return "";
        }
        String host = track.contentUri.getHost();
        return host == null ? "" : host;
    }

    private static String message(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private static String compactDiagnosticValue(String value, String fallback) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            return fallback;
        }
        String compact = clean.replaceAll("[^a-z0-9_.-]", "_");
        return compact.substring(0, Math.min(compact.length(), 48));
    }

    private static String compactErrorCode(String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        switch (clean) {
            case "AUTH_REQUIRED":
            case "RATE_LIMITED":
            case "REGION_BLOCKED":
            case "SOURCE_UNAVAILABLE":
            case "UNSUPPORTED_OPERATION":
            case "GATEWAY_UNAVAILABLE":
            case "UNKNOWN":
                return clean.toLowerCase(Locale.ROOT);
            default:
                return clean.isEmpty() ? "" : "unknown";
        }
    }

    private static void logDebug(String message) {
        try {
            DiagnosticLog.d(TAG, message);
        } catch (RuntimeException ignored) {
            // Local JVM tests do not provide android.util.DiagnosticLog.
        }
    }

    private static void logWarning(String message, Throwable error) {
        try {
            DiagnosticLog.w(TAG, message, error);
        } catch (RuntimeException ignored) {
            // Local JVM tests do not provide android.util.DiagnosticLog.
        }
    }

    public static final class Snapshot {
        public final int bufferingEvents;
        public final int recoveryEvents;
        public final int precacheAttempts;
        public final int precacheSuccesses;
        public final int precacheFailures;
        public final int precacheSegmentSuccesses;
        public final int precacheSegmentFailures;
        public final StageLatency prepareToReady;
        public final StageLatency prepareToFirstAudio;
        public final StageLatency resolveToUrl;
        public final StageLatency clickToReady;
        public final StageLatency clickToFirstAudio;
        public final Map<String, StageLatency> sourceToReady;
        public final Map<String, StageLatency> sourceToFirstAudio;
        public final int providerStageFailures;
        public final int providerStageTimeouts;
        public final int providerStageCancellations;
        public final Map<String, StageLatency> providerStages;
        public final List<Event> recentEvents;

        private Snapshot(
                int bufferingEvents,
                int recoveryEvents,
                int precacheAttempts,
                int precacheSuccesses,
                int precacheFailures,
                int precacheSegmentSuccesses,
                int precacheSegmentFailures,
                StageLatency prepareToReady,
                StageLatency prepareToFirstAudio,
                StageLatency resolveToUrl,
                StageLatency clickToReady,
                StageLatency clickToFirstAudio,
                Map<String, StageLatency> sourceToReady,
                Map<String, StageLatency> sourceToFirstAudio,
                int providerStageFailures,
                int providerStageTimeouts,
                int providerStageCancellations,
                Map<String, StageLatency> providerStages,
                List<Event> recentEvents
        ) {
            this.bufferingEvents = bufferingEvents;
            this.recoveryEvents = recoveryEvents;
            this.precacheAttempts = precacheAttempts;
            this.precacheSuccesses = precacheSuccesses;
            this.precacheFailures = precacheFailures;
            this.precacheSegmentSuccesses = precacheSegmentSuccesses;
            this.precacheSegmentFailures = precacheSegmentFailures;
            this.prepareToReady = prepareToReady;
            this.prepareToFirstAudio = prepareToFirstAudio;
            this.resolveToUrl = resolveToUrl;
            this.clickToReady = clickToReady;
            this.clickToFirstAudio = clickToFirstAudio;
            this.sourceToReady = sourceToReady;
            this.sourceToFirstAudio = sourceToFirstAudio;
            this.providerStageFailures = providerStageFailures;
            this.providerStageTimeouts = providerStageTimeouts;
            this.providerStageCancellations = providerStageCancellations;
            this.providerStages = providerStages;
            this.recentEvents = Collections.unmodifiableList(recentEvents);
        }
    }

    public static final class StageLatency {
        public final int sampleCount;
        public final long p50Ms;
        public final long p95Ms;

        private StageLatency(int sampleCount, long p50Ms, long p95Ms) {
            this.sampleCount = sampleCount;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
        }

        private static StageLatency from(ArrayDeque<Long> samples) {
            if (samples == null || samples.isEmpty()) {
                return new StageLatency(0, 0L, 0L);
            }
            ArrayList<Long> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            return new StageLatency(
                    sorted.size(),
                    percentile(sorted, 0.50d),
                    percentile(sorted, 0.95d)
            );
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }

    public static final class Event {
        public final String type;
        public final String trackKey;
        public final String host;
        public final long positionMs;
        public final long bytes;
        public final String quality;
        public final String message;
        public final String sourceCategory;
        public final long timestampMs;
        public final long durationMs;

        private Event(
                String type,
                Track track,
                long positionMs,
                long bytes,
                String quality,
                String message,
                String sourceCategory,
                long durationMs,
                long timestampMs
        ) {
            this.type = type;
            this.trackKey = trackKey(track);
            this.host = host(track);
            this.positionMs = Math.max(0L, positionMs);
            this.bytes = Math.max(0L, bytes);
            this.quality = quality == null ? "" : quality;
            this.message = message == null ? "" : message;
            this.sourceCategory = sourceCategory == null ? "" : sourceCategory;
            this.durationMs = Math.max(0L, durationMs);
            this.timestampMs = timestampMs;
        }

        private static Event buffering(Track track, long positionMs) {
            return basic("buffering", track, positionMs, 0L, "", "");
        }

        private static Event recovery(Track track, long positionMs, String quality) {
            return basic("recovery", track, positionMs, 0L, quality, "");
        }

        private static Event precacheQueued(Track track) {
            return basic("precache_queued", track, 0L, 0L, "", "");
        }

        private static Event precacheComplete(Track track, long bytes) {
            return basic("precache_complete", track, 0L, bytes, "", "");
        }

        private static Event precacheFailed(Track track, String message) {
            return basic("precache_failed", track, 0L, 0L, "", message);
        }

        private static Event precacheSegmentComplete(Track track, long position, long bytes) {
            return basic("precache_segment_complete", track, position, bytes, "", "");
        }

        private static Event precacheSegmentFailed(Track track, long position, String message) {
            return basic("precache_segment_failed", track, position, 0L, "", message);
        }

        private static Event stage(
                String type,
                Track track,
                String sourceCategory,
                long durationMs,
                long timestampMs
        ) {
            return new Event(type, track, 0L, 0L, "", "", sourceCategory, durationMs, timestampMs);
        }

        private static Event basic(
                String type,
                Track track,
                long positionMs,
                long bytes,
                String quality,
                String message
        ) {
            return new Event(
                    type,
                    track,
                    positionMs,
                    bytes,
                    quality,
                    message,
                    "",
                    0L,
                    System.currentTimeMillis()
            );
        }
    }

    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_WEBDAV = "webdav";
    public static final String SOURCE_CACHE_HIT = "cache_hit";
    public static final String SOURCE_COLD_RESOLVE = "cold_resolve";
}
