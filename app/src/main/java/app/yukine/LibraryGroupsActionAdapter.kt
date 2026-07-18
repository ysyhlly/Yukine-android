package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryGroupsActionAdapter @JvmOverloads constructor(
    private val groupOpener: GroupOpener,
    private val groupSelectionClearer: GroupSelectionClearer,
    private val groupCloser: GroupCloser,
    private val languageModeProvider: LanguageModeProvider,
    private val trackListPlayer: TrackListPlayer,
    private val groupDeleteConfirmer: GroupDeleteConfirmer,
    private val chromePublisher: ChromePublisher,
    private val trackListPublisher: TrackListPublisher,
    private val artistIdentityManager: ArtistIdentityManager = ArtistIdentityManager { _, _ -> }
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

    fun interface ArtistIdentityManager {
        fun manageArtistIdentity(artistId: String, title: String)
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

    override fun manageArtistIdentity(artistId: String, title: String) {
        artistIdentityManager.manageArtistIdentity(artistId, title)
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
        footerAlbums: ArrayList<TrackListAlbumCardUiState>,
        context: LibraryListContext
    ) {
        trackListPublisher.publishLibraryGroupTrackList(
            LibraryGroupTrackListRequest(
                title,
                tracks,
                headerMetrics,
                headerActions,
                footerAlbums,
                context
            )
        )
    }
}
