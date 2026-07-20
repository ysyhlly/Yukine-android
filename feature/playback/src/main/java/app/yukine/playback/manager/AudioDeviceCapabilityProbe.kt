package app.yukine.playback.manager

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler

/**
 * Probes the current audio output device capabilities to determine whether
 * bit-perfect playback (SRC bypass) is feasible.
 *
 * Results are cached and refreshed automatically when the audio device changes
 * (e.g., Bluetooth connect/disconnect, USB DAC plug/unplug).
 */
internal class AudioDeviceCapabilityProbe(context: Context) {

    data class AudioDeviceProfile(
        val nativeSampleRateHz: Int,
        val supportsOffload: Boolean,
        val supportedSampleRates: List<Int>,
        val deviceName: String,
        val isUsbAudioDeviceConnected: Boolean = false,
        val usbDeviceName: String = ""
    )

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    @Volatile
    var currentProfile: AudioDeviceProfile = AudioDeviceProfile(
        nativeSampleRateHz = DEFAULT_SAMPLE_RATE_HZ,
        supportsOffload = false,
        supportedSampleRates = emptyList(),
        deviceName = "unknown"
    )
        private set

    var onDeviceChanged: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
            onDeviceChanged?.invoke()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
            onDeviceChanged?.invoke()
        }
    }

    fun register(handler: Handler) {
        refresh()
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
    }

    fun unregister() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    fun refresh() {
        val nativeRate = audioManager
            .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE_HZ

        val defaultOutput = findDefaultOutputDevice()
        val supportedRates = defaultOutput
            ?.sampleRates
            ?.filter { it > 0 }
            ?.toList() ?: emptyList()

        val supportsOffload = defaultOutput?.let { device ->
            // Offload is generally supported on API 21+ for common compressed formats.
            // We check if the device advertises any non-PCM encoding support.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                device.encodings.any { encoding ->
                    encoding != android.media.AudioFormat.ENCODING_PCM_16BIT &&
                        encoding != android.media.AudioFormat.ENCODING_PCM_FLOAT &&
                        encoding != android.media.AudioFormat.ENCODING_PCM_8BIT &&
                        encoding != android.media.AudioFormat.ENCODING_INVALID
                }
        } ?: false

        // Detect USB Audio Class devices via USB Host API
        val usbAudioDevice = findUsbAudioDevice()

        currentProfile = AudioDeviceProfile(
            nativeSampleRateHz = nativeRate,
            supportsOffload = supportsOffload,
            supportedSampleRates = supportedRates,
            deviceName = defaultOutput?.productName?.toString() ?: "default",
            isUsbAudioDeviceConnected = usbAudioDevice != null,
            usbDeviceName = usbAudioDevice?.productName ?: ""
        )
    }

    /**
     * Returns true if the given sample rate can be output without SRC,
     * either because it matches the device native rate or the device
     * explicitly advertises support for it.
     */
    fun canOutputWithoutSrc(sampleRateHz: Int): Boolean {
        if (sampleRateHz <= 0) return false
        val profile = currentProfile
        return sampleRateHz == profile.nativeSampleRateHz ||
            profile.supportedSampleRates.contains(sampleRateHz)
    }

    private fun findDefaultOutputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // Prefer the default speaker/headset (TYPE_BUILTIN_SPEAKER or TYPE_WIRED_HEADSET)
        return devices.firstOrNull { it.isSink && isDefaultOutputType(it.type) }
            ?: devices.firstOrNull { it.isSink }
    }

    private fun isDefaultOutputType(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    /**
     * Finds a connected USB Audio Class device via USB Host API.
     * Returns the first USB device that has an Audio Class interface (class 0x01).
     */
    private fun findUsbAudioDevice(): android.hardware.usb.UsbDevice? {
        val manager = usbManager ?: return null
        return try {
            manager.deviceList.values.firstOrNull { device ->
                (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 48000
    }
}
