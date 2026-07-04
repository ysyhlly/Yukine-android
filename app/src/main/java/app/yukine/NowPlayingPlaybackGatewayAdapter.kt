package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.service.PlaybackServiceActions

interface NowPlayingPlaybackServicePort {
    fun snapshot(): PlaybackStateSnapshot?
    fun hasQueue(): Boolean
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(positionMs: Long)
    fun removeTracksById(trackIds: Set<Long>)
    fun clearQueue()
    fun moveQueueTrack(fromIndex: Int, toIndex: Int)
    fun replaceQueuedTrackById(oldTrackId: Long, updated: Track)
    fun retainTracksById(trackIds: Set<Long>)
    fun warmPlaybackTrack(track: Track)
    fun appendToQueue(tracks: List<Track>)
    fun replaceCurrentTrackAndResume(track: Track, positionMs: Long)
    fun startSleepTimerMinutes(minutes: Int)
    fun cancelSleepTimer()
    fun playQueue(tracks: List<Track>, index: Int)
    fun pause()
    fun play()
    fun setShuffleEnabled(enabled: Boolean)
    fun cycleRepeatMode()
    fun setRepeatMode(repeatMode: Int)
}

internal class MainNowPlayingPlaybackGatewayFactory(
    private val serviceStarter: (String?) -> Unit
) {
    fun create(serviceProvider: () -> NowPlayingPlaybackServicePort?): NowPlayingPlaybackGateway {
        return NowPlayingPlaybackGatewayAdapter(serviceProvider, serviceStarter)
    }
}

internal class NowPlayingPlaybackGatewayAdapter(
    private val serviceProvider: () -> NowPlayingPlaybackServicePort?,
    private val serviceStarter: (String?) -> Unit
) : NowPlayingPlaybackGateway {
    override fun serviceConnected(): Boolean = service() != null

    override fun snapshot(): PlaybackStateSnapshot? = service()?.snapshot()

    override fun hasQueue(): Boolean = service()?.hasQueue() == true

    override fun skipToPrevious() {
        val service = service()
        if (service != null) {
            service.skipToPrevious()
        } else {
            serviceStarter(PlaybackServiceActions.PREVIOUS)
        }
    }

    override fun skipToNext() {
        val service = service()
        if (service != null) {
            service.skipToNext()
        } else {
            serviceStarter(PlaybackServiceActions.NEXT)
        }
    }

    override fun seekTo(positionMs: Long) {
        service()?.seekTo(positionMs)
    }

    override fun removeTracksById(trackIds: Set<Long>) {
        service()?.removeTracksById(trackIds)
    }

    override fun clearQueue() {
        service()?.clearQueue()
    }

    override fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        service()?.moveQueueTrack(fromIndex, toIndex)
    }

    override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
        service()?.replaceQueuedTrackById(oldTrackId, updated)
    }

    override fun retainTracksById(trackIds: Set<Long>) {
        service()?.retainTracksById(trackIds)
    }

    override fun warmPlaybackTrack(track: Track) {
        service()?.warmPlaybackTrack(track)
    }

    override fun appendToQueue(tracks: List<Track>) {
        service()?.appendToQueue(tracks)
    }

    override fun replaceCurrentTrackAndResume(track: Track, positionMs: Long) {
        service()?.replaceCurrentTrackAndResume(track, positionMs)
    }

    override fun startSleepTimerMinutes(minutes: Int) {
        service()?.startSleepTimerMinutes(minutes)
    }

    override fun cancelSleepTimer() {
        service()?.cancelSleepTimer()
    }

    override fun playQueue(tracks: List<Track>, index: Int) {
        serviceStarter(null)
        service()?.playQueue(tracks, index)
    }

    override fun pause() {
        service()?.pause()
    }

    override fun play() {
        serviceStarter(null)
        service()?.play()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        service()?.setShuffleEnabled(enabled)
    }

    override fun cycleRepeatMode() {
        service()?.cycleRepeatMode()
    }

    override fun setRepeatMode(repeatMode: Int) {
        service()?.setRepeatMode(repeatMode)
    }

    private fun service(): NowPlayingPlaybackServicePort? = serviceProvider()
}
