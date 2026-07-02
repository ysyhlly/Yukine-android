package app.yukine.playback;

final class RealtimeBassDetector {
    static final int REALTIME_BAND_COUNT = 24;
    private static final long BEAT_HOLD_MS = 34L;
    private static final float SILENCE_GATE = 0.0065f;

    private int sampleRate = 44100;
    private int channelCount = 2;
    private int encoding;
    private int frameStride = 4;
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
    private long lastUpdateMs;

    synchronized void configure(int sampleRate, int channelCount, int encoding) {
        this.sampleRate = Math.max(sampleRate, 8000);
        this.channelCount = Math.max(channelCount, 1);
        this.encoding = encoding;
        this.frameStride = this.sampleRate >= 48000 ? 5 : 4;
        resetLocked();
    }

    synchronized void reset() {
        resetLocked();
    }

    void analyze16BitPcm(java.nio.ByteBuffer buffer, int bytesPerFrame) {
        if (buffer == null || bytesPerFrame <= 0 || channelCount <= 0) {
            return;
        }
        int start = buffer.position();
        int limit = buffer.limit();
        int frames = (limit - start) / bytesPerFrame;
        if (frames <= 0) {
            return;
        }
        int step = Math.max(frameStride, 1);
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
            for (int channel = 0; channel < channelCount; channel++) {
                int sampleOffset = base + channel * 2;
                if (sampleOffset + 1 >= limit) {
                    break;
                }
                int low = buffer.get(sampleOffset) & 0xFF;
                int high = buffer.get(sampleOffset + 1);
                short sample = (short) ((high << 8) | low);
                mono += sample / 32768f;
            }
            mono /= channelCount;
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
        outputBeat = clamp01(Math.max(outputBeat * 0.58f, localBeat));
        subEnergy = clamp01(localSub);
        kickEnergy = clamp01(localKick);
        bodyEnergy = clamp01(localBody);
        midEnergy = clamp01(localMid);
        presenceEnergy = clamp01(localPresence);
        highEnergy = clamp01(localHigh);
        previousMono = localPreviousMono;
        lastUpdateMs = System.currentTimeMillis();
    }

    synchronized float beat() {
        long elapsed = lastUpdateMs <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastUpdateMs;
        if (elapsed > BEAT_HOLD_MS) {
            float decay = Math.max(0f, 1f - (elapsed - BEAT_HOLD_MS) / 105f);
            outputBeat *= decay;
        }
        if (outputBeat < 0.015f) {
            outputBeat = 0f;
        }
        return outputBeat;
    }

    synchronized float[] bands() {
        long elapsed = lastUpdateMs <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastUpdateMs;
        float decay = elapsed <= BEAT_HOLD_MS ? 1f : Math.max(0f, 1f - (elapsed - BEAT_HOLD_MS) / 280f);
        float[] result = new float[REALTIME_BAND_COUNT];
        for (int index = 0; index < REALTIME_BAND_COUNT; index++) {
            float position = index / (float) (REALTIME_BAND_COUNT - 1);
            float value;
            if (index < 3) {
                value = subEnergy * 0.55f + kickEnergy * 0.45f;
            } else if (index < 7) {
                value = kickEnergy * 0.72f + bodyEnergy * 0.28f;
            } else if (index < 12) {
                value = bodyEnergy * 0.62f + midEnergy * 0.38f;
            } else if (index < 18) {
                value = midEnergy * 0.48f + presenceEnergy * 0.52f;
            } else {
                value = presenceEnergy * 0.34f + highEnergy * 0.66f;
            }
            float ripple = 0.82f + 0.18f * (float) Math.sin(position * Math.PI * 4.0 + elapsed * 0.022);
            float shaped = (float) Math.pow(clamp01(value * 0.95f), 0.72f);
            result[index] = clamp01(shaped * ripple * decay);
        }
        return result;
    }

    private void resetLocked() {
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
        lastUpdateMs = 0L;
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
}
