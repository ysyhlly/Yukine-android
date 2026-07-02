package app.yukine.common

interface StreamingDataPathParser {
    fun isStreamingTrack(dataPath: String): Boolean
    fun providerName(dataPath: String): String?
    fun providerTrackId(dataPath: String): String
}
