package app.yukine.navigation

/**
 * Type-safe route hierarchy for Yukine navigation.
 * Replaces the stringly-typed MainRoutes.java constants with a sealed class tree.
 *
 * Each route exposes a [route] string for Navigation Compose interop and a companion
 * [pattern] for composable() registration.
 */
sealed interface EchoRoute {
    val route: String
}

// ─── Top-level tab destinations ─────────────────────────────────────────────────

sealed interface TabRoute : EchoRoute {
    companion object {
        val all: List<TabRoute> = listOf(
            HomeTab, LibraryTab, QueueTab, SettingsTab
        )

        fun fromKey(key: String): TabRoute? = when (key) {
            HomeTab.route -> HomeTab
            LibraryTab.route -> LibraryTab
            CollectionsTab.route -> LibraryTab
            QueueTab.route -> QueueTab
            NowTab.route -> NowTab
            NetworkTab.route -> NetworkTab
            DownloadsTab.route -> DownloadsTab
            SearchTab.route -> SearchTab
            SettingsTab.route -> SettingsTab
            else -> null
        }
    }
}

data object HomeTab : TabRoute {
    override val route = "home"
}

data object LibraryTab : TabRoute {
    override val route = "library"
}

/** Retained only so stale in-memory callers can be normalized to [LibraryTab]. */
data object CollectionsTab : TabRoute {
    override val route = "collections"
}

data object QueueTab : TabRoute {
    override val route = "queue"
}

data object NowTab : TabRoute {
    override val route = "now"
}

data object NetworkTab : TabRoute {
    override val route = "network"
}

data object DownloadsTab : TabRoute {
    override val route = "downloads"
}

data object SearchTab : TabRoute {
    override val route = "search"
}

data object SettingsTab : TabRoute {
    override val route = "settings"
}

// ─── Network sub-routes ─────────────────────────────────────────────────────────

sealed interface NetworkRoute : EchoRoute

data object NetworkHome : NetworkRoute {
    override val route = "network/home"
}

data object NetworkStreaming : NetworkRoute {
    override val route = "network/streaming"
}

data object NetworkStreamingHub : NetworkRoute {
    override val route = "network/streaming_hub"
}

data object NetworkStreamList : NetworkRoute {
    override val route = "network/stream_list"
}

data object NetworkWebDav : NetworkRoute {
    override val route = "network/webdav"
}

data object NetworkWebDavTracks : NetworkRoute {
    override val route = "network/webdav_tracks"
}

data class NetworkWebDavSourceTracks(val sourceId: Long) : NetworkRoute {
    override val route = "network/webdav_source_tracks/$sourceId"

    companion object {
        const val pattern = "network/webdav_source_tracks/{sourceId}"
        const val argSourceId = "sourceId"
    }
}

data object NetworkSources : NetworkRoute {
    override val route = "network/sources"
}

// ─── Settings sub-routes ────────────────────────────────────────────────────────

sealed interface SettingsRoute : EchoRoute

data object SettingsHome : SettingsRoute {
    override val route = "settings/home"
}

data object SettingsAppearanceGroup : SettingsRoute {
    override val route = "settings/appearance_group"
}

data object SettingsPlaybackGroup : SettingsRoute {
    override val route = "settings/playback_group"
}

data object SettingsLibraryGroup : SettingsRoute {
    override val route = "settings/library_group"
}

data object SettingsLyricsGroup : SettingsRoute {
    override val route = "settings/lyrics_group"
}

data object SettingsSourcesGroup : SettingsRoute {
    override val route = "settings/sources_group"
}

data object SettingsAboutGroup : SettingsRoute {
    override val route = "settings/about_group"
}

data object SettingsAppearance : SettingsRoute {
    override val route = "settings/appearance"
}

data object SettingsAdvancedTheme : SettingsRoute {
    override val route = "settings/advanced_theme"
}

data object SettingsAccent : SettingsRoute {
    override val route = "settings/accent"
}

data object SettingsLanguage : SettingsRoute {
    override val route = "settings/language"
}

data object SettingsPlaybackSpeed : SettingsRoute {
    override val route = "settings/playback_speed"
}

data object SettingsAppVolume : SettingsRoute {
    override val route = "settings/app_volume"
}

data object SettingsStreamingAudioQuality : SettingsRoute {
    override val route = "settings/streaming_audio_quality"
}

data object SettingsConcurrentPlayback : SettingsRoute {
    override val route = "settings/concurrent_playback"
}

data object SettingsSleepTimer : SettingsRoute {
    override val route = "settings/sleep_timer"
}

data object SettingsLyrics : SettingsRoute {
    override val route = "settings/lyrics"
}

data object SettingsLibrary : SettingsRoute {
    override val route = "settings/library"
}

data object SettingsStreamingGateway : SettingsRoute {
    override val route = "settings/streaming_gateway"
}

// ─── Legacy interop ─────────────────────────────────────────────────────────────

/**
 * Maps legacy [MainRoutes] string constants to the new typed route hierarchy.
 * Use during the migration period; remove once MainRoutes.java is deleted.
 */
object RouteMigration {
    /**
     * Convert a legacy MainRoutes tab constant to a [TabRoute].
     */
    fun tabFromLegacy(legacyTab: String): TabRoute = when (legacyTab) {
        "home" -> HomeTab
        "library" -> LibraryTab
        "collections" -> LibraryTab
        "queue" -> QueueTab
        "now" -> NowTab
        "network" -> NetworkTab
        "downloads" -> DownloadsTab
        "settings" -> SettingsTab
        else -> HomeTab
    }

    /**
     * Convert a legacy network page constant to a [NetworkRoute].
     */
    fun networkFromLegacy(legacyPage: String): NetworkRoute = when (legacyPage) {
        "network_home" -> NetworkHome
        "network_streaming" -> NetworkStreaming
        "network_streaming_hub" -> NetworkStreamingHub
        "network_stream_list" -> NetworkStreamList
        "network_webdav" -> NetworkWebDav
        "network_webdav_tracks" -> NetworkWebDavTracks
        "network_sources" -> NetworkSources
        else -> NetworkHome
    }

    /**
     * Convert a legacy settings page constant to a [SettingsRoute].
     */
    fun settingsFromLegacy(legacyPage: String): SettingsRoute = when (legacyPage) {
        "settings_home" -> SettingsHome
        "settings_appearance_group" -> SettingsAppearanceGroup
        "settings_playback_group" -> SettingsPlaybackGroup
        "settings_library_group" -> SettingsLibraryGroup
        "settings_lyrics_group" -> SettingsLyricsGroup
        "settings_sources_group" -> SettingsSourcesGroup
        "settings_about_group" -> SettingsAboutGroup
        "settings_appearance" -> SettingsAppearance
        "settings_advanced_theme" -> SettingsAdvancedTheme
        "settings_accent" -> SettingsAccent
        "settings_language" -> SettingsLanguage
        "settings_playback_speed" -> SettingsPlaybackSpeed
        "settings_app_volume" -> SettingsAppVolume
        "settings_streaming_audio_quality" -> SettingsStreamingAudioQuality
        "settings_concurrent_playback" -> SettingsConcurrentPlayback
        "settings_sleep_timer" -> SettingsSleepTimer
        "settings_lyrics" -> SettingsLyrics
        "settings_library" -> SettingsLibrary
        "settings_streaming_gateway" -> SettingsStreamingGateway
        else -> SettingsHome
    }

    /**
     * Convert a [TabRoute] back to a legacy MainRoutes constant.
     * Useful while Java code still references the old constants.
     */
    fun tabToLegacy(tab: TabRoute): String = tab.route
}
