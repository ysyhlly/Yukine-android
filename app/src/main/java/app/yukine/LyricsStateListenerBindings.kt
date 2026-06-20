package app.yukine

internal fun interface LyricsNowPlayingContentUpdater {
    fun update(): Boolean
}

internal class LyricsStateListenerBindings(
    private val selectedTabProvider: SettingsSelectedTabProvider,
    private val renderNowBarAction: Runnable,
    private val updateNowPlayingContentAction: LyricsNowPlayingContentUpdater,
    private val renderSelectedTabAction: Runnable
) : LyricsStateListener {
    override fun onLyricsStateChanged() {
        renderNowBarAction.run()
        if (MainRoutes.TAB_NOW == selectedTabProvider.selectedTab() &&
            !updateNowPlayingContentAction.update()
        ) {
            renderSelectedTabAction.run()
        }
    }
}
