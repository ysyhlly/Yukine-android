package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackConnectionState
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackRepeatMode
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingSourceSwitchOwnerTest {
    @Test
    fun streamingSourceResolvesAndResumesAtCapturedPosition() {
        val current = track(1L, "Current", "file:current.mp3")
        val replacement = track(2L, "Resolved", "https://resolved.example/song.flac")
        val readModel = FakePlaybackReadModel(positionMs = 42_500L, current = current)
        val resolutions = mutableListOf<ResolveCall>()
        val replacements = mutableListOf<ReplacementCall>()
        val feedback = mutableListOf<String>()
        val owner = owner(
            readModel = readModel,
            resolver = StreamingSourceResolver { provider, providerTrackId, _, quality, callback ->
                resolutions += ResolveCall(provider, providerTrackId, quality)
                callback.onResult(replacement)
            },
            latest = { it == 7L },
            replacements = replacements,
            feedback = feedback
        )

        owner.handle(
            NowPlayingEffect.SwitchSource(
                current,
                StreamingProviderName.NETEASE,
                "source-7",
                null,
                7L
            )
        )

        assertEquals(
            listOf(ResolveCall(StreamingProviderName.NETEASE, "source-7", StreamingAudioQuality.LOSSLESS)),
            resolutions
        )
        assertEquals(listOf(ReplacementCall(1L, replacement, 42_500L)), replacements)
        assertEquals(listOf("正在切换音源：netease", "已切换音源：Resolved"), feedback)
    }

    @Test
    fun staleAsyncResolutionCannotReplaceTheNewerRequest() {
        val current = track(3L, "Old", "file:old.mp3")
        val replacement = track(4L, "Late", "https://resolved.example/late.flac")
        var latestRequestId = 9L
        var callback: StreamingCallback<Track?>? = null
        val replacements = mutableListOf<ReplacementCall>()
        val owner = owner(
            readModel = FakePlaybackReadModel(positionMs = 5_000L, current = current),
            resolver = StreamingSourceResolver { _, _, _, _, next -> callback = next },
            latest = { it == latestRequestId },
            replacements = replacements
        )

        owner.handle(
            NowPlayingEffect.SwitchSource(
                current,
                StreamingProviderName.QQ_MUSIC,
                "qq-9",
                StreamingAudioQuality.HIGH,
                9L
            )
        )
        latestRequestId = 10L
        requireNotNull(callback).onResult(replacement)

        assertTrue(replacements.isEmpty())
    }

    @Test
    fun localLibrarySourceSwitchDoesNotEnterStreamingResolver() {
        val current = track(5L, "Current", "file:current.mp3")
        val replacement = track(6L, "Local FLAC", "file:replacement.flac")
        val replacements = mutableListOf<ReplacementCall>()
        var resolveCalls = 0
        val owner = owner(
            planner = FakePlanner(sourceSwitchRequest = null),
            readModel = FakePlaybackReadModel(positionMs = 91_000L, current = current),
            resolver = StreamingSourceResolver { _, _, _, _, _ -> resolveCalls++ },
            latest = { it == 12L },
            replacements = replacements
        )

        owner.handle(NowPlayingEffect.SwitchLibrarySource(current, replacement, 12L))

        assertEquals(0, resolveCalls)
        assertEquals(listOf(ReplacementCall(5L, replacement, 91_000L)), replacements)
    }

    @Test
    fun unresolvedLibrarySourceRejectsMissingProviderWithoutReplacingQueue() {
        val current = track(7L, "Current", "file:current.mp3")
        val unresolved = track(8L, "Broken", "streaming:broken")
        val replacements = mutableListOf<ReplacementCall>()
        val feedback = mutableListOf<String>()
        val owner = owner(
            planner = FakePlanner(StreamingSourceSwitchResolveRequest(null, "missing-provider")),
            readModel = FakePlaybackReadModel(positionMs = 1_000L, current = current),
            resolver = StreamingSourceResolver { _, _, _, _, _ -> error("resolver must not be called") },
            latest = { true },
            replacements = replacements,
            feedback = feedback
        )

        owner.handle(NowPlayingEffect.SwitchLibrarySource(current, unresolved, 13L))

        assertTrue(replacements.isEmpty())
        assertEquals(listOf("音源切换暂不可用"), feedback)
    }

    private fun owner(
        planner: StreamingSourceSwitchPlanner = FakePlanner(),
        readModel: PlaybackReadModel,
        resolver: StreamingSourceResolver,
        latest: (Long) -> Boolean,
        replacements: MutableList<ReplacementCall>,
        feedback: MutableList<String> = mutableListOf()
    ): NowPlayingSourceSwitchOwner = NowPlayingSourceSwitchOwner(
        planner = planner,
        resolver = resolver,
        playbackReadModel = readModel,
        quality = object : StreamingPlaybackQuality {
            override fun adaptive() = StreamingAudioQuality.HIGH
            override fun selected() = StreamingAudioQuality.LOSSLESS
        },
        isLatestRequest = latest,
        replaceCurrentSourceAndResume = { expectedId, replacement, positionMs ->
            replacements += ReplacementCall(expectedId, replacement, positionMs)
        },
        feedback = feedback::add
    )

    private class FakePlanner(
        private val sourceSwitchRequest: StreamingSourceSwitchResolveRequest? = null
    ) : StreamingSourceSwitchPlanner {
        override fun metadataFor(
            track: Track?,
            provider: StreamingProviderName,
            providerTrackId: String
        ): StreamingTrack? = null

        override fun prepareSourceSwitch(track: Track?): StreamingSourceSwitchResolveRequest? =
            sourceSwitchRequest
    }

    private class FakePlaybackReadModel(positionMs: Long, current: Track) : PlaybackReadModel {
        override val state = MutableStateFlow(snapshot(current, positionMs))
        override val queue = MutableStateFlow(PlaybackQueueSnapshot())
        override val connection = MutableStateFlow(PlaybackConnectionState.Connected)
    }

    private data class ResolveCall(
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality
    )

    private data class ReplacementCall(
        val expectedId: Long,
        val replacement: Track,
        val positionMs: Long
    )

    companion object {
        private fun track(id: Long, title: String, path: String) =
            Track(id, title, "Artist", "Album", 180_000L, Uri.EMPTY, path)

        private fun snapshot(track: Track, positionMs: Long) = PlaybackStateSnapshot(
            track,
            0,
            1,
            positionMs,
            track.durationMs,
            true,
            false,
            "",
            false,
            PlaybackRepeatMode.REPEAT_ALL,
            1.0f,
            1.0f,
            0L
        )
    }
}
