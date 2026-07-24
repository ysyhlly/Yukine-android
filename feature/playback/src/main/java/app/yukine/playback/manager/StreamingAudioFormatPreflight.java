package app.yukine.playback.manager;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import app.yukine.diagnostics.DiagnosticLog;
import app.yukine.model.Track;
import app.yukine.playback.AudioFallbackReason;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves the first decoded PCM format of a streaming item before USB exclusive output is built.
 *
 * <p>The probe deliberately uses {@link PlaybackMediaSourceProvider#cacheDataSourceForTrack(Track)}
 * so authentication headers, HTTP Range behavior and the playback {@code SimpleCache} are shared
 * with the real player. A probe never causes an in-progress song to change output mode; callers
 * use the terminal result only while preparing a new media item.</p>
 */
@UnstableApi
public final class StreamingAudioFormatPreflight {
    private static final String TAG = "StreamingFormatPreflight";
    public static final long TIMEOUT_MS = 2_000L;
    public static final long MAX_READ_BYTES = 4L * 1024L * 1024L;
    private static final long CODEC_POLL_US = 5_000L;

    public enum Eligibility {
        UNKNOWN,
        PROBING,
        VERIFIED_COMPATIBLE,
        VERIFIED_INCOMPATIBLE,
        TIMED_OUT
    }

    public interface Callback {
        void onComplete(Result result);
    }

    interface ProbeEngine {
        Result probe(Track track, Cancellation cancellation) throws Exception;
    }

    interface Cancellation {
        boolean cancelled();

        default void onBytesRead(long bytesRead) {
        }
    }

    public static final class PcmFormat {
        public final int sampleRateHz;
        public final int bitDepth;
        public final int channelCount;
        public final int pcmEncoding;

        public PcmFormat(int sampleRateHz, int bitDepth, int channelCount, int pcmEncoding) {
            this.sampleRateHz = Math.max(0, sampleRateHz);
            this.bitDepth = Math.max(0, bitDepth);
            this.channelCount = Math.max(0, channelCount);
            this.pcmEncoding = pcmEncoding;
        }

        public boolean complete() {
            return sampleRateHz > 0 && bitDepth > 0 && channelCount > 0;
        }
    }

    public static final class Result {
        public final Eligibility eligibility;
        @Nullable public final PcmFormat pcmFormat;
        public final long durationMs;
        public final long bytesRead;
        public final String source;
        public final AudioFallbackReason fallbackReason;
        public final String detail;

        private Result(
                Eligibility eligibility,
                @Nullable PcmFormat pcmFormat,
                long durationMs,
                long bytesRead,
                String source,
                AudioFallbackReason fallbackReason,
                String detail
        ) {
            this.eligibility = eligibility;
            this.pcmFormat = pcmFormat;
            this.durationMs = Math.max(0L, durationMs);
            this.bytesRead = Math.max(0L, bytesRead);
            this.source = source == null ? "" : source;
            this.fallbackReason = fallbackReason == null
                    ? AudioFallbackReason.NONE
                    : fallbackReason;
            this.detail = detail == null ? "" : detail;
        }

        public static Result unknown() {
            return new Result(
                    Eligibility.UNKNOWN, null, 0L, 0L, "",
                    AudioFallbackReason.NONE, ""
            );
        }

        public static Result probing() {
            return new Result(
                    Eligibility.PROBING, null, 0L, 0L, "SHARED_PLAYBACK_CACHE",
                    AudioFallbackReason.NONE, ""
            );
        }

        public static Result decoded(PcmFormat format, long durationMs, long bytesRead) {
            boolean compatible = isUsbPcmCandidate(format);
            return new Result(
                    compatible
                            ? Eligibility.VERIFIED_COMPATIBLE
                            : Eligibility.VERIFIED_INCOMPATIBLE,
                    format,
                    durationMs,
                    bytesRead,
                    "DECODED_FIRST_PCM_FRAME",
                    compatible ? AudioFallbackReason.NONE : AudioFallbackReason.FORMAT_UNSUPPORTED,
                    compatible ? "" : "Decoded PCM format is not supported by the USB sink"
            );
        }

        public static Result incomplete(
                @Nullable PcmFormat format,
                long durationMs,
                long bytesRead,
                String detail
        ) {
            return new Result(
                    Eligibility.VERIFIED_INCOMPATIBLE,
                    format,
                    durationMs,
                    bytesRead,
                    "DECODED_FIRST_PCM_FRAME",
                    AudioFallbackReason.FORMAT_METADATA_INCOMPLETE,
                    detail
            );
        }

        public static Result timedOut(long durationMs, long bytesRead) {
            return new Result(
                    Eligibility.TIMED_OUT,
                    null,
                    durationMs,
                    bytesRead,
                    "SHARED_PLAYBACK_CACHE",
                    AudioFallbackReason.FORMAT_PREFLIGHT_TIMEOUT,
                    "Streaming PCM preflight exceeded " + TIMEOUT_MS + " ms"
            );
        }

        public boolean terminal() {
            return eligibility == Eligibility.VERIFIED_COMPATIBLE
                    || eligibility == Eligibility.VERIFIED_INCOMPATIBLE
                    || eligibility == Eligibility.TIMED_OUT;
        }

        public boolean formatVerified() {
            return eligibility == Eligibility.VERIFIED_COMPATIBLE
                    && pcmFormat != null
                    && pcmFormat.complete();
        }
    }

    private final PlaybackMediaSourceProvider mediaSourceProvider;
    private final ProbeEngine probeEngine;
    private final Executor callbackExecutor;
    private final ExecutorService coordinationExecutor;
    private final ExecutorService decoderExecutor;
    private final Map<String, Result> results = new ConcurrentHashMap<>();
    private final Map<String, List<Callback>> callbacks = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> activeDecodes = new ConcurrentHashMap<>();
    private final AtomicLong foregroundGeneration = new AtomicLong();
    private volatile String foregroundKey = "";
    private volatile boolean released;

    public StreamingAudioFormatPreflight(
            PlaybackMediaSourceProvider mediaSourceProvider,
            Executor callbackExecutor
    ) {
        this(
                mediaSourceProvider,
                null,
                callbackExecutor,
                Executors.newFixedThreadPool(2, threadFactory("format-preflight")),
                Executors.newCachedThreadPool(threadFactory("format-decode"))
        );
    }

    StreamingAudioFormatPreflight(
            PlaybackMediaSourceProvider mediaSourceProvider,
            @Nullable ProbeEngine probeEngine,
            Executor callbackExecutor,
            ExecutorService coordinationExecutor,
            ExecutorService decoderExecutor
    ) {
        this.mediaSourceProvider = mediaSourceProvider;
        this.probeEngine = probeEngine == null ? this::probeDecodedFormat : probeEngine;
        this.callbackExecutor = callbackExecutor == null ? Runnable::run : callbackExecutor;
        this.coordinationExecutor = coordinationExecutor;
        this.decoderExecutor = decoderExecutor;
    }

    public boolean appliesTo(Track track) {
        return track != null
                && mediaSourceProvider != null
                && mediaSourceProvider.isHttpTrack(track)
                && !mediaSourceProvider.isDsdTrack(track);
    }

    public Result resultFor(Track track) {
        if (!appliesTo(track)) {
            return Result.unknown();
        }
        return results.getOrDefault(keyFor(track), Result.unknown());
    }

    public void requestCurrent(Track track, Callback callback) {
        if (!appliesTo(track) || released) {
            dispatch(callback, Result.unknown());
            return;
        }
        String key = keyFor(track);
        long generation;
        synchronized (this) {
            if (!key.equals(foregroundKey)) {
                String previousKey = foregroundKey;
                foregroundKey = key;
                generation = foregroundGeneration.incrementAndGet();
                if (!previousKey.isEmpty()) {
                    callbacks.remove(previousKey);
                    Future<?> previous = activeDecodes.remove(previousKey);
                    if (previous != null) {
                        previous.cancel(true);
                    }
                    Result previousResult = results.get(previousKey);
                    if (previousResult != null
                            && previousResult.eligibility == Eligibility.PROBING) {
                        results.remove(previousKey);
                    }
                }
            } else {
                generation = foregroundGeneration.get();
            }
        }
        request(track, key, generation, callback);
    }

    public void requestUpcoming(Track track) {
        if (!appliesTo(track) || released) {
            return;
        }
        request(track, keyFor(track), 0L, null);
    }

    public void release() {
        released = true;
        foregroundGeneration.incrementAndGet();
        for (Future<?> future : activeDecodes.values()) {
            future.cancel(true);
        }
        activeDecodes.clear();
        callbacks.clear();
        coordinationExecutor.shutdownNow();
        decoderExecutor.shutdownNow();
    }

    private void request(Track track, String key, long generation, @Nullable Callback callback) {
        Result existing = results.get(key);
        if (existing != null && existing.terminal()) {
            dispatch(callback, existing);
            return;
        }
        if (callback != null) {
            callbacks.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(callback);
        }
        if (results.putIfAbsent(key, Result.probing()) != null) {
            return;
        }
        coordinationExecutor.execute(() -> runProbe(track, key, generation));
    }

    private void runProbe(Track track, String key, long generation) {
        if (released || cancelled(generation)) {
            results.remove(key);
            return;
        }
        final long startedMs = SystemClock.elapsedRealtime();
        final AtomicLong observedBytes = new AtomicLong();
        Future<Result> decode = decoderExecutor.submit(() ->
                probeEngine.probe(track, new Cancellation() {
                    @Override
                    public boolean cancelled() {
                        return released
                                || Thread.currentThread().isInterrupted()
                                || StreamingAudioFormatPreflight.this.cancelled(generation);
                    }

                    @Override
                    public void onBytesRead(long bytesRead) {
                        observedBytes.set(Math.max(0L, bytesRead));
                    }
                })
        );
        activeDecodes.put(key, decode);
        Result result;
        try {
            result = decode.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            decode.cancel(true);
            result = Result.timedOut(
                    SystemClock.elapsedRealtime() - startedMs,
                    observedBytes.get()
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            decode.cancel(true);
            results.remove(key);
            activeDecodes.remove(key);
            return;
        } catch (Exception failure) {
            result = Result.incomplete(
                    null,
                    SystemClock.elapsedRealtime() - startedMs,
                    observedBytes.get(),
                    failure.getCause() == null
                            ? failure.getMessage()
                            : failure.getCause().getMessage()
            );
        }
        activeDecodes.remove(key);
        if (released || cancelled(generation)) {
            results.remove(key);
            callbacks.remove(key);
            return;
        }
        results.put(key, result);
        log(track, result);
        List<Callback> pending = callbacks.remove(key);
        if (pending != null) {
            for (Callback callback : pending) {
                dispatch(callback, result);
            }
        }
    }

    private boolean cancelled(long generation) {
        return generation > 0L && generation != foregroundGeneration.get();
    }

    private void dispatch(@Nullable Callback callback, Result result) {
        if (callback != null) {
            callbackExecutor.execute(() -> callback.onComplete(result));
        }
    }

    private Result probeDecodedFormat(Track track, Cancellation cancellation) throws Exception {
        long startedMs = SystemClock.elapsedRealtime();
        long deadlineMs = startedMs + TIMEOUT_MS;
        DataSpec dataSpec = new DataSpec.Builder()
                .setUri(track.contentUri)
                .setPosition(0L)
                .setLength(MAX_READ_BYTES)
                .setKey(mediaSourceProvider.mediaCacheKeyForTrack(track))
                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build();
        DataSource dataSource = mediaSourceProvider.cacheDataSourceForTrack(track);
        BoundedMediaDataSource bounded = new BoundedMediaDataSource(
                dataSource,
                dataSpec,
                deadlineMs,
                cancellation
        );
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            checkDeadline(deadlineMs, cancellation);
            extractor.setDataSource(bounded);
            int audioTrack = selectAudioTrack(extractor);
            if (audioTrack < 0) {
                return Result.incomplete(
                        null,
                        elapsed(startedMs),
                        bounded.bytesRead(),
                        "No audio track was found in the preflight window"
                );
            }
            extractor.selectTrack(audioTrack);
            MediaFormat sourceFormat = extractor.getTrackFormat(audioTrack);
            String mime = sourceFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null || mime.isEmpty()) {
                return Result.incomplete(
                        null,
                        elapsed(startedMs),
                        bounded.bytesRead(),
                        "Audio MIME type is missing"
                );
            }
            if ("audio/raw".equals(mime)) {
                return resultFromOutputFormat(sourceFormat, startedMs, bounded.bytesRead());
            }
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(sourceFormat, null, null, 0);
            decoder.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            MediaFormat decodedOutputFormat = null;
            boolean inputEnded = false;
            while (true) {
                checkDeadline(deadlineMs, cancellation);
                if (!inputEnded) {
                    int inputIndex = decoder.dequeueInputBuffer(CODEC_POLL_US);
                    if (inputIndex >= 0) {
                        ByteBuffer input = decoder.getInputBuffer(inputIndex);
                        int sampleSize = input == null ? -1 : extractor.readSampleData(input, 0);
                        long sampleTimeUs = Math.max(0L, extractor.getSampleTime());
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                    inputIndex, 0, 0, sampleTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }
                int outputIndex = decoder.dequeueOutputBuffer(info, CODEC_POLL_US);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decodedOutputFormat = decoder.getOutputFormat();
                } else if (outputIndex >= 0) {
                    MediaFormat outputFormat = decoder.getOutputFormat(outputIndex);
                    boolean firstPcmFrame = info.size > 0;
                    boolean outputEnded =
                            (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                    if (firstPcmFrame) {
                        return resultFromOutputFormat(
                                completeOutputFormat(outputFormat)
                                        ? outputFormat
                                        : decodedOutputFormat,
                                startedMs,
                                bounded.bytesRead()
                        );
                    }
                    if (outputEnded) {
                        return Result.incomplete(
                                null,
                                elapsed(startedMs),
                                bounded.bytesRead(),
                                "Decoder ended before producing a PCM frame"
                        );
                    }
                }
            }
        } catch (PreflightTimeoutException timeout) {
            return Result.timedOut(elapsed(startedMs), bounded.bytesRead());
        } finally {
            bounded.close();
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (RuntimeException ignored) {
                }
                try {
                    decoder.release();
                } catch (RuntimeException ignored) {
                }
            }
            try {
                extractor.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static Result resultFromOutputFormat(
            MediaFormat format,
            long startedMs,
            long bytesRead
    ) {
        int sampleRate = intValue(format, MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = intValue(format, MediaFormat.KEY_CHANNEL_COUNT);
        int pcmEncoding = intValue(format, MediaFormat.KEY_PCM_ENCODING);
        if (pcmEncoding == 0) {
            pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        }
        PcmFormat pcm = new PcmFormat(
                sampleRate,
                bitDepthForPcmEncoding(pcmEncoding),
                channelCount,
                pcmEncoding
        );
        if (!pcm.complete()) {
            return Result.incomplete(
                    pcm,
                    elapsed(startedMs),
                    bytesRead,
                    "Decoded PCM format omitted sample rate, bit depth, or channel count"
            );
        }
        return Result.decoded(pcm, elapsed(startedMs), bytesRead);
    }

    static int bitDepthForPcmEncoding(int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) return 8;
        if (encoding == AudioFormat.ENCODING_PCM_16BIT) return 16;
        if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) return 24;
        if (encoding == AudioFormat.ENCODING_PCM_32BIT
                || encoding == AudioFormat.ENCODING_PCM_FLOAT) return 32;
        return 0;
    }

    static boolean isUsbPcmCandidate(@Nullable PcmFormat format) {
        return format != null
                && format.complete()
                && format.sampleRateHz >= 8_000
                && format.sampleRateHz <= 768_000
                && format.channelCount >= 1
                && format.channelCount <= 2
                && (format.bitDepth == 8
                    || format.bitDepth == 16
                    || format.bitDepth == 24
                    || format.bitDepth == 32);
    }

    private static boolean completeOutputFormat(MediaFormat format) {
        return intValue(format, MediaFormat.KEY_SAMPLE_RATE) > 0
                && intValue(format, MediaFormat.KEY_CHANNEL_COUNT) > 0;
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return index;
            }
        }
        return -1;
    }

    private static int intValue(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) {
            return 0;
        }
        try {
            return format.getInteger(key);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long elapsed(long startedMs) {
        return Math.max(0L, SystemClock.elapsedRealtime() - startedMs);
    }

    private static void checkDeadline(long deadlineMs, Cancellation cancellation)
            throws PreflightTimeoutException {
        if (Thread.currentThread().isInterrupted()
                || cancellation.cancelled()
                || SystemClock.elapsedRealtime() >= deadlineMs) {
            throw new PreflightTimeoutException();
        }
    }

    private String keyFor(Track track) {
        return track.dataPath + "|" + mediaSourceProvider.mediaCacheKeyForTrack(track)
                + "|" + track.contentUri
                + "|" + mediaSourceProvider.streamingQualityForTrack(track)
                + "|" + track.codec + "|" + track.bitrateKbps;
    }

    private static void log(Track track, Result result) {
        PcmFormat pcm = result.pcmFormat;
        String format = pcm == null
                ? "unknown"
                : pcm.sampleRateHz + "Hz/" + pcm.bitDepth + "bit/" + pcm.channelCount + "ch";
        String message = "PCM_PREFLIGHT track=" + track.id
                + " source=" + result.source
                + " durationMs=" + result.durationMs
                + " bytes=" + result.bytesRead
                + " format=" + format
                + " eligibility=" + result.eligibility
                + " reason=" + result.fallbackReason
                + (result.detail.isEmpty() ? "" : " detail=" + result.detail);
        if (result.formatVerified()) {
            DiagnosticLog.d(TAG, message);
        } else {
            DiagnosticLog.w(TAG, message);
        }
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class PreflightTimeoutException extends IOException {
    }

    private static final class BoundedMediaDataSource extends MediaDataSource {
        private final DataSource dataSource;
        private final DataSpec baseSpec;
        private final long deadlineMs;
        private final Cancellation cancellation;
        private long nextReadPosition = -1L;
        private long bytesRead;

        BoundedMediaDataSource(
                DataSource dataSource,
                DataSpec baseSpec,
                long deadlineMs,
                Cancellation cancellation
        ) {
            this.dataSource = dataSource;
            this.baseSpec = baseSpec;
            this.deadlineMs = deadlineMs;
            this.cancellation = cancellation;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            checkDeadline(deadlineMs, cancellation);
            if (position < 0L || position >= MAX_READ_BYTES || size <= 0
                    || bytesRead >= MAX_READ_BYTES) {
                return -1;
            }
            int readSize = (int) Math.min(
                    Math.min((long) size, MAX_READ_BYTES - position),
                    MAX_READ_BYTES - bytesRead
            );
            openAt(position);
            int total = 0;
            while (total < readSize) {
                checkDeadline(deadlineMs, cancellation);
                int read = dataSource.read(buffer, offset + total, readSize - total);
                if (read <= 0) {
                    break;
                }
                total += read;
                bytesRead += read;
                cancellation.onBytesRead(bytesRead);
                nextReadPosition += read;
            }
            return total == 0 ? -1 : total;
        }

        @Override
        public long getSize() {
            return MAX_READ_BYTES;
        }

        @Override
        public void close() {
            closeOpenDataSource();
        }

        long bytesRead() {
            return bytesRead;
        }

        private void openAt(long position) throws IOException {
            if (position == nextReadPosition) {
                return;
            }
            closeOpenDataSource();
            DataSpec rangeSpec = baseSpec.buildUpon()
                    .setPosition(baseSpec.position + position)
                    .setLength(MAX_READ_BYTES - position)
                    .build();
            dataSource.open(rangeSpec);
            nextReadPosition = position;
        }

        private void closeOpenDataSource() {
            try {
                dataSource.close();
            } catch (Exception ignored) {
            }
            nextReadPosition = -1L;
        }
    }
}
