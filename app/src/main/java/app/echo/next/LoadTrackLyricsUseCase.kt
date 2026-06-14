package app.echo.next

import app.echo.next.data.LyricsRepository
import app.echo.next.model.LyricsLine
import app.echo.next.model.Track

internal interface TrackLyricsOperations {
    fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine>
}

internal class LyricsRepositoryLoadOperations @JvmOverloads constructor(
    private val repository: LyricsRepository = LyricsRepository()
) : TrackLyricsOperations {
    override fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> = repository.loadForTrack(track, onlineEnabled, neteaseProviderTrackId)
}

internal class LoadTrackLyricsUseCase(
    private val operations: TrackLyricsOperations
) {
    fun execute(
        track: Track?,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String?
    ): List<LyricsLine> {
        if (track == null) {
            return emptyList()
        }
        return operations.loadForTrack(track, onlineEnabled, neteaseProviderTrackId.orEmpty())
    }
}
