package app.yukine

import android.content.Context
import app.yukine.streaming.MutablePlaybackSourcePolicy
import app.yukine.streaming.PlaybackSourcePolicySnapshot
import app.yukine.streaming.StreamingAudioCapabilityPolicy
import app.yukine.streaming.StreamingProviderName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentPlaybackSourcePolicy @Inject constructor(
    @ApplicationContext context: Context
) : MutablePlaybackSourcePolicy {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    init {
        migrateIfNeeded()
    }

    override fun snapshot(): PlaybackSourcePolicySnapshot = synchronized(lock) {
        val enabled = preferences.getStringSet(KEY_ENABLED, null)
            ?.mapNotNull(StreamingProviderName::fromWireName)
            ?.filterNot(StreamingAudioCapabilityPolicy::isPermanentlyMetadataOnly)
            ?.filter {
                it == StreamingProviderName.LUOXUE ||
                    it == StreamingProviderName.NETEASE ||
                    it == StreamingProviderName.BILIBILI
            }
            ?.toSet()
            ?: DEFAULT_ENABLED_PROVIDERS
        val priority = preferences.getString(KEY_PRIORITY, null)
            ?.split(',')
            ?.mapNotNull(StreamingProviderName::fromWireName)
            .orEmpty()
        PlaybackSourcePolicySnapshot(
            enabledRemoteProviders = enabled + StreamingProviderName.LUOXUE,
            remotePriority = (DEFAULT_PRIORITY + priority + enabled)
                .filterNot(StreamingAudioCapabilityPolicy::isPermanentlyMetadataOnly)
                .distinct()
        )
    }

    override fun setEnabled(provider: StreamingProviderName, enabled: Boolean) = synchronized(lock) {
        if (provider != StreamingProviderName.NETEASE && provider != StreamingProviderName.BILIBILI) {
            return@synchronized
        }
        val current = snapshot().enabledRemoteProviders.toMutableSet()
        if (enabled) current += provider else current -= provider
        preferences.edit().putStringSet(KEY_ENABLED, current.mapTo(linkedSetOf()) { it.wireName }).apply()
    }

    override fun setPriority(providers: List<StreamingProviderName>) = synchronized(lock) {
        preferences.edit().putString(KEY_PRIORITY, StreamingProviderName.LUOXUE.wireName).apply()
    }

    private fun migrateIfNeeded() = synchronized(lock) {
        if (preferences.getInt(KEY_VERSION, 0) >= VERSION) return@synchronized
        val previousEnabled = preferences.getStringSet(KEY_ENABLED, emptySet()).orEmpty()
        // Migration only creates playback policy keys. Account cookies and all sync stores live in
        // separate preferences/databases and are intentionally untouched.
        preferences.edit()
            .putInt(KEY_VERSION, VERSION)
            .putStringSet(
                KEY_ENABLED,
                buildSet {
                    addAll(DEFAULT_ENABLED_PROVIDERS.map(StreamingProviderName::wireName))
                    if (StreamingProviderName.NETEASE.wireName in previousEnabled) {
                        add(StreamingProviderName.NETEASE.wireName)
                    }
                }
            )
            .putString(KEY_PRIORITY, DEFAULT_PRIORITY.joinToString(",") { it.wireName })
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "playback_source_policy"
        const val KEY_VERSION = "version"
        const val KEY_ENABLED = "enabled_remote_providers"
        const val KEY_PRIORITY = "remote_priority"
        const val VERSION = 3
        val DEFAULT_ENABLED_PROVIDERS = setOf(
            StreamingProviderName.LUOXUE,
            StreamingProviderName.BILIBILI
        )
        val DEFAULT_PRIORITY = listOf(
            StreamingProviderName.LUOXUE,
            StreamingProviderName.BILIBILI
        )
    }
}
