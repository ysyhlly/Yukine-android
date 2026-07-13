package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryGroupsActionAdapter(
    private val groupOpener: GroupOpener,
    private val groupSelectionClearer: GroupSelectionClearer,
    private val groupCloser: GroupCloser,
    private val languageModeProvider: LanguageModeProvider,
    private val trackListPlayer: TrackListPlayer,
    private val groupDeleteConfirmer: GroupDeleteConfirmer,
    private val chromePublisher: ChromePublisher,
    private val trackListPublisher: TrackListPublisher
) : LibraryGroupsStateReducer.Listener {
    fun interface GroupOpener {
        fun openGroup(key: String, title: String)
    }

    fun interface GroupSelectionClearer {
        fun clearLibraryGroup()
    }

    fun interface GroupCloser {
        fun closeLibraryGroup()
    }

    fun interface LanguageModeProvider {
        fun languageMode(): String
    }

    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface GroupDeleteConfirmer {
        fun confirmDeleteGroup(title: String, tracks: List<Track>)
    }

    fun interface ChromePublisher {
        fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState)
    }

    fun interface TrackListPublisher {
        fun publishLibraryGroupTrackList(request: LibraryGroupTrackListRequest)
    }

    override fun selectLibraryGroup(key: String, title: String) {
        groupOpener.openGroup(key, title)
    }

    override fun clearLibraryGroupSelection() {
        groupSelectionClearer.clearLibraryGroup()
    }

    override fun closeLibraryGroup() {
        groupCloser.closeLibraryGroup()
    }

    override fun openFavoritesCollection() {
        groupOpener.openGroup(
            "virtual:favorites",
            AppLanguage.text(languageModeProvider.languageMode(), "favorite.playlist")
        )
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        trackListPlayer.playTrackList(tracks, index)
    }

    override fun confirmDeleteGroup(title: String, tracks: List<Track>) {
        groupDeleteConfirmer.confirmDeleteGroup(title, tracks)
    }

    override fun publishLibraryGroupsChrome(
        actions: List<LibraryGroupActions>,
        emptyText: String,
        modeActions: List<TrackListModeAction>
    ) {
        chromePublisher.publishLibraryGroupsChrome(
            LibraryGroupsChromeState(
                ArrayList(actions),
                emptyText,
                ArrayList(modeActions)
            )
        )
    }

    override fun publishTrackList(
        title: String,
        tracks: ArrayList<Track>,
        headerMetrics: ArrayList<TrackListHeaderMetric>,
        headerActions: ArrayList<TrackListHeaderAction>,
        footerAlbums: ArrayList<TrackListAlbumCardUiState>
    ) {
        trackListPublisher.publishLibraryGroupTrackList(
            LibraryGroupTrackListRequest(
                title,
                tracks,
                headerMetrics,
                headerActions,
                footerAlbums
            )
        )
    }
}
