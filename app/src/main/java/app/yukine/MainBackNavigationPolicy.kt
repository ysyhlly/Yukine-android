package app.yukine

internal object MainBackNavigationPolicy {
    fun resolve(
        selectedTab: String,
        networkPage: NetworkPage,
        settingsPage: SettingsPage,
        libraryPage: LibraryPage,
        selectedLibraryGroupKey: String?,
        selectedPlaylistId: Long
    ): Result {
        if (
            MainRoutes.TAB_LIBRARY == selectedTab &&
            !selectedLibraryGroupKey.isNullOrEmpty()
        ) {
            return Result.stay(
                selectedTab,
                networkPage,
                settingsPage.route,
                false,
                true,
                selectedLibraryGroupKey.startsWith("playlist:"),
                false
            )
        }
        if (
            MainRoutes.TAB_LIBRARY == selectedTab &&
            libraryPage == LibraryPage.Browse
        ) {
            return Result.stay(
                selectedTab,
                networkPage,
                settingsPage.route,
                false,
                true,
                true,
                true
            )
        }
        if (MainRoutes.TAB_COLLECTIONS == selectedTab && selectedPlaylistId >= 0L) {
            return Result.stay(selectedTab, networkPage, settingsPage.route, false, false, true)
        }
        if (MainRoutes.TAB_SETTINGS == selectedTab && SettingsPage.Home != settingsPage) {
            return Result.stay(selectedTab, networkPage, SettingsBackStack.parent(settingsPage).route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && NetworkPage.StreamList == networkPage) {
            return Result.stay(selectedTab, NetworkPage.Streaming, settingsPage.route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && NetworkPage.WebDavTracks == networkPage) {
            return Result.stay(selectedTab, NetworkPage.WebDav, settingsPage.route, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && NetworkPage.WebDavSourceTracks == networkPage) {
            return Result.stay(selectedTab, NetworkPage.Sources, settingsPage.route, true, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && NetworkPage.Home != networkPage) {
            return Result.stay(selectedTab, NetworkPage.Home, settingsPage.route, true, false)
        }
        if (MainRoutes.TAB_HOME != selectedTab) {
            return Result.navigate(MainRoutes.TAB_HOME, NetworkPage.Home, settingsPage.route, true)
        }
        return Result.notHandled()
    }

    class Result private constructor(
        @JvmField val handled: Boolean,
        @JvmField val navigateTab: Boolean,
        @JvmField val selectedTab: String,
        @JvmField val networkPage: NetworkPage,
        @JvmField val settingsPage: String,
        @JvmField val clearSelectedRemoteSource: Boolean,
        @JvmField val clearLibraryGroup: Boolean,
        @JvmField val clearSelectedPlaylist: Boolean,
        @JvmField val showLibraryOverview: Boolean
    ) {
        companion object {
            fun stay(
                selectedTab: String,
                networkPage: NetworkPage,
                settingsPage: String,
                clearSelectedRemoteSource: Boolean,
                clearLibraryGroup: Boolean,
                clearSelectedPlaylist: Boolean = false,
                showLibraryOverview: Boolean = false
            ): Result {
                return Result(
                    true,
                    false,
                    selectedTab,
                    networkPage,
                    settingsPage,
                    clearSelectedRemoteSource,
                    clearLibraryGroup,
                    clearSelectedPlaylist,
                    showLibraryOverview
                )
            }

            fun navigate(
                selectedTab: String,
                networkPage: NetworkPage,
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
                    false,
                    false
                )
            }

            fun notHandled(): Result {
                return Result(false, false, "", NetworkPage.Home, "", false, false, false, false)
            }
        }
    }
}
