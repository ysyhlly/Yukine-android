package app.yukine.playback

import app.yukine.StreamingRepositorySource
import app.yukine.diagnostics.DiagnosticLog
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackSource
import app.yukine.streaming.StreamingProviderName
import app.yukine.together.TogetherQueueSource
import app.yukine.together.TogetherStreamingResolverPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves expiring cloud playback URLs on the host device without exposing credentials to Junto.
 *
 * The native callback is synchronous, so the network work is explicitly confined to IO. Junto
 * keeps the logical queue id and validates the original content length before accepting this URL.
 * A hard timeout bounds how long the Go callback mutex can be held when an upstream hangs.
 */
class AppTogetherStreamingResolver(
    private val repositorySource: StreamingRepositorySource
) : TogetherStreamingResolverPort {
    override fun resolve(
        source: TogetherQueueSource.Streaming
    ): TogetherQueueSource.Streaming? = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(RESOLVE_TOTAL_TIMEOUT_MS) {
            val provider = StreamingProviderName.fromWireName(source.provider)
                ?: return@withTimeoutOrNull null
            val quality = StreamingAudioQuality.fromWireName(source.quality)
                ?: StreamingAudioQuality.LOSSLESS
            val result = runCatching {
                repositorySource.current().resolvePlayback(
                    provider = provider,
                    providerTrackId = source.providerTrackId,
                    quality = quality,
                    luoxueMusicInfoJson = source.luoxueMusicInfoJson,
                    forceRefresh = source.resolvedUrl.isNotBlank()
                )
            }
            result.onFailure { error ->
                DiagnosticLog.w(
                    TAG,
                    "Together source resolve failed provider=${provider.wireName} source=${sourceKey(source.providerTrackId)}",
                    error
                )
            }
            result.getOrNull()
                ?.takeIf { it.url.isNotBlank() }
                ?.let { refreshed ->
                    // Skip range probe when size is already verified — keeps Go callback lock short.
                    val sizeBytes = if (
                        source.supportsRange &&
                        source.sizeBytes > 0L &&
                        refreshed.supportsRange
                    ) {
                        source.sizeBytes
                    } else {
                        val probed = TogetherHttpRangeProbe.contentLength(
                            refreshed.url,
                            refreshed.headers
                        )
                        if (probed <= 0L) {
                            DiagnosticLog.w(
                                TAG,
                                "Together range probe unavailable provider=${provider.wireName} " +
                                    "source=${sourceKey(source.providerTrackId)} knownBytes=${source.sizeBytes}"
                            )
                        }
                        probed
                    }
                    resolvedTogetherSource(source, refreshed, sizeBytes)
                }
        }.also { resolved ->
            if (resolved == null) {
                DiagnosticLog.w(
                    TAG,
                    "Together source resolve timed out or failed source=${sourceKey(source.providerTrackId)}"
                )
            }
        }
    }

    private fun sourceKey(providerTrackId: String): String =
        Integer.toUnsignedString(providerTrackId.hashCode(), 16)

    private companion object {
        const val TAG = "TogetherResolver"
        /** Bounds native callback lock hold time (resolve + range probe). */
        const val RESOLVE_TOTAL_TIMEOUT_MS = 12_000L
    }
}

internal fun resolvedTogetherSource(
    source: TogetherQueueSource.Streaming,
    refreshed: StreamingPlaybackSource,
    probedSizeBytes: Long
): TogetherQueueSource.Streaming {
    val reusableSizeBytes = source.sizeBytes.takeIf {
        source.supportsRange && refreshed.supportsRange && it > 0L
    } ?: 0L
    val resolvedSizeBytes = probedSizeBytes.takeIf { it > 0L } ?: reusableSizeBytes
    return source.copy(
        provider = refreshed.provider.wireName,
        providerTrackId = refreshed.providerTrackId,
        resolvedUrl = refreshed.url,
        headers = refreshed.headers,
        expiresAtEpochMs = refreshed.expiresAtEpochMs,
        mimeType = refreshed.mimeType.orEmpty(),
        supportsRange = resolvedSizeBytes > 0L,
        sizeBytes = resolvedSizeBytes
    )
}
