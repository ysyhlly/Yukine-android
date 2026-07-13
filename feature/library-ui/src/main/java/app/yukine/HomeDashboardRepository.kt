package app.yukine

import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.HomeDashboardUiState

/** Data boundary consumed by the Library feature; the Android/network implementation lives in app. */
fun interface HomeDashboardRepository {
    suspend fun fetchHome(
        localTracks: List<Track>,
        localRecords: List<TrackPlayRecord>,
        localPlayback: PlaybackStateSnapshot?
    ): HomeDashboardUiState
}
