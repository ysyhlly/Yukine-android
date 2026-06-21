package app.yukine.playback;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

@OptIn(markerClass = UnstableApi.class)
final class PlaybackSpectrumGenerator {
    static final int FRAME_COUNT = 4800;
    static final int BAND_COUNT = 24;
    private static final int MAX_IDLE_CODEC_POLLS = 24;

    private PlaybackSpectrumGenerator() {
    }

    static PlaybackSpectrumSnapshot extract(
            Context context,
            Uri source,
            long durationMs,
            float cachedProgress
    ) {
        if (context == null || source == null || durationMs <= 0L || cachedProgress <= 0.0f) {
            return PlaybackSpectrumSnapshot.empty();
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, source, null);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                return PlaybackSpectrumSnapshot.empty();
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || mime.isEmpty()) {
                return PlaybackSpectrumSnapshot.empty();
            }
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decode(extractor, decoder, format, durationMs, cachedProgress);
        } catch (Exception ignored) {
            return PlaybackSpectrumSnapshot.empty();
        } finally {
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

    static PlaybackSpectrumSnapshot extract(
            DataSource dataSource,
            DataSpec dataSpec,
            long durationMs,
            float cachedProgress
    ) {
        if (dataSource == null || dataSpec == null || durationMs <= 0L || cachedProgress <= 0.0f) {
            return PlaybackSpectrumSnapshot.empty();
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        DataSourceMediaDataSource mediaDataSource = new DataSourceMediaDataSource(dataSource, dataSpec);
        try {
            extractor.setDataSource(mediaDataSource);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                return PlaybackSpectrumSnapshot.empty();
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || mime.isEmpty()) {
                return PlaybackSpectrumSnapshot.empty();
            }
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decode(extractor, decoder, format, durationMs, cachedProgress);
        } catch (Exception ignored) {
            return PlaybackSpectrumSnapshot.empty();
        } finally {
            try {
                mediaDataSource.close();
            } catch (RuntimeException ignored) {
            }
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

    private static PlaybackSpectrumSnapshot decode(
            MediaExtractor extractor,
            MediaCodec decoder,
            MediaFormat sourceFormat,
            long durationMs,
            float cachedProgress
    ) {
        SpectrumAccumulator accumulator = new SpectrumAccumulator(durationMs, cachedProgress);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        MediaFormat outputFormat = sourceFormat;
        long durationUs = Math.max(1L, durationMs * 1000L);
        long cachedDurationUs = Math.max(1L, (long) (durationUs * Math.min(1.0f, cachedProgress)));
        int idleCodecPolls = 0;
        while (!outputDone) {
            boolean madeProgress = false;
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(8_000L);
                if (inputIndex >= 0) {
                    madeProgress = true;
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    int sampleSize = input == null ? -1 : extractor.readSampleData(input, 0);
                    long sampleTimeUs = extractor.getSampleTime();
                    if (sampleSize <= 0 || sampleTimeUs > cachedDurationUs) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, Math.max(0L, sampleTimeUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, Math.max(0L, sampleTimeUs), 0);
                        extractor.advance();
                    }
                }
            }
            int outputIndex = decoder.dequeueOutputBuffer(info, 8_000L);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder.getOutputFormat();
                madeProgress = true;
            } else if (outputIndex >= 0) {
                madeProgress = true;
                ByteBuffer output = decoder.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) {
                    readPcm(output, info, outputFormat, accumulator);
                }
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outputIndex, false);
            }
            if (madeProgress) {
                idleCodecPolls = 0;
            } else if (++idleCodecPolls >= MAX_IDLE_CODEC_POLLS) {
                break;
            }
        }
        return accumulator.snapshot();
    }

    private static void readPcm(
            ByteBuffer output,
            MediaCodec.BufferInfo info,
            MediaFormat format,
            SpectrumAccumulator accumulator
    ) {
        int sampleRate = Math.max(1, intValue(format, MediaFormat.KEY_SAMPLE_RATE));
        int channels = Math.max(1, intValue(format, MediaFormat.KEY_CHANNEL_COUNT));
        int encoding = intValue(format, MediaFormat.KEY_PCM_ENCODING);
        if (encoding == 0) {
            encoding = AudioFormat.ENCODING_PCM_16BIT;
        }
        int bytesPerSample = encoding == AudioFormat.ENCODING_PCM_8BIT ? 1
                : encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4
                : 2;
        int bytesPerFrame = bytesPerSample * channels;
        if (bytesPerFrame <= 0) {
            return;
        }
        ByteBuffer data = output.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.position(info.offset);
        data.limit(info.offset + info.size);
        int frameCount = info.size / bytesPerFrame;
        for (int frame = 0; frame < frameCount; frame++) {
            float sample = 0f;
            for (int channel = 0; channel < channels; channel++) {
                sample += sampleAmplitude(data, encoding);
            }
            long timeUs = info.presentationTimeUs + (frame * 1_000_000L / sampleRate);
            accumulator.addSample(sample / channels, timeUs, sampleRate);
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static int intValue(MediaFormat format, String key) {
        if (!format.containsKey(key)) {
            return 0;
        }
        try {
            return format.getInteger(key);
        } catch (RuntimeException ignored) {
        }
        return 0;
    }

    private static float sampleAmplitude(ByteBuffer buffer, int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            int value = (buffer.get() & 0xff) - 128;
            return Math.max(-1.0f, Math.min(1.0f, value / 128.0f));
        }
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            return Math.max(-1.0f, Math.min(1.0f, buffer.getFloat()));
        }
        return Math.max(-1.0f, Math.min(1.0f, buffer.getShort() / 32768.0f));
    }

    private static final class SpectrumAccumulator {
        private final float[] energy = new float[FRAME_COUNT * BAND_COUNT];
        private final float[] peaks = new float[FRAME_COUNT * BAND_COUNT];
        private final int[] counts = new int[FRAME_COUNT * BAND_COUNT];
        private final long durationUs;
        private final float cachedProgress;
        private float lowPass;
        private float lowMidPass;
        private float midPass;
        private float highMidPass;
        private float previousSample;
        private float previousHigh;
        private float kickFastEnvelope;
        private float kickSlowEnvelope;
        private int lastBucket = -1;
        private final float[] bandScratch = new float[BAND_COUNT];
        private final float[] anchorScratch = new float[6];

        SpectrumAccumulator(long durationMs, float cachedProgress) {
            this.durationUs = Math.max(1L, durationMs * 1000L);
            this.cachedProgress = Math.max(0f, Math.min(1f, cachedProgress));
        }

        void addSample(float sample, long timeUs, int sampleRate) {
            int frame = (int) ((Math.max(0L, timeUs) * FRAME_COUNT) / durationUs);
            frame = Math.max(0, Math.min(FRAME_COUNT - 1, frame));
            float[] bandValues = fastBandValues(sample, Math.max(1, sampleRate));
            for (int band = 0; band < BAND_COUNT; band++) {
                int index = frame * BAND_COUNT + band;
                float magnitude = bandValues[band];
                energy[index] += magnitude * magnitude;
                peaks[index] = Math.max(peaks[index], magnitude);
                counts[index] += 1;
            }
            if (frame != lastBucket) {
                lastBucket = frame;
            }
        }

        private float[] fastBandValues(float sample, int sampleRate) {
            float absSample = Math.abs(sample);
            lowPass += lowPassAlpha(90f, sampleRate) * (sample - lowPass);
            lowMidPass += lowPassAlpha(220f, sampleRate) * (sample - lowMidPass);
            midPass += lowPassAlpha(1_100f, sampleRate) * (sample - midPass);
            highMidPass += lowPassAlpha(4_800f, sampleRate) * (sample - highMidPass);

            float sub = Math.abs(lowPass);
            float kick = Math.abs(lowMidPass - lowPass) * 1.35f;
            float body = Math.abs(midPass - lowMidPass) * 1.12f;
            float presence = Math.abs(highMidPass - midPass) * 1.08f;
            float high = Math.abs(sample - highMidPass);
            float transientHigh = Math.abs(high - previousHigh) * 0.72f;
            float edge = Math.abs(sample - previousSample) * 0.40f;
            kickFastEnvelope = follow(kickFastEnvelope, kick, 0.78f, 0.18f);
            kickSlowEnvelope = follow(kickSlowEnvelope, kick, 0.045f, 0.018f);
            float kickTransient = Math.max(0f, kickFastEnvelope - kickSlowEnvelope * 1.12f) * 3.1f;
            previousSample = sample;
            previousHigh = high;

            anchorScratch[0] = sub + edge * 0.10f + kickTransient * 0.16f;
            anchorScratch[1] = kick * 0.72f + edge * 0.28f + kickTransient;
            anchorScratch[2] = body + edge * 0.18f + kickTransient * 0.12f;
            anchorScratch[3] = presence + transientHigh * 0.32f;
            anchorScratch[4] = high + transientHigh * 0.55f;
            anchorScratch[5] = absSample * 0.18f + high * 0.38f + transientHigh * 0.35f;
            for (int band = 0; band < BAND_COUNT; band++) {
                float position = band * (anchorScratch.length - 1f) / Math.max(1, BAND_COUNT - 1);
                int left = (int) position;
                int right = Math.min(anchorScratch.length - 1, left + 1);
                float mix = position - left;
                bandScratch[band] = Math.max(0f, anchorScratch[left] + (anchorScratch[right] - anchorScratch[left]) * mix);
            }
            return bandScratch;
        }

        private float lowPassAlpha(float cutoffHz, int sampleRate) {
            float normalized = Math.max(0.0001f, Math.min(0.48f, cutoffHz / Math.max(1f, sampleRate)));
            return Math.max(0.001f, Math.min(0.45f, normalized * 2.0f));
        }

        private float follow(float current, float target, float attack, float release) {
            float coefficient = target > current ? attack : release;
            return current + (target - current) * coefficient;
        }

        PlaybackSpectrumSnapshot snapshot() {
            float[] bands = new float[FRAME_COUNT * BAND_COUNT];
            float max = 0f;
            int generatedFrames = Math.max(1, Math.min(FRAME_COUNT, (int) Math.ceil(FRAME_COUNT * cachedProgress)));
            for (int frame = 0; frame < generatedFrames; frame++) {
                for (int band = 0; band < BAND_COUNT; band++) {
                    int index = frame * BAND_COUNT + band;
                    if (counts[index] <= 0) {
                        continue;
                    }
                    float value = (float) Math.sqrt(energy[index] / counts[index]);
                    if (isKickBand(band)) {
                        value = Math.max(value, peaks[index] * 1.18f);
                    }
                    bands[index] = value;
                    max = Math.max(max, value);
                }
            }
            if (max <= 0f) {
                return PlaybackSpectrumSnapshot.empty();
            }
            for (int i = 0; i < bands.length; i++) {
                bands[i] = (float) Math.sqrt(Math.max(0f, Math.min(1f, bands[i] / max)));
            }
            return new PlaybackSpectrumSnapshot(bands, generatedFrames, BAND_COUNT, cachedProgress);
        }

        private boolean isKickBand(int band) {
            return band >= 1 && band <= 3;
        }
    }

    private static final class DataSourceMediaDataSource extends MediaDataSource {
        private final DataSource dataSource;
        private final DataSpec baseSpec;
        private final long length;
        private long nextReadPosition = -1L;

        DataSourceMediaDataSource(DataSource dataSource, DataSpec baseSpec) {
            this.dataSource = dataSource;
            this.baseSpec = baseSpec;
            this.length = Math.max(0L, baseSpec.length);
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) {
            if (position < 0L || position >= length || size <= 0) {
                return -1;
            }
            int readSize = (int) Math.min(size, length - position);
            try {
                openAt(position);
                int read = dataSource.read(buffer, offset, readSize);
                if (read <= 0) {
                    return -1;
                }
                nextReadPosition = position + read;
                return read;
            } catch (Exception ignored) {
                return -1;
            }
        }

        @Override
        public long getSize() {
            return length;
        }

        @Override
        public void close() {
            try {
                dataSource.close();
            } catch (Exception ignored) {
            }
            nextReadPosition = -1L;
        }

        private void openAt(long position) throws java.io.IOException {
            if (position == nextReadPosition) {
                return;
            }
            try {
                dataSource.close();
            } catch (Exception ignored) {
            }
            DataSpec spec = baseSpec.buildUpon()
                    .setPosition(baseSpec.position + position)
                    .setLength(length - position)
                    .build();
            dataSource.open(spec);
            nextReadPosition = position;
        }
    }
}
