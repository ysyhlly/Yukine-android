package app.yukine.playback.manager

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.PlaybackQueueState
import app.yukine.model.Track

internal interface PlaybackQueueStore {
    fun load(): PlaybackQueueState
    fun save(tracks: List<Track>, currentIndex: Int)
    fun loadResumeRequested(): Boolean
    fun saveResumeRequested(requested: Boolean)
    fun loadPlaybackPositionTrackId(): Long
    fun loadPlaybackPositionMs(): Long
    fun savePlaybackPosition(trackId: Long, positionMs: Long)
}

internal class PlaybackQueueStoreImpl(
    private val repository: MusicLibraryRepository
): PlaybackQueueStore {
    override fun load(): PlaybackQueueState {
        return repository.loadPlaybackQueue()
    }

    override fun save(tracks: List<Track>, currentIndex: Int) {
        repository.savePlaybackQueue(tracks, currentIndex)
    }

    override fun loadResumeRequested(): Boolean = repository.loadPlaybackResumeRequested()

    override fun saveResumeRequested(requested: Boolean) {
        repository.savePlaybackResumeRequested(requested)
    }

    override fun loadPlaybackPositionTrackId(): Long = repository.loadPlaybackPositionTrackId()

    override fun loadPlaybackPositionMs(): Long = repository.loadPlaybackPositionMs()

    override fun savePlaybackPosition(trackId: Long, positionMs: Long) {
        repository.savePlaybackPosition(trackId, positionMs)
    }
}
