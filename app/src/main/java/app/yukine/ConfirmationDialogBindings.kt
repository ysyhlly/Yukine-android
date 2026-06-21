package app.yukine

internal fun interface NetworkTrackDeleteAction {
    fun delete(trackId: Long, status: String)
}

internal fun interface NetworkTracksDeleteAction {
    fun delete(trackIds: List<Long>, status: String)
}

internal fun interface RemoteSourceDeleteAction {
    fun delete(sourceId: Long, name: String)
}

internal class ConfirmationDialogBindings(
    private val clearPlayHistoryAction: Runnable,
    private val clearQueueAction: Runnable,
    private val deleteAllStreamsAction: Runnable,
    private val deleteTrackAction: NetworkTrackDeleteAction,
    private val deleteTracksAction: NetworkTracksDeleteAction,
    private val deleteRemoteSourceAction: RemoteSourceDeleteAction
) : ConfirmationDialogController.Listener {
    override fun clearPlayHistory() {
        clearPlayHistoryAction.run()
    }

    override fun clearQueue() {
        clearQueueAction.run()
    }

    override fun deleteAllStreams() {
        deleteAllStreamsAction.run()
    }

    override fun deleteTrack(trackId: Long, status: String) {
        deleteTrackAction.delete(trackId, status)
    }

    override fun deleteTracks(trackIds: List<Long>, status: String) {
        deleteTracksAction.delete(trackIds, status)
    }

    override fun deleteRemoteSource(sourceId: Long, name: String) {
        deleteRemoteSourceAction.delete(sourceId, name)
    }
}
