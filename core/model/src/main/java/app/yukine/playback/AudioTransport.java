package app.yukine.playback;

/** The transport that is actually carrying audio, not merely the requested route. */
public enum AudioTransport {
    SYSTEM_STANDARD,
    SYSTEM_OFFLOAD,
    SYSTEM_DIRECT_PCM,
    USB_PCM,
    USB_DOP,
    USB_NATIVE_DSD
}
