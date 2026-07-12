package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingLyricSource
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaybackCandidate
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import java.util.ArrayList

data class ResolveStreamingPlaybackRequest(
    val tracks: List<Track>,
    val index: Int,
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val metadata: StreamingTrack?
)

data class StreamingPreResolveRequest(
    val key: String,
    val oldTrackId: Long,
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val metadata: StreamingTrack?
)

data class StreamingRecoveryRequest(
    val key: String,
    val expectedTrackId: Long,
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val quality: StreamingAudioQuality,
    val metadata: StreamingTrack?
)

data class StreamingDownloadResolveRequest(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val metadata: StreamingTrack?
)

data class StreamingSourceSwitchResolveRequest(
    val provider: StreamingProviderName?,
    val providerTrackId: String
)

interface StreamingPreResolvePlanner {
    fun prepareNextPreResolve(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?
    ): StreamingPreResolveRequest?

    fun clearPreResolve(key: String?)
}

interface StreamingPlaybackResolvePlanner : StreamingPreResolvePlanner {
    fun prepare(tracks: List<Track>?, index: Int): ResolveStreamingPlaybackRequest?

    fun replaceResolvedTrack(request: ResolveStreamingPlaybackRequest, resolved: Track): ArrayList<Track>

    fun prepareDownload(track: Track?): StreamingDownloadResolveRequest?

    fun prepareRecovery(
        snapshot: PlaybackStateSnapshot?,
        selectedQuality: StreamingAudioQuality,
        adaptiveQuality: StreamingAudioQuality,
        refuseAutomaticQualityDowngrade: Boolean
    ): StreamingRecoveryRequest?

    fun clearRecovery(key: String?)
}

