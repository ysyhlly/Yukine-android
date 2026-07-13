package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDeletionCompletionOwnerTest {
    @Test
    fun appliesQueueSelectionStatusAndReloadPolicyForCompletedResult() {
        val removedTrackIds = mutableListOf<Set<Long>>()
        var clearSelectionCount = 0
        val statuses = mutableListOf<String>()
        val reloadModes = mutableListOf<Boolean>()
        val owner = LibraryDeletionCompletionOwner(
            queueUpdater = { removedTrackIds += it },
            selectionClearer = { clearSelectionCount += 1 },
            languageModeSource = { AppLanguage.MODE_ENGLISH },
            statusSink = { statuses += it },
            libraryReloader = { reloadModes += it }
        )
        val result = LibraryDeletionResult(
            removed = listOf(track(7L), track(7L), track(9L)),
            failed = listOf(track(11L)),
            skipped = listOf(track(13L), track(15L)),
            cancelled = true
        )

        owner.onCompleted(result)

        assertEquals(listOf(linkedSetOf(7L, 9L)), removedTrackIds)
        assertEquals(1, clearSelectionCount)
        assertEquals(
            listOf(
                AppLanguage.text(AppLanguage.MODE_ENGLISH, "library.delete.result")
                    .replace("%d", "3")
                    .replace("%f", "1")
                    .replace("%s", "2")
            ),
            statuses
        )
        assertEquals(listOf(true), reloadModes)
    }

    @Test
    fun publishesEmptyRemovalSetAndStillRefreshesAfterFailureOnlyResult() {
        val removedTrackIds = mutableListOf<Set<Long>>()
        var clearSelectionCount = 0
        var reloadCount = 0
        val owner = LibraryDeletionCompletionOwner(
            queueUpdater = { removedTrackIds += it },
            selectionClearer = { clearSelectionCount += 1 },
            languageModeSource = { AppLanguage.MODE_SYSTEM },
            statusSink = { },
            libraryReloader = { reloadCount += 1 }
        )

        owner.onCompleted(LibraryDeletionResult(emptyList(), failed = listOf(track(21L))))

        assertEquals(listOf(emptySet<Long>()), removedTrackIds)
        assertEquals(1, clearSelectionCount)
        assertEquals(1, reloadCount)
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1_000L, null, "file:$id")
}
