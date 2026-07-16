package app.yukine.streaming

import app.yukine.model.Track

data class StreamingPlaylistImportSummary(
    val provider: StreamingProviderName,
    val playlistName: String,
    val matchedTracks: List<StreamingTrack>,
    val unresolvedTracks: List<Track>,
    val errors: List<String>,
    val remotePlaylist: StreamingPlaylist? = null
) {
    val totalRequested: Int = matchedTracks.size + unresolvedTracks.size
    val matchRatePercent: Int =
        if (totalRequested == 0) 0 else matchedTracks.size * 100 / totalRequested
}
