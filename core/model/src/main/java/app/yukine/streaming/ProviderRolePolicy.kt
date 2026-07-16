package app.yukine.streaming

import java.util.Locale

/**
 * Static provider responsibilities shared by identity, playback and persistence.
 *
 * User preferences are intentionally not stored here. Callers layer the current
 * [PlaybackSourcePolicy] snapshot on top of these invariant capabilities.
 */
object ProviderRolePolicy {
    private val physicalProviders = setOf("local", "document", "webdav")
    private val identityProviders = physicalProviders + setOf("netease", "qqmusic")

    @JvmStatic
    fun normalize(provider: String?): String {
        val clean = provider.orEmpty().trim().lowercase(Locale.ROOT)
        if (clean in physicalProviders || clean == "stream") return clean
        return StreamingProviderName.fromWireName(clean)?.wireName ?: clean
    }

    @JvmStatic
    fun isPhysical(provider: String?): Boolean = normalize(provider) in physicalProviders

    @JvmStatic
    fun contributesIdentity(provider: String?): Boolean = normalize(provider) in identityProviders

    @JvmStatic
    fun canPersistCanonicalSource(provider: String?): Boolean = contributesIdentity(provider)

    @JvmStatic
    fun canSyncFavorites(provider: String?): Boolean = normalize(provider) in setOf("netease", "qqmusic")

    @JvmStatic
    fun canSyncPlaylists(provider: String?): Boolean = normalize(provider) in setOf("netease", "qqmusic")

    @JvmStatic
    fun isPlaybackResolver(provider: String?): Boolean = normalize(provider) == "luoxue"

    @JvmStatic
    fun canEverBecomeActive(provider: String?): Boolean {
        val normalized = normalize(provider)
        return normalized in physicalProviders || normalized == "netease"
    }

    @JvmStatic
    fun canBecomeActive(
        provider: String?,
        neteasePlaybackEnabled: Boolean,
        hasEligiblePhysicalSource: Boolean
    ): Boolean {
        val normalized = normalize(provider)
        return when {
            normalized in physicalProviders -> true
            normalized == "netease" -> neteasePlaybackEnabled && !hasEligiblePhysicalSource
            else -> false
        }
    }
}
