package app.yukine.playback

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import app.yukine.model.Track

internal class PlaybackVisualizationAnalyzer(
    private val context: Context,
    private val taskScheduler: PlaybackTaskScheduler,
    private val stateProvider: StateProvider
) {
    interface StateProvider {
        fun isAppVisible(): Boolean
        fun isHttpUri(uri: Uri?): Boolean
        fun cacheDataSourceForTrack(track: Track): CacheDataSource
        fun mediaCacheKeyForTrack(track: Track): String
        fun continuousCachedBytes(cacheKey: String): Long
        fun bufferedProgress(durationMs: Long): Float
        fun contentLengthForCacheKey(cacheKey: String): Long
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

    fun waveformSnapshot(track: Track?, durationMs: Long, deferGeneration: Boolean): PlaybackWaveformSnapshot {
        if (track == null || durationMs <= 0L || !stateProvider.isHttpUri(track.contentUri)) {
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
        if (track == null || durationMs <= 0L || track.contentUri == null || Uri.EMPTY == track.contentUri) {
            return PlaybackSpectrumSnapshot.empty()
        }
        resetWaveformIfTrackChanged(track)
        if (!stateProvider.isAppVisible() || deferGeneration) {
            return spectrumSnapshot
        }
        val cachedProgress = if (stateProvider.isHttpUri(track.contentUri)) {
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
        visualizationWarmupUntilMs = System.currentTimeMillis() + PLAYBACK_VISUALIZATION_WARMUP_MS
    }

    fun shouldDeferPlaybackVisualization(): Boolean {
        return System.currentTimeMillis() < visualizationWarmupUntilMs
    }

    private fun maybeGenerateSpectrum(track: Track, durationMs: Long, cachedProgress: Float, allowQuickStart: Boolean) {
        if (track.contentUri == null || Uri.EMPTY == track.contentUri || cachedProgress <= 0.005f) {
            return
        }
        var targetProgress = cachedProgress
        var quickStart = false
        if (allowQuickStart && !spectrumSnapshot.hasBands()) {
            val quickProgress = if (durationMs <= 0L) cachedProgress else minOf(cachedProgress, SPECTRUM_QUICK_START_MS / durationMs.toFloat())
            targetProgress = maxOf(0.006f, minOf(cachedProgress, quickProgress))
            quickStart = targetProgress + SPECTRUM_PROGRESS_STEP < cachedProgress
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
        val requestedCachedProgress = cachedProgress
        val spectrumQuickStart = quickStart
        val spectrumIsHttp = stateProvider.isHttpUri(track.contentUri)
        val spectrumCacheKey = if (spectrumIsHttp) stateProvider.mediaCacheKeyForTrack(track) else ""
        val spectrumCachedBytes = if (spectrumIsHttp) stateProvider.continuousCachedBytes(spectrumCacheKey) else 0L
        if (spectrumIsHttp && (spectrumCacheKey.isEmpty() || spectrumCachedBytes <= 0L)) {
            spectrumGeneratingKey = ""
            return
        }
        taskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM) {
            val generated = if (spectrumIsHttp) {
                val dataSpec = DataSpec.Builder()
                    .setUri(spectrumTrack.contentUri)
                    .setPosition(0L)
                    .setLength(spectrumCachedBytes)
                    .setKey(spectrumCacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build()
                PlaybackSpectrumGenerator.extract(
                    stateProvider.cacheDataSourceForTrack(spectrumTrack),
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
                spectrumCachedProgress,
                requestedCachedProgress,
                spectrumQuickStart,
                spectrumDurationMs
            )
        }
    }

    private fun applySpectrumResult(
        spectrumTrack: Track,
        taskKey: String,
        generated: PlaybackSpectrumSnapshot?,
        spectrumCachedProgress: Float,
        requestedCachedProgress: Float,
        spectrumQuickStart: Boolean,
        spectrumDurationMs: Long
    ) {
        if (!waveformKey(spectrumTrack).equals(waveformTrackKey) || taskKey != spectrumGeneratingKey) {
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
        if (spectrumQuickStart && requestedCachedProgress > spectrumCachedProgress + SPECTRUM_PROGRESS_STEP) {
            maybeGenerateSpectrum(spectrumTrack, spectrumDurationMs, requestedCachedProgress, false)
        }
    }

    private fun maybeGenerateStreamingWaveform(track: Track, durationMs: Long, cachedProgress: Float) {
        val cacheKey = stateProvider.mediaCacheKeyForTrack(track)
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
        val cachedBytes = stateProvider.continuousCachedBytes(cacheKey)
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
        taskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM) {
            val generated = StreamingWaveformGenerator.extract(
                context,
                stateProvider.cacheDataSourceForTrack(waveformTrack),
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
        }
    }

    private fun applyWaveformResult(
        waveformTrack: Track,
        taskKey: String,
        generated: PlaybackWaveformSnapshot?,
        waveformCachedProgress: Float,
        targetGeneratedBars: Int
    ) {
        if (!waveformKey(waveformTrack).equals(waveformTrackKey) || taskKey != waveformGeneratingKey) {
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
        val cacheKey = stateProvider.mediaCacheKeyForTrack(track)
        val cachedBytes = stateProvider.continuousCachedBytes(cacheKey)
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
        return stateProvider.contentLengthForCacheKey(cacheKey)
    }

    private fun waveformKey(track: Track?): String {
        if (track == null) {
            return ""
        }
        return track.id.toString() + "|" + (track.contentUri?.toString() ?: "") + "|" + track.dataPath
    }

    companion object {
        private const val PLAYBACK_VISUALIZATION_WARMUP_MS = 1200L
        private const val SPECTRUM_QUICK_START_MS = 800L
        private const val SPECTRUM_PROGRESS_STEP = 0.08f
        private const val WAVEFORM_PROGRESS_STEP = 0.08f
        private const val WAVEFORM_BAR_COUNT = 48
    }
}
