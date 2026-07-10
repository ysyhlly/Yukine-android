package app.yukine.playback;

import java.util.function.LongSupplier;

final class RealtimeBassDetector {
    static final int REALTIME_BAND_COUNT = 24;
    private static final long BEAT_HOLD_MS = 34L;
    private static final float SILENCE_GATE = 0.0065f;

    private static final RealtimeFrame EMPTY_FRAME = new RealtimeFrame(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0L, Long.MIN_VALUE
    );

    private final LongSupplier clock;
    private volatile AnalysisConfig configuration = new AnalysisConfig(44100, 2);
    private volatile long resetGeneration;
    private long audioStateGeneration = Long.MIN_VALUE;
    private volatile RealtimeFrame realtimeFrame = EMPTY_FRAME;

    // These fields are exclusively owned by ExoPlayer's audio callback thread. UI readers
    // consume the immutable volatile RealtimeFrame above, so they can never observe a
    // partially-written DSP state or slow down audio processing with a shared lock.
    private float lowPass;
    private float bodyPass;
    private float kickFast;
    private float kickSlow;
    private float outputBeat;
    private float subEnergy;
    private float kickEnergy;
    private float bodyEnergy;
    private float midEnergy;
    private float presenceEnergy;
    private float highEnergy;
    private float previousMono;

    RealtimeBassDetector() {
        this(System::currentTimeMillis);
    }

