package app.yukine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class HandoffExperienceContractTest {
    @Test
    fun nowPlayingSwipeDownClosesWithoutVolumeGesture() {
        val screen = read("feature/player-ui/src/main/java/app/yukine/ui/NowPlayingScreen.kt")
        val language = read("app/src/main/java/app/yukine/AppLanguage.java")

        assertTrue(screen.contains("actions.onClose.run()"))
        assertTrue(screen.contains("totalY > 0f"))
        assertTrue(screen.contains("actions.onNext.run()"))
        assertTrue(screen.contains("actions.onPrevious.run()"))
        assertFalse(screen.contains("setAppVolume("))
        assertFalse(screen.contains("applyAppVolume("))
        assertFalse(language.contains("now.playing.gestures.hint\", \"Swipe on the player to switch songs and adjust volume"))
    }

    @Test
    fun progressBarSeeksFromAnyPointOnTapOrDrag() {
        val gestures = read("feature/player-ui/src/main/java/app/yukine/ui/NowBarGestures.kt")

        assertTrue(gestures.contains("awaitFirstDown()"))
        assertTrue(gestures.contains("onSeek.seekTo(targetPosition)"))
        assertTrue(gestures.contains("drag(down.id)"))
        assertFalse(gestures.contains("thumbHit"))
    }

    @Test
    fun defaultPlayerActionsUseChineseFallbacks() {
        val nowBarState = read("feature/player-ui/src/main/java/app/yukine/ui/NowBarState.kt")
        val trackList = read("feature/library-ui/src/main/java/app/yukine/ui/TrackListScreen.kt")
        val queue = read("feature/player-ui/src/main/java/app/yukine/ui/QueueScreen.kt")

        assertTrue(nowBarState.contains("favorite = \"\\u6536\\u85cf\""))
        assertTrue(nowBarState.contains("repeatOff = \"\\u5173\\u95ed\\u5faa\\u73af\""))
        assertTrue(trackList.contains("addToPlaylistLabel: String = \"\\u52a0\\u5165\\u6b4c\\u5355\""))
        assertTrue(queue.contains("val title: String = \"\\u64ad\\u653e\\u961f\\u5217\""))
    }

    @Test
    fun trackLongPressOpensBeginnerActionSheet() {
        val trackList = read("feature/library-ui/src/main/java/app/yukine/ui/TrackListScreen.kt")

        assertTrue(trackList.contains("ModalBottomSheet"))
        assertTrue(trackList.contains("TrackActionSheet("))
        assertTrue(trackList.contains("onLongPress = { actionSheetState = TrackActionSheetState(track, action) }"))
        assertTrue(trackList.contains("TrackActionSheetRow(EchoIconKind.Import, labels.downloadLabel)"))
    }

    @Test
    fun streamingSearchFiltersOutNonSongResults() {
        val searchOwner = read("app/src/main/java/app/yukine/StreamingSearchStateOwner.kt")
        val screen = read("feature/streaming-ui/src/main/java/app/yukine/ui/StreamingSearchScreen.kt")

        assertTrue(searchOwner.contains("trackOnlySearchResult()"))
        assertTrue(searchOwner.contains("albums = emptyList()"))
        assertTrue(searchOwner.contains("artists = emptyList()"))
        assertTrue(searchOwner.contains("playlists = emptyList()"))
        assertTrue(searchOwner.contains("mvs = emptyList()"))
        assertTrue(screen.contains("val tracks = result?.tracks.orEmpty()"))
        assertFalse(screen.contains("val albums = result?.albums.orEmpty()"))
        assertFalse(screen.contains("val artists = result?.artists.orEmpty()"))
        assertFalse(screen.contains("val playlists = result?.playlists.orEmpty()"))
        assertFalse(screen.contains("val mvs = result?.mvs.orEmpty()"))
    }

    @Test
    fun downloadsUseSharedFileNamePolicy() {
        val manager = read("app/src/main/java/app/yukine/TrackDownloadManager.kt")
        val policy = read("app/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt")

        assertTrue(manager.contains("TrackDownloadFileNamePolicy.audioFileName"))
        assertTrue(manager.contains("TrackDownloadFileNamePolicy.artworkFileName"))
        assertTrue(policy.contains("\"\$artist - \$title\""))
        assertTrue(policy.contains("invalidFileNameChars"))
    }

    private fun read(path: String): String {
        var current: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            for (candidatePath in candidatePaths(path)) {
                val candidate = current.resolve(candidatePath)
                if (Files.isRegularFile(candidate)) {
                    return String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
                }
                val appCandidate = current.resolve("echo-android").resolve(candidatePath)
                if (Files.isRegularFile(appCandidate)) {
                    return String(Files.readAllBytes(appCandidate), StandardCharsets.UTF_8)
                }
            }
            current = current.parent
        }
        throw java.io.FileNotFoundException(path)
    }

    private fun candidatePaths(path: String): List<String> {
        val candidates = mutableListOf(path)
        if (path == "app/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt") {
            candidates += "core/common/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt"
        }
        return candidates
    }
}
