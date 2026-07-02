package app.yukine

data class PageBackgrounds @JvmOverloads constructor(
    val sharedUri: String = "",
    val homeUri: String = "",
    val libraryUri: String = "",
    val playerUri: String = "",
    val settingsUri: String = "",
    // Encoded BackgroundTransform per slot (empty = identity). Kept as separate fields so the raw
    // URI strings stay untouched (permission grants, summaries, blank checks all keep working).
    val sharedTransform: String = "",
    val homeTransform: String = "",
    val libraryTransform: String = "",
    val playerTransform: String = "",
    val settingsTransform: String = ""
) {
    fun uriFor(page: String): String {
        val pageUri = when (normalizePage(page)) {
            PAGE_HOME -> homeUri
            PAGE_LIBRARY -> libraryUri
            PAGE_PLAYER -> playerUri
            PAGE_SETTINGS -> settingsUri
            else -> ""
        }
        return pageUri.ifBlank { sharedUri }
    }

    /**
     * Resolves the transform for [page], mirroring [uriFor]'s shared-fallback: a per-page transform
     * only applies when that page has its own URI; otherwise the shared transform is used alongside
     * the shared URI.
     */
    fun transformFor(page: String): BackgroundTransform {
        val encoded = when (normalizePage(page)) {
            PAGE_HOME -> if (homeUri.isNotBlank()) homeTransform else sharedTransform
            PAGE_LIBRARY -> if (libraryUri.isNotBlank()) libraryTransform else sharedTransform
            PAGE_PLAYER -> if (playerUri.isNotBlank()) playerTransform else sharedTransform
            PAGE_SETTINGS -> if (settingsUri.isNotBlank()) settingsTransform else sharedTransform
            else -> ""
        }
        return BackgroundTransform.decode(encoded)
    }

    fun withUri(page: String, uri: String): PageBackgrounds =
        withBackground(page, uri, BackgroundTransform.IDENTITY)

    /** Sets both the URI and its zoom/pan transform for [page] in one update. */
    fun withBackground(page: String, uri: String, transform: BackgroundTransform): PageBackgrounds {
        val cleanUri = uri.trim()
        val encoded = if (cleanUri.isBlank()) "" else transform.encode()
        return when (normalizePage(page)) {
            PAGE_ALL -> copy(
                sharedUri = cleanUri,
                homeUri = "",
                libraryUri = "",
                playerUri = "",
                settingsUri = "",
                sharedTransform = encoded,
                homeTransform = "",
                libraryTransform = "",
                playerTransform = "",
                settingsTransform = ""
            )
            PAGE_HOME -> copy(homeUri = cleanUri, homeTransform = encoded)
            PAGE_LIBRARY -> copy(libraryUri = cleanUri, libraryTransform = encoded)
            PAGE_PLAYER -> copy(playerUri = cleanUri, playerTransform = encoded)
            PAGE_SETTINGS -> copy(settingsUri = cleanUri, settingsTransform = encoded)
            else -> this
        }
    }

    fun clear(page: String): PageBackgrounds = withUri(page, "")

    companion object {
        const val PAGE_ALL = "all"
        const val PAGE_HOME = "home"
        const val PAGE_LIBRARY = "library"
        const val PAGE_PLAYER = "player"
        const val PAGE_SETTINGS = "settings"

        @JvmStatic
        fun empty(): PageBackgrounds = PageBackgrounds()

        @JvmStatic
        fun normalizePage(page: String?): String {
            return when (page?.trim()?.lowercase()) {
                PAGE_ALL -> PAGE_ALL
                PAGE_HOME -> PAGE_HOME
                PAGE_LIBRARY -> PAGE_LIBRARY
                MainRoutes.TAB_QUEUE, MainRoutes.TAB_NOW, PAGE_PLAYER -> PAGE_PLAYER
                PAGE_SETTINGS -> PAGE_SETTINGS
                else -> ""
            }
        }
    }
}

data class PageBackgroundUpdate(
    val page: String,
    val uri: String
)
