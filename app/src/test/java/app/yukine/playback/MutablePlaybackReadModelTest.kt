package app.yukine.playback

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MutablePlaybackReadModelTest {
    @Test
    fun progressTicksDoNotCopyAnUnchangedQueue() {
        val readModel = MutablePlaybackReadModel()
        val current = track(7L)
        var queueReads = 0
        val queue = List(500) { track(it.toLong()) }

        readModel.publish(snapshot(current, 1_000L, 500, 4L)) {
            queueReads += 1
            queue
        }
        readModel.publish(snapshot(current, 2_000L, 500, 4L)) {
            queueReads += 1
            queue
        }

        assertEquals(1, queueReads)
        assertEquals(500, readModel.queue.value.tracks.size)
        assertEquals(2_000L, readModel.state.value.positionMs)
    }

    @Test
    fun queueRevisionPublishesAFreshDefensiveSnapshot() {
        val readModel = MutablePlaybackReadModel()
        val mutableQueue = mutableListOf(track(1L), track(2L))

        readModel.publish(snapshot(mutableQueue.first(), 0L, 2, 4L)) { mutableQueue }
        mutableQueue[1] = track(22L)
        readModel.publish(snapshot(mutableQueue.first(), 1_000L, 2, 5L)) { mutableQueue }
        mutableQueue.clear()

        assertEquals(listOf(1L, 22L), readModel.queue.value.tracks.map { it.id })
        assertEquals(5L, readModel.queue.value.revision)
    }

    @Test
    fun disconnectClearsPublishedRuntimeState() {
        val readModel = MutablePlaybackReadModel()
        readModel.markConnected()
        readModel.publish(snapshot(track(1L), 1_000L, 1, 1L)) { listOf(track(1L)) }

        readModel.clear()

        assertEquals(PlaybackConnectionState.Disconnected, readModel.connection.value)
        assertEquals(null, readModel.state.value.currentTrack)
        assertEquals(emptyList<Track>(), readModel.queue.value.tracks)
    }

    private fun snapshot(
        track: Track,
        positionMs: Long,
        queueSize: Int,
        queueRevision: Long
    ) = PlaybackStateSnapshot(
        track,
        0,
        queueSize,
        positionMs,
        track.durationMs,
        true,
        false,
        "",
        false,
        0,
        1f,
        1f,
        0L,
        queueRevision
    )

    private fun track(id: Long) =
        Track(id, "Track $id", "Artist", "Album", 180_000L, Uri.EMPTY, "file:$id")
}
