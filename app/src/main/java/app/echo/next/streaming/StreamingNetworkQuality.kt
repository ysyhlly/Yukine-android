package app.echo.next.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Picks a [StreamingAudioQuality] that fits the device's current network so playback starts fast
 * and doesn't stall on weak links. Lossless/hi-res FLAC can be several MB per song; on a metered or
 * slow cellular connection that means a long buffer before the first note. We step the quality down
 * in those cases and only request the highest tiers on unmetered, validated Wi-Fi/Ethernet.
 *
 * This is intentionally conservative and side-effect free: if anything is unknown we fall back to a
 * safe middle tier rather than guessing high.
 */
object StreamingNetworkQuality {

    /**
     * @param ceiling the best quality the caller would ever want (e.g. the user's preference).
     *                The result never exceeds this.
     */
    @JvmStatic
    @JvmOverloads
    fun preferredQuality(
        context: Context,
        ceiling: StreamingAudioQuality = StreamingAudioQuality.LOSSLESS
    ): StreamingAudioQuality {
        val suggestion = suggestFromNetwork(context)
        // Quality tiers ascend by ordinal (STANDARD < HIGH < LOSSLESS < HIRES); never exceed the
        // caller's ceiling.
        return if (suggestion.ordinal <= ceiling.ordinal) suggestion else ceiling
    }

    private fun suggestFromNetwork(context: Context): StreamingAudioQuality {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return StreamingAudioQuality.HIGH
        val network = cm.activeNetwork ?: return StreamingAudioQuality.STANDARD
        val caps = cm.getNetworkCapabilities(network) ?: return StreamingAudioQuality.STANDARD

        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val onWifiOrEthernet =
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val onCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        return when {
            // Fast, unmetered, validated wired/Wi-Fi → let the caller's ceiling stand (lossless+).
            onWifiOrEthernet && unmetered && validated -> StreamingAudioQuality.LOSSLESS
            // Wi-Fi but metered (hotspot) → high, not lossless, to limit data + buffer time.
            onWifiOrEthernet -> StreamingAudioQuality.HIGH
            // Cellular: prefer high only if the link reports a healthy downstream, else standard.
            onCellular -> if (downstreamKbps(caps) >= GOOD_CELL_KBPS) {
                StreamingAudioQuality.HIGH
            } else {
                StreamingAudioQuality.STANDARD
            }
            else -> StreamingAudioQuality.STANDARD
        }
    }

    private fun downstreamKbps(caps: NetworkCapabilities): Int = caps.linkDownstreamBandwidthKbps

    // ~3 Mbps: comfortably above a 320kbps stream plus headroom for a quick initial buffer.
    private const val GOOD_CELL_KBPS = 3000
}
