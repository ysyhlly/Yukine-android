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
        val screen = read("app/src/main/java/app/yukine/ui/NowPlayingScreen.kt")
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
        val nowBar = read("app/src/main/java/app/yukine/ui/NowBar.kt")

        assertTrue(nowBar.contains("awaitFirstDown()"))
        assertTrue(nowBar.contains("onSeek.seekTo(targetPosition)"))
        assertTrue(nowBar.contains("drag(down.id)"))
        assertFalse(nowBar.contains("thumbHit"))
    }

    @Test
    fun defaultPlayerActionsUseChineseFallbacks() {
        val nowBar = read("app/src/main/java/app/yukine/ui/NowBar.kt")
        val trackList = read("app/src/main/java/app/yukine/ui/TrackListScreen.kt")
        val queue = read("app/src/main/java/app/yukine/ui/QueueScreen.kt")

        assertTrue(nowBar.contains("favoriteLabel = \"\\u6536\\u85cf\""))
        assertTrue(nowBar.contains("repeatOffLabel = \"\\u5173\\u95ed\\u5faa\\u73af\""))
        assertTrue(trackList.contains("addToPlaylistLabel: String = \"\\u52a0\\u5165\\u6b4c\\u5355\""))
        assertTrue(queue.contains("val title: String = \"\\u64ad\\u653e\\u961f\\u5217\""))
    }

    @Test
    fun trackLongPressOpensBeginnerActionSheet() {
        val trackList = read("app/src/main/java/app/yukine/ui/TrackListScreen.kt")

        assertTrue(trackList.contains("ModalBottomSheet"))
        assertTrue(trackList.contains("TrackActionSheet("))
        assertTrue(trackList.contains("onLongPress = { actionSheetState = TrackActionSheetState(track, action) }"))
        assertTrue(trackList.contains("TrackActionSheetRow(EchoIconKind.Import, labels.downloadLabel)"))
    }

    @Test
    fun streamingSearchFiltersOutNonSongResults() {
        val viewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt")
        val merger = read("app/src/main/java/app/yukine/streaming/StreamingSearchMerger.kt")
        val screen = read("feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt")

        assertTrue(viewModel.contains("trackOnlySearchResult()"))
        assertTrue(merger.contains("trackOnlySearchResult()"))
        assertTrue(merger.contains("albums = emptyList()"))
        assertTrue(merger.contains("artists = emptyList()"))
        assertTrue(merger.contains("playlists = emptyList()"))
        assertTrue(merger.contains("mvs = emptyList()"))
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
        if (path.startsWith("app/src/main/java/app/yukine/ui/")) {
            candidates += path.replace(
                "app/src/main/java/app/yukine/ui/",
                "feature/ui-common/src/main/java/app/yukine/ui/"
            )
        }
        if (path == "app/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt") {
            candidates += "core/common/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt"
        }
        return candidates
    }
}
