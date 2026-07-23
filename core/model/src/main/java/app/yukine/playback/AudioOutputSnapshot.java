package app.yukine.playback;

/**
 * Truthful, immutable view of the active audio path.
 *
 * <p>Requested settings intentionally live outside this object. This snapshot describes what
 * the runtime has actually negotiated, including a typed failure reason and transport counters.</p>
 */
public final class AudioOutputSnapshot {
    public final AudioTransport transport;
    public final AudioOutputPhase phase;
    public final String deviceName;
    public final int sampleRateHz;
    public final int previousSampleRateHz;
    public final int requestedSampleRateHz;
    public final int bitDepth;
    public final int channelCount;
    public final int dsdRate;
    public final AudioFallbackReason fallbackReason;
    public final int queueDepth;
    public final long submittedPackets;
    public final long completedPackets;
    public final long failedPackets;
    public final long underruns;
    public final long framesWritten;
    public final double feedbackRateHz;
    public final String lastError;

    public AudioOutputSnapshot(
            AudioTransport transport,
            AudioOutputPhase phase,
            String deviceName,
            int sampleRateHz,
            int bitDepth,
            int channelCount,
            int dsdRate,
            AudioFallbackReason fallbackReason,
            int queueDepth,
            long submittedPackets,
            long completedPackets,
            long failedPackets,
            long underruns,
            long framesWritten,
            double feedbackRateHz,
            String lastError
    ) {
        this(
                transport,
                phase,
                deviceName,
                sampleRateHz,
                sampleRateHz,
                sampleRateHz,
                bitDepth,
                channelCount,
                dsdRate,
                fallbackReason,
                queueDepth,
                submittedPackets,
                completedPackets,
                failedPackets,
                underruns,
                framesWritten,
                feedbackRateHz,
                lastError
        );
    }

    public AudioOutputSnapshot(
            AudioTransport transport,
            AudioOutputPhase phase,
            String deviceName,
            int sampleRateHz,
            int previousSampleRateHz,
            int requestedSampleRateHz,
            int bitDepth,
            int channelCount,
            int dsdRate,
            AudioFallbackReason fallbackReason,
            int queueDepth,
            long submittedPackets,
            long completedPackets,
            long failedPackets,
            long underruns,
            long framesWritten,
            double feedbackRateHz,
            String lastError
    ) {
        this.transport = transport == null ? AudioTransport.SYSTEM_STANDARD : transport;
        this.phase = phase == null ? AudioOutputPhase.IDLE : phase;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.sampleRateHz = Math.max(sampleRateHz, 0);
        this.previousSampleRateHz = Math.max(previousSampleRateHz, 0);
        this.requestedSampleRateHz = Math.max(requestedSampleRateHz, 0);
        this.bitDepth = Math.max(bitDepth, 0);
        this.channelCount = Math.max(channelCount, 0);
        this.dsdRate = Math.max(dsdRate, 0);
        this.fallbackReason = fallbackReason == null ? AudioFallbackReason.NONE : fallbackReason;
        this.queueDepth = Math.max(queueDepth, 0);
        this.submittedPackets = Math.max(submittedPackets, 0L);
        this.completedPackets = Math.max(completedPackets, 0L);
        this.failedPackets = Math.max(failedPackets, 0L);
        this.underruns = Math.max(underruns, 0L);
        this.framesWritten = Math.max(framesWritten, 0L);
        this.feedbackRateHz = Math.max(feedbackRateHz, 0.0d);
        this.lastError = lastError == null ? "" : lastError;
    }

    public static AudioOutputSnapshot idle() {
        return system(AudioTransport.SYSTEM_STANDARD, AudioOutputPhase.IDLE, 0, AudioFallbackReason.NONE);
    }

    public static AudioOutputSnapshot system(
            AudioTransport transport,
            AudioOutputPhase phase,
            int sampleRateHz,
            AudioFallbackReason reason
    ) {
        return new AudioOutputSnapshot(
                transport,
                phase,
                "",
                sampleRateHz,
                0,
                0,
                0,
                reason,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0d,
                ""
        );
    }

    public static AudioOutputSnapshot transition(
            AudioTransport transport,
            String deviceName,
            int previousSampleRateHz,
            int requestedSampleRateHz,
            int bitDepth,
            int channelCount
    ) {
        return new AudioOutputSnapshot(
                transport,
                AudioOutputPhase.NEGOTIATING,
                deviceName,
                previousSampleRateHz,
                previousSampleRateHz,
                requestedSampleRateHz,
                bitDepth,
                channelCount,
                0,
                AudioFallbackReason.NONE,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0d,
                ""
        );
    }

    public AudioOutputSnapshot withMetrics(
            int queueDepth,
            long submittedPackets,
            long completedPackets,
            long failedPackets,
            long underruns,
            long framesWritten,
            double feedbackRateHz,
            String lastError
    ) {
        return new AudioOutputSnapshot(
                transport,
                phase,
                deviceName,
                sampleRateHz,
                previousSampleRateHz,
                requestedSampleRateHz,
                bitDepth,
                channelCount,
                dsdRate,
                fallbackReason,
                queueDepth,
                submittedPackets,
                completedPackets,
                failedPackets,
                underruns,
                framesWritten,
                feedbackRateHz,
                lastError
        );
    }

    public boolean isUsbActive() {
        return phase == AudioOutputPhase.ACTIVE
                && (transport == AudioTransport.USB_PCM
                || transport == AudioTransport.USB_DOP
                || transport == AudioTransport.USB_NATIVE_DSD);
    }
}
