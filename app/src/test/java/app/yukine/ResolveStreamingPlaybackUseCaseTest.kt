package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveStreamingPlaybackUseCaseTest {
    @Test
    fun prepareReturnsRequestForUnresolvedStreamingPlaceholder() {
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("123"))
        val request = ResolveStreamingPlaybackUseCase().prepare(listOf(localTrack(1), placeholder), 5)

        requireNotNull(request)
        assertEquals(1, request.index)
        assertEquals(StreamingProviderName.NETEASE, request.provider)
        assertEquals("123", request.providerTrackId)
        assertEquals("Song 123", request.metadata?.title)
        assertEquals("Artist", request.metadata?.artist)
    }

    @Test
    fun prepareIgnoresNonStreamingTracksAndRefreshesResolvedStreamingTracks() {
        val useCase = ResolveStreamingPlaybackUseCase()

        assertNull(useCase.prepare(emptyList(), 0))
        assertNull(useCase.prepare(listOf(localTrack(1)), 0))
        assertEquals(
            "resolved",
            useCase.prepare(listOf(resolvedStreamingTrack("resolved")), 0)?.providerTrackId
        )
    }

    @Test
    fun replaceResolvedTrackOnlyReplacesPreparedIndex() {
        val useCase = ResolveStreamingPlaybackUseCase()
        val first = localTrack(1)
        val placeholder = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("123"))
        val request = requireNotNull(useCase.prepare(listOf(first, placeholder), 1))
        val resolved = localTrack(3)

        val replaced = useCase.replaceResolvedTrack(request, resolved)

        assertEquals(listOf(1L, 3L), replaced.map { it.id })
    }

    @Test
    fun prepareNextPreResolveSelectsUnresolvedNextTrackAndThrottlesDuplicate() {
        var now = 1_000L
        val useCase = ResolveStreamingPlaybackUseCase(clockMs = { now })
        val current = localTrack(10)
        val next = StreamingPlaybackAdapter.placeholderTrack(streamingTrack("next"))
        val snapshot = snapshot(current, currentIndex = 0, queueSize = 2, positionMs = 1_000L, durationMs = 100_000L)

        val request = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        val duplicate = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        useCase.clearPreResolve(request?.key)
        val cooledDownDuplicate = useCase.prepareNextPreResolve(snapshot, listOf(current, next))
        now += 121_000L
        val retriedSnapshot = snapshot(localTrack(11), currentIndex = 0, queueSize = 2, positionMs = 1_000L, durationMs = 100_000L)
        val retried = useCase.prepareNextPreResolve(retriedSnapshot, listOf(localTrack(11), next))

        requireNotNull(request)
        assertEquals("netease:next", request.key)
        assertEquals(next.id, request.oldTrackId)
        assertNull(duplicate)
        assertNull(cooledDownDuplicate)
        assertEquals("next", retried?.providerTrackId)
    }

    @Test
    fun prepareRecoveryDowngradesResolvedStreamingTrackAndThrottlesDuplicate() {
        var now = 2_000L
        val useCase = ResolveStreamingPlaybackUseCase(
            clockMs = { now },
            unresolvedStreamingTrack = { false }
        )
        val current = resolvedStreamingTrack("song-1")
        val snapshot = snapshot(current, currentIndex = 0, queueSize = 1, positionMs = 9_000L)

        val request = useCase.prepareRecovery(
            snapshot,
            selectedQuality = StreamingAudioQuality.HIRES,
            adaptiveQuality = StreamingAudioQuality.HIRES
        )
        val duplicate = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)
        useCase.clearRecovery(request?.key)
        val cooledDownDuplicate = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)
        now += 21_000L
        val retried = useCase.prepareRecovery(snapshot, StreamingAudioQuality.HIRES, StreamingAudioQuality.HIRES)

        requireNotNull(request)
        assertEquals(StreamingAudioQuality.LOSSLESS, request.quality)
        assertEquals("netease:song-1:LOSSLESS", request.key)
        assertNull(duplicate)
        assertNull(cooledDownDuplicate)
        assertEquals(StreamingAudioQuality.LOSSLESS, retried?.quality)
    }

    @Test
    fun recoveryQualityStepsDownTowardStandard() {
        val useCase = ResolveStreamingPlaybackUseCase()

        assertEquals(StreamingAudioQuality.LOSSLESS, useCase.recoveryQuality(StreamingAudioQuality.HIRES))
        assertEquals(StreamingAudioQuality.HIGH, useCase.recoveryQuality(StreamingAudioQuality.LOSSLESS))
        assertEquals(StreamingAudioQuality.STANDARD, useCase.recoveryQuality(StreamingAudioQuality.HIGH))
        assertEquals(StreamingAudioQuality.STANDARD, useCase.recoveryQuality(StreamingAudioQuality.STANDARD))
    }

    @Test
    fun metadataForPromotesSelectedSourceAndRetainsOtherPlaybackCandidates() {
        val current = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.NETEASE,
                providerTrackId = "netease-1",
                title = "Song",
                artist = "Artist",
                playbackCandidates = listOf(
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.QQ_MUSIC,
                        label = "QQ 音乐",
                        providerTrackId = "qq-1"
                    ),
                    StreamingPlaybackCandidate(
                        provider = StreamingProviderName.KUGOU,
                        quality = StreamingAudioQuality.LOSSLESS,
                        label = "酷狗音乐",
                        providerTrackId = "kugou-1"
                    )
                )
            )
        )

        val metadata = ResolveStreamingPlaybackUseCase().metadataFor(
            current,
            StreamingProviderName.QQ_MUSIC,
            "qq-1"
        )

        requireNotNull(metadata)
        assertEquals(StreamingProviderName.QQ_MUSIC, metadata.provider)
        assertEquals("qq-1", metadata.providerTrackId)
        assertEquals(
            listOf("netease:netease-1:", "kugou:kugou-1:lossless"),
            metadata.playbackCandidates.map { candidate ->
                "${candidate.provider.wireName}:${candidate.providerTrackId}:${candidate.quality?.wireName.orEmpty()}"
            }
        )

        val resolved = StreamingPlaybackAdapter.toTrack(
            StreamingPlaybackSource(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "qq-1",
                url = "https://stream.qq.example/qq-1.mp3"
            ),
            metadata
        )
        assertEquals(
            listOf("qqmusic:qq-1:", "netease:netease-1:", "kugou:kugou-1:lossless"),
            StreamingPlaybackAdapter.playbackCandidates(resolved).map { candidate ->
                "${candidate.provider.wireName}:${candidate.providerTrackId}:${candidate.quality?.wireName.orEmpty()}"
            }
        )
    }

    @Test
    fun metadataForRestoresLuoxueMusicInfoFromPersistedDataPath() {
        val musicInfo = """{"hash":"abc123","album_id":"22","nested":{"name":"恢复"}}"""
        val persistedQueueTrack = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:abc123.22.33",
                title = "Song",
                artist = "Artist",
                luoxueMusicInfoJson = musicInfo
            )
        )

        val metadata = ResolveStreamingPlaybackUseCase().metadataFor(
            persistedQueueTrack,
            StreamingProviderName.LUOXUE,
            "kg:abc123.22.33"
        )

        assertEquals(
            StreamingPlaybackAdapter.luoxueMusicInfoJson(persistedQueueTrack.dataPath),
            metadata?.luoxueMusicInfoJson
        )
    }

    @Test
    fun prepareSourceSwitchOnlyRequestsResolutionForUnresolvedStreamingCandidates() {
        val streaming = StreamingPlaybackAdapter.placeholderTrack(
            StreamingTrack(
                provider = StreamingProviderName.QQ_MUSIC,
                providerTrackId = "qq-1",
                title = "Song",
                artist = "Artist"
            )
        )
        val useCase = ResolveStreamingPlaybackUseCase()

        val request = requireNotNull(useCase.prepareSourceSwitch(streaming))

        assertEquals(StreamingProviderName.QQ_MUSIC, request.provider)
        assertEquals("qq-1", request.providerTrackId)
        assertNull(useCase.prepareSourceSwitch(localTrack(1L)))

        val malformed = Track(9L, "Song", "Artist", "Album", 1_000L, Uri.EMPTY, "streaming:")
        val malformedRequest = requireNotNull(useCase.prepareSourceSwitch(malformed))
        assertNull(malformedRequest.provider)
        assertEquals("", malformedRequest.providerTrackId)
    }

    private fun localTrack(id: Long): Track =
        Track(id, "Local $id", "Artist", "Album", 1000L, Uri.EMPTY, "local:$id")

    private fun resolvedStreamingTrack(id: String): Track =
        Track(
            id.hashCode().toLong(),
            "Resolved $id",
            "Artist",
            "Album",
            1000L,
            Uri.EMPTY,
            "streaming:netease:$id"
        )

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            durationMs = 3000L
        )

    private fun snapshot(
        track: Track,
        currentIndex: Int,
        queueSize: Int,
        positionMs: Long = 0L,
        durationMs: Long = 1000L
    ): PlaybackStateSnapshot =
        PlaybackStateSnapshot(
            track,
            currentIndex,
            queueSize,
            positionMs,
            durationMs,
            true,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )

}
