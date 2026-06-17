package app.yukine

import app.yukine.data.MusicLibraryRepository

internal data class LoadedLyricsSettings(
    @JvmField val onlineLyricsEnabled: Boolean,
    @JvmField val lyricsOffsetMs: Long
)

internal interface LyricsSettingsOperations {
    fun loadOnlineLyricsEnabled(): Boolean
    fun loadLyricsOffsetMs(): Long
}

internal class MusicLibraryLyricsSettingsOperations(
    private val repository: MusicLibraryRepository
) : LyricsSettingsOperations {
    override fun loadOnlineLyricsEnabled(): Boolean =
        repository.loadOnlineLyricsEnabled()

    override fun loadLyricsOffsetMs(): Long =
        repository.loadLyricsOffsetMs()
}

internal class LoadLyricsSettingsUseCase(
    private val operations: LyricsSettingsOperations
) {
    fun execute(): LoadedLyricsSettings =
        LoadedLyricsSettings(
            onlineLyricsEnabled = operations.loadOnlineLyricsEnabled(),
            lyricsOffsetMs = operations.loadLyricsOffsetMs()
        )
}
