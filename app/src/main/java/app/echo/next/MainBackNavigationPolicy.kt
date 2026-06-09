package app.echo.next

internal object MainBackNavigationPolicy {
    fun resolve(
        selectedTab: String,
        networkPage: String,
        settingsPage: String,
        selectedLibraryGroupKey: String?
    ): Result {
        if (
            MainRoutes.TAB_LIBRARY == selectedTab &&
            !selectedLibraryGroupKey.isNullOrEmpty()
        ) {
            return Result.render(selectedTab, networkPage, settingsPage, false, true)
        }
        if (MainRoutes.TAB_SETTINGS == selectedTab && MainRoutes.SETTINGS_HOME != settingsPage) {
            return Result.render(selectedTab, networkPage, MainRoutes.SETTINGS_HOME, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_STREAM_LIST == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_STREAMING, settingsPage, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_WEBDAV_TRACKS == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_WEBDAV, settingsPage, false, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS == networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_SOURCES, settingsPage, true, false)
        }
        if (MainRoutes.TAB_NETWORK == selectedTab && MainRoutes.NETWORK_HOME != networkPage) {
            return Result.render(selectedTab, MainRoutes.NETWORK_HOME, settingsPage, true, false)
        }
        if (MainRoutes.TAB_HOME != selectedTab) {
            return Result.navigate(MainRoutes.TAB_HOME, MainRoutes.NETWORK_HOME, MainRoutes.SETTINGS_HOME, true)
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
        @JvmField val clearLibraryGroup: Boolean
    ) {
        companion object {
            fun render(
                selectedTab: String,
                networkPage: String,
                settingsPage: String,
                clearSelectedRemoteSource: Boolean,
                clearLibraryGroup: Boolean
            ): Result {
                return Result(
                    true,
                    false,
                    selectedTab,
                    networkPage,
                    settingsPage,
                    clearSelectedRemoteSource,
                    clearLibraryGroup
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
                    false
                )
            }

            fun notHandled(): Result {
                return Result(false, false, "", "", "", false, false)
            }
        }
    }
}
