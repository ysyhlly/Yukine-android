package app.yukine.playback.manager

/**
 * Runtime guard that enforces Bit-Perfect constraints.
 *
 * When Bit-Perfect output is active (HARDWARE_OFFLOAD or DIRECT_PCM), audio effects
 * (EQ, BassBoost, Virtualizer), playback speed changes, and ReplayGain are incompatible
 * because they require software processing that breaks the bit-perfect signal path.
 *
 * This guard provides a single point of truth for whether such operations are allowed.
 */
internal class BitPerfectGuard(private val modeProvider: () -> AudioOutputMode) {

    val isActive: Boolean get() = modeProvider() != AudioOutputMode.STANDARD

    fun canApplyEqualizer(): Boolean = !isActive

    fun canApplyBassBoost(): Boolean = !isActive

    fun canChangePlaybackSpeed(): Boolean = !isActive

    fun canApplyReplayGain(): Boolean = !isActive

    /**
     * Returns a human-readable reason why effects are blocked, or null if not blocked.
     */
    fun blockedReason(): String? =
        if (isActive) "Bit-Perfect output active — effects disabled to preserve audio integrity"
        else null
}
