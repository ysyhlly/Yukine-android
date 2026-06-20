package app.yukine

internal class LibraryCollectionGatewayBindings(
    operations: LibraryCollectionOperations
) : LibraryCollectionGateway {
    private val loadCollectionsUseCase = LoadLibraryCollectionsUseCase(operations)
    private val clearPlayHistoryUseCase = ClearPlayHistoryUseCase(operations)
    private val setLibraryFavoriteUseCase = SetLibraryFavoriteUseCase(operations)

    override fun loadCollections(selectedPlaylistId: Long): LibraryCollectionsResult {
        val loaded = loadCollectionsUseCase.execute(selectedPlaylistId)
        return LibraryCollectionsResult(
            selectedPlaylistId = loaded.selectedPlaylistId,
            favoriteIds = loaded.favoriteIds,
            favoriteTracks = loaded.favoriteTracks,
            recentRecords = loaded.recentRecords,
            mostPlayedRecords = loaded.mostPlayedRecords,
            playlists = loaded.playlists,
            remoteSources = loaded.remoteSources,
            selectedPlaylistTracks = loaded.selectedPlaylistTracks
        )
    }

    override fun clearPlayHistory(): Int = clearPlayHistoryUseCase.execute()

    override fun setFavorite(trackId: Long, favorite: Boolean) {
        setLibraryFavoriteUseCase.execute(trackId, favorite)
    }
}
