package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal fun interface PlaybackSnapshotReplacer {
    fun replace(snapshot: PlaybackStateSnapshot)
}

internal fun interface PlaybackRouteNavigator {
    fun navigateNow()
}

internal class PlaybackActionResultBindings(
    private val snapshotReplacer: PlaybackSnapshotReplacer,
    private val statusSink: QueueStatusSink,
    private val publishPlaybackStateAction: QueueNoArgAction,
    private val renderNowBarAction: QueueNoArgAction,
    private val renderSelectedTabAction: QueueNoArgAction,
    private val routeNavigator: PlaybackRouteNavigator
) : PlaybackActionResultController.Listener {
    override fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot) {
        snapshotReplacer.replace(snapshot)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }

    override fun publishPlaybackState() {
        publishPlaybackStateAction.run()
    }

    override fun renderNowBar() {
        renderNowBarAction.run()
    }

    override fun renderSelectedTab() {
        renderSelectedTabAction.run()
    }

    override fun navigateNow() {
        routeNavigator.navigateNow()
    }
}
