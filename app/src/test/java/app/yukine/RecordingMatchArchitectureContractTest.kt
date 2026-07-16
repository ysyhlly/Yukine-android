package app.yukine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RecordingMatchArchitectureContractTest {
    @Test
    fun independentMatchManagementPageUsesOnlyTheRoomBackedRepository() {
        val viewModel = read("feature/library-ui/src/main/java/app/yukine/RecordingMatchViewModel.kt")
        val screen = read("feature/library-ui/src/main/java/app/yukine/ui/RecordingMatchScreen.kt")
        val scaffold = read("feature/navigation/src/main/java/app/yukine/navigation/EchoScaffold.kt")
        val repository = read("feature/data/src/main/java/app/yukine/data/RecordingMatchRepository.kt")
        val combined = viewModel + screen + repository

        assertTrue(viewModel.contains("RecordingMatchDataSource"))
        assertTrue(screen.contains("fun RecordingMatchScreen("))
        assertTrue(screen.contains("contentPadding = echoPagePadding(includeBottomChrome = true)"))
        assertTrue(scaffold.contains("LocalEchoPageBottomChromeInset provides"))
        assertTrue(screen.contains("EchoPageTitle("))
        assertTrue(screen.contains("EchoGlassSurface("))
        assertTrue(screen.contains("EchoSectionTitle(title)"))
        assertTrue(screen.contains("EchoEmptyCard("))
        assertFalse(screen.contains("MaterialTheme.colorScheme.background"))
        assertTrue(repository.contains("RoomRecordingIdentityRepository"))
        assertTrue(repository.contains("RoomIdentityCandidateRepository"))
        assertFalse(Files.exists(root().resolve("app/src/main/java/app/yukine/RecordingMatchController.kt")))
        for (networkType in listOf(
            "HttpURLConnection",
            "OkHttpClient",
            "Retrofit",
            "URL(",
            "https://",
            "http://",
            "StreamingGateway",
            "MetadataProvider",
        )) {
            assertFalse("Match management must not use network dependency: $networkType", combined.contains(networkType))
        }
    }

    private fun read(relativePath: String): String =
        String(Files.readAllBytes(root().resolve(relativePath)), StandardCharsets.UTF_8)

    private fun root(): Path {
        var current: Path? = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) return current
            current = current.parent
        }
        error("Could not locate echo-android workspace root")
    }
}
