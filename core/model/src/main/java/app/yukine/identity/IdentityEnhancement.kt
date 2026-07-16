package app.yukine.identity

interface AnonymousRecordingMetadataProvider {
    val providerName: String
    fun search(recording: CanonicalRecording, primaryArtist: String): AnonymousProviderResult
}

interface AnonymousArtistMetadataProvider {
    val providerName: String
    fun search(artist: CanonicalArtist, aliases: List<ArtistAlias>): AnonymousArtistProviderResult
}

data class IdentityEnhancementRunResult(
    val claimed: Int,
    val succeeded: Int,
    val retried: Int,
    val failed: Int,
    val candidatesSaved: Int
)
