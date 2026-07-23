package app.yukine.playback.manager;

import app.yukine.playback.AudioFallbackReason;
import app.yukine.playback.AudioOutputPhase;
import app.yukine.playback.AudioOutputSnapshot;
import app.yukine.playback.AudioTransport;

/**
 * Single owner for requested output policy and the transport that is actually active.
 *
 * <p>The service delegates device events and sink state here instead of maintaining parallel
 * booleans and free-form fallback strings.</p>
 */
public final class PlaybackAudioOutputCoordinator {
    private volatile boolean bitPerfectRequested;
    private volatile boolean usbExclusiveRequested;
    private boolean usbFailureLatched;
    private boolean mediaTransitionRetryArmed;
    private UsbPcmFormatKey failedUsbFormat;
    private AudioFallbackReason failedUsbReason = AudioFallbackReason.NONE;
    private volatile AudioOutputSnapshot snapshot = AudioOutputSnapshot.idle();

    public synchronized AudioOutputMode updateRequests(
            boolean bitPerfectRequested,
            boolean usbExclusiveRequested,
            AudioDeviceCapabilityProbe.AudioDeviceProfile profile
    ) {
        this.bitPerfectRequested = bitPerfectRequested;
        this.usbExclusiveRequested = usbExclusiveRequested;
        boolean usbConnected = profile != null && profile.isUsbAudioDeviceConnected();
        if (!usbExclusiveRequested || !usbConnected) {
            // An explicit off request or a real detach arms the next user request/reconnect for
            // a fresh negotiation. Ordinary AudioDevice callbacks while the DAC remains present
            // must not immediately re-enter a session that has just failed.
            clearUsbFailureLatch();
        }
        AudioOutputMode resolved = AudioOutputModeResolver.resolve(
                bitPerfectRequested,
                usbExclusiveRequested,
                profile
        );
        if (usbFailureLatched && resolved == AudioOutputMode.USB_EXCLUSIVE) {
            if (mediaTransitionRetryArmed) {
                mediaTransitionRetryArmed = false;
                return AudioOutputMode.USB_EXCLUSIVE;
            }
            return bitPerfectRequested ? AudioOutputMode.DIRECT_PCM : AudioOutputMode.STANDARD;
        }
        return resolved;
    }

    public synchronized void onTargetMode(
            AudioOutputMode mode,
            int sampleRateHz,
            String usbDeviceName
    ) {
        if (mode == AudioOutputMode.USB_EXCLUSIVE) {
            snapshot = new AudioOutputSnapshot(
                    AudioTransport.USB_PCM,
                    AudioOutputPhase.NEGOTIATING,
                    usbDeviceName,
                    sampleRateHz,
                    0,
                    0,
                    0,
                    AudioFallbackReason.NONE,
                    0, 0L, 0L, 0L, 0L, 0L, 0.0d, ""
            );
            return;
        }
        AudioFallbackReason fallbackReason = snapshot.phase == AudioOutputPhase.FALLBACK
                ? snapshot.fallbackReason
                : AudioFallbackReason.NONE;
        if (fallbackReason == AudioFallbackReason.NONE) {
            snapshot = AudioOutputSnapshot.system(
                    transportFor(mode),
                    AudioOutputPhase.ACTIVE,
                    sampleRateHz,
                    AudioFallbackReason.NONE
            );
        } else {
            snapshot = new AudioOutputSnapshot(
                    transportFor(mode),
                    AudioOutputPhase.FALLBACK,
                    "",
                    sampleRateHz,
                    0,
                    0,
                    0,
                    fallbackReason,
                    0,
                    snapshot.submittedPackets,
                    snapshot.completedPackets,
                    snapshot.failedPackets,
                    snapshot.underruns,
                    snapshot.framesWritten,
                    snapshot.feedbackRateHz,
                    snapshot.lastError
            );
        }
    }

    public synchronized void onUsbSnapshot(AudioOutputSnapshot usbSnapshot) {
        if (usbSnapshot == null) return;
        snapshot = usbSnapshot;
        if (usbSnapshot.phase == AudioOutputPhase.ACTIVE) {
            clearUsbFailureLatch();
        } else if (usbSnapshot.phase == AudioOutputPhase.NEGOTIATING
                && failedUsbFormat != null
                && !failedUsbFormat.matches(UsbPcmFormatKey.from(usbSnapshot))) {
            // The sink has now revealed the decoded format for the new item. A different PCM
            // request clears the old format-specific failure while negotiation is in progress.
            clearUsbFailureLatch();
        } else if (usbSnapshot.phase == AudioOutputPhase.FALLBACK
                || usbSnapshot.phase == AudioOutputPhase.ERROR) {
            if (!isUsbFailure(usbSnapshot.fallbackReason)) {
                return;
            }
            usbFailureLatched = true;
            mediaTransitionRetryArmed = false;
            failedUsbFormat = UsbPcmFormatKey.from(usbSnapshot);
            failedUsbReason = usbSnapshot.fallbackReason;
        }
    }

