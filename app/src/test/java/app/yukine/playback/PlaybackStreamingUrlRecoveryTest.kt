package app.yukine.playback

import android.net.Uri
import app.yukine.StreamingRepositorySource
import app.yukine.model.Track
import app.yukine.streaming.RegistryStreamingGateway
import app.yukine.streaming.StreamingPlaybackRequest
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProvider
import app.yukine.streaming.StreamingProviderCatalog
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderRegistry
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingSearchRequest
import app.yukine.streaming.StreamingSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamingUrlRecoveryTest {
    @Test
    fun serviceRecoveryResolvesAndReturnsFreshTrackWithoutActivity() {
        val resolved = mutableListOf<Triple<Long, Track, Long>>()
        val failures = mutableListOf<Long>()
        val recovery = PlaybackStreamingUrlRecovery(
            confirmedNeteaseRepositorySource(),
            PlaybackStreamingUrlRecovery.BackgroundScheduler(Runnable::run),
            PlaybackStreamingUrlRecovery.MainPoster { task -> task.run(); true },
            PlaybackStreamingUrlRecovery.ResolvedSink { expectedId, track, positionMs ->
                resolved += Triple(expectedId, track, positionMs)
            },
            PlaybackStreamingUrlRecovery.FailureSink(failures::add)
        )
        val expired = Track(
            77L,
            "Expired",
            "Artist",
            "Album",
            120_000L,
            Uri.parse("https://expired.example.test/song.mp3"),
            "streaming:netease:netease-track-echo"
        )

        assertTrue(recovery.refresh(expired, 12_345L))

        assertEquals(listOf(77L), resolved.map { it.first })
        assertEquals(listOf(12_345L), resolved.map { it.third })
        assertTrue(resolved.single().second.dataPath.startsWith("streaming:netease:netease-track-echo"))
        assertEquals(emptyList<Long>(), failures)
    }

    @Test
    fun serviceRecoveryRejectsNonStreamingTrack() {
        val recovery = PlaybackStreamingUrlRecovery(
            confirmedNeteaseRepositorySource(),
            PlaybackStreamingUrlRecovery.BackgroundScheduler(Runnable::run),
            PlaybackStreamingUrlRecovery.MainPoster { task -> task.run(); true },
            PlaybackStreamingUrlRecovery.ResolvedSink { _, _, _ -> },
            PlaybackStreamingUrlRecovery.FailureSink { }
        )
        val local = Track(1L, "Local", "Artist", "Album", 1_000L, Uri.EMPTY, "file:local")

        assertFalse(recovery.refresh(local, 0L))
    }

    private fun confirmedNeteaseRepositorySource(): StreamingRepositorySource {
        val provider = object : StreamingProvider {
            override val descriptor = StreamingProviderCatalog.localFirstDescriptor(
                StreamingProviderName.NETEASE
            )

            override suspend fun search(request: StreamingSearchRequest): StreamingSearchResult {
                error("Exact provider ID recovery must not search by title")
            }

            override suspend fun resolvePlayback(request: StreamingPlaybackRequest) = StreamingPlaybackSource(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = request.providerTrackId,
                url = "https://audio.example.test/${request.providerTrackId}.mp3"
            )
        }
        val repository = StreamingRepository(
            RegistryStreamingGateway(StreamingProviderRegistry(listOf(provider)))
        )
        return object : StreamingRepositorySource {
            override fun current(): StreamingRepository = repository
        }
    }
}
