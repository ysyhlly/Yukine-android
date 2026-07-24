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
    private String failedUsbDeviceName = "";
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
            failedUsbDeviceName = usbSnapshot.deviceName == null ? "" : usbSnapshot.deviceName;
            failedUsbReason = usbSnapshot.fallbackReason;
        }
    }

    /**
     * Allows exactly one fresh USB negotiation after a real media-item change. The next decoded
     * PCM format must have been verified and must differ from the failed format. Merely replacing
     * an HTTP URL is not enough to re-enter a deterministic failing USB negotiation.
     */
    public synchronized boolean armUsbRetryForMediaItemTransition(
            int sampleRateHz,
            int bitDepth,
            int channelCount,
            boolean formatVerified
    ) {
        return armUsbRetryForMediaItemTransition(
                sampleRateHz,
                bitDepth,
                channelCount,
                formatVerified,
                ""
        );
    }

    public synchronized boolean armUsbRetryForMediaItemTransition(
            int sampleRateHz,
            int bitDepth,
            int channelCount,
            boolean formatVerified,
            String usbDeviceName
    ) {
        if (!usbExclusiveRequested || !usbFailureLatched || mediaTransitionRetryArmed) {
            return false;
        }
        if (!isRetryableMediaTransitionFailure(failedUsbReason)) {
            return false;
        }
        if (!formatVerified) {
            return false;
        }
        UsbPcmFormatKey nextFormat = new UsbPcmFormatKey(sampleRateHz, bitDepth, channelCount);
        boolean differentDevice = usbDeviceName != null
                && !usbDeviceName.isEmpty()
                && !failedUsbDeviceName.isEmpty()
                && !failedUsbDeviceName.equals(usbDeviceName);
        boolean knownDifferentFormat = nextFormat.isComplete()
                && failedUsbFormat != null
                && failedUsbFormat.isComplete()
                && (differentDevice || !failedUsbFormat.matches(nextFormat));
        if (!knownDifferentFormat) {
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
            failedUsbDeviceName = snapshot.deviceName == null ? "" : snapshot.deviceName;
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

    /**
     * Publishes a per-item preflight fallback without treating it as a failed USB session.
     * A later item with a verified format may therefore negotiate normally.
     */
    public synchronized void onFormatPreflightSkipped(
            AudioOutputMode fallbackMode,
            int sampleRateHz,
            AudioFallbackReason reason,
            String error
    ) {
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
        failedUsbDeviceName = "";
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
