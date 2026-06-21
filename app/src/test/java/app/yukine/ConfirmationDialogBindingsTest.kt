package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationDialogBindingsTest {
    @Test
    fun forwardsConfirmationActionsToBoundOperations() {
        val calls = mutableListOf<String>()
        val bindings = ConfirmationDialogBindings(
            clearPlayHistoryAction = Runnable { calls += "history" },
            clearQueueAction = Runnable { calls += "queue" },
            deleteAllStreamsAction = Runnable { calls += "deleteAllStreams" },
            deleteTrackAction = NetworkTrackDeleteAction { trackId, status ->
                calls += "deleteTrack:$trackId:$status"
            },
            deleteTracksAction = NetworkTracksDeleteAction { trackIds, status ->
                calls += "deleteTracks:${trackIds.joinToString(",")}:$status"
            },
            deleteRemoteSourceAction = RemoteSourceDeleteAction { sourceId, name ->
                calls += "deleteSource:$sourceId:$name"
            }
        )

        bindings.clearPlayHistory()
        bindings.clearQueue()
        bindings.deleteAllStreams()
        bindings.deleteTrack(7L, "Deleted")
        bindings.deleteTracks(listOf(1L, 2L), "Removed")
        bindings.deleteRemoteSource(9L, "NAS")

        assertEquals(
            listOf(
                "history",
                "queue",
                "deleteAllStreams",
                "deleteTrack:7:Deleted",
                "deleteTracks:1,2:Removed",
                "deleteSource:9:NAS"
            ),
            calls
        )
    }
}
