package app.yukine.streaming

object StreamingCapabilityResolver {
    @JvmStatic
    fun canSearch(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsSearch
    }

    @JvmStatic
    fun canPlayback(descriptor: StreamingProviderDescriptor?): Boolean {
        return descriptor?.enabled == true && descriptor.capabilities.supportsPlayback
    }

    @JvmStatic
    fun canAuth(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsAuth
    }

    @JvmStatic
    fun canFavorites(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsFavorites
    }

    @JvmStatic
    fun canPlaylists(descriptor: StreamingProviderDescriptor): Boolean {
        return descriptor.enabled && descriptor.capabilities.supportsPlaylists
    }

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
            actions = actionLabels(descriptor)
        )
    }

    @JvmStatic
    fun providerCapabilities(descriptors: List<StreamingProviderDescriptor>): List<StreamingProviderCapability> {
        return descriptors.map { providerCapability(it) }
    }
}