internal class ResolveStreamingPlaybackUseCase @JvmOverloads constructor(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val preResolveRemainingMs: Long = 45_000L,
    private val preResolveProgress: Float = 0.70f,
    private val preResolveRetryMs: Long = 120_000L,
    private val recoveryCooldownMs: Long = 20_000L,
    private val recoveryWarmupMs: Long = 2_000L,
    private val unresolvedStreamingTrack: (Track?) -> Boolean = StreamingPlaybackAdapter::isUnresolvedStreamingTrack
) : StreamingPlaybackResolvePlanner {
    private var preResolvingKey = ""
    private var lastPreResolveAttemptKey = ""
    private var lastPreResolveAttemptAtMs = 0L
    private var lastPreResolveCurrentTrackId = -1L
    private var recoveryKey = ""
    private var lastRecoveryKey = ""
    private var lastRecoveryAtMs = 0L

    override fun prepare(tracks: List<Track>?, index: Int): ResolveStreamingPlaybackRequest? {
        if (tracks.isNullOrEmpty()) {
            return null
        }
        val safeIndex = index.coerceIn(0, tracks.size - 1)
        val selected = tracks[safeIndex]
        if (!StreamingPlaybackAdapter.isStreamingTrack(selected)) {
            return null
        }
        val provider = StreamingPlaybackAdapter.streamingProviderName(selected.dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(selected.dataPath)
            .takeIf { it.isNotBlank() }
            ?: return null
        return ResolveStreamingPlaybackRequest(
            tracks = tracks,
            index = safeIndex,
            provider = provider,
            providerTrackId = providerTrackId,
            metadata = metadataFor(selected, provider, providerTrackId)
        )
    }

    override fun prepareNextPreResolve(
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track>?
    ): StreamingPreResolveRequest? {
        if (snapshot == null || !snapshot.playing || queue.isNullOrEmpty()) {
            return null
        }
        if (snapshot.queueSize <= 1 || queue.size <= 1) {
            return null
        }
        if (unresolvedStreamingTrack(snapshot.currentTrack)) {
            return null
        }
        val currentTrackId = snapshot.currentTrack?.id ?: -1L
        val earlyWarmup = currentTrackId > 0L && currentTrackId != lastPreResolveCurrentTrackId
        val nearEnd = snapshot.durationMs > 0L && (
            snapshot.durationMs - snapshot.positionMs <= preResolveRemainingMs ||
                snapshot.positionMs / snapshot.durationMs.toFloat() >= preResolveProgress
            )
        if (!earlyWarmup && !nearEnd) {
            return null
        }
        if (earlyWarmup) {
            lastPreResolveCurrentTrackId = currentTrackId
        }
        var nextIndex = snapshot.currentIndex + 1
        if (nextIndex >= queue.size) {
            nextIndex = 0
        }
        if (nextIndex == snapshot.currentIndex || nextIndex < 0 || nextIndex >= queue.size) {
            return null
        }
        val next = queue[nextIndex]
        if (!unresolvedStreamingTrack(next)) {
            return null
        }
        val provider = StreamingPlaybackAdapter.streamingProviderName(next.dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(next.dataPath)
            .takeIf { it.isNotBlank() }
            ?: return null
        val key = "${provider.wireName}:$providerTrackId"
        val now = clockMs()
        if (key == preResolvingKey ||
            (key == lastPreResolveAttemptKey && now - lastPreResolveAttemptAtMs < preResolveRetryMs)
        ) {
            return null
        }
        preResolvingKey = key
        lastPreResolveAttemptKey = key
        lastPreResolveAttemptAtMs = now
        return StreamingPreResolveRequest(
            key = key,
            oldTrackId = next.id,
            provider = provider,
            providerTrackId = providerTrackId,
            metadata = metadataFor(next, provider, providerTrackId)
        )
    }

    override fun clearPreResolve(key: String?) {
        if (key == preResolvingKey) {
            preResolvingKey = ""
        }
    }

    override fun prepareRecovery(
        snapshot: PlaybackStateSnapshot?,
        selectedQuality: StreamingAudioQuality,
        adaptiveQuality: StreamingAudioQuality,
        refuseAutomaticQualityDowngrade: Boolean
    ): StreamingRecoveryRequest? {
        val current = snapshot?.currentTrack ?: return null
        if (!StreamingPlaybackAdapter.isStreamingTrack(current) ||
            unresolvedStreamingTrack(current)
        ) {
            return null
        }
        // Initial source preparation can legitimately buffer at a restored position. Only recover
        // once the source has reached READY (preparing=false) and then stalls after real playback.
        if (snapshot.preparing || snapshot.positionMs < recoveryWarmupMs) {
            return null
        }
        val provider = StreamingPlaybackAdapter.streamingProviderName(current.dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(current.dataPath)
            .takeIf { it.isNotBlank() }
            ?: return null
        val activeQuality = if (adaptiveQuality.ordinal < selectedQuality.ordinal) {
            adaptiveQuality
        } else {
            selectedQuality
        }
        val recoveryQuality = if (refuseAutomaticQualityDowngrade) {
            activeQuality
        } else {
            recoveryQuality(activeQuality)
        }
        val key = "${provider.wireName}:$providerTrackId:${recoveryQuality.name}"
        val now = clockMs()
        if (key == recoveryKey ||
            (key == lastRecoveryKey && now - lastRecoveryAtMs < recoveryCooldownMs)
        ) {
            return null
        }
        recoveryKey = key
        lastRecoveryKey = key
        lastRecoveryAtMs = now
        return StreamingRecoveryRequest(
            key = key,
            expectedTrackId = current.id,
            provider = provider,
            providerTrackId = providerTrackId,
            quality = recoveryQuality,
            metadata = metadataFor(current, provider, providerTrackId)
        )
    }

    override fun clearRecovery(key: String?) {
        if (key == recoveryKey) {
            recoveryKey = ""
        }
    }

    fun recoveryQuality(activeQuality: StreamingAudioQuality): StreamingAudioQuality {
        return when (activeQuality) {
            StreamingAudioQuality.HIRES -> StreamingAudioQuality.LOSSLESS
            StreamingAudioQuality.LOSSLESS -> StreamingAudioQuality.HIGH
            StreamingAudioQuality.HIGH -> StreamingAudioQuality.STANDARD
            StreamingAudioQuality.STANDARD -> StreamingAudioQuality.STANDARD
        }
    }

    override fun replaceResolvedTrack(request: ResolveStreamingPlaybackRequest, resolved: Track): ArrayList<Track> {
        val resolvedTracks = ArrayList(request.tracks)
        resolvedTracks[request.index] = resolved
        return resolvedTracks
    }

    override fun prepareDownload(track: Track?): StreamingDownloadResolveRequest? {
        if (!unresolvedStreamingTrack(track)) {
            return null
        }
        val selected = track ?: return null
        val provider = StreamingPlaybackAdapter.streamingProviderName(selected.dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(selected.dataPath)
            .takeIf { it.isNotBlank() }
            ?: return null
        return StreamingDownloadResolveRequest(
            provider = provider,
            providerTrackId = providerTrackId,
            metadata = metadataFor(selected, provider, providerTrackId)
        )
    }

    fun metadataFor(
        track: Track?,
        provider: StreamingProviderName,
        providerTrackId: String
    ): StreamingTrack? {
        if (track == null) {
            return null
        }
        val primaryCandidate = StreamingPlaybackCandidate(
            provider = provider,
            quality = null,
            label = "${provider.wireName} 播放源",
            providerTrackId = providerTrackId,
            available = true
        )
        val retainedPlaybackCandidates = retainOtherPlaybackCandidates(
            primaryCandidate,
            StreamingPlaybackAdapter.playbackCandidates(track)
        )
        val coverUrl = track.albumArtUri?.toString()
        return StreamingTrack(
            provider = provider,
            providerTrackId = providerTrackId,
            title = track.title,
            artist = track.artist,
            artists = emptyList(),
            album = track.album,
            albumId = null,
            durationMs = track.durationMs,
            coverUrl = coverUrl,
            coverThumbUrl = coverUrl,
            qualities = emptySet(),
            explicit = false,
            playable = true,
            unavailableReason = null,
            description = track.album.takeIf { it.isNotBlank() }?.let { "专辑：$it" },
            lyricSources = listOf(
                StreamingLyricSource(
                    provider = provider,
                    name = "${provider.wireName} 歌词",
                    providerTrackId = providerTrackId,
                    priority = 0
                )
            ),
            playbackCandidates = retainedPlaybackCandidates,
            luoxueMusicInfoJson = StreamingPlaybackAdapter.luoxueMusicInfoJson(track.dataPath)
        )
    }

    /** Identifies a library-backed source that still needs its streaming URL resolved. */
    fun prepareSourceSwitch(track: Track?): StreamingSourceSwitchResolveRequest? {
        if (!unresolvedStreamingTrack(track)) {
            return null
        }
        val selected = track ?: return null
        val provider = StreamingPlaybackAdapter.streamingProviderName(selected.dataPath)
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(selected.dataPath)
            .trim()
        return StreamingSourceSwitchResolveRequest(provider, providerTrackId)
    }

    private fun retainOtherPlaybackCandidates(
        primary: StreamingPlaybackCandidate,
        candidates: List<StreamingPlaybackCandidate>
    ): List<StreamingPlaybackCandidate> {
        val seen = linkedSetOf(playbackCandidateKey(primary))
        return candidates.filter { candidate ->
            candidate.providerTrackId?.isNotBlank() == true && seen.add(playbackCandidateKey(candidate))
        }
    }

    private fun playbackCandidateKey(candidate: StreamingPlaybackCandidate): String {
        return "${candidate.provider.wireName}:${candidate.providerTrackId.orEmpty()}:${candidate.quality?.wireName.orEmpty()}"
    }
}
