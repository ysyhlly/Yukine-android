package app.yukine

internal object SettingsBackStack {
    fun parentPage(settingsPage: String): String {
        return when (settingsPage) {
            MainRoutes.SETTINGS_APPEARANCE_GROUP,
            MainRoutes.SETTINGS_PLAYBACK_GROUP,
            MainRoutes.SETTINGS_LIBRARY_GROUP,
            MainRoutes.SETTINGS_LYRICS_GROUP,
            MainRoutes.SETTINGS_SOURCES_GROUP,
            MainRoutes.SETTINGS_ABOUT_GROUP -> MainRoutes.SETTINGS_HOME

            MainRoutes.SETTINGS_APPEARANCE,
            MainRoutes.SETTINGS_ACCENT,
            MainRoutes.SETTINGS_LANGUAGE -> MainRoutes.SETTINGS_APPEARANCE_GROUP
            MainRoutes.SETTINGS_ADVANCED_THEME -> MainRoutes.SETTINGS_APPEARANCE

            MainRoutes.SETTINGS_PLAYBACK_SPEED,
            MainRoutes.SETTINGS_APP_VOLUME,
            MainRoutes.SETTINGS_AUDIO_EFFECTS,
            MainRoutes.SETTINGS_REPLAY_GAIN,
            MainRoutes.SETTINGS_NOW_PLAYING_GESTURES,
            MainRoutes.SETTINGS_PLAYBACK_RESTORE,
            MainRoutes.SETTINGS_CONCURRENT_PLAYBACK,
            MainRoutes.SETTINGS_SLEEP_TIMER -> MainRoutes.SETTINGS_PLAYBACK_GROUP

            MainRoutes.SETTINGS_LIBRARY -> MainRoutes.SETTINGS_LIBRARY_GROUP
            MainRoutes.SETTINGS_LYRICS,
            MainRoutes.SETTINGS_STATUS_BAR_LYRICS,
            MainRoutes.SETTINGS_FLOATING_LYRICS -> MainRoutes.SETTINGS_LYRICS_GROUP
            MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY,
            MainRoutes.SETTINGS_STREAMING_GATEWAY,
            MainRoutes.SETTINGS_DOWNLOADS -> MainRoutes.SETTINGS_SOURCES_GROUP

            else -> MainRoutes.SETTINGS_HOME
        }
    }
}
