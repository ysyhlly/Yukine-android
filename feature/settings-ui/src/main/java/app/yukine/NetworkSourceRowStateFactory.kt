package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.NetworkSourceUiState

internal object NetworkSourceRowStateFactory {
    fun create(source: RemoteSource, tracks: List<Track>, languageMode: String): NetworkSourceUiState =
        NetworkSourceUiState(
            source.id,
            source.name,
            NetworkLibrary.remoteSourceSubtitle(source, tracks, languageMode),
            source.lastStatus
        )
}
