package app.yukine.playback.dsd

/** Packs raw per-channel DSD bytes into DoP 1.1 24-bit little-endian PCM frames. */
internal class DoPPacker {
    private var marker = 0x05

    fun reset() {
        marker = 0x05
    }

    fun pack(channelData: List<ByteArray>): ByteArray {
        require(channelData.size in 1..2) { "DoP supports one or two channels in this release" }
        val bytesPerChannel = channelData.first().size
        require(channelData.all { it.size == bytesPerChannel }) { "DSD channel sizes differ" }
        require(bytesPerChannel % 2 == 0) { "DoP requires pairs of DSD bytes" }
        val frameCount = bytesPerChannel / 2
        val output = ByteArray(frameCount * channelData.size * 3)
        var outputOffset = 0
        for (frame in 0 until frameCount) {
            for (channel in channelData.indices) {
                val source = channelData[channel]
                output[outputOffset++] = source[frame * 2]
                output[outputOffset++] = source[frame * 2 + 1]
                output[outputOffset++] = marker.toByte()
            }
            marker = if (marker == 0x05) 0xFA else 0x05
        }
        return output
    }
}
