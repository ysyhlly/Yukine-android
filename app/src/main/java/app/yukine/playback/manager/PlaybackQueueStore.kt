package app.yukine.playback.manager

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track

internal class PlaybackQueueStore(
    private val repository: MusicLibraryRepository
) {
    fun load(): PlaybackQueueState {
        return repository.loadPlaybackQueue()
    }

    fun save(tracks: List<Track>, currentIndex: Int) {
        repository.savePlaybackQueue(tracks, currentIndex)
    }

    fun loadResumeRequested(): Boolean = repository.loadPlaybackResumeRequested()

    fun saveResumeRequested(requested: Boolean) {
        repository.savePlaybackResumeRequested(requested)
    }

    fun loadPlaybackPositionTrackId(): Long = repository.loadPlaybackPositionTrackId()

    fun loadPlaybackPositionMs(): Long = repository.loadPlaybackPositionMs()

    fun savePlaybackPosition(trackId: Long, positionMs: Long) {
        repository.savePlaybackPosition(trackId, positionMs)
    }
}
