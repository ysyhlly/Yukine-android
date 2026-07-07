package app.yukine.playback

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackMediaSourceProvider

@OptIn(UnstableApi::class)
internal class PlaybackVisualizationAnalyzer internal constructor(
    private val context: Context,
    private val taskScheduler: VisualizationTaskScheduler,
    private val stateProvider: StateProvider,
    private val mediaSourceProvider: PlaybackMediaSourceProvider
) {
    constructor(
        context: Context,
        taskScheduler: PlaybackTaskScheduler,
        stateProvider: StateProvider,
        mediaSourceProvider: PlaybackMediaSourceProvider
    ) : this(
        context,
        VisualizationTaskScheduler { priority, task -> taskScheduler.schedule(priority, task) },
        stateProvider,
        mediaSourceProvider
    )

    fun interface VisualizationTaskScheduler {
        fun schedule(priority: PlaybackTaskScheduler.Priority, task: Runnable)
    }

    interface StateProvider {
        fun isAppVisible(): Boolean
        fun bufferedProgress(durationMs: Long): Float
        fun publishState()
    }

    private var waveformTrackKey = ""
    private var waveformGeneratingKey = ""
    private var waveformGeneratedProgress = 0.0f
    private var waveformGeneratedBarCount = 0
    private var waveformSnapshot = PlaybackWaveformSnapshot.empty()
    private var spectrumGeneratingKey = ""
    private var spectrumGeneratedProgress = 0.0f
    private var spectrumSnapshot = PlaybackSpectrumSnapshot.empty()
    private var visualizationWarmupUntilMs = 0L
    private var released = false

    fun waveformSnapshot(track: Track?, durationMs: Long, deferGeneration: Boolean): PlaybackWaveformSnapshot {
        if (released || track == null || durationMs <= 0L || !mediaSourceProvider.isHttpTrack(track) || isStreamingTrack(track)) {
            return PlaybackWaveformSnapshot.empty()
        }
        resetWaveformIfTrackChanged(track)
        if (!stateProvider.isAppVisible() || deferGeneration) {
            return waveformSnapshot
        }
        val cachedProgress = visualizationCachedProgress(track, durationMs)
        var current = waveformSnapshot
        if (cachedProgress > current.cachedProgress && !current.hasBars()) {
            current = PlaybackWaveformSnapshot(current.bars, current.generatedBars, cachedProgress)
            waveformSnapshot = current
        }
        maybeGenerateStreamingWaveform(track, durationMs, cachedProgress)
        return current
    }

    fun spectrumSnapshot(track: Track?, durationMs: Long, deferGeneration: Boolean): PlaybackSpectrumSnapshot {
        if (released || track == null || durationMs <= 0L || !PlaybackMediaSourceProvider.hasPlayableMediaUri(track) || isStreamingTrack(track)) {
            return PlaybackSpectrumSnapshot.empty()
        }
        resetWaveformIfTrackChanged(track)
        if (!stateProvider.isAppVisible() || deferGeneration) {
            return spectrumSnapshot
        }
        val cachedProgress = if (mediaSourceProvider.isHttpTrack(track)) {
            visualizationCachedProgress(track, durationMs)
        } else {
            1.0f
        }
        var current = spectrumSnapshot
        if (cachedProgress > current.cachedProgress && !current.hasBands()) {
            current = PlaybackSpectrumSnapshot(current.bands, current.generatedFrames, current.bandCount, cachedProgress)
            spectrumSnapshot = current
        }
        maybeGenerateSpectrum(track, durationMs, cachedProgress, true)
        return current
    }

    fun resetWaveformIfTrackChanged(track: Track?) {
        if (released) {
            return
        }
        val key = waveformKey(track)
        if (key == waveformTrackKey) {
            return
        }
        waveformTrackKey = key
        waveformGeneratingKey = ""
        waveformGeneratedProgress = 0.0f
        waveformGeneratedBarCount = 0
        waveformSnapshot = PlaybackWaveformSnapshot.empty()
        spectrumGeneratingKey = ""
        spectrumGeneratedProgress = 0.0f
        spectrumSnapshot = PlaybackSpectrumSnapshot.empty()
    }

    fun postponePlaybackVisualizationWarmup() {
        if (released) {
            return
        }
        visualizationWarmupUntilMs = System.currentTimeMillis() + PLAYBACK_VISUALIZATION_WARMUP_MS
    }

    fun shouldDeferPlaybackVisualization(): Boolean {
        return !released && System.currentTimeMillis() < visualizationWarmupUntilMs
    }

    fun release() {
        released = true
        waveformGeneratingKey = ""
        spectrumGeneratingKey = ""
        visualizationWarmupUntilMs = 0L
    }

    private fun maybeGenerateSpectrum(track: Track, durationMs: Long, cachedProgress: Float, allowQuickStart: Boolean) {
        if (released || !PlaybackMediaSourceProvider.hasPlayableMediaUri(track) || cachedProgress <= 0.005f) {
            return
        }
        var targetProgress = minOf(cachedProgress, spectrumDecodeProgressLimit(durationMs))
        if (allowQuickStart && !spectrumSnapshot.hasBands()) {
            val quickProgress = if (durationMs <= 0L) targetProgress else minOf(targetProgress, SPECTRUM_QUICK_START_MS / durationMs.toFloat())
            targetProgress = maxOf(0.006f, minOf(cachedProgress, quickProgress))
        }
        val targetGeneratedFrames = maxOf(
            1,
            minOf(
                PlaybackSpectrumGenerator.FRAME_COUNT,
                kotlin.math.ceil(PlaybackSpectrumGenerator.FRAME_COUNT * minOf(1.0f, targetProgress)).toInt()
            )
        )
        if (spectrumSnapshot.hasBands()
            && targetGeneratedFrames <= spectrumSnapshot.generatedFrames
            && targetProgress - spectrumGeneratedProgress < SPECTRUM_PROGRESS_STEP
        ) {
            return
        }
        val taskKey = waveformKey(track) + "|spectrum|" + targetGeneratedFrames
        if (taskKey == spectrumGeneratingKey) {
            return
        }
        spectrumGeneratingKey = taskKey
        val spectrumTrack = track
        val spectrumDurationMs = durationMs
        val spectrumCachedProgress = targetProgress
        val spectrumIsHttp = mediaSourceProvider.isHttpTrack(track)
        val spectrumCacheKey = if (spectrumIsHttp) mediaSourceProvider.mediaCacheKeyForTrack(track) else ""
        val spectrumCachedBytes = if (spectrumIsHttp) mediaSourceProvider.continuousCachedBytes(spectrumCacheKey) else 0L
        if (spectrumIsHttp && (spectrumCacheKey.isEmpty() || spectrumCachedBytes <= 0L)) {
            spectrumGeneratingKey = ""
            return
        }
        taskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, Runnable {
            if (released) {
                return@Runnable
            }
            val generated = if (spectrumIsHttp) {
                val dataSpec = DataSpec.Builder()
                    .setUri(spectrumTrack.contentUri)
                    .setPosition(0L)
                    .setLength(spectrumCachedBytes)
                    .setKey(spectrumCacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build()
                PlaybackSpectrumGenerator.extract(
                    mediaSourceProvider.cacheDataSourceForTrack(spectrumTrack),
                    dataSpec,
                    spectrumDurationMs,
                    spectrumCachedProgress
                )
            } else {
                PlaybackSpectrumGenerator.extract(
                    context,
                    spectrumTrack.contentUri,
                    spectrumDurationMs,
                    spectrumCachedProgress
                )
            }
            applySpectrumResult(
                spectrumTrack,
                taskKey,
                generated,
                spectrumCachedProgress
            )
        })
    }

    private fun applySpectrumResult(
        spectrumTrack: Track,
        taskKey: String,
        generated: PlaybackSpectrumSnapshot?,
        spectrumCachedProgress: Float
    ) {
        if (released || !waveformKey(spectrumTrack).equals(waveformTrackKey) || taskKey != spectrumGeneratingKey) {
            return
        }
        spectrumGeneratingKey = ""
        spectrumGeneratedProgress = maxOf(spectrumGeneratedProgress, spectrumCachedProgress)
        if (generated != null && generated.hasBands()
            && generated.generatedFrames >= spectrumSnapshot.generatedFrames
        ) {
            spectrumSnapshot = generated
        } else if (spectrumCachedProgress > spectrumSnapshot.cachedProgress) {
            spectrumSnapshot = PlaybackSpectrumSnapshot(
                spectrumSnapshot.bands,
                spectrumSnapshot.generatedFrames,
                spectrumSnapshot.bandCount,
                spectrumCachedProgress
            )
        }
        stateProvider.publishState()
    }

    private fun maybeGenerateStreamingWaveform(track: Track, durationMs: Long, cachedProgress: Float) {
        if (released) {
            return
        }
        val cacheKey = mediaSourceProvider.mediaCacheKeyForTrack(track)
        if (cacheKey.isEmpty() || cachedProgress <= 0.005f) {
            return
        }
        val targetGeneratedBars = maxOf(
            1,
            minOf(
                WAVEFORM_BAR_COUNT,
                kotlin.math.ceil(WAVEFORM_BAR_COUNT * minOf(1.0f, cachedProgress)).toInt()
            )
        )
        val currentGeneratedBars = maxOf(waveformGeneratedBarCount, waveformSnapshot.generatedBars)
        if (targetGeneratedBars <= currentGeneratedBars && waveformSnapshot.hasBars()) {
            return
        }
        if (cachedProgress - waveformGeneratedProgress < WAVEFORM_PROGRESS_STEP
            && targetGeneratedBars <= currentGeneratedBars + 1
            && waveformSnapshot.hasBars()
        ) {
            return
        }
        val cachedBytes = mediaSourceProvider.continuousCachedBytes(cacheKey)
        if (cachedBytes <= 0L) {
            return
        }
        val taskKey = waveformKey(track) + "|" + targetGeneratedBars
        if (taskKey == waveformGeneratingKey) {
            return
        }
        waveformGeneratingKey = taskKey
        val waveformTrack = track
        val waveformDurationMs = durationMs
        val waveformCachedProgress = cachedProgress
        val waveformCachedBytes = cachedBytes
        taskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, Runnable {
            if (released) {
                return@Runnable
            }
            val generated = StreamingWaveformGenerator.extract(
                context,
                mediaSourceProvider.cacheDataSourceForTrack(waveformTrack),
                DataSpec.Builder()
                    .setUri(waveformTrack.contentUri)
                    .setPosition(0L)
                    .setLength(waveformCachedBytes)
                    .setKey(cacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build(),
                waveformDurationMs,
                waveformCachedProgress,
                cacheKey
            )
            applyWaveformResult(
                waveformTrack,
                taskKey,
                generated,
                waveformCachedProgress,
                targetGeneratedBars
            )
        })
    }

    private fun applyWaveformResult(
        waveformTrack: Track,
        taskKey: String,
        generated: PlaybackWaveformSnapshot?,
        waveformCachedProgress: Float,
        targetGeneratedBars: Int
    ) {
        if (released || !waveformKey(waveformTrack).equals(waveformTrackKey) || taskKey != waveformGeneratingKey) {
            return
        }
        waveformGeneratingKey = ""
        waveformGeneratedProgress = waveformCachedProgress
        waveformGeneratedBarCount = maxOf(waveformGeneratedBarCount, targetGeneratedBars)
        waveformSnapshot = PlaybackWaveformMergePolicy.merge(
            waveformSnapshot,
            generated,
            waveformCachedProgress
        )
        stateProvider.publishState()
    }

    private fun visualizationCachedProgress(track: Track, durationMs: Long): Float {
        if (durationMs <= 0L) {
            return 0.0f
        }
        val cacheKey = mediaSourceProvider.mediaCacheKeyForTrack(track)
        val cachedBytes = mediaSourceProvider.continuousCachedBytes(cacheKey)
        val contentLength = contentLengthForCacheKey(cacheKey)
        val byteProgress = if (contentLength > 0L && cachedBytes > 0L) {
            maxOf(0.0f, minOf(1.0f, cachedBytes / contentLength.toFloat()))
        } else {
            0.0f
        }
        return maxOf(stateProvider.bufferedProgress(durationMs), byteProgress)
    }

    private fun contentLengthForCacheKey(cacheKey: String): Long {
        if (cacheKey.isEmpty()) {
            return -1L
        }
        return mediaSourceProvider.contentLengthForCacheKey(cacheKey)
    }

    private fun waveformKey(track: Track?): String {
        if (track == null) {
            return ""
        }
        return track.id.toString() + "|" + (track.contentUri?.toString() ?: "") + "|" + track.dataPath
    }

    private fun isStreamingTrack(track: Track?): Boolean {
        return track?.dataPath?.startsWith("streaming:") == true
    }

    companion object {
        private const val PLAYBACK_VISUALIZATION_WARMUP_MS = 1200L
        private const val SPECTRUM_QUICK_START_MS = 800L
        private const val SPECTRUM_MAX_BACKGROUND_DECODE_MS = 45_000L
        private const val SPECTRUM_MAX_BACKGROUND_PROGRESS = 0.25f
        private const val SPECTRUM_PROGRESS_STEP = 0.08f
        private const val WAVEFORM_PROGRESS_STEP = 0.08f
        private const val WAVEFORM_BAR_COUNT = 48
    }

    private fun spectrumDecodeProgressLimit(durationMs: Long): Float {
        if (durationMs <= SPECTRUM_MAX_BACKGROUND_DECODE_MS) {
            return 1.0f
        }
        return maxOf(
            0.006f,
            minOf(
                SPECTRUM_MAX_BACKGROUND_PROGRESS,
                SPECTRUM_MAX_BACKGROUND_DECODE_MS / durationMs.toFloat()
            )
        )
    }
}
