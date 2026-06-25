package app.yukine

internal object MainBackNavigationPolicy {
    fun resolve(
        selectedTab: String,
        networkPage: String,
        settingsPage: SettingsPage,
        selectedLibraryGroupKey: String?,
        selectedPlaylistId: Long
    ): Result {
        if (
            MainRoutes.TAB_LIBRARY == selectedTab &&
            !selectedLibraryGroupKey.isNullOrEmpty()
        ) {
            return Result.render(
                selectedTab,
                networkPage,
                settingsPage.route,
                false,
                true,
                selectedLibraryGroupKey.startsWith("playlist:")
            )
        }
        if (MainRoutes.TAB_COLLECTIONS == selectedTab && selectedPlaylistId >= 0L) {
            return Result.render(selectedTab, networkPage, settingsPage.route, false, false, true)
        }
        if (MainRoutes.TAB_SETTINGS == selectedTab && SettingsPage.Home != settingsPage) {
            return Result.render(selectedTab, networkPage, SettingsBackStack.parent(settingsPage).route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_STREAM_LIST == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_STREAMING, settingsPage.route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_WEBDAV_TRACKS == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_WEBDAV, settingsPage.route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_SOURCES, settingsPage.route, true, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_HOME != networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_HOME, settingsPage.route, true, false)
        }
        if (MainRoutes.TAB_HOME != selectedTab) {
            return Result.navigate(MainRoutes.TAB_HOME, MainRoutes.NETWORK_HOME, settingsPage.route, true)
        }
        return Result.notHandled()
    }

    class Result private constructor(
        @JvmField val handled: Boolean,
        @JvmField val navigateTab: Boolean,
        @JvmField val selectedTab: String,
        @JvmField val networkPage: String,
        @JvmField val settingsPage: String,
        @JvmField val clearSelectedRemoteSource: Boolean,
        @JvmField val clearLibraryGroup: Boolean,
        @JvmField val clearSelectedPlaylist: Boolean
    ) {
        companion object {
            fun render(
                selectedTab: String,
                networkPage: String,
                settingsPage: String,
                clearSelectedRemoteSource: Boolean,
                clearLibraryGroup: Boolean,
                clearSelectedPlaylist: Boolean = false
            ): Result {
                return Result(
                    true,
                    false,
                    selectedTab,
                    networkPage,
                    settingsPage,
                    clearSelectedRemoteSource,
                    clearLibraryGroup,
                    clearSelectedPlaylist
                )
            }

            fun navigate(
                selectedTab: String,
                networkPage: String,
                settingsPage: String,
                clearSelectedRemoteSource: Boolean
            ): Result {
                return Result(
                    true,
                    true,
                    selectedTab,
                    networkPage,
                    settingsPage,
                    clearSelectedRemoteSource,
                    false,
                    false
                )
            }

            fun notHandled(): Result {
                return Result(false, false, "", "", "", false, false, false)
            }
        }
    }
}
