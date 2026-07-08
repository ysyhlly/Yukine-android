package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackRowKeyPolicyTest {
    @Test
    fun occurrenceKeysMatchPerRowOccurrenceKeyForDuplicateTracks() {
        val tracks = listOf(
            track(7L),
            track(8L),
            track(7L),
            track(7L),
            track(8L)
        )

        val bulkKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        val singleKeys = tracks.indices.map { index ->
            TrackRowKeyPolicy.occurrenceKey(tracks, index)
        }

        assertEquals(singleKeys, bulkKeys)
        assertEquals(listOf("7:1", "8:1", "7:2", "7:3", "8:2"), bulkKeys)
    }

    @Test
    fun occurrenceKeysReturnsEmptyListForMissingTracks() {
        assertEquals(emptyList<String>(), TrackRowKeyPolicy.occurrenceKeys(null))
        assertEquals(emptyList<String>(), TrackRowKeyPolicy.occurrenceKeys(emptyList()))
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
