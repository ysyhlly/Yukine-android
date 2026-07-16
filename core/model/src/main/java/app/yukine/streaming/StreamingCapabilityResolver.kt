package app.yukine.streaming

object StreamingCapabilityResolver {
    @JvmStatic
    fun canSearch(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsSearch
    }

    @JvmStatic
    fun canPlayback(descriptor: StreamingProviderDescriptor?): Boolean {
        return StreamingAudioCapabilityPolicy.canResolve(descriptor)
    }

    @JvmStatic
    fun canAuth(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsAuth
    }

    @JvmStatic
    fun canFavorites(descriptor: StreamingProviderDescriptor): Boolean {
        return canReadFavorites(descriptor) || canWriteFavorites(descriptor)
    }

    @JvmStatic
    fun canPlaylists(descriptor: StreamingProviderDescriptor): Boolean {
        return canReadPlaylists(descriptor) || canWritePlaylists(descriptor)
    }

    @JvmStatic
    fun canReadFavorites(descriptor: StreamingProviderDescriptor): Boolean =
        descriptor.enabled && descriptor.capabilities.supportsFavoritesRead

    @JvmStatic
    fun canWriteFavorites(descriptor: StreamingProviderDescriptor): Boolean =
        descriptor.enabled && descriptor.capabilities.supportsFavoritesWrite

    @JvmStatic
    fun canReadPlaylists(descriptor: StreamingProviderDescriptor): Boolean =
        descriptor.enabled && (
            descriptor.capabilities.supportsPlaylistImport ||
                descriptor.capabilities.supportsPlaylistReadSync
            )

    @JvmStatic
    fun canWritePlaylists(descriptor: StreamingProviderDescriptor): Boolean =
        descriptor.enabled && (
            descriptor.capabilities.supportsPlaylistCreate ||
                descriptor.capabilities.supportsPlaylistWrite ||
                descriptor.capabilities.supportsPlaylistDelete ||
                descriptor.capabilities.supportsPlaylistRename ||
                descriptor.capabilities.supportsPlaylistReorder
            )

    @JvmStatic
    fun supportedSearchMediaTypes(descriptor: StreamingProviderDescriptor?): Set<StreamingMediaType> {
        if (descriptor == null) {
            return setOf(StreamingMediaType.TRACK)
        }
        if (!canSearch(descriptor)) {
            return emptySet()
        }
        val source = descriptor.capabilities.supportedMediaTypes.ifEmpty {
            setOf(StreamingMediaType.TRACK)
        }
        return source.filterTo(LinkedHashSet()) { type ->
            when (type) {
                StreamingMediaType.MV -> descriptor.capabilities.supportsMv
                StreamingMediaType.PLAYLIST -> descriptor.capabilities.supportsPlaylists
                else -> true
            }
        }
    }

    @JvmStatic
    fun actionLabels(descriptor: StreamingProviderDescriptor): List<String> {
        return listOfNotNull(
            "search".takeIf { canSearch(descriptor) },
            "playback".takeIf { canPlayback(descriptor) },
            "auth".takeIf { canAuth(descriptor) },
            "favorites".takeIf { canFavorites(descriptor) },
            "playlists".takeIf { canPlaylists(descriptor) }
        )
    }

    @JvmStatic
    fun providerCapability(descriptor: StreamingProviderDescriptor): StreamingProviderCapability {
        return StreamingProviderCapability(
            provider = descriptor.name,
            displayName = descriptor.displayName,
            enabled = descriptor.enabled,
            status = descriptor.status,
            supportsSearch = canSearch(descriptor),
            supportsPlayback = canPlayback(descriptor),
            supportsAuth = canAuth(descriptor),
            supportsFavorites = canFavorites(descriptor),
            supportsPlaylists = canPlaylists(descriptor),
            supportsLyrics = descriptor.enabled && descriptor.capabilities.supportsLyrics,
            supportsMv = descriptor.enabled && descriptor.capabilities.supportsMv,
            supportedSearchMediaTypes = supportedSearchMediaTypes(descriptor),
            actions = actionLabels(descriptor),
            supportsPlaylistImport = descriptor.enabled && descriptor.capabilities.supportsPlaylistImport,
            supportsPlaylistReadSync = descriptor.enabled && descriptor.capabilities.supportsPlaylistReadSync,
            supportsPlaylistCreate = descriptor.enabled && descriptor.capabilities.supportsPlaylistCreate,
            supportsPlaylistWrite = descriptor.enabled && descriptor.capabilities.supportsPlaylistWrite,
            supportsPlaylistDelete = descriptor.enabled && descriptor.capabilities.supportsPlaylistDelete,
            supportsPlaylistRename = descriptor.enabled && descriptor.capabilities.supportsPlaylistRename,
            supportsPlaylistReorder = descriptor.enabled && descriptor.capabilities.supportsPlaylistReorder,
            supportsFavoritesRead = canReadFavorites(descriptor),
            supportsFavoritesWrite = canWriteFavorites(descriptor),
            supportsAudioResolve = canPlayback(descriptor),
            supportsAudioFallback = canPlayback(descriptor) && descriptor.capabilities.supportsAudioFallback,
            supportsAudioDownload = canPlayback(descriptor) && descriptor.capabilities.supportsAudioDownload,
            supportsAudioCache = canPlayback(descriptor) && descriptor.capabilities.supportsAudioCache
        )
    }

    @JvmStatic
    fun providerCapabilities(descriptors: List<StreamingProviderDescriptor>): List<StreamingProviderCapability> {
        return descriptors.map { providerCapability(it) }
    }
}
