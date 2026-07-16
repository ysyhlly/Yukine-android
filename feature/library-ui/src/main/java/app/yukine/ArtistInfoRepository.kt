package app.yukine

import app.yukine.streaming.StreamingAlbum
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

/** Room-backed artist page presentation model; it deliberately contains no network behavior. */
data class ArtistInfo(
    val artist: String,
    val source: String,
    val summary: String,
    val albums: List<ArtistAlbumInfo> = emptyList(),
    val preview: Boolean = false
)

data class ArtistAlbumInfo(
    val provider: StreamingProviderName,
    val providerAlbumId: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val trackCount: Int? = null,
    val tracks: List<StreamingTrack> = emptyList()
)

fun ArtistAlbumInfo.toStreamingAlbum(): StreamingAlbum = StreamingAlbum(
    provider = provider,
    providerAlbumId = providerAlbumId,
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    trackCount = trackCount
)
