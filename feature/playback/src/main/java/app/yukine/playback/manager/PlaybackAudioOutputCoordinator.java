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
            usbFailureLatched = false;
        }
        AudioOutputMode resolved = AudioOutputModeResolver.resolve(
                bitPerfectRequested,
                usbExclusiveRequested,
                profile
        );
        if (usbFailureLatched && resolved == AudioOutputMode.USB_EXCLUSIVE) {
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
        if (usbSnapshot.phase == AudioOutputPhase.FALLBACK
                || usbSnapshot.phase == AudioOutputPhase.ERROR) {
            usbFailureLatched = true;
        }
    }

    public synchronized void onSystemFallback(
            AudioOutputMode fallbackMode,
            int sampleRateHz,
            AudioFallbackReason reason,
            String error
    ) {
        if (usbExclusiveRequested && isUsbFailure(reason)) {
            usbFailureLatched = true;
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
                && reason != AudioFallbackReason.OFFLOAD_UNAVAILABLE
                && reason != AudioFallbackReason.OFFLOAD_FAILED;
    }

    private static AudioTransport transportFor(AudioOutputMode mode) {
        if (mode == AudioOutputMode.HARDWARE_OFFLOAD) return AudioTransport.SYSTEM_OFFLOAD;
        if (mode == AudioOutputMode.DIRECT_PCM) return AudioTransport.SYSTEM_DIRECT_PCM;
        if (mode == AudioOutputMode.USB_EXCLUSIVE) return AudioTransport.USB_PCM;
        return AudioTransport.SYSTEM_STANDARD;
    }
}
