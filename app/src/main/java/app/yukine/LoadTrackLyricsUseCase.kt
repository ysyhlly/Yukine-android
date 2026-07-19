package app.yukine

import app.yukine.data.CustomLyricsRepository
import app.yukine.data.LyricsRepository
import app.yukine.model.LyricsDocument
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

    suspend fun loadDocumentForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): LyricsDocument = LyricsDocument.fromLegacy(
        loadForTrack(track, onlineEnabled, neteaseProviderTrackId),
        format = "legacy"
    )
}

internal class LyricsRepositoryLoadOperations @JvmOverloads constructor(
    private val repository: LyricsRepository = LyricsRepository()
) : TrackLyricsOperations {
    override suspend fun loadForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> = repository.loadForTrack(track, onlineEnabled, neteaseProviderTrackId)

    override suspend fun loadDocumentForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): LyricsDocument = repository.loadDocumentForTrack(track, onlineEnabled, neteaseProviderTrackId)
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

    override suspend fun loadDocumentForTrack(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): LyricsDocument {
        val lxDocument = try {
            resolver.resolveLyrics(track)?.let { lyrics ->
                repository.parseProviderLyricsDocument(lyrics.lyric, lyrics.translation.orEmpty())
            } ?: LyricsDocument.empty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            LyricsDocument.empty()
        }
        if (!lxDocument.isEmpty()) {
            return lxDocument
        }
        return fallback.loadDocumentForTrack(track, onlineEnabled, neteaseProviderTrackId)
    }
}

internal class LoadTrackLyricsUseCase(
    private val operations: TrackLyricsOperations,
    private val customLyricsRepository: CustomLyricsRepository? = null
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

    suspend fun executeDocument(
        track: Track?,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String?
    ): LyricsDocument {
        if (track == null) return LyricsDocument.empty()
        customLyricsRepository?.loadForTrack(track)?.document?.let { return it }
        return operations.loadDocumentForTrack(
            track,
            onlineEnabled,
            neteaseProviderTrackId.orEmpty()
        )
    }
}

internal class LoadTrackLyricsUseCaseLyricsLoader(
    private val useCase: LoadTrackLyricsUseCase
) : LyricsLoader, RichLyricsLoader {
    override suspend fun load(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): List<LyricsLine> = useCase.execute(track, onlineEnabled, neteaseProviderTrackId)

    override suspend fun loadDocument(
        track: Track,
        onlineEnabled: Boolean,
        neteaseProviderTrackId: String
    ): LyricsDocument = useCase.executeDocument(track, onlineEnabled, neteaseProviderTrackId)
}
