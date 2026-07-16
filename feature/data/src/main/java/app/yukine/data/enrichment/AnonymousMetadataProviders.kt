package app.yukine.data.enrichment

import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistAlias
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.ProviderArtistCandidate
import app.yukine.identity.RecordingVariantRecognizer
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult

class MusicBrainzRecordingMetadataProvider(
    private val client: MusicBrainzMetadataClient
) : AnonymousRecordingMetadataProvider {
    override val providerName: String = "musicbrainz"

    override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult =
        client.searchRecording(
            RecordingMetadataQuery(
                title = recording.title,
                artist = primaryArtist,
                isrc = recording.isrc
            )
        )
}

class MusicBrainzArtistMetadataProvider(
    private val client: MusicBrainzMetadataClient
) : AnonymousArtistMetadataProvider {
    override val providerName: String = "musicbrainz"

    override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>): AnonymousArtistProviderResult =
        client.searchArtist(artist.displayName)
}

class WikimediaArtistMetadataProvider(
    private val client: WikimediaArtistMetadataClient
) : AnonymousArtistMetadataProvider {
    override val providerName: String = "wikimedia"

    override fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>): AnonymousArtistProviderResult =
        client.searchArtist(artist.displayName, aliases.map(ArtistAlias::alias))
}

class ItunesRecordingMetadataProvider(
    private val client: ItunesMetadataClient
) : AnonymousRecordingMetadataProvider {
    override val providerName: String = "itunes"

    override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult =
        client.searchRecording(recording.title, primaryArtist)
}

class StreamingSearchRecordingMetadataProvider(
    private val provider: StreamingProviderName,
    private val search: (StreamingSearchRequest) -> StreamingSearchResult
) : AnonymousRecordingMetadataProvider {
    override val providerName: String = provider.wireName

    override fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult {
        val query = listOf(recording.title, primaryArtist).filter(String::isNotBlank).joinToString(" ")
        val result = search(
            StreamingSearchRequest(
                provider = provider,
                query = query,
                mediaTypes = setOf(StreamingMediaType.TRACK),
                page = 1,
                pageSize = 12
            )
        )
        if (result.error != null) return AnonymousProviderResult(emptyList(), allEndpointsFailed = true)
        return AnonymousProviderResult(
            candidates = result.tracks.mapIndexed { index, track ->
                app.yukine.identity.AnonymousRecordingCandidate(
                    provider = track.provider.wireName,
                    providerItemId = track.providerTrackId,
                    title = track.title,
                    artists = track.artists.map {
                        ProviderArtistCandidate(it.providerArtistId, it.name)
                    }.ifEmpty {
                        listOf(ProviderArtistCandidate("", track.artist))
                    },
                    album = track.album.orEmpty(),
                    durationMs = track.durationMs ?: 0L,
                    isrc = track.isrc.orEmpty(),
                    variantType = RecordingVariantRecognizer.recognize(track.title, track.album.orEmpty()),
                    providerScore = (1.0 - index * 0.01).coerceAtLeast(0.0)
                )
            },
            fromCache = result.cached
        )
    }
}
