package app.yukine

/** Typed settings destination; [route] is the legacy saved-state serialization boundary. */
sealed class SettingsPage(
    @JvmField val route: String
) {
    data object Home : SettingsPage(MainRoutes.SETTINGS_HOME)
    data object AppearanceGroup : SettingsPage(MainRoutes.SETTINGS_APPEARANCE_GROUP)
    data object PlaybackGroup : SettingsPage(MainRoutes.SETTINGS_PLAYBACK_GROUP)
    data object LibraryGroup : SettingsPage(MainRoutes.SETTINGS_LIBRARY_GROUP)
    data object LyricsGroup : SettingsPage(MainRoutes.SETTINGS_LYRICS_GROUP)
    data object SourcesGroup : SettingsPage(MainRoutes.SETTINGS_SOURCES_GROUP)
    data object AboutGroup : SettingsPage(MainRoutes.SETTINGS_ABOUT_GROUP)
    data object Appearance : SettingsPage(MainRoutes.SETTINGS_APPEARANCE)
    data object AdvancedTheme : SettingsPage(MainRoutes.SETTINGS_ADVANCED_THEME)
    data object Accent : SettingsPage(MainRoutes.SETTINGS_ACCENT)
    data object Language : SettingsPage(MainRoutes.SETTINGS_LANGUAGE)
    data object PageBackground : SettingsPage(MainRoutes.SETTINGS_PAGE_BACKGROUND)
    data object PlaybackSpeed : SettingsPage(MainRoutes.SETTINGS_PLAYBACK_SPEED)
    data object AppVolume : SettingsPage(MainRoutes.SETTINGS_APP_VOLUME)
    data object AudioEffects : SettingsPage(MainRoutes.SETTINGS_AUDIO_EFFECTS)
    data object StatusBarLyrics : SettingsPage(MainRoutes.SETTINGS_STATUS_BAR_LYRICS)
    data object FloatingLyrics : SettingsPage(MainRoutes.SETTINGS_FLOATING_LYRICS)
    data object NowPlayingGestures : SettingsPage(MainRoutes.SETTINGS_NOW_PLAYING_GESTURES)
    data object PlaybackRestore : SettingsPage(MainRoutes.SETTINGS_PLAYBACK_RESTORE)
    data object ReplayGain : SettingsPage(MainRoutes.SETTINGS_REPLAY_GAIN)
    data object StreamingAudioQuality : SettingsPage(MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY)
    data object ShareStyle : SettingsPage(MainRoutes.SETTINGS_SHARE_STYLE)
    data object Downloads : SettingsPage(MainRoutes.SETTINGS_DOWNLOADS)
    data object SleepTimer : SettingsPage(MainRoutes.SETTINGS_SLEEP_TIMER)
    data object Lyrics : SettingsPage(MainRoutes.SETTINGS_LYRICS)
    data object Library : SettingsPage(MainRoutes.SETTINGS_LIBRARY)
    data object MusicFolders : SettingsPage(MainRoutes.SETTINGS_MUSIC_FOLDERS)
    data object DuplicateCandidates : SettingsPage(MainRoutes.SETTINGS_DUPLICATE_CANDIDATES)
    data object StreamingGateway : SettingsPage(MainRoutes.SETTINGS_STREAMING_GATEWAY)

    companion object {
        @JvmStatic
        fun fromRoute(route: String?): SettingsPage {
            return when (route) {
                MainRoutes.SETTINGS_APPEARANCE_GROUP -> AppearanceGroup
                MainRoutes.SETTINGS_PLAYBACK_GROUP -> PlaybackGroup
                MainRoutes.SETTINGS_LIBRARY_GROUP -> LibraryGroup
                MainRoutes.SETTINGS_LYRICS_GROUP -> LyricsGroup
                MainRoutes.SETTINGS_SOURCES_GROUP -> SourcesGroup
                MainRoutes.SETTINGS_ABOUT_GROUP -> AboutGroup
                MainRoutes.SETTINGS_APPEARANCE -> Appearance
                MainRoutes.SETTINGS_ADVANCED_THEME -> AdvancedTheme
                MainRoutes.SETTINGS_ACCENT -> Accent
                MainRoutes.SETTINGS_LANGUAGE -> Language
                MainRoutes.SETTINGS_PAGE_BACKGROUND -> PageBackground
                MainRoutes.SETTINGS_PLAYBACK_SPEED -> PlaybackSpeed
                MainRoutes.SETTINGS_APP_VOLUME -> AppVolume
                MainRoutes.SETTINGS_AUDIO_EFFECTS -> AudioEffects
                MainRoutes.SETTINGS_STATUS_BAR_LYRICS -> StatusBarLyrics
                MainRoutes.SETTINGS_FLOATING_LYRICS -> FloatingLyrics
                MainRoutes.SETTINGS_NOW_PLAYING_GESTURES -> NowPlayingGestures
                MainRoutes.SETTINGS_PLAYBACK_RESTORE -> PlaybackRestore
                MainRoutes.SETTINGS_REPLAY_GAIN -> ReplayGain
                MainRoutes.SETTINGS_STREAMING_AUDIO_QUALITY -> StreamingAudioQuality
                MainRoutes.SETTINGS_SHARE_STYLE -> ShareStyle
                MainRoutes.SETTINGS_DOWNLOADS -> Downloads
                MainRoutes.SETTINGS_SLEEP_TIMER -> SleepTimer
                MainRoutes.SETTINGS_LYRICS -> Lyrics
                MainRoutes.SETTINGS_LIBRARY -> Library
                MainRoutes.SETTINGS_MUSIC_FOLDERS -> MusicFolders
                MainRoutes.SETTINGS_DUPLICATE_CANDIDATES -> DuplicateCandidates
                MainRoutes.SETTINGS_STREAMING_GATEWAY -> StreamingGateway
                else -> Home
            }
        }

        @JvmStatic
        fun route(page: SettingsPage): String = page.route

        val all: List<SettingsPage>
            get() = listOf(
                Home,
                AppearanceGroup,
                PlaybackGroup,
                LibraryGroup,
                LyricsGroup,
                SourcesGroup,
                AboutGroup,
                Appearance,
                AdvancedTheme,
                Accent,
                Language,
                PageBackground,
                PlaybackSpeed,
                AppVolume,
                AudioEffects,
                StatusBarLyrics,
                FloatingLyrics,
                NowPlayingGestures,
                PlaybackRestore,
                ReplayGain,
                StreamingAudioQuality,
                ShareStyle,
                Downloads,
                SleepTimer,
                Lyrics,
                Library,
                MusicFolders,
                DuplicateCandidates,
                StreamingGateway
            )
    }
}
