package app.echo.next.playback;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

final class StreamingWaveformGenerator {
    private static final int BAR_COUNT = 96;
    private static final int MAX_IDLE_CODEC_POLLS = 24;

    private StreamingWaveformGenerator() {
    }

    static PlaybackWaveformSnapshot extract(
            Context context,
            DataSource dataSource,
            DataSpec dataSpec,
            long durationMs,
            float cachedProgress,
            String key
    ) {
        if (context == null || dataSource == null || dataSpec == null || durationMs <= 0L || cachedProgress <= 0.0f) {
            return PlaybackWaveformSnapshot.empty();
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        DataSourceMediaDataSource mediaDataSource = new DataSourceMediaDataSource(dataSource, dataSpec);
        try {
            extractor.setDataSource(mediaDataSource);
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                return PlaybackWaveformSnapshot.empty();
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || mime.isEmpty()) {
                return PlaybackWaveformSnapshot.empty();
            }
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decode(extractor, decoder, format, durationMs, cachedProgress);
        } catch (Exception ignored) {
            return PlaybackWaveformSnapshot.empty();
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

    private static PlaybackWaveformSnapshot decode(
            MediaExtractor extractor,
            MediaCodec decoder,
            MediaFormat sourceFormat,
            long durationMs,
            float cachedProgress
    ) {
        float[] energy = new float[BAR_COUNT];
        int[] counts = new int[BAR_COUNT];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        MediaFormat outputFormat = sourceFormat;
        long durationUs = Math.max(1L, durationMs * 1000L);
        long cachedDurationUs = Math.max(1L, (long) (durationUs * Math.min(1.0f, cachedProgress)));
        int generatedBars = Math.max(1, Math.min(BAR_COUNT, (int) Math.ceil(BAR_COUNT * Math.min(1.0f, cachedProgress))));
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
                        decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                Math.max(0L, sampleTimeUs),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
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
                    readPcm(output, info, outputFormat, durationUs, generatedBars, energy, counts);
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
        int actualGeneratedBars = continuousGeneratedBars(counts, generatedBars);
        float[] peaks = new float[BAR_COUNT];
        float maxPeak = 0f;
        float minPeak = Float.MAX_VALUE;
        for (int i = 0; i < actualGeneratedBars; i++) {
            if (counts[i] <= 0) {
                continue;
            }
            float rms = (float) Math.sqrt(energy[i] / counts[i]);
            peaks[i] = rms;
            maxPeak = Math.max(maxPeak, rms);
            minPeak = Math.min(minPeak, rms);
        }
        if (maxPeak <= 0f) {
            return PlaybackWaveformSnapshot.empty();
        }
        float floor = minPeak == Float.MAX_VALUE ? 0f : minPeak * 0.72f;
        float span = Math.max(0.001f, maxPeak - floor);
        for (int i = 0; i < actualGeneratedBars; i++) {
            peaks[i] = Math.max(0f, Math.min(1.0f, (peaks[i] - floor) / span));
        }
        return new PlaybackWaveformSnapshot(peaks, actualGeneratedBars, cachedProgress);
    }

    static int continuousGeneratedBars(int[] counts, int generatedBars) {
        if (counts == null || generatedBars <= 0) {
            return 0;
        }
        int limit = Math.min(generatedBars, counts.length);
        int continuous = 0;
        for (int i = 0; i < limit; i++) {
            if (counts[i] <= 0) {
                break;
            }
            continuous++;
        }
        return continuous;
    }

    private static void readPcm(
            ByteBuffer output,
            MediaCodec.BufferInfo info,
            MediaFormat format,
            long durationUs,
            int generatedBars,
            float[] energy,
            int[] counts
    ) {
        int sampleRate = Math.max(1, intValue(format, MediaFormat.KEY_SAMPLE_RATE));
        int channels = Math.max(1, intValue(format, MediaFormat.KEY_CHANNEL_COUNT));
        int encoding = intValue(format, MediaFormat.KEY_PCM_ENCODING);
        if (encoding == 0) {
            encoding = AudioFormat.ENCODING_PCM_16BIT;
        }
        int bytesPerSample;
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            bytesPerSample = 1;
        } else if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            bytesPerSample = 4;
        } else {
            bytesPerSample = 2;
        }
        int bytesPerFrame = bytesPerSample * channels;
        if (bytesPerFrame <= 0) {
            return;
        }
        ByteBuffer data = output.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.position(info.offset);
        data.limit(info.offset + info.size);
        int frameCount = info.size / bytesPerFrame;
        for (int frame = 0; frame < frameCount; frame++) {
            float peak = 0f;
            for (int channel = 0; channel < channels; channel++) {
                peak = Math.max(peak, sampleAmplitude(data, encoding));
            }
            long timeUs = info.presentationTimeUs + (frame * 1_000_000L / sampleRate);
            int bucket = (int) ((Math.max(0L, timeUs) * energy.length) / durationUs);
            bucket = Math.max(0, Math.min(Math.min(generatedBars, energy.length) - 1, bucket));
            energy[bucket] += peak * peak;
            counts[bucket] += 1;
        }
    }

    private static float sampleAmplitude(ByteBuffer buffer, int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            int value = (buffer.get() & 0xff) - 128;
            return Math.min(1.0f, Math.abs(value) / 128.0f);
        }
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            return Math.min(1.0f, Math.abs(buffer.getFloat()));
        }
        return Math.min(1.0f, Math.abs(buffer.getShort()) / 32768.0f);
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
                int total = 0;
                while (total < readSize) {
                    int read = dataSource.read(buffer, offset + total, readSize - total);
                    if (read <= 0) {
                        break;
                    }
                    total += read;
                    nextReadPosition += read;
                }
                return total == 0 ? -1 : total;
            } catch (Exception ignored) {
                closeOpenDataSource();
                return -1;
            }
        }

        @Override
        public long getSize() {
            return length;
        }

        @Override
        public void close() {
            closeOpenDataSource();
        }

        private void openAt(long position) throws java.io.IOException {
            if (position == nextReadPosition) {
                return;
            }
            closeOpenDataSource();
            DataSpec rangeSpec = baseSpec.buildUpon()
                    .setPosition(baseSpec.position + position)
                    .setLength(length - position)
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
