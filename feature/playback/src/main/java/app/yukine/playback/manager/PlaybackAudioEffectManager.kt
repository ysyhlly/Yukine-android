package app.yukine.playback.manager

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import app.yukine.playback.AudioEffectSettings
import androidx.media3.exoplayer.ExoPlayer
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal class PlaybackAudioEffectManager(
    private val logTag: String
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bitPerfectGuard: BitPerfectGuard? = null

    fun setBitPerfectGuard(guard: BitPerfectGuard?) {
        bitPerfectGuard = guard
    }

    /**
     * Called when Bit-Perfect state changes at runtime.
     * When activated, releases all audio effects immediately.
     */
    fun onBitPerfectStateChanged(active: Boolean) {
        if (active) {
            release()
        }
    }

    fun bind(player: ExoPlayer?, settings: AudioEffectSettings?) {
        release()
        // Bit-Perfect guard: effects are incompatible with offload/direct PCM output.
        if (bitPerfectGuard?.isActive == true) {
            return
        }
        if (player == null || settings == null || !settings.enabled) {
            return
        }
        val sessionId = try {
            player.audioSessionId
        } catch (error: IllegalStateException) {
            Log.w(logTag, "Unable to read audio session for effects", error)
            return
        }
        if (sessionId == 0) {
            return
        }
        try {
            equalizer = Equalizer(0, sessionId)
            applyEqualizerSettings(settings)
        } catch (error: RuntimeException) {
            Log.w(logTag, "Equalizer unavailable", error)
            equalizer = null
        }
        try {
            bassBoost = BassBoost(0, sessionId)
            bassBoost?.setStrength(settings.bassBoostStrength)
            bassBoost?.setEnabled(settings.bassBoostStrength > 0)
        } catch (error: RuntimeException) {
            Log.w(logTag, "BassBoost unavailable", error)
            bassBoost = null
        }
        try {
            virtualizer = Virtualizer(0, sessionId)
            virtualizer?.setStrength(settings.virtualizerStrength)
            virtualizer?.setEnabled(settings.virtualizerStrength > 0)
        } catch (error: RuntimeException) {
            Log.w(logTag, "Virtualizer unavailable", error)
            virtualizer = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain(settings.loudnessGainMb)
                loudnessEnhancer?.setEnabled(settings.loudnessGainMb != 0)
            } catch (error: RuntimeException) {
                Log.w(logTag, "LoudnessEnhancer unavailable", error)
                loudnessEnhancer = null
            }
        }
    }

    fun release() {
        release(equalizer)
        equalizer = null
        release(bassBoost)
        bassBoost = null
        release(virtualizer)
        virtualizer = null
        release(loudnessEnhancer)
        loudnessEnhancer = null
    }

    private fun applyEqualizerSettings(settings: AudioEffectSettings) {
        val currentEqualizer = equalizer ?: return
        val presetCount = currentEqualizer.numberOfPresets
        if (settings.preset >= 0 && settings.preset < presetCount) {
            currentEqualizer.usePreset(settings.preset.toShort())
            return
        }
        val bands = currentEqualizer.numberOfBands
        val range = currentEqualizer.bandLevelRange
        val min = if (range.isNotEmpty()) range[0] else -1500
        val max = if (range.size > 1) range[1] else 1500
        for (band in 0 until bands.toInt()) {
            if (band >= settings.bandLevels.size) {
                break
            }
            var level = settings.bandLevels[band]
            if (level < min) {
                level = min
            } else if (level > max) {
                level = max
            }
            currentEqualizer.setBandLevel(band.toShort(), level)
        }
        currentEqualizer.setEnabled(settings.enabled)
    }

    private fun release(effect: Any?) {
        try {
            when (effect) {
                is Equalizer -> effect.release()
                is BassBoost -> effect.release()
                is Virtualizer -> effect.release()
                is LoudnessEnhancer -> effect.release()
            }
        } catch (_: RuntimeException) {
        }
    }
}
