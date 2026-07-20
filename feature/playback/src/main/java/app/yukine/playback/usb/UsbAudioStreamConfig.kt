package app.yukine.playback.usb

/**
 * Configuration for a USB Audio Streaming endpoint, parsed from USB descriptors.
 *
 * @param endpointAddress The bulk OUT endpoint address for audio data transfer.
 * @param maxPacketSize Maximum packet size in bytes for the endpoint.
 * @param sampleRateHz Negotiated sample rate in Hz (e.g., 44100, 48000, 96000).
 * @param bitDepth Bits per sample (16, 24, or 32).
 * @param channelCount Number of audio channels (typically 2 for stereo).
 * @param interfaceNumber USB interface number for the Audio Streaming interface.
 * @param alternateSetting Alternate setting index that provides the audio endpoint.
 * @param feedbackEndpointAddress Optional feedback endpoint address for clock synchronization (0 if none).
 */
internal data class UsbAudioStreamConfig(
    val endpointAddress: Int,
    val maxPacketSize: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val channelCount: Int,
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val feedbackEndpointAddress: Int = 0
) {
    /** Bytes per audio frame (one sample across all channels). */
    val bytesPerFrame: Int get() = (bitDepth / 8) * channelCount

    /** Number of audio frames per USB packet. */
    val framesPerPacket: Int get() = if (bytesPerFrame > 0) maxPacketSize / bytesPerFrame else 0

    companion object {
        /** Fallback configuration for 16-bit/48kHz stereo — the most widely supported format. */
        val FALLBACK = UsbAudioStreamConfig(
            endpointAddress = 0,
            maxPacketSize = 192, // 48kHz * 2ch * 16bit / 1000 = 192 bytes per ms
            sampleRateHz = 48000,
            bitDepth = 16,
            channelCount = 2,
            interfaceNumber = 0,
            alternateSetting = 1
        )
    }
}
