package app.yukine

import app.yukine.identity.LibraryDedupMode

/** One-time platform commands emitted by focused settings page owners. */
sealed interface SettingsEffect {
    data class ShowStatus(val message: String) : SettingsEffect
    data class NavigatePage(val page: SettingsPage) : SettingsEffect
    data class OpenNetworkPage(val page: NetworkPage) : SettingsEffect
    data object OpenDownloads : SettingsEffect
    data object RequestNeededPermissions : SettingsEffect
    data object LoadLibrary : SettingsEffect
    data object OpenAudioFilePicker : SettingsEffect
    data object OpenAudioFolderPicker : SettingsEffect
    data object RebuildSongIdentity : SettingsEffect
    data object CancelIdentityBackfill : SettingsEffect
    data class SetLibraryDedupMode(val mode: LibraryDedupMode) : SettingsEffect
    data class ConfirmDuplicateCandidate(
        val leftRecordingId: Long,
        val rightRecordingId: Long
    ) : SettingsEffect
    data object ConfirmHighConfidenceDuplicates : SettingsEffect
    data object OpenLuoxueSourceManager : SettingsEffect
    data object ImportLuoxueSource : SettingsEffect
    data object ReloadCurrentLyrics : SettingsEffect
    data object ImportCurrentLyrics : SettingsEffect
    data object ImportLyricsDirectory : SettingsEffect
    data object ViewLyricsImportReport : SettingsEffect
    data class StartSleepTimer(val minutes: Int) : SettingsEffect
    data object CancelSleepTimer : SettingsEffect
    data object OpenFloatingLyricsPermission : SettingsEffect
    data class ChoosePageBackground(val page: String) : SettingsEffect
    data object ExportBackup : SettingsEffect
    data object ImportBackup : SettingsEffect
    data object ExportDiagnostics : SettingsEffect
    data class ApplyStreamingGatewayEndpoint(val endpoint: String) : SettingsEffect
    data class SetKugouExperimentalSyncEnabled(val enabled: Boolean) : SettingsEffect
    data object EditMusicBrainzProxy : SettingsEffect
    data class RestoreHiddenLibraryItem(val sourceKey: String) : SettingsEffect
    data object RestoreAllHiddenLibraryItems : SettingsEffect
    data object CheckGitHubUpdate : SettingsEffect
}
