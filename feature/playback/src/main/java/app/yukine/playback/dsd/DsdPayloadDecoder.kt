package app.yukine.playback.dsd

internal object DsdPayloadDecoder {
    fun toCanonicalChannels(
        payload: ByteArray,
        channelCount: Int,
        metadata: DsdFormatMetadata
    ): List<ByteArray> {
        require(channelCount in 1..2 && payload.size % channelCount == 0) {
            "Misaligned DSD access unit"
        }
        val channels = Array(channelCount) { ByteArray(payload.size / channelCount) }
        if (metadata.container == DsdContainer.DSF) {
            val block = metadata.blockSizePerChannel
            val groupSize = block * channelCount
            require(block > 0 && payload.size % groupSize == 0) { "Misaligned DSF access unit" }
            var sourceOffset = 0
            var targetOffset = 0
            while (sourceOffset < payload.size) {
                for (channel in 0 until channelCount) {
                    payload.copyInto(channels[channel], targetOffset, sourceOffset, sourceOffset + block)
                    sourceOffset += block
                }
                targetOffset += block
            }
        } else {
            payload.forEachIndexed { index, value ->
                channels[index % channelCount][index / channelCount] = value
            }
        }
        if (metadata.lsbFirst) {
            channels.forEach { channel ->
                for (index in channel.indices) {
                    channel[index] = Integer.reverse(channel[index].toInt() and 0xff).ushr(24).toByte()
                }
            }
        }
        return channels.toList()
    }
}
