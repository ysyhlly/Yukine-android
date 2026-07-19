package app.yukine.data

import app.yukine.data.room.YukineDatabase
import app.yukine.identity.LibraryDedupMode

data class LibraryDedupMaintenanceResult(
    val mode: LibraryDedupMode,
    val reverted: Int,
    val reviewRequired: Int
)

/**
 * Cold-path preparation performed by the unique identity backfill worker.
 *
 * It never scans audio itself. SAFE rollback delegates to the existing exact-snapshot undo path.
 */
class LibraryDedupMaintenance(private val database: YukineDatabase) {
    fun currentMode(): LibraryDedupMode =
        LibraryDedupModeStore(database.settingsDao()).mode()

    fun currentGeneration(): Long =
        LibraryDedupModeStore(database.settingsDao()).generation()

    fun prepareForCurrentMode(): LibraryDedupMaintenanceResult {
        val mode = currentMode()
        if (mode != LibraryDedupMode.SAFE) {
            return LibraryDedupMaintenanceResult(mode, 0, 0)
        }
        val result = IdentityOperationStore(database).rollbackUnsafeAggressiveMerges()
        return LibraryDedupMaintenanceResult(
            mode = mode,
            reverted = result.reverted,
            reviewRequired = result.reviewRequired
        )
    }
}
