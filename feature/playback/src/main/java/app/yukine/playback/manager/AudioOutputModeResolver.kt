package app.yukine.playback.manager

/**
 * Resolves the appropriate [AudioOutputMode] based on user preference and device capabilities.
 *
 * Decision logic (priority order):
 * - USB Exclusive requested + USB DAC connected → [AudioOutputMode.USB_EXCLUSIVE]
 * - Bit-Perfect not requested → [AudioOutputMode.STANDARD]
 * - Bit-Perfect requested + device supports offload → [AudioOutputMode.HARDWARE_OFFLOAD]
 * - Bit-Perfect requested + no offload support → [AudioOutputMode.DIRECT_PCM]
 */
internal object AudioOutputModeResolver {

    @JvmStatic
    fun resolve(
        bitPerfectRequested: Boolean,
        usbExclusiveRequested: Boolean = false,
        profile: AudioDeviceCapabilityProbe.AudioDeviceProfile
    ): AudioOutputMode {
        // USB Exclusive has highest priority — completely bypasses AudioFlinger
        if (usbExclusiveRequested && profile.isUsbAudioDeviceConnected) {
            return AudioOutputMode.USB_EXCLUSIVE
        }
        if (!bitPerfectRequested) return AudioOutputMode.STANDARD
        return if (profile.supportsOffload) AudioOutputMode.HARDWARE_OFFLOAD
        else AudioOutputMode.DIRECT_PCM
    }
}
