package app.yukine

import android.content.Context
import android.content.Intent
import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService
import app.yukine.playback.PlaybackStateSnapshot
import java.util.ArrayList

internal class MainNowPlayingPlaybackGatewayFactory(
    private val serviceStarter: (String?) -> Unit
) {
    fun create(serviceProvider: () -> EchoPlaybackService?): NowPlayingPlaybackGateway {
        return NowPlayingPlaybackGatewayAdapter(serviceProvider, serviceStarter)
    }
}

internal class NowPlayingPlaybackServiceStarter(
    private val context: Context
) {
    fun startPlaybackService(action: String?) {
        val intent = Intent(context, EchoPlaybackService::class.java)
        if (action != null) {
            intent.action = action
        }
        context.startService(intent)
    }
}

internal class NowPlayingPlaybackGatewayAdapter(
    private val serviceProvider: () -> EchoPlaybackService?,
    private val serviceStarter: (String?) -> Unit
) : NowPlayingPlaybackGateway {
    override fun serviceConnected(): Boolean = service() != null

    override fun startPlaybackService(action: String?) {
        serviceStarter(action)
    }

    override fun snapshot(): PlaybackStateSnapshot? = service()?.snapshot()

    override fun queueSnapshot(): List<Track> = service()?.queueSnapshot().orEmpty()

    override fun skipToPrevious() {
        service()?.skipToPrevious()
    }

    override fun skipToNext() {
        service()?.skipToNext()
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

    override fun replaceQueuedTrack(updated: Track) {
        service()?.replaceQueuedTrack(updated)
    }

    override fun replaceQueuedTrackById(oldTrackId: Long, updated: Track) {
        service()?.replaceQueuedTrackById(oldTrackId, updated)
    }

    override fun retainTracksById(trackIds: Set<Long>) {
        service()?.retainTracksById(trackIds)
    }

    override fun precacheTrack(track: Track) {
        service()?.precacheTrack(track)
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
        service()?.playQueue(ArrayList(tracks), index)
    }

    override fun pause() {
        service()?.pause()
    }

    override fun play() {
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

    private fun service(): EchoPlaybackService? = serviceProvider()
}
