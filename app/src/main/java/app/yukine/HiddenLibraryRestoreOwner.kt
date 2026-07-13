package app.yukine

/** Owns hidden-library restore completion policy without mirroring library state. */
internal class HiddenLibraryRestoreOwner(
    private val libraryViewModel: LibraryViewModel,
    private val reloadLibrary: Runnable,
    private val refreshSettings: Runnable
) {
    fun restore(sourceKey: String) {
        libraryViewModel.restoreHiddenLibraryItemJava(sourceKey, ::onRestored)
    }

    fun restoreAll() {
        libraryViewModel.restoreAllHiddenLibraryItemsJava(::onRestored)
    }

    internal fun onRestored(changed: Boolean) {
        if (changed) {
            reloadLibrary.run()
        }
        refreshSettings.run()
    }
}
