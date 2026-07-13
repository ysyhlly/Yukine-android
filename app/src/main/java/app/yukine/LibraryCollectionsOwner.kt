package app.yukine

/** Owns collection reload selection and publication without routing callbacks through Activity. */
internal class LibraryCollectionsOwner(
    private val viewModel: LibraryViewModel,
    private val routeController: MainRouteController,
    private val libraryStore: LibraryDataStateOwner
) {
    fun load() {
        viewModel.playlists.loadCollections(routeController.selectedPlaylistId()) { result ->
            routeController.setSelectedPlaylistId(result.selectedPlaylistId)
            libraryStore.applyCollections(result)
        }
    }

    fun selectAndLoad(playlistId: Long) {
        routeController.setSelectedPlaylistId(playlistId)
        load()
    }
}
