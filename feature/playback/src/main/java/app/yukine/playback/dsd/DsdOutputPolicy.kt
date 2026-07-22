package app.yukine.playback.dsd

import app.yukine.playback.AudioFallbackReason

internal enum class DsdTransportDecision { XMOS_NATIVE, DOP, ERROR }

internal data class NativeDsdProfile(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val lsbFirst: Boolean,
    val supportedDsdRates: Set<Int>,
    val verified: Boolean
)

internal data class DsdOutputDecision(
    val transport: DsdTransportDecision,
    val profile: NativeDsdProfile? = null,
    val fallbackReason: AudioFallbackReason = AudioFallbackReason.NONE
)

internal object DsdOutputPolicy {
    // Reference shape for an XMOS implementation. It deliberately remains unverified and is not
    // selected until a retail DAC's exact VID/PID, alternate setting and wire format are certified.
    val XMOS_REFERENCE = NativeDsdProfile(
        name = "XMOS reference (gated)",
        vendorId = 0x20B1,
        productId = 0x000A,
        interfaceNumber = 0,
        alternateSetting = 0,
        lsbFirst = true,
        supportedDsdRates = setOf(64, 128, 256, 512),
        verified = false
    )

    fun decide(
        bitPerfectRequested: Boolean,
        usbExclusiveRequested: Boolean,
        vendorId: Int,
        productId: Int,
        dsdRate: Int,
        dopPcmRates: Set<Int>,
        profiles: List<NativeDsdProfile> = listOf(XMOS_REFERENCE)
    ): DsdOutputDecision {
        if (!bitPerfectRequested || !usbExclusiveRequested) {
            return DsdOutputDecision(DsdTransportDecision.ERROR, fallbackReason = AudioFallbackReason.FORMAT_UNSUPPORTED)
        }
        val native = profiles.firstOrNull {
            it.verified && it.vendorId == vendorId && it.productId == productId && dsdRate in it.supportedDsdRates
        }
        if (native != null) return DsdOutputDecision(DsdTransportDecision.XMOS_NATIVE, native)
        val dopRate = 44_100 * dsdRate / 16
        if (dopRate in dopPcmRates) return DsdOutputDecision(DsdTransportDecision.DOP)
        return DsdOutputDecision(
            DsdTransportDecision.ERROR,
            fallbackReason = if (profiles.any { it.vendorId == vendorId && it.productId == productId }) {
                AudioFallbackReason.NATIVE_DSD_PROFILE_MISSING
            } else {
                AudioFallbackReason.DOP_UNSUPPORTED
            }
        )
    }
}