    /**
     * Allows exactly one fresh USB negotiation after a real media-item change. Local PCM formats
     * must differ from the failed format. HTTP media may retry even when its persisted format is
     * complete and unchanged because the URL replacement is a new decoder/session boundary.
     */
    public synchronized boolean armUsbRetryForMediaItemTransition(
            int sampleRateHz,
            int bitDepth,
            int channelCount,
            boolean allowStreamingMedia
    ) {
        if (!usbExclusiveRequested || !usbFailureLatched || mediaTransitionRetryArmed) {
            return false;
        }
        if (!isRetryableMediaTransitionFailure(failedUsbReason)) {
            return false;
        }
        UsbPcmFormatKey nextFormat = new UsbPcmFormatKey(sampleRateHz, bitDepth, channelCount);
        boolean knownDifferentFormat = nextFormat.isComplete()
                && failedUsbFormat != null
                && failedUsbFormat.isComplete()
                && !failedUsbFormat.matches(nextFormat);
        boolean streamingMediaRetry = allowStreamingMedia
                && isRetryableMediaTransitionFailure(failedUsbReason);
        if (!knownDifferentFormat && !streamingMediaRetry) {
            return false;
        }
        mediaTransitionRetryArmed = true;
        return true;
    }

    public synchronized void onSystemFallback(
            AudioOutputMode fallbackMode,
            int sampleRateHz,
            AudioFallbackReason reason,
            String error
    ) {
        if (reason == AudioFallbackReason.DEVICE_DETACHED) {
            clearUsbFailureLatch();
        } else if (usbExclusiveRequested && isUsbFailure(reason)) {
            usbFailureLatched = true;
            mediaTransitionRetryArmed = false;
            failedUsbReason = reason;
            failedUsbFormat = UsbPcmFormatKey.from(snapshot);
        }
        snapshot = new AudioOutputSnapshot(
                transportFor(fallbackMode),
                AudioOutputPhase.FALLBACK,
                "",
                sampleRateHz,
                0,
                0,
                0,
                reason,
                0,
                snapshot.submittedPackets,
                snapshot.completedPackets,
                snapshot.failedPackets,
                snapshot.underruns,
                snapshot.framesWritten,
                snapshot.feedbackRateHz,
                error
        );
    }

    public AudioOutputSnapshot snapshot() {
        return snapshot;
    }

    public boolean bitPerfectRequested() {
        return bitPerfectRequested;
    }

    public boolean usbExclusiveRequested() {
        return usbExclusiveRequested;
    }

    public boolean usbActive() {
        return snapshot.isUsbActive();
    }

    private static boolean isUsbFailure(AudioFallbackReason reason) {
        return reason != null
                && reason != AudioFallbackReason.NONE
                && reason != AudioFallbackReason.DEVICE_DETACHED
                && reason != AudioFallbackReason.OFFLOAD_UNAVAILABLE
                && reason != AudioFallbackReason.OFFLOAD_FAILED;
    }

    private static boolean isRetryableMediaTransitionFailure(AudioFallbackReason reason) {
        return reason == AudioFallbackReason.NO_COMPATIBLE_ENDPOINT
                || reason == AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
                || reason == AudioFallbackReason.SESSION_RECONFIGURE_FAILED
                || reason == AudioFallbackReason.FORMAT_UNSUPPORTED;
    }

    private void clearUsbFailureLatch() {
        usbFailureLatched = false;
        mediaTransitionRetryArmed = false;
        failedUsbFormat = null;
        failedUsbReason = AudioFallbackReason.NONE;
    }

    private static AudioTransport transportFor(AudioOutputMode mode) {
        if (mode == AudioOutputMode.HARDWARE_OFFLOAD) return AudioTransport.SYSTEM_OFFLOAD;
        if (mode == AudioOutputMode.DIRECT_PCM) return AudioTransport.SYSTEM_DIRECT_PCM;
        if (mode == AudioOutputMode.USB_EXCLUSIVE) return AudioTransport.USB_PCM;
        return AudioTransport.SYSTEM_STANDARD;
    }

    private static final class UsbPcmFormatKey {
        final int sampleRateHz;
        final int bitDepth;
        final int channelCount;

        UsbPcmFormatKey(int sampleRateHz, int bitDepth, int channelCount) {
            this.sampleRateHz = sampleRateHz;
            this.bitDepth = bitDepth;
            this.channelCount = channelCount;
        }

        static UsbPcmFormatKey from(AudioOutputSnapshot snapshot) {
            int requestedRate = snapshot.requestedSampleRateHz > 0
                    ? snapshot.requestedSampleRateHz
                    : snapshot.sampleRateHz;
            return new UsbPcmFormatKey(requestedRate, snapshot.bitDepth, snapshot.channelCount);
        }

        boolean matches(UsbPcmFormatKey other) {
            return other != null
                    && sampleRateHz == other.sampleRateHz
                    && bitDepth == other.bitDepth
                    && channelCount == other.channelCount;
        }

        boolean isComplete() {
            return sampleRateHz > 0 && bitDepth > 0 && channelCount > 0;
        }
    }
}
