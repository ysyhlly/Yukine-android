package app.yukine

import app.yukine.data.LyricsRepository
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import app.yukine.streaming.LuoxueTrackMetadataResolver
import kotlinx.coroutines.CancellationException

internal interface TrackLyricsOperations {
    suspend fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine>
}

internal class LyricsRepositoryLoadOperations @JvmOverloads constructor(
    private val repository: LyricsRepository = LyricsRepository()
) : TrackLyricsOperations {
    override suspend fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> = repository.loadForTrack(track, onlineEnabled, neteaseProviderTrackId)
}

/**
 * Lets imported LX sources supply lyrics before the existing local/provider/online fallback
 * chain. This is intentionally a lyrics operation, not a playback resolver: it is invoked only
 * from the LyricsViewModel's background load path.
 */
internal class LuoxueFirstTrackLyricsOperations(
    private val resolver: LuoxueTrackMetadataResolver,
    private val repository: LyricsRepository,
    private val fallback: TrackLyricsOperations
) : TrackLyricsOperations {
    override suspend fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> {
        val lxLines = try {
            resolver.resolveLyrics(track)?.let { lyrics ->
                repository.parseProviderLyrics(lyrics.lyric, lyrics.translation.orEmpty())
            }.orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyList()
        }
        if (lxLines.isNotEmpty()) {
            return lxLines
        }
        return fallback.loadForTrack(track, onlineEnabled, neteaseProviderTrackId)
    }
}

internal class LoadTrackLyricsUseCase(
    private val operations: TrackLyricsOperations
) {
    suspend fun execute(
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

internal class LoadTrackLyricsUseCaseLyricsLoader(
    private val useCase: LoadTrackLyricsUseCase
) : LyricsLoader {
    override suspend fun load(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> = useCase.execute(track, onlineEnabled, neteaseProviderTrackId)
}