    RealtimeBassDetector(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    synchronized void configure(int sampleRate, int channelCount, int encoding) {
        configuration = new AnalysisConfig(sampleRate, channelCount);
        requestResetLocked();
    }

    synchronized void reset() {
        requestResetLocked();
    }

    void analyze16BitPcm(java.nio.ByteBuffer buffer, int bytesPerFrame) {
        long generation = resetGeneration;
        AnalysisConfig config = configuration;
        if (audioStateGeneration != generation) {
            resetAudioState();
            audioStateGeneration = generation;
        }
        if (buffer == null || bytesPerFrame <= 0 || config.channelCount <= 0) {
            return;
        }
        int start = buffer.position();
        int limit = buffer.limit();
        int frames = (limit - start) / bytesPerFrame;
        if (frames <= 0) {
            return;
        }
        int step = Math.max(config.frameStride, 1);
        float localBeat = outputBeat * 0.66f;
        float localSub = subEnergy * 0.92f;
        float localKick = kickEnergy * 0.84f;
        float localBody = bodyEnergy * 0.88f;
        float localMid = midEnergy * 0.90f;
        float localPresence = presenceEnergy * 0.91f;
        float localHigh = highEnergy * 0.93f;
        float localPreviousMono = previousMono;
        for (int frame = 0; frame < frames; frame += step) {
            int base = start + frame * bytesPerFrame;
            float mono = 0f;
            for (int channel = 0; channel < config.channelCount; channel++) {
                int sampleOffset = base + channel * 2;
                if (sampleOffset + 1 >= limit) {
                    break;
                }
                int low = buffer.get(sampleOffset) & 0xFF;
                int high = buffer.get(sampleOffset + 1);
                short sample = (short) ((high << 8) | low);
                mono += sample / 32768f;
            }
            mono /= config.channelCount;
            float abs = Math.abs(mono);
            lowPass += (mono - lowPass) * 0.055f;
            bodyPass += (lowPass - bodyPass) * 0.15f;
            float kick = Math.abs(lowPass - bodyPass) + abs * 0.10f;
            float body = Math.abs(bodyPass) + abs * 0.08f;
            float mid = Math.abs(mono - lowPass) * 0.72f + abs * 0.05f;
            float high = Math.abs(mono - localPreviousMono) * 0.42f;
            localPreviousMono = mono;
            float fastAttack = kick > kickFast ? 0.72f : 0.42f;
            kickFast += (kick - kickFast) * fastAttack;
            kickSlow += (kick - kickSlow) * (kick > kickSlow ? 0.018f : 0.006f);
            float onset = kickFast - kickSlow * 1.42f - SILENCE_GATE;
            if (onset > 0f) {
                float shaped = 1f - (1f - clamp01(onset * 12.0f)) * (1f - clamp01(onset * 12.0f));
                localBeat = Math.max(localBeat, shaped);
            } else {
                localBeat *= 0.90f;
            }
            localSub = follow(localSub, Math.abs(lowPass) * 5.4f, 0.42f, 0.052f);
            localKick = follow(localKick, kick * 7.4f, 0.68f, 0.078f);
            localBody = follow(localBody, body * 4.8f, 0.46f, 0.062f);
            localMid = follow(localMid, mid * 4.2f, 0.38f, 0.052f);
            localPresence = follow(localPresence, (mid + high * 0.48f) * 3.6f, 0.32f, 0.046f);
            localHigh = follow(localHigh, high * 3.4f, 0.28f, 0.040f);
        }
        // A control-thread reset/configure may happen while one audio buffer is being
        // analyzed. Discard that obsolete result; the next audio buffer starts cleanly.
        if (resetGeneration != generation) {
            return;
        }
        outputBeat = clamp01(Math.max(outputBeat * 0.58f, localBeat));
        subEnergy = clamp01(localSub);
        kickEnergy = clamp01(localKick);
        bodyEnergy = clamp01(localBody);
        midEnergy = clamp01(localMid);
        presenceEnergy = clamp01(localPresence);
        highEnergy = clamp01(localHigh);
        previousMono = localPreviousMono;
        realtimeFrame = new RealtimeFrame(
                outputBeat,
                subEnergy,
                kickEnergy,
                bodyEnergy,
                midEnergy,
                presenceEnergy,
                highEnergy,
                clock.getAsLong(),
                generation
        );
    }

    float beat() {
        RealtimeFrame frame = realtimeFrame;
        if (frame.generation != resetGeneration) {
            return 0f;
        }
        long elapsed = elapsedSince(frame.updatedAtMs);
        float beat = frame.beat;
        if (elapsed > BEAT_HOLD_MS) {
            float decay = Math.max(0f, 1f - (elapsed - BEAT_HOLD_MS) / 105f);
            beat *= decay;
        }
        return beat < 0.015f ? 0f : beat;
    }

    float[] bands() {
        RealtimeFrame frame = realtimeFrame;
        if (frame.generation != resetGeneration) {
            return new float[REALTIME_BAND_COUNT];
        }
        long elapsed = elapsedSince(frame.updatedAtMs);
        float decay = elapsed <= BEAT_HOLD_MS ? 1f : Math.max(0f, 1f - (elapsed - BEAT_HOLD_MS) / 280f);
        float[] result = new float[REALTIME_BAND_COUNT];
        for (int index = 0; index < REALTIME_BAND_COUNT; index++) {
            float position = index / (float) (REALTIME_BAND_COUNT - 1);
            float value;
            if (index < 3) {
                value = frame.subEnergy * 0.55f + frame.kickEnergy * 0.45f;
            } else if (index < 7) {
                value = frame.kickEnergy * 0.72f + frame.bodyEnergy * 0.28f;
            } else if (index < 12) {
                value = frame.bodyEnergy * 0.62f + frame.midEnergy * 0.38f;
            } else if (index < 18) {
                value = frame.midEnergy * 0.48f + frame.presenceEnergy * 0.52f;
            } else {
                value = frame.presenceEnergy * 0.34f + frame.highEnergy * 0.66f;
            }
            float ripple = 0.82f + 0.18f * (float) Math.sin(position * Math.PI * 4.0 + elapsed * 0.022);
            float shaped = (float) Math.pow(clamp01(value * 0.95f), 0.72f);
            result[index] = clamp01(shaped * ripple * decay);
        }
        return result;
    }

    private synchronized void requestResetLocked() {
        resetGeneration++;
        realtimeFrame = new RealtimeFrame(
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0L, resetGeneration
        );
    }

    private long elapsedSince(long updatedAtMs) {
        if (updatedAtMs <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, clock.getAsLong() - updatedAtMs);
    }

    private void resetAudioState() {
        lowPass = 0f;
        bodyPass = 0f;
        kickFast = 0f;
        kickSlow = 0f;
        outputBeat = 0f;
        subEnergy = 0f;
        kickEnergy = 0f;
        bodyEnergy = 0f;
        midEnergy = 0f;
        presenceEnergy = 0f;
        highEnergy = 0f;
        previousMono = 0f;
    }

    private static float follow(float current, float target, float attack, float decay) {
        float boundedTarget = clamp01(target);
        float coefficient = boundedTarget > current ? attack : decay;
        return current + (boundedTarget - current) * coefficient;
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static final class AnalysisConfig {
        final int channelCount;
        final int frameStride;

        AnalysisConfig(int sampleRate, int channelCount) {
            int boundedSampleRate = Math.max(sampleRate, 8000);
            this.channelCount = Math.max(channelCount, 1);
            this.frameStride = boundedSampleRate >= 48000 ? 5 : 4;
        }
    }

    private static final class RealtimeFrame {
        final float beat;
        final float subEnergy;
        final float kickEnergy;
        final float bodyEnergy;
        final float midEnergy;
        final float presenceEnergy;
        final float highEnergy;
        final long updatedAtMs;
        final long generation;

        RealtimeFrame(
                float beat,
                float subEnergy,
                float kickEnergy,
                float bodyEnergy,
                float midEnergy,
                float presenceEnergy,
                float highEnergy,
                long updatedAtMs,
                long generation
        ) {
            this.beat = beat;
            this.subEnergy = subEnergy;
            this.kickEnergy = kickEnergy;
            this.bodyEnergy = bodyEnergy;
            this.midEnergy = midEnergy;
            this.presenceEnergy = presenceEnergy;
            this.highEnergy = highEnergy;
            this.updatedAtMs = updatedAtMs;
            this.generation = generation;
        }
    }
}
