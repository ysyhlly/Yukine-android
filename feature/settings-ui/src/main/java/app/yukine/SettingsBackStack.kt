package app.yukine

object SettingsBackStack {
    fun parent(settingsPage: SettingsPage): SettingsPage {
        return when (settingsPage) {
            SettingsPage.AppearanceGroup,
            SettingsPage.PlaybackGroup,
            SettingsPage.LibraryGroup,
            SettingsPage.LyricsGroup,
            SettingsPage.SourcesGroup,
            SettingsPage.Downloads,
            SettingsPage.AboutGroup -> SettingsPage.Home

            SettingsPage.Appearance,
            SettingsPage.Accent,
            SettingsPage.Language,
            SettingsPage.PageBackground,
            SettingsPage.NowPlayingGestures,
            SettingsPage.ShareStyle -> SettingsPage.AppearanceGroup
            SettingsPage.AdvancedTheme -> SettingsPage.Appearance

            SettingsPage.PlaybackSpeed,
            SettingsPage.AppVolume,
            SettingsPage.AudioEffects,
            SettingsPage.ReplayGain,
            SettingsPage.PlaybackRestore,
            SettingsPage.ConcurrentPlayback,
            SettingsPage.SleepTimer -> SettingsPage.PlaybackGroup

            SettingsPage.Library,
            SettingsPage.DuplicateCandidates -> SettingsPage.LibraryGroup
            SettingsPage.Lyrics,
            SettingsPage.StatusBarLyrics,
            SettingsPage.FloatingLyrics -> SettingsPage.LyricsGroup
            SettingsPage.StreamingAudioQuality,
            SettingsPage.StreamingGateway -> SettingsPage.SourcesGroup

            else -> SettingsPage.Home
        }
    }
}
