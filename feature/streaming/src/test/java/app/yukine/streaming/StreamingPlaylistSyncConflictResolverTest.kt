package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamingPlaylistSyncConflictResolverTest {
    private val baseline = StreamingPlaylistSyncSnapshot(
        title = "歌单",
        orderedTrackIds = listOf("a.1.1", "b.2.2"),
        updatedAtMs = 100L
    )

    @Test
    fun unchangedSnapshotsDoNothing() {
        assertEquals(
            StreamingPlaylistSyncWinner.NONE,
            StreamingPlaylistSyncConflictResolver.resolve(baseline, baseline, baseline)
        )
    }

    @Test
    fun oneSidedChangeWins() {
        val local = baseline.copy(orderedTrackIds = listOf("b.2.2", "a.1.1"), updatedAtMs = 200L)

        assertEquals(
            StreamingPlaylistSyncWinner.LOCAL,
            StreamingPlaylistSyncConflictResolver.resolve(baseline, local, baseline)
        )
        assertEquals(
            StreamingPlaylistSyncWinner.REMOTE,
            StreamingPlaylistSyncConflictResolver.resolve(baseline, baseline, local)
        )
    }

    @Test
    fun newerSideWinsAndRemoteWinsTies() {
        val local = baseline.copy(title = "本地", updatedAtMs = 300L)
        val remoteOlder = baseline.copy(title = "远端", updatedAtMs = 200L)
        val remoteTie = remoteOlder.copy(updatedAtMs = 300L)

        assertEquals(
            StreamingPlaylistSyncWinner.LOCAL,
            StreamingPlaylistSyncConflictResolver.resolve(baseline, local, remoteOlder)
        )
        assertEquals(
            StreamingPlaylistSyncWinner.REMOTE,
            StreamingPlaylistSyncConflictResolver.resolve(baseline, local, remoteTie)
        )
    }

    @Test
    fun observedRemoteChangeTimeReplacesMissingRemoteTimestamp() {
        val local = baseline.copy(title = "本地", updatedAtMs = 200L)
        val remote = baseline.copy(title = "远端", updatedAtMs = null)

        assertEquals(
            StreamingPlaylistSyncWinner.REMOTE,
            StreamingPlaylistSyncConflictResolver.resolve(
                baseline,
                local,
                remote,
                remoteObservedChangeAtMs = 250L
            )
        )
    }

    @Test
    fun orderAndDeletionArePartOfFingerprint() {
        assertNotEquals(
            baseline.fingerprint,
            baseline.copy(orderedTrackIds = baseline.orderedTrackIds.reversed()).fingerprint
        )
        assertNotEquals(
            baseline.fingerprint,
            baseline.copy(deletedAtMs = 500L).fingerprint
        )
    }
}
