package app.yukine.playback.manager

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL
import app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF
import app.yukine.playback.PlaybackRepeatMode.REPEAT_ONE
import app.yukine.model.Track
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.math.pow

internal class PlaybackRuntimeStateManager(
    private val stateProvider: StateProvider
) {
    private var replayGainEnabled = true
    private var playbackSpeed = 1.0f
    private var appVolume = 1.0f
    private var shuffleEnabled = false
    private var repeatMode = REPEAT_ALL
    private var preparing = false
    private var errorMessage = ""
    private var bitPerfectActive = false
    private var lastDeferredAction: String? = null

    interface StateProvider {
        fun player(): ExoPlayer?
        fun playerMirrorsQueue(): Boolean
        fun currentTrack(): Track?
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        if (bitPerfectActive && enabled) {
            // Value is recorded but deferred until Bit-Perfect is disabled.
            lastDeferredAction = "ReplayGain"
        }
        replayGainEnabled = enabled
        applyPlaybackParametersToPlayer()
    }

    fun replayGainEnabled(): Boolean {
        return replayGainEnabled
    }

    fun setPlaybackSpeed(speed: Float) {
        if (bitPerfectActive && speed != 1.0f) {
            // Value is recorded but deferred until Bit-Perfect is disabled.
            lastDeferredAction = "PlaybackSpeed"
        }
        playbackSpeed = normalizePlaybackSpeed(speed)
        applyPlaybackParametersToPlayer()
    }

    fun setAppVolume(volume: Float) {
        if (bitPerfectActive && volume != 1.0f) {
            // Value is recorded but deferred until Bit-Perfect is disabled.
            lastDeferredAction = "AppVolume"
        }
        appVolume = normalizeAppVolume(volume)
        applyPlaybackParametersToPlayer()
    }

    fun playbackSpeed(): Float {
        return playbackSpeed
    }

    fun appVolume(): Float {
        return appVolume
    }

    fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        repeatMode = if (mode == REPEAT_OFF || mode == REPEAT_ONE) mode else REPEAT_ALL
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            REPEAT_ALL -> REPEAT_ONE
            REPEAT_ONE -> REPEAT_OFF
            else -> REPEAT_ALL
        }
    }

    fun shuffleEnabled(): Boolean {
        return shuffleEnabled
    }

    fun repeatMode(): Int {
        return repeatMode
    }

    fun setPreparing(preparing: Boolean) {
        this.preparing = preparing
    }

    fun preparing(): Boolean {
        return preparing
    }

    fun isPlaying(): Boolean {
        return stateProvider.player()?.isPlaying ?: false
    }

    fun setErrorMessage(message: String?) {
        errorMessage = message.orEmpty()
    }

    fun errorMessage(): String {
        return errorMessage
    }

    fun applyPlaybackModeToPlayer() {
        val player = stateProvider.player() ?: return
        player.setShuffleModeEnabled(shuffleEnabled)
        player.setRepeatMode(
            media3RepeatModeForAppRepeatMode(
                repeatMode,
                stateProvider.playerMirrorsQueue()
            )
        )
    }

    fun applyPlaybackModeAndParametersToPlayer() {
        applyPlaybackParametersToPlayer()
        applyPlaybackModeToPlayer()
    }

    fun normalizePlaybackSpeed(speed: Float): Float = when {
        speed < 0.5f -> 0.5f
        speed > 2.0f -> 2.0f
        else -> kotlin.math.round(speed * 100.0f) / 100.0f
    }

    fun normalizeAppVolume(volume: Float): Float = when {
        volume < 0.0f -> 0.0f
        volume > 1.0f -> 1.0f
        else -> kotlin.math.round(volume * 100.0f) / 100.0f
    }

    fun applyPlaybackSpeed() {
        val player = stateProvider.player() ?: return
        player.setPlaybackParameters(PlaybackParameters(playbackSpeed))
    }

    fun applyPlaybackParametersToPlayer() {
        if (bitPerfectActive) {
            // Bit-Perfect mode: lock speed to 1.0x and volume to 1.0 to avoid
            // triggering software mixing/SRC in the audio pipeline.
            applyBitPerfectPlaybackParameters()
            return
        }
        applyPlaybackSpeed()
        applyAppVolume()
    }

    private fun applyBitPerfectPlaybackParameters() {
        val player = stateProvider.player() ?: return
        player.setPlaybackParameters(PlaybackParameters(1.0f))
        player.setVolume(1.0f)
    }

    fun currentTrackVolume(): Float {
        return normalizeAppVolume(
            appVolume * replayGainMultiplierForTrack(
                replayGainEnabled,
                stateProvider.currentTrack()
            )
        )
    }

    fun applyCurrentTrackVolumeToPlayer() {
        val player = stateProvider.player() ?: return
        player.setVolume(currentTrackVolume())
    }

    fun applyAppVolume() {
        applyCurrentTrackVolumeToPlayer()
    }

    /**
     * Sets audio attributes for the player.
     * Media3's internal focus management is permanently disabled (handleAudioFocus=false);
     * focus is fully managed by [NativeAudioFocusController] via native AudioManager APIs.
     */
    fun applyAudioAttributes() {
        val player = stateProvider.player() ?: return
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false // Focus managed by NativeAudioFocusController, not Media3
        )
    }

    fun setBitPerfectActive(active: Boolean) {
        bitPerfectActive = active
        if (!active) {
            lastDeferredAction = null
        }
    }

    fun bitPerfectActive(): Boolean {
        return bitPerfectActive
    }

    /**
     * Returns the last action that was deferred by Bit-Perfect guard, or null if none.
     * Deferred values are stored internally and will apply when Bit-Perfect is disabled.
     */
    fun lastDeferredAction(): String? {
        return if (bitPerfectActive) lastDeferredAction else null
    }

    fun replayGainMultiplierForTrack(enabled: Boolean, track: Track?): Float {
        if (!enabled || track == null) {
            return 1.0f
        }
        val gainDb = if (kotlin.math.abs(track.replayGainTrackDb) > 0.001f) {
            track.replayGainTrackDb
        } else {
            track.replayGainAlbumDb
        }
        if (kotlin.math.abs(gainDb) <= 0.001f) {
            return 1.0f
        }
        return 10.0.pow((gainDb / 20.0).toDouble()).toFloat()
    }

    fun replayGainMultiplierForTrack(track: Track?): Float {
        return replayGainMultiplierForTrack(replayGainEnabled, track)
    }

    companion object {
        @JvmStatic
        fun stateProviderFromPlaybackState(
            playerSupplier: Supplier<ExoPlayer?>?,
            mirroredQueueSupplier: BooleanSupplier?,
            currentTrackSupplier: Supplier<Track?>?
        ): StateProvider = object : StateProvider {
            override fun player(): ExoPlayer? = playerSupplier?.get()

            override fun playerMirrorsQueue(): Boolean = mirroredQueueSupplier?.asBoolean == true

            override fun currentTrack(): Track? = currentTrackSupplier?.get()
        }

        fun media3RepeatModeForAppRepeatMode(appRepeatMode: Int, playerMirrorsQueue: Boolean): Int {
            if (appRepeatMode == REPEAT_ONE) {
                return Player.REPEAT_MODE_ONE
            }
            if (!playerMirrorsQueue) {
                return Player.REPEAT_MODE_OFF
            }
            if (appRepeatMode == REPEAT_OFF) {
                return Player.REPEAT_MODE_OFF
            }
            return Player.REPEAT_MODE_ALL
        }
    }
}
