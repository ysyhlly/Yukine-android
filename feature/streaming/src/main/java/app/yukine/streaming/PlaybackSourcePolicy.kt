package app.yukine.streaming

data class PlaybackSourcePolicySnapshot(
    val enabledRemoteProviders: Set<StreamingProviderName> = setOf(
        StreamingProviderName.LUOXUE,
        StreamingProviderName.BILIBILI
    ),
    val remotePriority: List<StreamingProviderName> = listOf(
        StreamingProviderName.LUOXUE,
        StreamingProviderName.BILIBILI
    )
) {
    fun isEnabled(provider: StreamingProviderName): Boolean =
        provider != StreamingProviderName.QQ_MUSIC && provider in enabledRemoteProviders
}

interface PlaybackSourcePolicy {
    fun snapshot(): PlaybackSourcePolicySnapshot

    fun isEnabled(provider: StreamingProviderName): Boolean = snapshot().isEnabled(provider)
}

interface MutablePlaybackSourcePolicy : PlaybackSourcePolicy {
    fun setEnabled(provider: StreamingProviderName, enabled: Boolean)
    fun setPriority(providers: List<StreamingProviderName>)
}

object DefaultPlaybackSourcePolicy : PlaybackSourcePolicy {
    // Compatibility default for repositories created outside the Android composition root (unit
    // tests and offline tools). The shipped app always injects PersistentPlaybackSourcePolicy.
    private val defaults = PlaybackSourcePolicySnapshot(
        enabledRemoteProviders = StreamingProviderName.entries
            .filterNot { it == StreamingProviderName.QQ_MUSIC }
            .toSet(),
        remotePriority = listOf(StreamingProviderName.LUOXUE) +
            StreamingProviderName.entries.filterNot {
                it == StreamingProviderName.LUOXUE || it == StreamingProviderName.QQ_MUSIC
            }
    )
    override fun snapshot(): PlaybackSourcePolicySnapshot = defaults
}

fun PlaybackSourcePolicy.orderedEnabledProviders(): List<StreamingProviderName> {
    val value = snapshot()
    val enabled = value.enabledRemoteProviders
        .filterNot(StreamingAudioCapabilityPolicy::isPermanentlyMetadataOnly)
        .toSet()
    return buildList {
        if (StreamingProviderName.LUOXUE in enabled) add(StreamingProviderName.LUOXUE)
        value.remotePriority.filterTo(this) { it in enabled && it != StreamingProviderName.LUOXUE }
        enabled.filterTo(this) { it !in this }
    }.distinct()
}
